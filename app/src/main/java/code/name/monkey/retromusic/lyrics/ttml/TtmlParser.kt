/*
 * Adapted from LocalPlayer (MIT License).
 * Copyright (c) 2026 Christian (cvc953).
 * Original project license: https://github.com/riynk/RetroMatch/blob/integration/localplayer/LICENSE
 */
package code.name.monkey.retromusic.lyrics.ttml

import code.name.monkey.retromusic.model.lyrics.ttml.TtmlLine
import code.name.monkey.retromusic.model.lyrics.ttml.TtmlLyrics
import code.name.monkey.retromusic.model.lyrics.ttml.TtmlMetadata
import code.name.monkey.retromusic.model.lyrics.ttml.TtmlSyllable
import org.xmlpull.v1.XmlPullParser
import org.kxml2.io.KXmlParser

/**
 * Parser para archivos TTML (Timed Text Markup Language)
 * Basado en el formato de Apple Music usado por YouLyPlus
 */
object TtmlParser {

    private const val NS_ITUNES = "http://music.apple.com/lyric-ttml-internal"

    fun parseTtml(content: String): TtmlLyrics {
        return try {
            val parser = KXmlParser().apply {
                setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
                setInput(content.reader())
            }

            val result = parseTtmlDocument(parser)

            result
        } catch (e: Exception) {
            TtmlLyrics() // Return empty lyrics on error
        }
    }

    private fun parseTtmlDocument(parser: XmlPullParser): TtmlLyrics {
        val metadata = TtmlMetadata()
        val lines = mutableListOf<TtmlLine>()
        var timingMode = "Word"

        var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "tt" -> {
                            // Get timing mode from root element
                            timingMode = parser.getAttributeValue(NS_ITUNES, "timing") ?: "Word"
                        }
                        "div" -> {
                            // Parse lyrics lines within div
                                lines.addAll(parseDiv(parser))
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        // --- Nueva lógica para unir palabras entre líneas ---
        for (i in 1 until lines.size) {
            val prevLine = lines[i - 1]
            val currLine = lines[i]
            if (prevLine.syllabus.isNotEmpty() && currLine.syllabus.isNotEmpty()) {
                val lastPrev = prevLine.syllabus.last()
                val firstCurr = currLine.syllabus.first()
                // Si la última sílaba de la línea anterior y la primera de la actual no terminan ni empiezan con espacio, es una palabra partida
                if (!lastPrev.text.endsWith(" ") && !firstCurr.text.startsWith(" ")) {
                    // Marcar la primera sílaba de la línea actual como continuación de palabra
                    val newFirst = firstCurr.copy(continuesWord = true)
                    val newSyllabus = currLine.syllabus.toMutableList()
                    newSyllabus[0] = newFirst
                    lines[i] = currLine.copy(syllabus = newSyllabus)
                }
            }
        }

        return TtmlLyrics(
            type = timingMode,
            metadata = metadata,
            lines = lines
        )
    }

    private fun parseDiv(parser: XmlPullParser): List<TtmlLine> {
        val lines = mutableListOf<TtmlLine>()
        var depth = 1

        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == "p") {
                        parseParagraph(parser)?.let { lines.add(it) }
                    } else {
                        depth++
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "div") {
                        depth--
                    }
                }
            }
        }
        return lines
    }

    private fun parseParagraph(parser: XmlPullParser): TtmlLine? {
        val beginAttr = parser.getAttributeValue(null, "begin")
        val endAttr = parser.getAttributeValue(null, "end")
        
        if (beginAttr == null || endAttr == null) {
            // Consumir hasta el cierre del <p>
            var depth = 1
            while (depth > 0) {
                when (parser.next()) {
                    XmlPullParser.START_TAG -> depth++
                    XmlPullParser.END_TAG -> depth--
                }
            }
            return null
        }

        val timeMs = parseTime(beginAttr)
        val endMs = parseTime(endAttr)
        val durationMs = endMs - timeMs

        val syllabus = mutableListOf<TtmlSyllable>()
        val textBuilder = StringBuilder()
        var insideParenthesis = false
        var previousContinuesWord = false

        // Procesar contenido del <p>
        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == "span") {
                        val syllable = parseSpan(parser, insideParenthesis)
                        if (syllable != null) {
                            // Detectar apertura de paréntesis
                            if (syllable.text.startsWith("(")) {
                                insideParenthesis = true
                            }
                            
                            // Actualizar isBackground si estamos dentro de paréntesis
                            val updatedSyllable = if (insideParenthesis) {
                                syllable.copy(isBackground = true)
                            } else {
                                syllable
                            }
                            
                            // Eliminar paréntesis
                            var cleanedText = updatedSyllable.text.replace("(", "").replace(")", "")

                            // Si la sílaba anterior continúa palabra, evitar espacios iniciales aquí
                            if (previousContinuesWord) {
                                cleanedText = cleanedText.trimStart()
                            }

                            // Detectar si continúa en la siguiente palabra (no termina con espacio)
                            val continuesWord = cleanedText.isNotEmpty() && !cleanedText.endsWith(" ")

                            // Si continúa la palabra, eliminar el espacio final
                            val finalText = if (continuesWord) {
                                cleanedText.trimEnd()
                            } else {
                                cleanedText
                            }

                            if (finalText.isNotEmpty()) {
                                val cleanedSyllable = updatedSyllable.copy(
                                    text = finalText,
                                    continuesWord = continuesWord
                                )

                                syllabus.add(cleanedSyllable)
                                textBuilder.append(finalText)
                            }

                            previousContinuesWord = continuesWord
                            
                            // Detectar cierre de paréntesis
                            if (syllable.text.endsWith(")")) {
                                insideParenthesis = false
                            }
                        }
                        // parseSpan ya consumió su END_TAG
                    } else {
                        depth++
                    }
                }
                XmlPullParser.TEXT -> {
                    val text = parser.text
                    if (!text.isNullOrBlank()) {
                        textBuilder.append(text)
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "p") {
                        depth--
                    }
                }
            }
        }

        // Construir el texto de la línea a partir de las sílabas, respetando continuesWord
        val lineText = buildString {
            syllabus.forEachIndexed { i, syl ->
                if (i > 0 && !syl.continuesWord) append(" ")
                append(syl.text)
            }
        }.trim()
        if (lineText.isEmpty() && syllabus.isEmpty()) {
            return null
        }

        return TtmlLine(
            timeMs = timeMs,
            durationMs = durationMs,
            text = lineText,
            syllabus = syllabus
        )
    }

    private fun parseSpan(parser: XmlPullParser, isBackground: Boolean = false): TtmlSyllable? {
        val beginAttr = parser.getAttributeValue(null, "begin")
        val endAttr = parser.getAttributeValue(null, "end")
        val durAttr = parser.getAttributeValue(null, "dur")

        if (beginAttr == null) {
            // Consumir hasta el cierre del <span>
            var depth = 1
            while (depth > 0) {
                when (parser.next()) {
                    XmlPullParser.START_TAG -> depth++
                    XmlPullParser.END_TAG -> depth--
                }
            }
            return null
        }

        val timeMs = parseTime(beginAttr)
        val endMs: Long
        val durationMs: Long

        // Soportar tanto 'end' como 'dur'
        if (endAttr != null) {
            endMs = parseTime(endAttr)
            durationMs = endMs - timeMs
        } else if (durAttr != null) {
            durationMs = parseDuration(durAttr)
            endMs = timeMs + durationMs
        } else {
            // Sin end ni dur, consumir y retornar null
            var depth = 1
            while (depth > 0) {
                when (parser.next()) {
                    XmlPullParser.START_TAG -> depth++
                    XmlPullParser.END_TAG -> depth--
                }
            }
            return null
        }

        val textBuilder = StringBuilder()
        var depth = 1

        // Procesar contenido del <span>
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.TEXT -> {
                    val text = parser.text
                    if (!text.isNullOrBlank()) {
                        textBuilder.append(text)
                    }
                }
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> {
                    if (parser.name == "span") {
                        depth--
                    }
                }
            }
        }

        val text = textBuilder.toString()
        if (text.isBlank()) return null

        return TtmlSyllable(
            text = text,
            timeMs = timeMs,
            durationMs = durationMs,
            isBackground = isBackground
        )
    }

    /**
     * Convierte un tiempo TTML (formato: HH:MM:SS.mmm o MM:SS.mmm) a milisegundos
     */
    private fun parseTime(timeStr: String): Long {
        val parts = timeStr.split(":")
        
        return when (parts.size) {
            3 -> {
                // HH:MM:SS.mmm
                val hours = parts[0].toLongOrNull() ?: 0
                val minutes = parts[1].toLongOrNull() ?: 0
                val seconds = parts[2].toDoubleOrNull() ?: 0.0
                
                (hours * 3600_000 + minutes * 60_000 + (seconds * 1000).toLong())
            }
            2 -> {
                // MM:SS.mmm
                val minutes = parts[0].toLongOrNull() ?: 0
                val seconds = parts[1].toDoubleOrNull() ?: 0.0
                
                (minutes * 60_000 + (seconds * 1000).toLong())
            }
            else -> 0
        }
    }

    /**
     * Parsea duraciones en formato "3.917s" o "3917ms"
     */
    private fun parseDuration(durStr: String): Long {
        return when {
            durStr.endsWith("ms") -> {
                // Formato: "3917ms"
                durStr.removeSuffix("ms").toLongOrNull() ?: 0L
            }
            durStr.endsWith("s") -> {
                val seconds = durStr.removeSuffix("s").toDoubleOrNull() ?: 0.0
                (seconds * 1000).toLong()
            }
            else -> {
                // Asumir milisegundos si no hay sufijo
                durStr.toLongOrNull() ?: 0L
            }
        }
    }
}
