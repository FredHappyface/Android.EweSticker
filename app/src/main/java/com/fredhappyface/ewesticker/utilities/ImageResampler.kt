package com.fredhappyface.ewesticker.utilities

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin

/** Lobe count of the Lanczos kernel; 3 is what Pillow's LANCZOS filter uses */
private const val LOBES = 3

/**
 * Resizes ARGB pixel data with a Lanczos filter.
 *
 * Bitmap.createScaledBitmap only offers bilinear filtering, which samples too few source pixels to
 * shrink an image cleanly and leaves the result softer and slightly aliased. Since the reference
 * conversion in tools/gif-variants.py downscales with Lanczos, matching its output means matching its
 * filter rather than settling for what the platform hands out.
 */
object ImageResampler {
	/**
	 * Resize to the given dimensions. Alpha is premultiplied for the duration so that colour from
	 * transparent pixels cannot bleed into its neighbours.
	 *
	 * @param source IntArray ARGB pixels, sourceWidth * sourceHeight of them
	 * @return IntArray ARGB pixels, targetWidth * targetHeight of them
	 */
	fun resize(
		source: IntArray,
		sourceWidth: Int,
		sourceHeight: Int,
		targetWidth: Int,
		targetHeight: Int,
	): IntArray {
		if (sourceWidth == targetWidth && sourceHeight == targetHeight) {
			return source.copyOf()
		}

		// Premultiplied planes, so each pass is a plain weighted sum.
		val size = sourceWidth * sourceHeight
		val a = FloatArray(size)
		val r = FloatArray(size)
		val g = FloatArray(size)
		val b = FloatArray(size)
		for (i in 0 until size) {
			val pixel = source[i]
			val alpha = (pixel ushr 24) / 255f
			a[i] = alpha
			r[i] = ((pixel shr 16) and 0xFF) * alpha
			g[i] = ((pixel shr 8) and 0xFF) * alpha
			b[i] = (pixel and 0xFF) * alpha
		}

		val horizontal = Weights.of(sourceWidth, targetWidth)
		val ha = FloatArray(targetWidth * sourceHeight)
		val hr = FloatArray(targetWidth * sourceHeight)
		val hg = FloatArray(targetWidth * sourceHeight)
		val hb = FloatArray(targetWidth * sourceHeight)
		for (y in 0 until sourceHeight) {
			val rowIn = y * sourceWidth
			val rowOut = y * targetWidth
			for (x in 0 until targetWidth) {
				var sa = 0f
				var sr = 0f
				var sg = 0f
				var sb = 0f
				val from = horizontal.starts[x]
				val taps = horizontal.weights[x]
				for (t in taps.indices) {
					val at = rowIn + from + t
					val weight = taps[t]
					sa += a[at] * weight
					sr += r[at] * weight
					sg += g[at] * weight
					sb += b[at] * weight
				}
				ha[rowOut + x] = sa
				hr[rowOut + x] = sr
				hg[rowOut + x] = sg
				hb[rowOut + x] = sb
			}
		}

		val vertical = Weights.of(sourceHeight, targetHeight)
		val out = IntArray(targetWidth * targetHeight)
		for (y in 0 until targetHeight) {
			val from = vertical.starts[y]
			val taps = vertical.weights[y]
			for (x in 0 until targetWidth) {
				var sa = 0f
				var sr = 0f
				var sg = 0f
				var sb = 0f
				for (t in taps.indices) {
					val at = (from + t) * targetWidth + x
					val weight = taps[t]
					sa += ha[at] * weight
					sr += hr[at] * weight
					sg += hg[at] * weight
					sb += hb[at] * weight
				}
				val alpha = sa.coerceIn(0f, 1f)
				out[y * targetWidth + x] = if (alpha <= 0f) {
					0
				} else {
					// Undo the premultiply; a filter with negative lobes can overshoot, so clamp.
					val red = (sr / alpha).coerceIn(0f, 255f)
					val green = (sg / alpha).coerceIn(0f, 255f)
					val blue = (sb / alpha).coerceIn(0f, 255f)
					((alpha * 255f + 0.5f).toInt() shl 24) or
						((red + 0.5f).toInt() shl 16) or
						((green + 0.5f).toInt() shl 8) or
						(blue + 0.5f).toInt()
				}
			}
		}
		return out
	}

	/** Per-output-pixel filter taps, clamped to the edges of the source */
	private class Weights(val starts: IntArray, val weights: Array<FloatArray>) {
		companion object {
			fun of(sourceSize: Int, targetSize: Int): Weights {
				val scale = sourceSize.toDouble() / targetSize
				// Shrinking widens the kernel so that it still covers every contributing pixel;
				// enlarging leaves it at its natural width.
				val filterScale = max(1.0, scale)
				val support = LOBES * filterScale

				val starts = IntArray(targetSize)
				val weights = Array(targetSize) { FloatArray(0) }
				for (out in 0 until targetSize) {
					val center = (out + 0.5) * scale
					var from = floor(center - support).toInt()
					var to = ceil(center + support).toInt()
					if (from < 0) from = 0
					if (to > sourceSize) to = sourceSize
					if (from >= to) {
						from = (center.toInt()).coerceIn(0, sourceSize - 1)
						to = from + 1
					}
					val taps = FloatArray(to - from)
					var total = 0.0
					for (i in from until to) {
						val value = lanczos((i + 0.5 - center) / filterScale)
						taps[i - from] = value.toFloat()
						total += value
					}
					if (total != 0.0) {
						for (i in taps.indices) {
							taps[i] = (taps[i] / total).toFloat()
						}
					}
					starts[out] = from
					weights[out] = taps
				}
				return Weights(starts, weights)
			}

			private fun lanczos(x: Double): Double {
				if (x == 0.0) {
					return 1.0
				}
				if (x <= -LOBES || x >= LOBES) {
					return 0.0
				}
				val piX = PI * x
				return LOBES * sin(piX) * sin(piX / LOBES) / (piX * piX)
			}
		}
	}
}
