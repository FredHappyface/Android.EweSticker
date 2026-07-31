package com.fredhappyface.ewesticker.utilities

/**
 * A minimal GIF reader, for asserting in tests that what GifEncoder wrote is what a decoder sees.
 *
 * Android unit tests compile against android.jar, which has no javax.imageio, so there is no stock
 * decoder available to check against. Reading the format back by hand is the alternative, and it has
 * the advantage of failing loudly on exactly the structural mistakes a hand-written encoder makes.
 *
 * @property width Int canvas width from the logical screen descriptor
 * @property height Int canvas height from the logical screen descriptor
 * @property frames List<Frame> every image in the file, in order
 */
class GifReader private constructor(
	val width: Int,
	val height: Int,
	val frames: List<Frame>,
) {
	/**
	 * @property pixels IntArray ARGB pixels, transparent entries being fully zero
	 * @property delayCentiseconds Int delay declared for this frame
	 * @property paletteSize Int number of entries in the colour table this frame used
	 */
	class Frame(
		val width: Int,
		val height: Int,
		val pixels: IntArray,
		val delayCentiseconds: Int,
		val paletteSize: Int,
	)

	companion object {
		fun read(bytes: ByteArray): GifReader {
			require(String(bytes, 0, 6, Charsets.US_ASCII) == "GIF89a") { "not a GIF89a file" }
			var pos = 6
			val width = readShort(bytes, pos)
			val height = readShort(bytes, pos + 2)
			val screenPacked = bytes[pos + 4].toInt() and 0xFF
			pos += 7
			require(screenPacked and 0x80 == 0) { "these tests expect no global colour table" }

			val frames = mutableListOf<Frame>()
			var delay = 0
			var transparentIndex = -1

			while (pos < bytes.size) {
				when (bytes[pos].toInt() and 0xFF) {
					0x3B -> return GifReader(width, height, frames)

					0x21 -> {
						val label = bytes[pos + 1].toInt() and 0xFF
						pos += 2
						if (label == 0xF9) {
							val size = bytes[pos].toInt() and 0xFF
							val packed = bytes[pos + 1].toInt() and 0xFF
							delay = readShort(bytes, pos + 2)
							transparentIndex =
								if (packed and 0x01 != 0) bytes[pos + 4].toInt() and 0xFF else -1
							pos += 1 + size
						}
						pos = skipBlocks(bytes, pos)
					}

					0x2C -> {
						val frameWidth = readShort(bytes, pos + 5)
						val frameHeight = readShort(bytes, pos + 7)
						val packed = bytes[pos + 9].toInt() and 0xFF
						pos += 10
						require(packed and 0x80 != 0) { "expected a local colour table" }
						val paletteSize = 1 shl ((packed and 0x07) + 1)
						val palette = IntArray(paletteSize) {
							val at = pos + it * 3
							((bytes[at].toInt() and 0xFF) shl 16) or
								((bytes[at + 1].toInt() and 0xFF) shl 8) or
								(bytes[at + 2].toInt() and 0xFF)
						}
						pos += paletteSize * 3

						val minCodeSize = bytes[pos].toInt() and 0xFF
						pos++
						val data = mutableListOf<Byte>()
						while (true) {
							val length = bytes[pos].toInt() and 0xFF
							pos++
							if (length == 0) {
								break
							}
							for (i in 0 until length) {
								data.add(bytes[pos + i])
							}
							pos += length
						}

						val indices = inflate(data.toByteArray(), minCodeSize, frameWidth * frameHeight)
						val pixels = IntArray(indices.size) {
							val index = indices[it]
							if (index == transparentIndex) {
								0
							} else {
								0xFF000000.toInt() or palette[index]
							}
						}
						frames.add(Frame(frameWidth, frameHeight, pixels, delay, paletteSize))
					}

					else -> throw IllegalStateException(
						"unexpected block 0x${(bytes[pos].toInt() and 0xFF).toString(16)} at $pos",
					)
				}
			}
			throw IllegalStateException("file ended without a trailer")
		}

		private fun skipBlocks(bytes: ByteArray, start: Int): Int {
			var pos = start
			while (true) {
				val length = bytes[pos].toInt() and 0xFF
				pos++
				if (length == 0) {
					return pos
				}
				pos += length
			}
		}

		/** Standard GIF LZW decode, the inverse of what GifEncoder emits */
		private fun inflate(data: ByteArray, minCodeSize: Int, expected: Int): IntArray {
			val clearCode = 1 shl minCodeSize
			val endCode = clearCode + 1
			val table = arrayOfNulls<IntArray>(4096)
			for (i in 0 until clearCode) {
				table[i] = intArrayOf(i)
			}

			var codeSize = minCodeSize + 1
			var next = endCode + 1
			var previous: IntArray? = null
			val out = IntArray(expected)
			var written = 0
			var bitPos = 0

			while (written < expected) {
				var code = 0
				var readable = true
				for (bit in 0 until codeSize) {
					val index = bitPos + bit
					if (index / 8 >= data.size) {
						readable = false
						break
					}
					code = code or (((data[index / 8].toInt() shr (index % 8)) and 1) shl bit)
				}
				if (!readable || code == endCode) {
					break
				}
				bitPos += codeSize

				if (code == clearCode) {
					codeSize = minCodeSize + 1
					next = endCode + 1
					previous = null
					continue
				}

				val known = table[code]
				// A code can legally refer to the entry it is about to define, which always means
				// "the previous sequence plus its own first symbol".
				val entry = known ?: previous?.let { it + it[0] } ?: break
				for (value in entry) {
					if (written < expected) {
						out[written++] = value
					}
				}
				previous?.let {
					if (next < 4096) {
						table[next] = it + entry[0]
						next++
						if (next > (1 shl codeSize) - 1 && codeSize < 12) {
							codeSize++
						}
					}
				}
				previous = entry
			}
			return out
		}

		private fun readShort(bytes: ByteArray, at: Int): Int =
			(bytes[at].toInt() and 0xFF) or ((bytes[at + 1].toInt() and 0xFF) shl 8)
	}
}
