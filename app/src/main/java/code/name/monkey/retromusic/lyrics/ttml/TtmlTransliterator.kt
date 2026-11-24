/*
 * Optional device-local transliteration for TTML lines that lack a supplied variant.
 * Uses Android ICU only when the device exposes the required transliterator.
 */
package code.name.monkey.retromusic.lyrics.ttml

import android.os.Build
import androidx.annotation.RequiresApi

object TtmlTransliterator {

    fun toLatinOrNull(source: String): String? {
        if (source.isBlank() || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return transliterateWithIcu(source)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun transliterateWithIcu(source: String): String? = try {
        val availableIds = java.util.Collections.list(
            android.icu.text.Transliterator.getAvailableIDs()
        )
        if ("Any-Latin" !in availableIds) return null

        android.icu.text.Transliterator.getInstance("Any-Latin")
            .transliterate(source)
            .takeIf { it.isNotBlank() && it != source }
    } catch (_: RuntimeException) {
        null
    } catch (_: LinkageError) {
        null
    }
}
