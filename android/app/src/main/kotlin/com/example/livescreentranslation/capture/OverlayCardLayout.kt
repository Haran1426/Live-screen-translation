package com.example.livescreentranslation.capture

/** Logical dp geometry; no screen text or session state is retained here. */
data class OverlayCardLayout(val width: Int, val height: Int, val compact: Boolean) {
    companion object {
        fun calculate(widthDp: Int, heightDp: Int, fontScale: Float, cardTextSp: Int = 16, cardHeightDp: Int = 300): OverlayCardLayout {
            require(widthDp > 0 && heightDp > 0)
            require(cardTextSp in setOf(16, 20, 24) && cardHeightDp in setOf(300, 420))
            val compact = widthDp > heightDp
            val scale = if (fontScale.isFinite() && fontScale > 0) fontScale.coerceIn(1f, 1.5f) else 1f
            val availableWidth = (widthDp - 24).coerceAtLeast(1)
            val availableHeight = (heightDp - 48).coerceAtLeast(1)
            val width = if (compact) minOf(availableWidth, (widthDp / 2).coerceIn(300, 420)) else availableWidth
            val targetHeight = ((cardHeightDp + (cardTextSp - 16) * 6) * scale).toInt()
            val portraitLimit = if (cardHeightDp == 420) heightDp * 2 / 3 else heightDp / 2
            val height = minOf(targetHeight, if (compact) availableHeight else minOf(availableHeight, portraitLimit).coerceAtLeast(1))
            return OverlayCardLayout(width, height, compact)
        }
    }
}
