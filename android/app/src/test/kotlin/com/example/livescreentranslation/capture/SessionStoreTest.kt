package com.example.livescreentranslation.capture

import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class SessionStoreTest {
    @After fun cleanup() { SessionStore.clear() }

    @Test fun stopRemovesSensitiveTextAndTiming() {
        SessionStore.update("running", "읽는 중", listOf(mapOf("original" to "secret", "translated" to "비밀")), 123)
        assertTrue(SessionStore.active)
        SessionStore.clear()
        assertFalse(SessionStore.active)
        assertEquals(emptyList<Map<String, String>>(), SessionStore.snapshot()["blocks"])
        assertEquals(0L, SessionStore.snapshot()["elapsedMs"])
    }

    @Test fun failureDoesNotRetainPreviousTranslation() {
        SessionStore.update("running", "", listOf(mapOf("translated" to "sensitive")))
        SessionStore.clear("공유가 종료됐어요")
        assertEquals("error", SessionStore.phase)
        assertFalse(SessionStore.active)
        assertEquals(emptyList<Map<String, String>>(), SessionStore.snapshot()["blocks"])
    }

    @Test fun removedActivityListenerDoesNotReceiveFurtherEvents() {
        var calls = 0
        val listener: (Map<String, Any>) -> Unit = { calls++ }
        SessionStore.addListener(listener)
        assertEquals(1, calls)
        SessionStore.update("paused")
        assertEquals(2, calls)
        SessionStore.removeListener(listener)
        SessionStore.clear()
        assertEquals(2, calls)
    }
}
