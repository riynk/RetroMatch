/*
 * RetroMatch word-level TTML karaoke renderer.
 */
package code.name.monkey.retromusic.lyrics.ttml

import android.content.Context
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.graphics.Typeface
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import code.name.monkey.retromusic.model.lyrics.ttml.TtmlLyrics

/**
 * Displays the active TTML line and highlights the syllable whose time window
 * contains the current playback position. Timing remains in milliseconds.
 */
class TtmlKaraokeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private var lyrics: TtmlLyrics? = null
    private var lastLineIndex = -1
    private var highlightColor: Int = currentTextColor

    init {
        gravity = android.view.Gravity.CENTER
        setTextIsSelectable(false)
    }

    fun setHighlightColor(color: Int) {
        highlightColor = color
        updateTime(lastPositionMs)
    }

    private var lastPositionMs: Long = 0L

    fun setLyrics(value: TtmlLyrics?) {
        lyrics = value?.takeIf { it.lines.isNotEmpty() }
        lastLineIndex = -1
        lastPositionMs = 0L
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
        val syllables = line.syllabus
        val active = syllables.indexOfLast { syllable ->
            lastPositionMs >= syllable.timeMs &&
                (syllable.durationMs <= 0L ||
                    lastPositionMs < syllable.timeMs + syllable.durationMs)
        }

        val rendered = SpannableString(line.text)
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
