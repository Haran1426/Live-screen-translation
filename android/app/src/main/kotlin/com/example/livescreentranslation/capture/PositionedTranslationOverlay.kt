package com.example.livescreentranslation.capture

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.TypedValue
import android.view.View
import com.example.livescreentranslation.R
import kotlin.math.roundToInt

internal data class PositionedTranslation(val bounds: Rect, val text: String)

/** One non-interactive layer. The sibling card exposes the translations as readable text. */
internal class PositionedTranslationOverlay(context: Context) : View(context) {
    private data class Label(val bounds: Rect, val layout: StaticLayout, val padding: Int)

    private val background = Paint().apply { color = Color.rgb(250, 252, 255) }
    // The service uses a platform theme and the official bitmap needs no tint.
    @android.annotation.SuppressLint("UseCompatLoadingForDrawables")
    private val badge = checkNotNull(context.getDrawable(R.drawable.google_translate))
    private val origin = IntArray(2)
    private var labels = emptyList<Label>()
    private var badgeBounds = Rect()

    init {
        visibility = INVISIBLE
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun setTranslations(translations: List<PositionedTranslation>, width: Int, height: Int, exclusions: List<Rect>) {
        val screen = Rect(0, 0, width, height)
        val margin = dp(6)
        val available = Rect(screen).apply { inset(margin, margin) }
        // System bars and the control window are already screen-space exclusions.
        for (edge in exclusions) {
            if (edge.width() == width && edge.top == 0) available.top = maxOf(available.top, edge.bottom + margin)
            if (edge.width() == width && edge.bottom == height) available.bottom = minOf(available.bottom, edge.top - margin)
            if (edge.height() == height && edge.left == 0) available.left = maxOf(available.left, edge.right + margin)
            if (edge.height() == height && edge.right == width) available.right = minOf(available.right, edge.left - margin)
        }
        val badgeHeight = dp(16)
        val badgeWidth = (badge.intrinsicWidth.toFloat() / badge.intrinsicHeight * badgeHeight).roundToInt()
        badgeBounds = Rect(
            available.right - badgeWidth - margin * 2, available.bottom - badgeHeight - margin * 2,
            available.right, available.bottom,
        )
        if (!available.contains(badgeBounds) || exclusions.any { Rect.intersects(it, badgeBounds) }) {
            clear()
            return
        }
        badge.setBounds(
            badgeBounds.left + margin, badgeBounds.top + margin,
            badgeBounds.right - margin, badgeBounds.bottom - margin,
        )
        val next = mutableListOf<Label>()
        for ((source, text) in translations) {
            if (text.isBlank() || !available.contains(source)) continue
            val box = Rect(source).apply { inset(-dp(3), -dp(2)) }
            if (!available.contains(box) || Rect.intersects(box, badgeBounds) ||
                exclusions.any { Rect.intersects(it, box) } ||
                translations.any { it.bounds !== source && Rect.intersects(it.bounds, box) } ||
                next.any { Rect.intersects(it.bounds, box) }
            ) continue
            val padding = dp(2)
            val layout = fitText(text, box.width() - padding * 2, box.height() - padding * 2) ?: continue
            next.add(Label(box, layout, padding))
        }
        labels = next
        visibility = if (labels.isEmpty()) INVISIBLE else VISIBLE
        invalidate()
    }

    fun clear() {
        labels = emptyList()
        visibility = INVISIBLE
        invalidate()
    }

    private fun fitText(text: String, width: Int, height: Int): StaticLayout? {
        if (width <= 0 || height <= 0) return null
        for (size in 20 downTo 12) {
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(24, 35, 54)
                textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, size.toFloat(), resources.displayMetrics)
            }
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false)
                .build()
            if (layout.height <= height && (0 until layout.lineCount).all { layout.getLineWidth(it) <= width }) {
                return layout
            }
        }
        return null
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        getLocationOnScreen(origin)
        canvas.save()
        canvas.translate(-origin[0].toFloat(), -origin[1].toFloat())
        for ((bounds, layout, padding) in labels) {
            canvas.drawRect(bounds, background)
            canvas.save()
            canvas.clipRect(bounds)
            canvas.translate((bounds.left + padding).toFloat(), bounds.top + (bounds.height() - layout.height) / 2f)
            layout.draw(canvas)
            canvas.restore()
        }
        if (labels.isNotEmpty()) {
            canvas.drawRect(badgeBounds, background)
            badge.draw(canvas)
        }
        canvas.restore()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
}
