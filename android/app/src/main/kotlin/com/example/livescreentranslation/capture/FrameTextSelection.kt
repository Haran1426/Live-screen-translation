package com.example.livescreentranslation.capture

/** A bounded, in-memory translation request. Counts never include overlay exclusions. */
data class FrameTextSelection(
    val texts: List<String>,
    val omittedBlocks: Int,
    val shortenedBlocks: Int,
) {
    val limited: Boolean get() = omittedBlocks > 0 || shortenedBlocks > 0

    fun message(source: String, target: String): String {
        val summary = "${texts.size}개 문단 · ${source.uppercase()} → ${target.uppercase()}"
        return if (limited) "$summary\n일부만 번역했어요 · 최대 12문단/문단당 600자\n스크롤해서 나눠 읽어 주세요."
            else summary
    }

    companion object {
        fun select(candidates: List<String>): FrameTextSelection {
            val readable = candidates.map { it.trim() }.filter { it.length >= 2 }
            var shortened = 0
            val texts = readable.take(12).map { text ->
                if (text.codePointCount(0, text.length) > 600) {
                    shortened++
                    text.substring(0, text.offsetByCodePoints(0, 600))
                } else text
            }
            return FrameTextSelection(texts, (readable.size - 12).coerceAtLeast(0), shortened)
        }
    }
}
