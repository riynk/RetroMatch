/*
 * RetroMatch word-level TTML karaoke renderer.
 */
package code.name.monkey.retromusic.lyrics.ttml

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import code.name.monkey.retromusic.model.lyrics.ttml.TtmlLyrics

/**
 * Displays timed source lyrics and optional line-level translation/transliteration.
 * Secondary text is appended without modifying source syllable offsets or timing.
 */
class TtmlKaraokeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle
) : AppCompatTextView(context, attrs, defStyleAttr) {

    enum class DisplayMode { ORIGINAL, TRANSLATION, TRANSLITERATION, ALL }

    private var lyrics: TtmlLyrics? = null
    private var lastLineIndex = -1
    private var highlightColor: Int = currentTextColor
    private var displayMode = DisplayMode.ORIGINAL
    private var lastPositionMs: Long = 0L

    init {
        gravity = android.view.Gravity.CENTER
        setTextIsSelectable(false)
    }

    fun setKaraokeHighlightColor(color: Int) {
        highlightColor = color
        updateTime(lastPositionMs)
    }

    fun cycleDisplayMode(): DisplayMode {
        displayMode = when (displayMode) {
            DisplayMode.ORIGINAL -> DisplayMode.TRANSLATION
            DisplayMode.TRANSLATION -> DisplayMode.TRANSLITERATION
            DisplayMode.TRANSLITERATION -> DisplayMode.ALL
            DisplayMode.ALL -> DisplayMode.ORIGINAL
        }
        updateTime(lastPositionMs)
        return displayMode
    }

    fun setLyrics(value: TtmlLyrics?) {
        lyrics = value?.takeIf { it.lines.isNotEmpty() }
        lastLineIndex = -1
        lastPositionMs = 0L
        displayMode = DisplayMode.ORIGINAL
        isSelected = lyrics != null
        updateTime(0L)
    }

    fun updateTime(positionMs: Long) {
        lastPositionMs = positionMs.coerceAtLeast(0L)
        val lines = lyrics?.lines ?: run {
            text = null
            return
        }
        val lineIndex = lines.indexOfLast { it.timeMs <= lastPositionMs }
        if (lineIndex < 0) {
            text = lines.firstOrNull()?.text.orEmpty()
            return
        }

        val line = lines[lineIndex]
        val secondary = buildList {
            when (displayMode) {
                DisplayMode.ORIGINAL -> Unit
                DisplayMode.TRANSLATION -> line.translation?.takeIf { it.isNotBlank() }?.let(::add)
                DisplayMode.TRANSLITERATION -> line.transliteration?.takeIf { it.isNotBlank() }?.let(::add)
                DisplayMode.ALL -> {
                    line.translation?.takeIf { it.isNotBlank() }?.let(::add)
                    line.transliteration?.takeIf { it.isNotBlank() }?.let(::add)
                }
            }
        }
        val renderedText = buildString {
            append(line.text)
            secondary.forEach { append('\n').append(it) }
        }
        val rendered = SpannableString(renderedText)
        val syllables = line.syllabus
        val active = syllables.indexOfLast { syllable ->
            lastPositionMs >= syllable.timeMs &&
                (syllable.durationMs <= 0L ||
                    lastPositionMs < syllable.timeMs + syllable.durationMs)
        }

        var searchFrom = 0
        syllables.forEachIndexed { index, syllable ->
            val start = line.text.indexOf(syllable.text, searchFrom)
            if (start >= 0) {
                val end = (start + syllable.text.length).coerceAtMost(line.text.length)
                if (index == active && end > start) {
                    rendered.setSpan(
                        ForegroundColorSpan(highlightColor),
                        start,
                        end,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    rendered.setSpan(
                        StyleSpan(Typeface.BOLD),
                        start,
                        end,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                searchFrom = end
            }
        }

        if (lineIndex != lastLineIndex) {
            animate().cancel()
            alpha = 0.65f
            animate().alpha(1f).setDuration(180L).start()
            lastLineIndex = lineIndex
        }
        text = rendered
    }
}
