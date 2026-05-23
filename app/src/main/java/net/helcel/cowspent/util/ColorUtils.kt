package net.helcel.cowspent.util

import android.content.Context
import android.content.res.Configuration
import android.graphics.*
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.WriterException
import net.helcel.cowspent.R
import androidx.core.graphics.createBitmap

object ColorUtils {

    fun primaryColor(context: Context, isDark: Boolean? = null): Int {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)

        val resolvedIsDark = isDark ?: ((context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES)

        // Determine color mode, migrating from old boolean flags if necessary
        val modeKey = context.getString(R.string.pref_key_color_mode)
        val colorMode = if (prefs.contains(modeKey)) {
            prefs.getString(modeKey, "system")
        } else {
            val useServer = prefs.getBoolean(context.getString(R.string.pref_key_use_server_color), true)
            val useSystem = prefs.getBoolean(context.getString(R.string.pref_key_use_system_color), true)
            when {
                useServer -> "server"
                useSystem -> "system"
                else -> "manual"
            }
        }

        if (colorMode == "server") {
            val serverColor = prefs.getInt(context.getString(R.string.pref_key_server_color), -1)
            if (serverColor != -1) {
                return serverColor
            }
        }

        if (colorMode == "system" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return if (resolvedIsDark) {
                context.getColor(android.R.color.system_accent1_200)
            } else {
                context.getColor(android.R.color.system_accent1_600)
            }
        }

        val themedContext = if (isDark != null) {
            val config = Configuration(context.resources.configuration)
            config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (isDark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            context.createConfigurationContext(config)
        } else {
            context
        }

        return prefs.getInt(
            context.getString(R.string.pref_key_color),
            ContextCompat.getColor(themedContext, R.color.primary)
        )
    }

    fun isLightColor(color: Int): Boolean {
        return androidx.core.graphics.ColorUtils.calculateLuminance(color) > 0.5
    }

    @Throws(WriterException::class)
    fun encodeAsBitmap(str: String): Bitmap? {
        val result = try {
            MultiFormatWriter().encode(
                str,
                BarcodeFormat.QR_CODE, 400, 400, null
            )
        } catch (_: IllegalArgumentException) {
            // Unsupported format
            return null
        }
        val w = result.width
        val h = result.height
        val pixels = IntArray(w * h)
        for (y in 0 until h) {
            val offset = y * w
            for (x in 0 until w) {
                pixels[offset + x] = if (result[x, y]) Color.BLACK else Color.WHITE
            }
        }
        val bitmap = createBitmap(w, h)
        bitmap.setPixels(pixels, 0, 400, 0, 0, w, h)
        return bitmap
    }

}
