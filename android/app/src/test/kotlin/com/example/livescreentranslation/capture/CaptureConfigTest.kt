package com.example.livescreentranslation.capture

import org.junit.Assert.*
import org.junit.Test

class CaptureConfigTest {
    @Test fun legacySettingsKeepTheirLanguageAndDefaultReadingSizes() {
        val config = CaptureConfig.fromMap(mapOf("source" to "ja", "target" to "en"))
        assertEquals("ja", config.source)
        assertEquals("en", config.target)
        assertEquals(16, config.cardTextSp)
        assertEquals(300, config.cardHeightDp)
    }
    @Test fun invalidLanguageAndIntervalsAreRejectedAtTheNativeBoundary() {
        for (arguments in listOf(
            mapOf("source" to "en", "target" to "en"),
            mapOf("source" to "invalid", "target" to "ko"),
            mapOf("intervalMs" to 0),
            mapOf("intervalMs" to -1000),
            mapOf("cardTextSp" to 17),
            mapOf("cardHeightDp" to 0),
            mapOf("cardHeightDp" to 100000),
        )) {
            assertTrue(runCatching { CaptureConfig.fromMap(arguments) }.isFailure)
        }
    }

    @Test fun settingsRoundTripPreservesNetworkChoiceAndLanguagePair() {
        val config = CaptureConfig("ja", "ko", false, 4000, 24, 420)
        assertEquals(config, CaptureConfig.fromMap(config.toMap()))
    }
}
