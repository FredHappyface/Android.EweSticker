package com.fredhappyface.ewesticker.utilities

import java.io.OutputStream

/** Largest LZW code width the GIF format allows */
private const val MAX_CODE_BITS = 12

/** Number of LZW codes available before the dictionary has to be reset */
private const val MAX_CODES = 1 shl MAX_CODE_BITS

/** Deflated sub-blocks in a GIF data stream carry at most this many bytes */
private const val MAX_BLOCK_SIZE = 255

/** Alpha at or above this counts as opaque; GIF transparency is all-or-nothing */
private const val ALPHA_THRESHOLD = 128

/**
 * Writes an animated GIF, one frame at a time.
 *
 * GIF is limited to 256 colours per frame, so every frame is reduced with median cut and then
 * Floyd-Steinberg dithered against the palette it produced. That is deliberately the same pairing
 * the reference implementation in tools/gif-variants.py gets from Pillow, so on-device output stays
 * comparable to what a desktop conversion produces rather than visibly worse.
 *
 * Each frame carries its own local colour table, since a palette chosen for the whole animation
 * would waste entries on colours only a few frames use.
 *
 * @property output OutputStream stream to write the GIF to; the caller retains ownership
 * @property width Int canvas width in pixels
 * @property height Int canvas height in pixels
 * @property colors Int how many palette entries a frame may use, 2..256
 */
class GifEncoder(
	private val output: OutputStream,
	private val width: Int,
	private val height: Int,
	private val colors: Int,
) {
	private var started = false

	/** Reused row error buffers for dithering, three channels interleaved per pixel */
	private val currentError = IntArray(width * 3 + 6)
	private val nextError = IntArray(width * 3 + 6)

	/** Scratch space for the counting sort in sortByChannel, grown as boxes demand and reused */
	private val bucketOffsets = IntArray(256)
	private var colourScratch = IntArray(0)
	private var countScratch = IntArray(0)

	/**
	 * Add a frame. Pixels are ARGB as returned by Bitmap.getPixels.
	 *
	 * @param argb IntArray width * height pixels
	 * @param delayMs Int how long to show this frame
	 */
	fun addFrame(argb: IntArray, delayMs: Int) {
		if (!started) {
			writeHeader()
			started = true
		}

		val hasTransparency = argb.any { (it ushr 24) < ALPHA_THRESHOLD }
		val opaqueBudget = if (hasTransparency) colors - 1 else colors
		val palette = buildPalette(argb, opaqueBudget.coerceAtLeast(1))
		val transparentIndex = if (hasTransparency) palette.size else -1
		val entries = palette.size + if (hasTransparency) 1 else 0
		val tableBits = tableBitsFor(entries)

		val indices = mapPixels(argb, palette, transparentIndex)

		writeGraphicControl(delayMs, transparentIndex)
		writeImageDescriptor(tableBits)
		writeColorTable(palette, 1 shl tableBits)
		writeImageData(indices, maxOf(tableBits, 2))
	}

	/** Close out the stream. Does not close the underlying OutputStream. */
	fun finish() {
		if (!started) {
			writeHeader()
			started = true
		}
		output.write(0x3B) // trailer
	}

	// ---- palette selection -------------------------------------------------------------------

	/**
	 * Reduce the opaque pixels of a frame to at most [target] colours using median cut: start with
	 * every distinct colour in one box, then repeatedly split the box that spans the most colour
	 * space, cutting at the median so both halves hold a similar number of pixels.
	 */
	private fun buildPalette(argb: IntArray, target: Int): IntArray {
		val histogram = HashMap<Int, Int>()
		for (pixel in argb) {
			if ((pixel ushr 24) >= ALPHA_THRESHOLD) {
				val rgb = pixel and 0xFFFFFF
				histogram[rgb] = (histogram[rgb] ?: 0) + 1
			}
		}
		if (histogram.isEmpty()) {
			return intArrayOf(0)
		}

		val distinct = IntArray(histogram.size)
		val counts = IntArray(histogram.size)
		var at = 0
		for ((rgb, count) in histogram) {
			distinct[at] = rgb
			counts[at] = count
			at++
		}
		if (distinct.size <= target) {
			return distinct
		}

		// Boxes are [start, end) ranges over distinct/counts, which get reordered as boxes split.
		val boxes = ArrayList<IntArray>()
		boxes.add(intArrayOf(0, distinct.size))
		while (boxes.size < target) {
			var chosen = -1
			var bestScore = -1L
			for (index in boxes.indices) {
				val box = boxes[index]
				if (box[1] - box[0] < 2) {
					continue
				}
				val score = spread(distinct, counts, box[0], box[1])
				if (score > bestScore) {
					bestScore = score
					chosen = index
				}
			}
			if (chosen < 0) {
				break
			}
			val box = boxes[chosen]
			val channel = widestChannel(distinct, box[0], box[1])
			sortByChannel(distinct, counts, box[0], box[1], channel)
			val split = medianIndex(counts, box[0], box[1])
			if (split <= box[0] || split >= box[1]) {
				break
			}
			boxes[chosen] = intArrayOf(box[0], split)
			boxes.add(intArrayOf(split, box[1]))
		}

		return IntArray(boxes.size) { average(distinct, counts, boxes[it][0], boxes[it][1]) }
	}

	/** How much colour space a box covers, weighted by how many pixels fall in it */
	private fun spread(distinct: IntArray, counts: IntArray, from: Int, to: Int): Long {
		var rMin = 255
		var rMax = 0
		var gMin = 255
		var gMax = 0
		var bMin = 255
		var bMax = 0
		var total = 0L
		for (i in from until to) {
			val rgb = distinct[i]
			val r = (rgb shr 16) and 0xFF
			val g = (rgb shr 8) and 0xFF
			val b = rgb and 0xFF
			if (r < rMin) rMin = r
			if (r > rMax) rMax = r
			if (g < gMin) gMin = g
			if (g > gMax) gMax = g
			if (b < bMin) bMin = b
			if (b > bMax) bMax = b
			total += counts[i]
		}
		val widest = maxOf(rMax - rMin, gMax - gMin, bMax - bMin)
		return widest.toLong() * total
	}

	/** Which channel a box spans most widely: 0 red, 1 green, 2 blue */
	private fun widestChannel(distinct: IntArray, from: Int, to: Int): Int {
		var rMin = 255
		var rMax = 0
		var gMin = 255
		var gMax = 0
		var bMin = 255
		var bMax = 0
		for (i in from until to) {
			val rgb = distinct[i]
			val r = (rgb shr 16) and 0xFF
			val g = (rgb shr 8) and 0xFF
			val b = rgb and 0xFF
			if (r < rMin) rMin = r
			if (r > rMax) rMax = r
			if (g < gMin) gMin = g
			if (g > gMax) gMax = g
			if (b < bMin) bMin = b
			if (b > bMax) bMax = b
		}
		val rangeR = rMax - rMin
		val rangeG = gMax - gMin
		val rangeB = bMax - bMin
		return when {
			rangeR >= rangeG && rangeR >= rangeB -> 0
			rangeG >= rangeB -> 1
			else -> 2
		}
	}

	/**
	 * Order a box by one colour channel.
	 *
	 * The sort key is a single byte, so this counts occurrences of each of the 256 possible values
	 * and scatters the entries into place, rather than comparing them. That matters because the very
	 * first split covers every distinct colour in the frame -- several thousand of them for a
	 * photographic sticker -- where a quadratic sort dominates the entire conversion.
	 *
	 * Scattering left to right within each bucket keeps entries that share a key in their original
	 * order, exactly as the comparison sort this replaced did, so the box boundaries and the palette
	 * that falls out of them are unchanged.
	 */
	private fun sortByChannel(
		distinct: IntArray,
		counts: IntArray,
		from: Int,
		to: Int,
		channel: Int,
	) {
		val shift = 16 - channel * 8
		val length = to - from
		if (length < 2) {
			return
		}

		val offsets = bucketOffsets
		offsets.fill(0)
		for (i in from until to) {
			offsets[(distinct[i] shr shift) and 0xFF]++
		}
		var running = 0
		for (bucket in offsets.indices) {
			val size = offsets[bucket]
			offsets[bucket] = running
			running += size
		}

		val sortedColours = scratchColours(length)
		val sortedCounts = scratchCounts(length)
		for (i in from until to) {
			val colour = distinct[i]
			val slot = offsets[(colour shr shift) and 0xFF]++
			sortedColours[slot] = colour
			sortedCounts[slot] = counts[i]
		}
		sortedColours.copyInto(distinct, from, 0, length)
		sortedCounts.copyInto(counts, from, 0, length)
	}

	private fun scratchColours(length: Int): IntArray {
		if (colourScratch.size < length) {
			colourScratch = IntArray(length)
		}
		return colourScratch
	}

	private fun scratchCounts(length: Int): IntArray {
		if (countScratch.size < length) {
			countScratch = IntArray(length)
		}
		return countScratch
	}

	/** Index that divides a box into two halves of roughly equal pixel population */
	private fun medianIndex(counts: IntArray, from: Int, to: Int): Int {
		var total = 0L
		for (i in from until to) {
			total += counts[i]
		}
		var running = 0L
		for (i in from until to) {
			running += counts[i]
			if (running * 2 >= total) {
				return (i + 1).coerceAtMost(to - 1)
			}
		}
		return to - 1
	}

	private fun average(distinct: IntArray, counts: IntArray, from: Int, to: Int): Int {
		var r = 0L
		var g = 0L
		var b = 0L
		var total = 0L
		for (i in from until to) {
			val rgb = distinct[i]
			val count = counts[i].toLong()
			r += ((rgb shr 16) and 0xFF) * count
			g += ((rgb shr 8) and 0xFF) * count
			b += (rgb and 0xFF) * count
			total += count
		}
		if (total == 0L) {
			return 0
		}
		return (((r / total).toInt() and 0xFF) shl 16) or
			(((g / total).toInt() and 0xFF) shl 8) or
			((b / total).toInt() and 0xFF)
	}

	// ---- dithering ---------------------------------------------------------------------------

	/**
	 * Map every pixel to a palette index, diffusing the quantisation error of each pixel into its
	 * neighbours so that gradients do not band. Transparent pixels take the transparent index and
	 * contribute no error, since nothing was approximated there.
	 */
	private fun mapPixels(argb: IntArray, palette: IntArray, transparentIndex: Int): ByteArray {
		val indices = ByteArray(width * height)
		currentError.fill(0)

		for (y in 0 until height) {
			nextError.fill(0)
			val rowStart = y * width
			for (x in 0 until width) {
				val pixel = argb[rowStart + x]
				if (transparentIndex >= 0 && (pixel ushr 24) < ALPHA_THRESHOLD) {
					indices[rowStart + x] = transparentIndex.toByte()
					continue
				}
				val base = x * 3 + 3
				val r = (((pixel shr 16) and 0xFF) + currentError[base]).coerceIn(0, 255)
				val g = (((pixel shr 8) and 0xFF) + currentError[base + 1]).coerceIn(0, 255)
				val b = ((pixel and 0xFF) + currentError[base + 2]).coerceIn(0, 255)

				val index = nearest(palette, r, g, b)
				indices[rowStart + x] = index.toByte()

				val chosen = palette[index]
				diffuse(x, r - ((chosen shr 16) and 0xFF), 0)
				diffuse(x, g - ((chosen shr 8) and 0xFF), 1)
				diffuse(x, b - (chosen and 0xFF), 2)
			}
			currentError.indices.forEach { currentError[it] = nextError[it] }
		}
		return indices
	}

	/** Spread one channel's error over the neighbours Floyd-Steinberg reaches */
	private fun diffuse(x: Int, error: Int, channel: Int) {
		if (error == 0) {
			return
		}
		val base = x * 3 + 3 + channel
		currentError[base + 3] += error * 7 / 16
		nextError[base - 3] += error * 3 / 16
		nextError[base] += error * 5 / 16
		nextError[base + 3] += error / 16
	}

	/**
	 * Closest palette entry to a colour, by squared distance.
	 *
	 * This is an exhaustive scan on purpose. Caching it against a 5-bits-per-channel key was around
	 * four times faster but cost 1.3dB against the reference implementation, because rounding the
	 * query colour throws away exactly the small differences dithering works in. Conversion happens
	 * once per sticker on a spare core, so accuracy is the better trade.
	 */
	private fun nearest(palette: IntArray, r: Int, g: Int, b: Int): Int {
		var best = 0
		var bestDistance = Int.MAX_VALUE
		for (i in palette.indices) {
			val entry = palette[i]
			val dr = r - ((entry shr 16) and 0xFF)
			val dg = g - ((entry shr 8) and 0xFF)
			val db = b - (entry and 0xFF)
			val distance = dr * dr + dg * dg + db * db
			if (distance < bestDistance) {
				bestDistance = distance
				best = i
				if (distance == 0) {
					break
				}
			}
		}
		return best
	}

	// ---- container ---------------------------------------------------------------------------

	private fun writeHeader() {
		output.write("GIF89a".toByteArray(Charsets.US_ASCII))
		writeShort(width)
		writeShort(height)
		output.write(0x70) // no global colour table, 8 bits of colour resolution
		output.write(0)
		output.write(0)
		// Netscape application extension, the only way to say "loop forever" in a GIF
		output.write(0x21)
		output.write(0xFF)
		output.write(0x0B)
		output.write("NETSCAPE2.0".toByteArray(Charsets.US_ASCII))
		output.write(0x03)
		output.write(0x01)
		writeShort(0) // zero means loop indefinitely
		output.write(0)
	}

	private fun writeGraphicControl(delayMs: Int, transparentIndex: Int) {
		output.write(0x21)
		output.write(0xF9)
		output.write(0x04)
		// Disposal 2 clears the frame back to the background before the next one is drawn, which is
		// what keeps transparent areas from accumulating over the animation.
		val transparencyFlag = if (transparentIndex >= 0) 1 else 0
		output.write((2 shl 2) or transparencyFlag)
		writeShort(centiseconds(delayMs))
		output.write(if (transparentIndex >= 0) transparentIndex else 0)
		output.write(0)
	}

	private fun writeImageDescriptor(tableBits: Int) {
		output.write(0x2C)
		writeShort(0)
		writeShort(0)
		writeShort(width)
		writeShort(height)
		output.write(0x80 or (tableBits - 1)) // local colour table of 2^tableBits entries
	}

	private fun writeColorTable(palette: IntArray, entries: Int) {
		for (i in 0 until entries) {
			val rgb = if (i < palette.size) palette[i] else 0
			output.write((rgb shr 16) and 0xFF)
			output.write((rgb shr 8) and 0xFF)
			output.write(rgb and 0xFF)
		}
	}

	// ---- LZW --------------------------------------------------------------------------------

	private fun writeImageData(indices: ByteArray, minCodeSize: Int) {
		output.write(minCodeSize)
		val blocks = BlockWriter(output)
		val clearCode = 1 shl minCodeSize
		val endCode = clearCode + 1
		var codeSize = minCodeSize + 1
		var nextCode = endCode + 1
		val dictionary = HashMap<Int, Int>()

		blocks.write(clearCode, codeSize)
		var prefix = indices[0].toInt() and 0xFF
		for (i in 1 until indices.size) {
			val pixel = indices[i].toInt() and 0xFF
			val key = (prefix shl 8) or pixel
			val known = dictionary[key]
			if (known != null) {
				prefix = known
				continue
			}
			blocks.write(prefix, codeSize)
			if (nextCode < MAX_CODES) {
				dictionary[key] = nextCode
				nextCode++
				// A decoder only learns an entry on the code after the one that created it, so its
				// dictionary trails this one by a single code. Widening when nextCode merely reaches
				// the current ceiling would therefore start emitting wider codes a code before the
				// decoder starts reading them, which desynchronises the whole stream.
				if (nextCode > (1 shl codeSize) && codeSize < MAX_CODE_BITS) {
					codeSize++
				}
			} else {
				blocks.write(clearCode, codeSize)
				dictionary.clear()
				codeSize = minCodeSize + 1
				nextCode = endCode + 1
			}
			prefix = pixel
		}
		blocks.write(prefix, codeSize)
		blocks.write(endCode, codeSize)
		blocks.finish()
	}

	private fun writeShort(value: Int) {
		output.write(value and 0xFF)
		output.write((value shr 8) and 0xFF)
	}

	private companion object {
		/** Palette sizes must be a power of two, and GIF has no one-entry colour table */
		fun tableBitsFor(entries: Int): Int {
			var bits = 1
			while ((1 shl bits) < entries && bits < 8) {
				bits++
			}
			return bits
		}

		/**
		 * GIF stores delays in hundredths of a second. Zero-delay frames are rendered as fast as a
		 * viewer feels like, so round anything that would truncate to zero up to a single tick.
		 */
		fun centiseconds(delayMs: Int): Int = maxOf(1, (delayMs + 5) / 10)
	}
}

/** Packs LZW codes into the length-prefixed sub-blocks a GIF image data stream is made of */
private class BlockWriter(private val output: OutputStream) {
	private val block = ByteArray(MAX_BLOCK_SIZE)
	private var blockLength = 0
	private var bitBuffer = 0
	private var bitCount = 0

	fun write(code: Int, codeSize: Int) {
		bitBuffer = bitBuffer or (code shl bitCount)
		bitCount += codeSize
		while (bitCount >= 8) {
			append(bitBuffer and 0xFF)
			bitBuffer = bitBuffer ushr 8
			bitCount -= 8
		}
	}

	fun finish() {
		if (bitCount > 0) {
			append(bitBuffer and 0xFF)
			bitBuffer = 0
			bitCount = 0
		}
		flush()
		output.write(0) // block terminator
	}

	private fun append(byte: Int) {
		block[blockLength++] = byte.toByte()
		if (blockLength == MAX_BLOCK_SIZE) {
			flush()
		}
	}

	private fun flush() {
		if (blockLength == 0) {
			return
		}
		output.write(blockLength)
		output.write(block, 0, blockLength)
		blockLength = 0
	}
}
