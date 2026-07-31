package com.fredhappyface.ewesticker.utilities

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageResamplerTest {
	private fun solid(width: Int, height: Int, argb: Int) = IntArray(width * height) { argb }

	@Test
	fun `returns the source untouched when nothing changes`() {
		val source = solid(4, 4, 0xFF102030.toInt())
		val result = ImageResampler.resize(source, 4, 4, 4, 4)
		assertEquals(source.toList(), result.toList())
	}

	@Test
	fun `produces the requested dimensions`() {
		val result = ImageResampler.resize(solid(512, 512, 0xFF808080.toInt()), 512, 512, 320, 200)
		assertEquals(320 * 200, result.size)
	}

	@Test
	fun `preserves a flat colour through a downscale`() {
		// Lanczos has negative lobes, so a uniform image is the case that catches a normalisation
		// bug: the weights must sum to one or flat colour drifts or overshoots.
		val colour = 0xFF3C7A28.toInt()
		val result = ImageResampler.resize(solid(512, 512, colour), 512, 512, 320, 320)
		result.forEach { assertEquals(colour, it) }
	}

	@Test
	fun `keeps fully transparent input transparent`() {
		val result = ImageResampler.resize(solid(64, 64, 0), 64, 64, 32, 32)
		result.forEach { assertEquals(0, it) }
	}

	@Test
	fun `does not bleed colour out of transparent pixels`() {
		// Half opaque red, half fully transparent but carrying blue in its unused colour channels.
		// Premultiplying means that hidden blue must not tint the opaque side.
		val width = 32
		val height = 8
		val source = IntArray(width * height) { index ->
			if (index % width < width / 2) 0xFFFF0000.toInt() else 0x000000FF
		}
		val result = ImageResampler.resize(source, width, height, width / 2, height / 2)

		val leftEdge = result[0]
		assertEquals(255, leftEdge ushr 24)
		assertTrue("blue leaked in: ${leftEdge.toString(16)}", (leftEdge and 0xFF) < 8)
	}

	@Test
	fun `keeps a gradient monotonic`() {
		val size = 256
		val source = IntArray(size * size) { index ->
			val x = index % size
			0xFF000000.toInt() or (x shl 16) or (x shl 8) or x
		}
		val result = ImageResampler.resize(source, size, size, 64, 64)

		var previous = -1
		for (x in 0 until 64) {
			val value = result[x] and 0xFF
			assertTrue("value went backwards at $x", value >= previous - 1)
			previous = value
		}
		// Ends should still span most of the original range rather than being washed out.
		assertTrue(abs((result[63] and 0xFF) - (result[0] and 0xFF)) > 200)
	}

	@Test
	fun `handles upscaling`() {
		val result = ImageResampler.resize(solid(8, 8, 0xFF204060.toInt()), 8, 8, 24, 24)
		assertEquals(24 * 24, result.size)
		result.forEach { assertEquals(0xFF204060.toInt(), it) }
	}
}
