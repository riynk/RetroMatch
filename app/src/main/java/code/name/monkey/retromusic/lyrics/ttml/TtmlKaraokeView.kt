/*
 * RetroMatch word-level TTML karaoke renderer.
 */
package code.name.monkey.retromusic.lyrics.ttml

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.util.AttributeSet
import android.view.Gravity
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.res.ResourcesCompat
import code.name.monkey.retromusic.R
import code.name.monkey.retromusic.model.lyrics.ttml.TtmlLyrics

/**
 * Displays timed source lyrics and optional line-level translation/transliteration.
 * Secondary text is styled independently and never changes source word offsets.
 */
class TtmlKaraokeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle
) : AppCompatTextView(context, attrs, defStyleAttr) {

    enum class DisplayMode { ORIGINAL, TRANSLATION, TRANSLITERATION, ALL }

    private var lyrics: TtmlLyrics? = null
    private var lastLineIndex = -1
    private var lastRenderedMode: DisplayMode? = null
    private var highlightColor: Int = currentTextColor
    private var secondaryTextColor: Int = currentTextColor
    private var displayMode = DisplayMode.ORIGINAL
    private var lastPositionMs: Long = 0L
    private val generatedTransliterations = mutableMapOf<Int, String>()

    init {
        gravity = Gravity.CENTER
        setTextIsSelectable(false)
        includeFontPadding = false
        letterSpacing = 0.012f
        typeface = ResourcesCompat.getFont(context, R.font.manrope) ?: typeface
        setLineSpacing(6f * resources.displayMetrics.scaledDensity, 1.06f)
        secondaryTextColor = context.obtainStyledAttributes(
            intArrayOf(android.R.attr.textColorSecondary)
        ).use { array ->
            array.getColorStateList(0)?.defaultColor ?: currentTextColor
        }
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
        lastRenderedMode = null
        lastPositionMs = 0L
        displayMode = DisplayMode.ORIGINAL
        generatedTransliterations.clear()
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
                DisplayMode.TRANSLATION ->
                    line.translation?.takeIf { it.isNotBlank() }?.let(::add)
                DisplayMode.TRANSLITERATION ->
                    transliterationForLine(lineIndex, line)?.let(::add)
                DisplayMode.ALL -> {
                    line.translation?.takeIf { it.isNotBlank() }?.let(::add)
                    transliterationForLine(lineIndex, line)?.let(::add)
                }
            }
        }
        val renderedText = buildString {
            append(line.text)
            secondary.forEach { append('\n').append(it) }
        }
        val rendered = SpannableString(renderedText)
        val secondaryStart = line.text.length
        if (secondary.isNotEmpty()) {
            rendered.setSpan(
                RelativeSizeSpan(0.72f),
                secondaryStart,
                rendered.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            rendered.setSpan(
                ForegroundColorSpan(secondaryTextColor),
                secondaryStart,
                rendered.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            rendered.setSpan(
                StyleSpan(Typeface.NORMAL),
                secondaryStart,
                rendered.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        val syllables = line.syllabus
        val active = syllables.indexOfLast { syllable ->
            lastPositionMs >= syllable.timeMs &&
                (syllable.durationMs <= 0L ||
                    lastPositionMs < syllable.timeMs + syllable.durationMs)
        }

        var searchFrom = 0
        syllables.forEachIndexed { index, syllable ->
            val range = findWordRange(line.text, syllable.text, searchFrom)
            if (range != null) {
                if (index == active) {
                    rendered.setSpan(
                        ForegroundColorSpan(highlightColor),
                        range.first,
                        range.last + 1,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    rendered.setSpan(
                        StyleSpan(Typeface.BOLD),
                        range.first,
                        range.last + 1,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                searchFrom = range.last + 1
            }
        }

        if (lineIndex != lastLineIndex || displayMode != lastRenderedMode) {
            animate().cancel()
            alpha = 0.55f
            translationY = 8f * resources.displayMetrics.density
            animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(220L)
                .setInterpolator(DecelerateInterpolator())
                .start()
            lastLineIndex = lineIndex
            lastRenderedMode = displayMode
        }
        text = rendered
    }

    private fun transliterationForLine(
        index: Int,
        line: code.name.monkey.retromusic.model.lyrics.ttml.TtmlLine
    ): String? {
        line.transliteration?.takeIf { it.isNotBlank() }?.let { return it }
        val generated = generatedTransliterations.getOrPut(index) {
            TtmlTransliterator.toLatinOrNull(line.text).orEmpty()
        }
        return generated.takeIf { it.isNotBlank() }
    }

    private fun findWordRange(
        source: String,
        timedText: String,
        searchFrom: Int
    ): IntRange? {
        val firstVisible = timedText.indexOfFirst { !it.isWhitespace() }
        if (firstVisible < 0) return null
        val lastVisible = timedText.indexOfLast { !it.isWhitespace() }
        val visibleText = timedText.substring(firstVisible, lastVisible + 1)
        val start = source.indexOf(visibleText, searchFrom)
        if (start < 0) return null
        return start until (start + visibleText.length).coerceAtMost(source.length)
    }
}
