/*
 * Adapted from LocalPlayer (MIT License).
 * Copyright (c) 2026 Christian (cvc953).
 * Original project license: https://github.com/riynk/RetroMatch/blob/integration/localplayer/LICENSE
 */
package code.name.monkey.retromusic.model.lyrics.ttml

/**
 * Representa una sílaba individual con sincronización temporal
 */
data class TtmlSyllable(
    val text: String,
    val timeMs: Long,
    val durationMs: Long,
    val isBackground: Boolean = false,
    val continuesWord: Boolean = false, // true si esta sílaba continúa en la siguiente (sin espacio)
    val startIndex: Int = -1,
    val endIndex: Int = -1
)

/**
 * Representa una línea de letra con múltiples sílabas/palabras sincronizadas
 */
data class TtmlLine(
    val timeMs: Long,
    val durationMs: Long,
    val text: String,
    val syllabus: List<TtmlSyllable> = emptyList(),
    val translation: String? = null,
    val transliteration: String? = null
)

/**
 * Metadatos de las letras TTML
 */
data class TtmlMetadata(
    val source: String = "TTML",
    val title: String = "",
    val language: String = "",
    val songWriters: List<String> = emptyList()
)

/**
 * Estructura completa de letras TTML
 */
data class TtmlLyrics(
    val type: String = "Word", // Word, Line, Syllable
    val metadata: TtmlMetadata = TtmlMetadata(),
    val lines: List<TtmlLine> = emptyList()
)
