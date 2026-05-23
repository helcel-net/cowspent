package net.helcel.cowspent.android.helper

import android.graphics.*
import android.graphics.drawable.Drawable
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.*
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sqrt

/**
 * A Drawable object that draws text (1 character) on top of a circular/filled background.
 */
class TextDrawable private constructor(
    private val mText: String,
    r: Int,
    g: Int,
    b: Int,
    private val mRadius: Float,
    private val mDisabled: Boolean
) : Drawable() {
    private val mTextPaint: Paint = Paint()
    private val mBackground: Paint = Paint()
    private val mDisabledCircle: Paint = Paint()

    init {
        mBackground.style = Paint.Style.FILL
        mBackground.isAntiAlias = true
        mBackground.color = Color.rgb(r, g, b)

        if ((r + g + b) / 3 < 220) {
            mTextPaint.color = Color.WHITE
        } else {
            mTextPaint.color = Color.BLACK
        }
        mTextPaint.textSize = mRadius
        mTextPaint.isAntiAlias = true
        mTextPaint.textAlign = Paint.Align.CENTER

        mDisabledCircle.style = Paint.Style.STROKE
        mDisabledCircle.strokeWidth = mRadius * 0.2f
        mDisabledCircle.isAntiAlias = true
        mDisabledCircle.color = Color.DKGRAY
    }

    override fun draw(canvas: Canvas) {
        canvas.drawCircle(mRadius, mRadius, mRadius, mBackground)
        canvas.drawText(
            mText,
            mRadius,
            mRadius - (mTextPaint.descent() + mTextPaint.ascent()) / 2,
            mTextPaint
        )
        if (mDisabled) {
            canvas.drawCircle(mRadius, mRadius, mRadius * 0.9f, mDisabledCircle)
            canvas.drawLine(
                mRadius * 0.4f,
                mRadius * 1.6f,
                mRadius * 1.6f,
                mRadius * 0.4f,
                mDisabledCircle
            )
        }
    }

    override fun setAlpha(alpha: Int) {
        mTextPaint.alpha = alpha
    }

    override fun setColorFilter(cf: ColorFilter?) {
        mTextPaint.colorFilter = cf
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int {
        return PixelFormat.TRANSLUCENT
    }

    companion object {
        private const val INDEX_RED = 0
        private const val INDEX_GREEN = 1
        private const val INDEX_BLUE = 2
        private const val INDEX_HUE = 0
        private const val INDEX_SATURATION = 1
        private const val INDEX_LUMINATION = 2

        fun getColorFromName(name: String): Int {
            return try {
                val hsl = calculateHSL(name)
                val rgb = hslToRgb(hsl[0].toFloat(), hsl[1].toFloat(), hsl[2].toFloat(), 1f)
                Color.rgb(rgb[0], rgb[1], rgb[2])
            } catch (_: NoSuchAlgorithmException) {
                Color.WHITE
            }
        }

        @Throws(NoSuchAlgorithmException::class)
        private fun calculateHSL(name: String): IntArray {
            val result = arrayOf("0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "0", "0")
            val rgb = doubleArrayOf(0.0, 0.0, 0.0)
            var sat = 70
            val lum = 68
            val modulo = 16

            var hash = name.lowercase(Locale.ROOT).replace("[^0-9a-f]".toRegex(), "")
            if (!hash.matches("^[0-9a-f]{32}$".toRegex())) {
                hash = md5(hash)
            }

            for (i in hash.indices) {
                result[i % modulo] = (result[i % modulo].toInt() + hash.substring(i, i + 1).toInt(16)).toString()
            }

            for (count in 1 until modulo) {
                rgb[count % 3] += result[count].toDouble()
            }

            rgb[INDEX_RED] = rgb[INDEX_RED] % 255
            rgb[INDEX_GREEN] = rgb[INDEX_GREEN] % 255
            rgb[INDEX_BLUE] = rgb[INDEX_BLUE] % 255

            val hsl = rgbToHsl(rgb[INDEX_RED], rgb[INDEX_GREEN], rgb[INDEX_BLUE])

            val bright = sqrt(
                0.299 * rgb[INDEX_RED].pow(2.0) + 0.587 * rgb[INDEX_GREEN].pow(2.0) + 0.114 * rgb[INDEX_BLUE].pow(2.0)
            )

            if (bright >= 200) {
                sat = 60
            }

            return intArrayOf((hsl[INDEX_HUE] * 360).toInt(), sat, lum)
        }

        private fun hslToRgb(hParam: Float, sParam: Float, lParam: Float, alpha: Float): IntArray {
            var h = hParam
            var s = sParam
            var l = lParam
            if (s !in 0.0f..100.0f) {
                throw IllegalArgumentException("Color parameter outside of expected range - Saturation")
            }
            if (l !in 0.0f..100.0f) {
                throw IllegalArgumentException("Color parameter outside of expected range - Luminance")
            }
            if (alpha !in 0.0f..1.0f) {
                throw IllegalArgumentException("Color parameter outside of expected range - Alpha")
            }

            h %= 360.0f
            h /= 360f
            s /= 100f
            l /= 100f

            val q = if (l < 0.5) {
                l * (1 + s)
            } else {
                (l + s) - s * l
            }
            val p = 2 * l - q
            val r = round(max(0f, hueToRgb(p, q, h + 1.0f / 3.0f)) * 256).toInt()
            val g = round(max(0f, hueToRgb(p, q, h)) * 256).toInt()
            val b = round(max(0f, hueToRgb(p, q, h - 1.0f / 3.0f)) * 256).toInt()
            return intArrayOf(r, g, b)
        }

        private fun hueToRgb(p: Float, q: Float, hParam: Float): Float {
            var h = hParam
            if (h < 0) h += 1f
            if (h > 1) h -= 1f
            if (6 * h < 1) return p + (q - p) * 6 * h
            if (2 * h < 1) return q
            if (3 * h < 2) return p + (q - p) * 6 * (2.0f / 3.0f - h)
            return p
        }

        private fun rgbToHsl(rUntrimmed: Double, gUntrimmed: Double, bUntrimmed: Double): DoubleArray {
            val r = rUntrimmed / 255
            val g = gUntrimmed / 255
            val b = bUntrimmed / 255
            val max = max(r, max(g, b))
            val min = r.coerceAtMost(g.coerceAtMost(b))
            var h = (max + min) / 2
            val s: Double
            val l = (max + min) / 2
            if (max == min) {
                s = 0.0
                h = s // achromatic
            } else {
                val d = max - min
                s = if (l > 0.5) d / (2 - max - min) else d / (max + min)
                when (max) {
                    r -> {
                        h = (g - b) / d + (if (g < b) 6 else 0)
                    }
                    g -> {
                        h = (b - r) / d + 2
                    }
                    b -> {
                        h = (r - g) / d + 4
                    }
                }
                h /= 6.0
            }
            val hsl = DoubleArray(3)
            hsl[INDEX_HUE] = h
            hsl[INDEX_SATURATION] = s
            hsl[INDEX_LUMINATION] = l
            return hsl
        }

        @Throws(NoSuchAlgorithmException::class)
        private fun md5(string: String): String {
            val md5 = MessageDigest.getInstance("MD5").digest(string.toByteArray())
            return md5.joinToString("") { "%02x".format(it) }
        }
    }
}
