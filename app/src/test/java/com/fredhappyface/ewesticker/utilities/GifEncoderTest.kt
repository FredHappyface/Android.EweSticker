package com.fredhappyface.ewesticker.utilities

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GifEncoder writes a byte format by hand, so what is worth asserting is that decoding its output
 * gives back what went in. These run on the JVM rather than a device because the encoder deliberately
 * depends on nothing from Android.
 */
class GifEncoderTest {
	private fun encode(
		frames: List<IntArray>,
		width: Int,
		height: Int,
		colors: Int = 128,
		delayMs: Int = 100,
	): ByteArray {
		val out = ByteArrayOutputStream()
		val encoder = GifEncoder(out, width, height, colors)
		frames.forEach { encoder.addFrame(it, delayMs) }
		encoder.finish()
		return out.toByteArray()
	}

	@Test
	fun `writes a well formed gif`() {
		val width = 32
		val height = 24
		val frames = (0 until 5).map { frame ->
			IntArray(width * height) { pixel ->
				val x = pixel % width
				0xFF000000.toInt() or (((x * 8 + frame * 20) and 0xFF) shl 16) or (frame * 40 and 0xFF)
			}
		}

		val bytes = encode(frames, width, height)
		assertEquals("GIF89a", String(bytes, 0, 6, Charsets.US_ASCII))
		assertEquals(0x3B.toByte(), bytes.last())

		val gif = GifReader.read(bytes)
		assertEquals(width, gif.width)
		assertEquals(height, gif.height)
		assertEquals(frames.size, gif.frames.size)
		gif.frames.forEach {
			assertEquals(width, it.width)
			assertEquals(height, it.height)
			assertEquals(width * height, it.pixels.size)
		}
	}

	@Test
	fun `reproduces flat colour exactly`() {
		// With far fewer distinct colours than palette slots, quantisation has no excuse to
		// approximate anything, so this should survive the round trip untouched.
		val width = 16
		val height = 16
		val red = 0xFFCC2211.toInt()
		val blue = 0xFF1122CC.toInt()
		val frame = IntArray(width * height) { if (it % width < width / 2) red else blue }

		val decoded = GifReader.read(encode(listOf(frame), width, height)).frames.single()

		assertEquals(red, decoded.pixels[0])
		assertEquals(blue, decoded.pixels[width - 1])
	}

	@Test
	fun `keeps transparent pixels transparent`() {
		val width = 8
		val height = 8
		val opaque = 0xFF00FF00.toInt()
		val frame = IntArray(width * height) { if (it % 2 == 0) opaque else 0 }

		val decoded = GifReader.read(encode(listOf(frame), width, height)).frames.single()

		assertEquals(0, decoded.pixels[1])
		assertEquals(opaque, decoded.pixels[0])
	}

	@Test
	fun `respects the palette budget`() {
		// A gradient holds far more colours than the budget allows, so the encoder has to reduce it
		// and must not emit a colour table bigger than it declared.
		val width = 64
		val height = 64
		val frame = IntArray(width * height) { pixel ->
			val x = pixel % width
			val y = pixel / width
			0xFF000000.toInt() or ((x * 4) shl 16) or ((y * 4) shl 8) or ((x + y) * 2 and 0xFF)
		}

		val decoded = GifReader.read(encode(listOf(frame), width, height, colors = 16))
			.frames
			.single()

		assertTrue("colour table was ${decoded.paletteSize}", decoded.paletteSize <= 16)
		assertTrue(
			"expected at most 16 distinct colours, saw ${decoded.pixels.toSet().size}",
			decoded.pixels.toSet().size <= 16,
		)
	}

	@Test
	fun `converts frame delays to centiseconds`() {
		val frame = IntArray(4) { 0xFF808080.toInt() }
		val gif = GifReader.read(encode(listOf(frame), 2, 2, delayMs = 83))
		assertEquals(8, gif.frames.single().delayCentiseconds)
	}

	@Test
	fun `never emits a zero delay`() {
		// A zero delay leaves the frame rate up to the viewer, so a very short frame has to round up.
		val frame = IntArray(4) { 0xFF808080.toInt() }
		val gif = GifReader.read(encode(listOf(frame), 2, 2, delayMs = 1))
		assertTrue(gif.frames.single().delayCentiseconds >= 1)
	}

	@Test
	fun `survives a single pixel frame`() {
		val gif = GifReader.read(encode(listOf(intArrayOf(0xFF123456.toInt())), 1, 1))
		assertEquals(1, gif.frames.single().pixels.size)
		assertEquals(0xFF123456.toInt(), gif.frames.single().pixels[0])
	}

	@Test
	fun `handles a photographic frame without losing pixels`() {
		// Enough pseudo-random colour to push the LZW dictionary past its first few widenings and
		// force a code size increase, which is where a hand-written encoder tends to go wrong.
		val width = 96
		val height = 96
		var seed = 12345
		val frame = IntArray(width * height) {
			seed = seed * 1103515245 + 12345
			0xFF000000.toInt() or ((seed ushr 8) and 0xFFFFFF)
		}

		val decoded = GifReader.read(encode(listOf(frame), width, height, colors = 256))
			.frames
			.single()

		assertEquals(width * height, decoded.pixels.size)
		assertTrue("decode produced only blanks", decoded.pixels.toSet().size > 1)
	}
}
