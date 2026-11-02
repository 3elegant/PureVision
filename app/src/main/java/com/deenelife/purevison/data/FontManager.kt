package com.deenelife.purevison.data

import android.content.Context
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.deenelife.purevison.R
import java.io.File

object FontManager {
    const val FONT_SOLAIMANLIPI = "solaimanlipi"
    const val FONT_AMIRI = "amiri"
    const val FONT_POPPINS = "poppins"
    const val FONT_SYSTEM = "system"
    const val FONT_CUSTOM = "custom"

    const val CUSTOM_FONT_FILE_NAME = "custom_user_font.ttf"

    data class FontItem(
        val id: String,
        val displayNameRes: Int,
        val fontResId: Int?
    )

    val supportedFonts = listOf(
        FontItem(FONT_SOLAIMANLIPI, R.string.font_solaimanlipi, R.font.solaimanlipi),
        FontItem(FONT_AMIRI, R.string.font_amiri, R.font.amiri),
        FontItem(FONT_POPPINS, R.string.font_poppins, R.font.poppins),
        FontItem(FONT_SYSTEM, R.string.font_system, null),
        FontItem(FONT_CUSTOM, R.string.font_custom, null)
    )

    fun getCustomFontFile(context: Context): File {
        return File(context.filesDir, CUSTOM_FONT_FILE_NAME)
    }

    fun getDefaultFontId(language: String): String {
        return if (language == "bn") FONT_SOLAIMANLIPI else FONT_SYSTEM
    }

    fun getTypeface(context: Context, fontId: String?): Typeface {
        return try {
            when (fontId) {
                FONT_CUSTOM -> {
                    val file = getCustomFontFile(context)
                    if (file.exists()) {
                        Typeface.createFromFile(file)
                    } else {
                        ResourcesCompat.getFont(context, R.font.solaimanlipi) ?: Typeface.DEFAULT
                    }
                }
                FONT_AMIRI -> ResourcesCompat.getFont(context, R.font.amiri) ?: Typeface.DEFAULT
                FONT_POPPINS -> ResourcesCompat.getFont(context, R.font.poppins) ?: Typeface.DEFAULT
                FONT_SYSTEM -> Typeface.DEFAULT
                FONT_SOLAIMANLIPI -> ResourcesCompat.getFont(context, R.font.solaimanlipi) ?: Typeface.DEFAULT
                else -> {
                    val locale = androidx.core.os.ConfigurationCompat.getLocales(context.resources.configuration)[0]
                    val lang = locale?.language ?: "bn"
                    if (lang == "bn") {
                        ResourcesCompat.getFont(context, R.font.solaimanlipi) ?: Typeface.DEFAULT
                    } else {
                        Typeface.DEFAULT
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Typeface.DEFAULT
        }
    }

    fun getFontIndex(fontId: String?): Int {
        val index = supportedFonts.indexOfFirst { it.id == fontId }
        return if (index >= 0) index else 0
    }
}
