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
import android.view.Choreographer
import android.view.Gravity
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.res.ResourcesCompat
import code.name.monkey.retromusic.R
import code.name.monkey.retromusic.model.lyrics.ttml.TtmlLine
import code.name.monkey.retromusic.model.lyrics.ttml.TtmlLyrics
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min

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
    private var lastRenderedMode: DisplayMode? = null
    private var highlightColor: Int = currentTextColor
    private var secondaryTextColor: Int = currentTextColor
    private var displayMode = DisplayMode.ORIGINAL
    private var lastPositionMs = 0L
    private val progressSpans = mutableListOf<TimedProgressSpan>()
    private var playbackRunning = false
    private var frameCallback: Choreographer.FrameCallback? = null
    private var lastFrameNanos = 0L
    private var interpolatedPositionMs = 0.0
    private var clockDriftMs = 0.0
    private var clockInitialized = false
    private val choreographer: Choreographer by lazy { Choreographer.getInstance() }
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
        interpolatedPositionMs = 0.0
        clockDriftMs = 0.0
        clockInitialized = false
        progressSpans.clear()
        displayMode = DisplayMode.ORIGINAL
        generatedTransliterations.clear()
        isSelected = lyrics != null
        renderLyrics(0L)
    }

    fun setPlaybackRunning(running: Boolean) {
        playbackRunning = running
        if (running && isAttachedToWindow) {
            startFrameClock()
        } else {
            stopFrameClock()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (playbackRunning) startFrameClock()
    }

    override fun onDetachedFromWindow() {
        stopFrameClock()
        super.onDetachedFromWindow()
    }

    private fun startFrameClock() {
        if (frameCallback != null || !playbackRunning || !isAttachedToWindow) return
        lastFrameNanos = 0L
        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (!playbackRunning || !isAttachedToWindow) {
                    frameCallback = null
                    lastFrameNanos = 0L
                    return
                }
                if (lastFrameNanos != 0L && clockInitialized) {
                    val elapsedMs = (frameTimeNanos - lastFrameNanos) / 1_000_000.0
                    val correction = clockDriftMs * min(1.0, elapsedMs / CLOCK_BLEND_MS)
                    clockDriftMs -= correction
                    interpolatedPositionMs += elapsedMs + correction
                    renderAt(interpolatedPositionMs.toLong())
                }
                lastFrameNanos = frameTimeNanos
                choreographer.postFrameCallback(this)
            }
        }
        frameCallback = callback
        choreographer.postFrameCallback(callback)
    }

    private fun stopFrameClock() {
        frameCallback?.let(choreographer::removeFrameCallback)
        frameCallback = null
        lastFrameNanos = 0L
    }

    fun updateTime(positionMs: Long) {
        val reported = positionMs.coerceAtLeast(0L)
        if (!clockInitialized || !playbackRunning ||
            abs(reported - interpolatedPositionMs) >= CLOCK_SNAP_MS
        ) {
            interpolatedPositionMs = reported.toDouble()
            clockDriftMs = 0.0
            clockInitialized = true
        } else {
            // Blend small player-reported corrections instead of jumping the lyric fill.
            clockDriftMs = reported - interpolatedPositionMs
        }
        renderAt(interpolatedPositionMs.toLong())
    }

    private fun renderAt(positionMs: Long) {
        lastPositionMs = positionMs.coerceAtLeast(0L)
        val lines = lyrics?.lines ?: run {
            text = null
            progressSpans.clear()
            return
        }
        if (lines.isEmpty()) {
            text = null
            progressSpans.clear()
            return
        }

        val activeLine = lines.indexOfLast { it.timeMs <= lastPositionMs }.coerceAtLeast(0)
        if (activeLine != lastLineIndex || displayMode != lastRenderedMode) {
            renderLyrics(lastPositionMs)
            return
        }

        // Update each timed word's paint fraction without replacing text or recalculating layout.
        if (progressSpans.isEmpty()) return
        progressSpans.forEach { timed ->
            timed.span.setProgress(progressFraction(lastPositionMs, timed.startMs, timed.endMs))
        }
        invalidate()
    }

    private companion object {
        const val CLOCK_SNAP_MS = 400.0
        const val CLOCK_BLEND_MS = 250.0
    }

    private fun renderLyrics(positionMs: Long) {
        val lines = lyrics?.lines ?: run {
            text = null
            progressSpans.clear()
            return
        }
        if (lines.isEmpty()) {
            text = null
            progressSpans.clear()
            return
        }

        val activeLine = lines.indexOfLast { it.timeMs <= positionMs }.coerceAtLeast(0)
        val visibleCount = adaptiveVisibleLineCount(lines.size)
        val beforeCount = (visibleCount - 1) / 2
        val firstVisible = (activeLine - beforeCount).coerceAtLeast(0)
            .coerceAtMost((lines.size - visibleCount).coerceAtLeast(0))
        val lastVisible = (firstVisible + visibleCount - 1).coerceAtMost(lines.lastIndex)
        val rendered = SpannableStringBuilder()
        progressSpans.clear()

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
        lastRenderedMode = displayMode
        setText(rendered, BufferType.SPANNABLE)
    }

    private fun adaptiveVisibleLineCount(totalLines: Int): Int {
        if (totalLines <= 3) return totalLines
        val density = resources.displayMetrics.density
        val availableHeight = (height.takeIf { it > 0 } ?: (textSize * 8f).toInt()).toFloat()
        val estimatedLineHeight = textSize * 1.65f + 6f * density
        return (availableHeight / estimatedLineHeight).toInt().coerceIn(1, 7)
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
        syllables.forEachIndexed { index, syllable ->
            val range = findSyllableRange(line.text, syllable, searchFrom) ?: return@forEachIndexed
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
            val span = KaraokeProgressSpan(
                highlightColor,
                progressFraction(positionMs, syllable.timeMs, endMs)
            )
            rendered.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            rendered.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            progressSpans.add(TimedProgressSpan(span, syllable.timeMs, endMs))
        }
    }

    private fun progressFraction(positionMs: Long, startMs: Long, endMs: Long): Float {
        if (positionMs < startMs) return 0f
        if (endMs <= startMs || positionMs >= endMs) return 1f
        return ((positionMs - startMs).toFloat() / (endMs - startMs).toFloat())
            .coerceIn(0f, 1f)
    }

    private data class TimedProgressSpan(
        val span: KaraokeProgressSpan,
        val startMs: Long,
        val endMs: Long
    )

    private fun transliterationForLine(index: Int, line: TtmlLine): String? {
        line.transliteration?.takeIf { it.isNotBlank() }?.let { return it }
        val generated = generatedTransliterations.getOrPut(index) {
            TtmlTransliterator.toLatinOrNull(line.text).orEmpty()
        }
        return generated.takeIf { it.isNotBlank() }
    }

    private fun findSyllableRange(
        source: String,
        syllable: code.name.monkey.retromusic.model.lyrics.ttml.TtmlSyllable,
        searchFrom: Int
    ): IntRange? {
        if (syllable.startIndex >= 0 && syllable.endIndex > syllable.startIndex &&
            syllable.endIndex <= source.length
        ) {
            var start = syllable.startIndex.coerceAtLeast(searchFrom)
            var end = syllable.endIndex
            while (start < end && source[start].isWhitespace()) start++
            while (end > start && source[end - 1].isWhitespace()) end--
            if (end > start) return start until end
        }
        return findWordRange(source, syllable.text, searchFrom)
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
