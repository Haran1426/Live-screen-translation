package com.example.livescreentranslation.capture

import java.text.BreakIterator
import java.util.Locale

/** A bounded, in-memory translation request. Counts never include overlay exclusions. */
data class FrameTextSelection(
    val texts: List<String>,
    val omittedBlocks: Int,
    val shortenedBlocks: Int,
    val candidateIndices: List<Int>,
    val shortenedIndices: Set<Int>,
) {
    val limited: Boolean get() = omittedBlocks > 0 || shortenedBlocks > 0

    fun message(source: String, target: String): String {
        val summary = "${texts.size}개 문단 · ${source.uppercase()} → ${target.uppercase()}"
        return if (limited) "$summary\n일부만 번역했어요 · 최대 12문단/문단당 600자\n스크롤해서 나눠 읽어 주세요."
            else summary
    }

    companion object {
        private val lineBreak = Regex("\\h*\\R\\h*")

        fun select(candidates: List<String>, source: String = "en"): FrameTextSelection {
            val readable = candidates.mapIndexedNotNull { index, candidate ->
                val text = normalize(candidate)
                if (text.codePointCount(0, text.length) >= 2) index to text else null
            }
            val selected = readable.take(12)
            val shortened = mutableSetOf<Int>()
            val texts = selected.mapIndexed { index, (_, text) ->
                if (text.codePointCount(0, text.length) <= 600) text
                else {
                    shortened.add(index)
                    boundedPrefix(text, Locale.forLanguageTag(source))
                }
            }
            return FrameTextSelection(
                texts, (readable.size - selected.size), shortened.size, selected.map { it.first }, shortened,
            )
        }

        private fun normalize(candidate: String): String {
            val text = candidate.trim()
            return lineBreak.replace(text) { match ->
                if (match.range.first == 0 || match.range.last == text.lastIndex) return@replace ""
                val before = text.codePointBefore(match.range.first)
                val after = text.codePointAt(match.range.last + 1)
                // Japanese and Chinese line wrapping usually introduces no word space.
                if (unspacedScript(before) && unspacedScript(after)) "" else " "
            }
        }

        private fun unspacedScript(codePoint: Int): Boolean =
            when (Character.UnicodeScript.of(codePoint)) {
                Character.UnicodeScript.HAN, Character.UnicodeScript.HIRAGANA,
                Character.UnicodeScript.KATAKANA -> true
                else -> codePoint in 0x3000..0x303F || codePoint in 0xFF01..0xFF65
            }

        private fun boundedPrefix(text: String, locale: Locale): String {
            val limit = text.offsetByCodePoints(0, 600)
            val minimum = text.offsetByCodePoints(0, 420)
            val sentences = BreakIterator.getSentenceInstance(locale).apply { setText(text) }
            val sentenceEnd = sentences.preceding(limit + 1)
            if (sentenceEnd >= minimum) return text.substring(0, sentenceEnd).trimEnd()
            val words = BreakIterator.getWordInstance(locale).apply { setText(text) }
            val end = if (words.isBoundary(limit)) limit else words.preceding(limit)
            return text.substring(0, if (end >= minimum) end else limit).trimEnd()
        }
    }
}
