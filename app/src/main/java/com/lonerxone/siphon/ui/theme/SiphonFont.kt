package com.lonerxone.siphon.ui.theme

import android.content.Context
import android.graphics.Typeface
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight

/** Loads the bundled Inter font. Returns null when the file is missing, so the system font is used instead. */
@OptIn(ExperimentalTextApi::class)
fun loadSiphonFont(context: Context): FontFamily? {
    return try {
        val assets = context.assets
        Typeface.createFromAsset(assets, "fonts/inter.ttf")
        FontFamily(
            Font("fonts/inter.ttf", assets, FontWeight.Normal, FontStyle.Normal, FontVariation.Settings(FontVariation.weight(400))),
            Font("fonts/inter.ttf", assets, FontWeight.Medium, FontStyle.Normal, FontVariation.Settings(FontVariation.weight(500))),
            Font("fonts/inter.ttf", assets, FontWeight.SemiBold, FontStyle.Normal, FontVariation.Settings(FontVariation.weight(600))),
            Font("fonts/inter.ttf", assets, FontWeight.Bold, FontStyle.Normal, FontVariation.Settings(FontVariation.weight(700))),
            Font("fonts/inter.ttf", assets, FontWeight.ExtraBold, FontStyle.Normal, FontVariation.Settings(FontVariation.weight(800)))
        )
    } catch (e: Exception) {
        null
    }
}