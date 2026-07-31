package com.fredhappyface.ewesticker.utilities

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.Rect
import com.elvishew.xlog.XLog
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/** Byte offset of the frame duration within an ANMF payload, after four 24-bit x/y/w/h fields */
private const val ANMF_DURATION_OFFSET = 12

/** Length of the fixed part of an ANMF payload, before its own sub-chunks begin */
private const val ANMF_HEADER_SIZE = 16

/** ANMF flag bit meaning "overwrite the canvas rectangle" rather than alpha-blend onto it */
private const val FLAG_DO_NOT_BLEND = 0x02

/** ANMF flag bit meaning "clear the canvas rectangle once this frame has been shown" */
private const val FLAG_DISPOSE_TO_BACKGROUND = 0x01

/** Payload length of a VP8X chunk: 4 flag bytes then two 24-bit canvas dimensions */
private const val VP8X_PAYLOAD_SIZE = 10

/** VP8X flag bit declaring that the image carries an alpha channel */
private const val VP8X_FLAG_ALPHA = 0x10

/**
 * Reads the frames of an animated webp.
 *
 * The platform can play an animated webp back as an AnimatedImageDrawable but will not hand over
 * individual frames, and re-encoding a sticker as a GIF needs every frame. Rather than bundling a
 * second copy of libwebp to get at them, this walks the webp's RIFF container itself and re-wraps
 * each ANMF frame as a standalone webp, which BitmapFactory decodes with the platform's own
 * decoder. Frames are then composited onto a canvas following the blend and disposal rules from the
 * container, so what a caller sees is what a viewer would see.
 *
 * @property width Int canvas width in pixels
 * @property height Int canvas height in pixels
 * @property durationsMs List<Int> display duration of each frame in milliseconds
 */
class WebpAnimation private constructor(
	private val data: ByteArray,
	val width: Int,
	val height: Int,
	private val frames: List<Frame>,
) {
	private class Frame(
		val x: Int,
		val y: Int,
		val width: Int,
		val height: Int,
		val durationMs: Int,
		val doNotBlend: Boolean,
		val disposeToBackground: Boolean,
		val payload: ByteArray,
	)

	val frameCount: Int
		get() = frames.size

	val durationsMs: List<Int>
		get() = frames.map { it.durationMs }

	/**
	 * Composite and hand over each frame in turn. The same bitmap is reused across the whole
	 * animation, so a consumer that needs to keep a frame must copy it.
	 *
	 * @param onFrame (Int, Bitmap) -> Unit called with the index and the composited canvas
	 * @return Boolean false if any frame failed to decode
	 */
	fun forEachFrame(onFrame: (Int, Bitmap) -> Unit): Boolean {
		val canvasBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
		val canvas = Canvas(canvasBitmap)
		try {
			frames.forEachIndexed { index, frame ->
				val decoded = BitmapFactory.decodeByteArray(frame.payload, 0, frame.payload.size)
				if (decoded == null) {
					XLog.e("Could not decode frame $index of an animated webp")
					return false
				}
				val target = Rect(frame.x, frame.y, frame.x + frame.width, frame.y + frame.height)
				// "Do not blend" replaces the rectangle outright, so the area under it has to go
				// first; otherwise the new frame is composited over what is already there.
				if (frame.doNotBlend) {
					canvas.save()
					canvas.clipRect(target)
					canvas.drawColor(0, PorterDuff.Mode.CLEAR)
					canvas.restore()
				}
				canvas.drawBitmap(decoded, null, target, null)
				decoded.recycle()

				onFrame(index, canvasBitmap)

				if (frame.disposeToBackground) {
					canvas.save()
					canvas.clipRect(target)
					canvas.drawColor(0, PorterDuff.Mode.CLEAR)
					canvas.restore()
				}
			}
		} finally {
			canvasBitmap.recycle()
		}
		return true
	}

	companion object {
		/**
		 * Is this file an animated webp? An animated webp declares an ANIM chunk immediately after
		 * VP8X, so this only reads the start of the file rather than decoding it.
		 *
		 * @param file File the file to test
		 * @return Boolean true if the file is an animated webp
		 */
		fun isAnimated(file: File): Boolean {
			if (!file.name.endsWith(".webp", ignoreCase = true)) {
				return false
			}
			// Walks the chunk headers, skipping over payloads rather than reading them. Searching a
			// fixed-size prefix for "ANIM" was cheaper but wrong: anything sizeable sitting between
			// VP8X and ANIM -- an ICC profile, say -- pushes the marker out of the window, and the
			// sticker is then treated as still and never gets a GIF version.
			return try {
				RandomAccessFile(file, "r").use { raf ->
					if (raf.length() < 12) {
						return false
					}
					val header = ByteArray(12)
					raf.readFully(header)
					if (String(header, 0, 4, Charsets.ISO_8859_1) != "RIFF" ||
						String(header, 8, 4, Charsets.ISO_8859_1) != "WEBP"
					) {
						return false
					}
					var position = 12L
					val end = raf.length()
					val chunk = ByteArray(8)
					while (position + 8 <= end) {
						raf.seek(position)
						raf.readFully(chunk)
						val fourcc = String(chunk, 0, 4, Charsets.ISO_8859_1)
						if (fourcc == "ANIM" || fourcc == "ANMF") {
							return true
						}
						val size = (chunk[4].toLong() and 0xFF) or
							((chunk[5].toLong() and 0xFF) shl 8) or
							((chunk[6].toLong() and 0xFF) shl 16) or
							((chunk[7].toLong() and 0xFF) shl 24)
						if (size < 0) {
							return false
						}
						position += 8 + size + (size and 1)
					}
					false
				}
			} catch (e: IOException) {
				XLog.e("Could not read the header of '${file.name}'")
				XLog.e(e)
				false
			}
		}

		/**
		 * Read an animated webp, or return null if the file is not one. A still webp has no ANMF
		 * chunks and is not an error, just not animated.
		 *
		 * @param file File the webp to read
		 * @return WebpAnimation? the animation, or null if the file holds no animation
		 */
		fun read(file: File): WebpAnimation? {
			val data = try {
				file.readBytes()
			} catch (e: IOException) {
				XLog.e("Could not read '${file.name}'")
				XLog.e(e)
				return null
			}
			if (data.size < 16 || !startsWith(data, 0, "RIFF") || !startsWith(data, 8, "WEBP")) {
				return null
			}

			var canvasWidth = 0
			var canvasHeight = 0
			val frames = mutableListOf<Frame>()
			forEachChunk(data, 12, riffEnd(data)) { fourcc, offset, size ->
				when (fourcc) {
					"VP8X" -> if (size >= VP8X_PAYLOAD_SIZE) {
						canvasWidth = read24(data, offset + 4) + 1
						canvasHeight = read24(data, offset + 7) + 1
					}

					"ANMF" -> if (size >= ANMF_HEADER_SIZE) {
						readFrame(data, offset, size)?.let { frames.add(it) }
					}
				}
			}

			if (frames.isEmpty() || canvasWidth <= 0 || canvasHeight <= 0) {
				return null
			}
			return WebpAnimation(data, canvasWidth, canvasHeight, frames)
		}

		private fun readFrame(data: ByteArray, offset: Int, size: Int): Frame? {
			// Frame offsets are stored divided by two, so they are always even.
			val x = read24(data, offset) * 2
			val y = read24(data, offset + 3) * 2
			val width = read24(data, offset + 6) + 1
			val height = read24(data, offset + 9) + 1
			val durationMs = read24(data, offset + ANMF_DURATION_OFFSET)
			val flags = data[offset + 15].toInt()

			var alpha: IntArray? = null
			var image: IntArray? = null
			var lossless: IntArray? = null
			forEachChunk(data, offset + ANMF_HEADER_SIZE, offset + size) { fourcc, at, len ->
				when (fourcc) {
					"ALPH" -> alpha = intArrayOf(at, len)
					"VP8 " -> image = intArrayOf(at, len)
					"VP8L" -> lossless = intArrayOf(at, len)
				}
			}

			val payload = buildFrameWebp(data, width, height, alpha, image, lossless) ?: return null
			return Frame(
				x,
				y,
				width,
				height,
				durationMs,
				flags and FLAG_DO_NOT_BLEND != 0,
				flags and FLAG_DISPOSE_TO_BACKGROUND != 0,
				payload,
			)
		}

		/**
		 * Wrap a single frame's compressed data in a RIFF container so that it becomes an ordinary
		 * one-image webp file. Lossless frames carry their own alpha; a lossy frame with alpha needs
		 * a VP8X header declaring it, followed by the alpha and colour chunks in that order.
		 */
		private fun buildFrameWebp(
			data: ByteArray,
			width: Int,
			height: Int,
			alpha: IntArray?,
			image: IntArray?,
			lossless: IntArray?,
		): ByteArray? {
			val body = ByteArrayBuilder()
			when {
				lossless != null -> body.chunk("VP8L", data, lossless[0], lossless[1])

				image == null -> return null

				alpha != null -> {
					val header = ByteArrayBuilder()
					header.byte(VP8X_FLAG_ALPHA)
					header.byte(0).byte(0).byte(0)
					header.int24(width - 1)
					header.int24(height - 1)
					body.chunk("VP8X", header.toByteArray(), 0, VP8X_PAYLOAD_SIZE)
					body.chunk("ALPH", data, alpha[0], alpha[1])
					body.chunk("VP8 ", data, image[0], image[1])
				}

				else -> body.chunk("VP8 ", data, image[0], image[1])
			}

			val bodyBytes = body.toByteArray()
			val file = ByteArrayBuilder()
			file.ascii("RIFF")
			file.int32(4 + bodyBytes.size)
			file.ascii("WEBP")
			file.bytes(bodyBytes, 0, bodyBytes.size)
			return file.toByteArray()
		}

		/** Walk the RIFF chunks between two offsets, honouring the pad byte on odd lengths */
		private inline fun forEachChunk(
			data: ByteArray,
			from: Int,
			to: Int,
			action: (String, Int, Int) -> Unit,
		) {
			var pos = from
			while (pos + 8 <= to) {
				val fourcc = String(data, pos, 4, Charsets.ISO_8859_1)
				val size = readInt32(data, pos + 4)
				if (size < 0 || pos + 8 + size > to) {
					return
				}
				action(fourcc, pos + 8, size)
				pos += 8 + size + (size and 1)
			}
		}

		private fun riffEnd(data: ByteArray): Int =
			minOf(data.size, 8 + readInt32(data, 4))

		private fun startsWith(data: ByteArray, offset: Int, text: String): Boolean =
			text.indices.all { data[offset + it].toInt() and 0xFF == text[it].code }

		private fun read24(data: ByteArray, offset: Int): Int =
			(data[offset].toInt() and 0xFF) or
				((data[offset + 1].toInt() and 0xFF) shl 8) or
				((data[offset + 2].toInt() and 0xFF) shl 16)

		private fun readInt32(data: ByteArray, offset: Int): Int =
			read24(data, offset) or ((data[offset + 3].toInt() and 0xFF) shl 24)
	}
}

/** Minimal growable byte buffer, enough to assemble a RIFF container */
private class ByteArrayBuilder {
	private var buffer = ByteArray(64)
	private var length = 0

	private fun ensure(extra: Int) {
		if (length + extra > buffer.size) {
			buffer = buffer.copyOf(maxOf(buffer.size * 2, length + extra))
		}
	}

	fun byte(value: Int): ByteArrayBuilder {
		ensure(1)
		buffer[length++] = value.toByte()
		return this
	}

	fun bytes(source: ByteArray, offset: Int, count: Int): ByteArrayBuilder {
		ensure(count)
		source.copyInto(buffer, length, offset, offset + count)
		length += count
		return this
	}

	fun ascii(text: String): ByteArrayBuilder {
		text.forEach { byte(it.code) }
		return this
	}

	fun int24(value: Int): ByteArrayBuilder =
		byte(value).byte(value shr 8).byte(value shr 16)

	fun int32(value: Int): ByteArrayBuilder =
		int24(value).byte(value shr 24)

	fun chunk(fourcc: String, source: ByteArray, offset: Int, count: Int): ByteArrayBuilder {
		ascii(fourcc)
		int32(count)
		bytes(source, offset, count)
		if (count and 1 == 1) {
			byte(0)
		}
		return this
	}

	fun toByteArray(): ByteArray = buffer.copyOf(length)
}
