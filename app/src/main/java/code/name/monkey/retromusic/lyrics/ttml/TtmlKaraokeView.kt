/*
 * RetroMatch multi-line TTML karaoke renderer.
 */
package code.name.monkey.retromusic.lyrics.ttml

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.ReplacementSpan
import android.text.style.StyleSpan
import android.util.AttributeSet
import android.view.Gravity
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.res.ResourcesCompat
import code.name.monkey.retromusic.R
import code.name.monkey.retromusic.model.lyrics.ttml.TtmlLine
import code.name.monkey.retromusic.model.lyrics.ttml.TtmlLyrics
import kotlin.math.ceil

/**
 * Shows the active lyric line with adjacent context and progressively fills
 * timed words. The renderer deliberately rebuilds only the visible three-line
 * window, keeping source offsets separate from translations/transliterations.
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
    private var lastPositionMs = 0L
    private val generatedTransliterations = mutableMapOf<Int, String>()

    init {
        gravity = Gravity.CENTER
        setTextIsSelectable(false)
        includeFontPadding = false
        letterSpacing = 0.012f
        typeface = ResourcesCompat.getFont(context, R.font.manrope) ?: typeface
        setLineSpacing(6f * resources.displayMetrics.scaledDensity, 1.04f)
        val attributes = context.obtainStyledAttributes(intArrayOf(android.R.attr.textColorSecondary))
        secondaryTextColor = try {
            attributes.getColorStateList(0)?.defaultColor ?: currentTextColor
        } finally {
            attributes.recycle()
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
        if (lines.isEmpty()) {
            text = null
            return
        }

        val activeLine = lines.indexOfLast { it.timeMs <= lastPositionMs }
            .coerceAtLeast(0)
        val firstVisible = (activeLine - 1).coerceAtLeast(0)
        val lastVisible = (activeLine + 1).coerceAtMost(lines.lastIndex)
        val rendered = SpannableStringBuilder()

        for (lineIndex in firstVisible..lastVisible) {
            if (rendered.isNotEmpty()) rendered.append("\n\n")
            val line = lines[lineIndex]
            val lineStart = rendered.length
            rendered.append(line.text)
            val lineEnd = rendered.length

            if (lineIndex == activeLine) {
                appendSecondaryText(rendered, line, lineIndex)
                applyWordProgress(rendered, line, lineStart, lastPositionMs)
            } else {
                rendered.setSpan(
                    ForegroundColorSpan(secondaryTextColor),
                    lineStart,
                    lineEnd,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                rendered.setSpan(
                    RelativeSizeSpan(0.82f),
                    lineStart,
                    lineEnd,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        if (activeLine != lastLineIndex || displayMode != lastRenderedMode) {
            animate().cancel()
            alpha = 0.72f
            translationY = 5f * resources.displayMetrics.density
            animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(150L)
                .setInterpolator(DecelerateInterpolator())
                .start()
            lastLineIndex = activeLine
            lastRenderedMode = displayMode
        }
        setText(rendered, BufferType.SPANNABLE)
    }

    private fun appendSecondaryText(
        rendered: SpannableStringBuilder,
        line: TtmlLine,
        lineIndex: Int
    ) {
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
        if (secondary.isEmpty()) return
        val start = rendered.length
        secondary.forEach { rendered.append("\n").append(it) }
        rendered.setSpan(
            RelativeSizeSpan(0.72f),
            start,
            rendered.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        rendered.setSpan(
            ForegroundColorSpan(secondaryTextColor),
            start,
            rendered.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        rendered.setSpan(
            StyleSpan(Typeface.NORMAL),
            start,
            rendered.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }

    private fun applyWordProgress(
        rendered: SpannableStringBuilder,
        line: TtmlLine,
        lineStart: Int,
        positionMs: Long
    ) {
        val syllables = line.syllabus
        if (syllables.isEmpty()) return

        var searchFrom = 0
        val activeIndex = syllables.indexOfLast { it.timeMs <= positionMs }
        syllables.forEachIndexed { index, syllable ->
            val range = findWordRange(line.text, syllable.text, searchFrom) ?: return@forEachIndexed
            searchFrom = range.last + 1
            val absoluteStart = lineStart + range.first
            val absoluteEnd = lineStart + range.last + 1
            val nextStart = syllables.getOrNull(index + 1)?.timeMs
            val inferredEnd = when {
                syllable.durationMs > 0L -> syllable.timeMs + syllable.durationMs
                nextStart != null && nextStart > syllable.timeMs -> nextStart
                line.durationMs > 0L -> line.timeMs + line.durationMs
                else -> syllable.timeMs
            }

            when {
                index < activeIndex || (index == activeIndex && inferredEnd > syllable.timeMs &&
                    positionMs >= inferredEnd) -> {
                    rendered.setSpan(
                        ForegroundColorSpan(highlightColor),
                        absoluteStart,
                        absoluteEnd,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    rendered.setSpan(
                        StyleSpan(Typeface.BOLD),
                        absoluteStart,
                        absoluteEnd,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                index == activeIndex && inferredEnd > syllable.timeMs &&
                    positionMs < inferredEnd -> {
                    val fraction = ((positionMs - syllable.timeMs).toFloat() /
                        (inferredEnd - syllable.timeMs).toFloat()).coerceIn(0f, 1f)
                    rendered.setSpan(
                        KaraokeProgressSpan(highlightColor, fraction),
                        absoluteStart,
                        absoluteEnd,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    rendered.setSpan(
                        StyleSpan(Typeface.BOLD),
                        absoluteStart,
                        absoluteEnd,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }
        }
    }

    private fun transliterationForLine(index: Int, line: TtmlLine): String? {
        line.transliteration?.takeIf { it.isNotBlank() }?.let { return it }
        val generated = generatedTransliterations.getOrPut(index) {
            TtmlTransliterator.toLatinOrNull(line.text).orEmpty()
        }
        return generated.takeIf { it.isNotBlank() }
    }

    private fun findWordRange(source: String, timedText: String, searchFrom: Int): IntRange? {
        val firstVisible = timedText.indexOfFirst { !it.isWhitespace() }
        if (firstVisible < 0) return null
        val lastVisible = timedText.indexOfLast { !it.isWhitespace() }
        val visibleText = timedText.substring(firstVisible, lastVisible + 1)
        val start = source.indexOf(visibleText, searchFrom)
        if (start < 0) return null
        return start until (start + visibleText.length).coerceAtMost(source.length)
    }

    /** Draws the timed word twice and clips the accent pass to its elapsed fraction. */
    private class KaraokeProgressSpan(
        private val accentColor: Int,
        private val progress: Float
    ) : ReplacementSpan() {
        override fun getSize(
            paint: Paint,
            text: CharSequence,
            start: Int,
            end: Int,
            fm: Paint.FontMetricsInt?
        ): Int {
            if (fm != null) paint.getFontMetricsInt(fm)
            return ceil(paint.measureText(text, start, end)).toInt()
        }

        override fun draw(
            canvas: Canvas,
            text: CharSequence,
            start: Int,
            end: Int,
            x: Float,
            top: Int,
            y: Int,
            bottom: Int,
            paint: Paint
        ) {
            val width = paint.measureText(text, start, end)
            val originalColor = paint.color
            canvas.drawText(text, start, end, x, y.toFloat(), paint)
            if (progress > 0f && width > 0f) {
                val save = canvas.save()
                canvas.clipRect(x, top.toFloat(), x + width * progress, bottom.toFloat())
                paint.color = accentColor
                canvas.drawText(text, start, end, x, y.toFloat(), paint)
                paint.color = originalColor
                canvas.restoreToCount(save)
            }
            paint.color = originalColor
        }
    }
}
