/*
 * RetroMatch adaptive TTML karaoke renderer.
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
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.res.ResourcesCompat
import code.name.monkey.retromusic.R
import code.name.monkey.retromusic.model.lyrics.ttml.TtmlLine
import code.name.monkey.retromusic.model.lyrics.ttml.TtmlLyrics
import kotlin.math.ceil

/**
 * Keeps a stable text layout while updating only the active word's paint progress.
 * The amount of lyric context adapts to the available vertical space.
 */
class TtmlKaraokeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle
) : AppCompatTextView(context, attrs, defStyleAttr) {

    enum class DisplayMode { ORIGINAL, TRANSLATION, TRANSLITERATION, ALL }

    private var lyrics: TtmlLyrics? = null
    private var lastLineIndex = -1
    private var lastActiveSyllableIndex = -1
    private var lastRenderedMode: DisplayMode? = null
    private var highlightColor: Int = currentTextColor
    private var secondaryTextColor: Int = currentTextColor
    private var displayMode = DisplayMode.ORIGINAL
    private var lastPositionMs = 0L
    private var progressSpan: KaraokeProgressSpan? = null
    private var progressSpanStartMs = 0L
    private var progressSpanEndMs = 0L
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

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (h != oldh) {
            lastLineIndex = -1
            renderLyrics(lastPositionMs)
        }
    }

    fun setKaraokeHighlightColor(color: Int) {
        highlightColor = color
        lastLineIndex = -1
        renderLyrics(lastPositionMs)
    }

    fun cycleDisplayMode(): DisplayMode {
        displayMode = when (displayMode) {
            DisplayMode.ORIGINAL -> DisplayMode.TRANSLATION
            DisplayMode.TRANSLATION -> DisplayMode.TRANSLITERATION
            DisplayMode.TRANSLITERATION -> DisplayMode.ALL
            DisplayMode.ALL -> DisplayMode.ORIGINAL
        }
        lastLineIndex = -1
        renderLyrics(lastPositionMs)
        return displayMode
    }

    fun setLyrics(value: TtmlLyrics?) {
        lyrics = value?.takeIf { it.lines.isNotEmpty() }
        lastLineIndex = -1
        lastRenderedMode = null
        lastPositionMs = 0L
        progressSpan = null
        displayMode = DisplayMode.ORIGINAL
        generatedTransliterations.clear()
        isSelected = lyrics != null
        renderLyrics(0L)
    }

    fun updateTime(positionMs: Long) {
        lastPositionMs = positionMs.coerceAtLeast(0L)
        val lines = lyrics?.lines ?: run {
            text = null
            progressSpan = null
            return
        }
        if (lines.isEmpty()) {
            text = null
            progressSpan = null
            return
        }

        val activeLine = lines.indexOfLast { it.timeMs <= lastPositionMs }.coerceAtLeast(0)
        val activeSyllable = lines[activeLine].syllabus.indexOfLast { it.timeMs <= lastPositionMs }
        if (activeLine != lastLineIndex || activeSyllable != lastActiveSyllableIndex ||
            displayMode != lastRenderedMode
        ) {
            renderLyrics(lastPositionMs)
            return
        }

        // Do not replace TextView text on every tick: that forces layout/measurement
        // repeatedly and is a major source of visible karaoke jitter.
        val span = progressSpan ?: return
        val fraction = if (progressSpanEndMs > progressSpanStartMs) {
            ((lastPositionMs - progressSpanStartMs).toFloat() /
                (progressSpanEndMs - progressSpanStartMs).toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
        span.setProgress(fraction)
        invalidate()
    }

    private fun renderLyrics(positionMs: Long) {
        val lines = lyrics?.lines ?: run {
            text = null
            progressSpan = null
            return
        }
        if (lines.isEmpty()) {
            text = null
            progressSpan = null
            return
        }

        val activeLine = lines.indexOfLast { it.timeMs <= positionMs }.coerceAtLeast(0)
        val visibleCount = adaptiveVisibleLineCount(lines.size)
        val beforeCount = (visibleCount - 1) / 2
        val firstVisible = (activeLine - beforeCount).coerceAtLeast(0)
            .coerceAtMost((lines.size - visibleCount).coerceAtLeast(0))
        val lastVisible = (firstVisible + visibleCount - 1).coerceAtMost(lines.lastIndex)
        val rendered = SpannableStringBuilder()
        progressSpan = null

        for (lineIndex in firstVisible..lastVisible) {
            if (rendered.isNotEmpty()) rendered.append("\n\n")
            val line = lines[lineIndex]
            val lineStart = rendered.length
            rendered.append(line.text)
            val lineEnd = rendered.length

            if (lineIndex == activeLine) {
                appendSecondaryText(rendered, line, lineIndex)
                applyWordProgress(rendered, line, lineStart, positionMs)
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

        lastLineIndex = activeLine
        lastActiveSyllableIndex = lines[activeLine].syllabus.indexOfLast { it.timeMs <= positionMs }
        lastRenderedMode = displayMode
        setText(rendered, BufferType.SPANNABLE)
    }

    private fun adaptiveVisibleLineCount(totalLines: Int): Int {
        if (totalLines <= 3) return totalLines
        val density = resources.displayMetrics.density
        val availableHeight = (height.takeIf { it > 0 } ?: (textSize * 8f).toInt()).toFloat()
        val estimatedLineHeight = textSize * 1.65f + 6f * density
        return (availableHeight / estimatedLineHeight).toInt().coerceIn(3, 7)
            .coerceAtMost(totalLines)
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
        rendered.setSpan(RelativeSizeSpan(0.72f), start, rendered.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        rendered.setSpan(ForegroundColorSpan(secondaryTextColor), start, rendered.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        rendered.setSpan(StyleSpan(Typeface.NORMAL), start, rendered.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
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
            val start = lineStart + range.first
            val end = lineStart + range.last + 1
            val nextStart = syllables.getOrNull(index + 1)?.timeMs
            val endMs = when {
                syllable.durationMs > 0L -> syllable.timeMs + syllable.durationMs
                nextStart != null && nextStart > syllable.timeMs -> nextStart
                line.durationMs > 0L -> line.timeMs + line.durationMs
                else -> syllable.timeMs
            }

            when {
                index < activeIndex || (index == activeIndex && endMs > syllable.timeMs && positionMs >= endMs) -> {
                    rendered.setSpan(ForegroundColorSpan(highlightColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    rendered.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                index == activeIndex && endMs > syllable.timeMs -> {
                    val fraction = ((positionMs - syllable.timeMs).toFloat() /
                        (endMs - syllable.timeMs).toFloat()).coerceIn(0f, 1f)
                    val span = KaraokeProgressSpan(highlightColor, fraction)
                    rendered.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    rendered.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    progressSpan = span
                    progressSpanStartMs = syllable.timeMs
                    progressSpanEndMs = endMs
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

    private class KaraokeProgressSpan(
        private val accentColor: Int,
        progress: Float
    ) : ReplacementSpan() {
        @Volatile
        private var progress = progress

        fun setProgress(value: Float) {
            progress = value.coerceIn(0f, 1f)
        }

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
            val currentProgress = progress
            if (currentProgress > 0f && width > 0f) {
                val save = canvas.save()
                canvas.clipRect(x, top.toFloat(), x + width * currentProgress, bottom.toFloat())
                paint.color = accentColor
                canvas.drawText(text, start, end, x, y.toFloat(), paint)
                canvas.restoreToCount(save)
            }
            paint.color = originalColor
        }
    }
}
