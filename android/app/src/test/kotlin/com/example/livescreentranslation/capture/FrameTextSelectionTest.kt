package com.example.livescreentranslation.capture

import org.junit.Assert.*
import org.junit.Test

class FrameTextSelectionTest {
    @Test fun layoutLineBreaksKeepWordsAndUnspacedScriptsTogether() {
        val result = FrameTextSelection.select(listOf(
            "Do not\r\n  open the door.",
            "図書\n館へ行きます。",
            "문을\n열지 마세요.",
            "图书\n馆今天开放。",
        ))
        assertEquals(listOf("Do not open the door.", "図書館へ行きます。", "문을 열지 마세요.", "图书馆今天开放。"), result.texts)
    }

    @Test fun boundedSentenceAndSkippedFragmentsKeepTheirSourceIndices() {
        val wholeSentences = "The library is closed today. ".repeat(19).trimEnd()
        val longText = "$wholeSentences Unordered " + "text ".repeat(40)
        val result = FrameTextSelection.select(listOf("x", "First visible block", " ", longText, "Another block"))
        assertEquals(listOf(1, 3, 4), result.candidateIndices)
        assertEquals(setOf(1), result.shortenedIndices)
        assertEquals(wholeSentences, result.texts[1])
    }

    @Test fun exactBoundariesDoNotClaimMissingText() {
        val result = FrameTextSelection.select(List(12) { "A".repeat(600) })
        assertEquals(12, result.texts.size)
        assertFalse(result.limited)
    }

    @Test fun paragraphLimitPreservesOrderAndReportsOmissions() {
        val result = FrameTextSelection.select(List(14) { "Authored paragraph $it" })
        assertEquals("Authored paragraph 11", result.texts.last())
        assertEquals(2, result.omittedBlocks)
        assertTrue(result.limited)
    }

    @Test fun longParagraphKeepsAUnicodeSafePrefixAndReportsTheCut() {
        val input = "A".repeat(599) + "😀" + "omitted"
        val result = FrameTextSelection.select(listOf(input))
        assertEquals("A".repeat(599) + "😀", result.texts.single())
        assertEquals(600, result.texts.single().codePointCount(0, result.texts.single().length))
        assertEquals(1, result.shortenedBlocks)
    }

    @Test fun whitespaceAndUnreadableFragmentsDoNotConsumeParagraphBudget() {
        val result = FrameTextSelection.select(listOf(" ", "X", "  authored text  "))
        assertEquals(listOf("authored text"), result.texts)
        assertFalse(result.limited)
    }

    @Test fun mixedScriptsArePreservedWithoutPretendingToDetectLanguages() {
        val input = "A17 library 図書館 도서관 图书馆"
        assertEquals(listOf(input), FrameTextSelection.select(listOf(input)).texts)
    }

    @Test fun identicalTranslatedPrefixCanHaveDifferentLimitMetadata() {
        val full = FrameTextSelection.select(listOf("A".repeat(600)))
        val cut = FrameTextSelection.select(listOf("A".repeat(601)))
        assertEquals(full.texts, cut.texts)
        assertNotEquals(full, cut)
        assertFalse(full.message("en", "ko").contains("일부만"))
        assertTrue(cut.message("en", "ko").contains("일부만 번역했어요"))
    }

    @Test fun noticeNeverIncludesCapturedText() {
        val result = FrameTextSelection.select(listOf("Authored-secret-".repeat(80)))
        assertFalse(result.message("en", "ko").contains("Authored-secret"))
        assertTrue(result.message("en", "ko").contains("EN → KO"))
    }
}
