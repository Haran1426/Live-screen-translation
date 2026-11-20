package com.example.livescreentranslation.capture

import org.junit.Assert.*
import org.junit.Test

class FrameWorkGuardTest {
    private class Fixture {
        var now = 0L
        var timeouts = 0
        val queued = mutableMapOf<Runnable, Long>()
        val guard = FrameWorkGuard(
            clock = { now },
            schedule = { callback, delay -> queued[callback] = now + delay },
            cancel = { queued.remove(it); Unit },
            onTimeout = { timeouts++ },
        )

        fun dispatch() {
            val due = queued.filterValues { it <= now }.keys.toList()
            due.forEach { queued.remove(it); it.run() }
        }
    }

    @Test fun stuckWorkClosesOnceAndRejectsLateResultsAndNewFrames() {
        val f = Fixture()
        val frame = f.guard.begin()!!
        f.now = 29_999
        f.dispatch()
        assertTrue(f.guard.accepts(frame))
        f.now = 30_000
        f.dispatch()
        assertEquals(1, f.timeouts)
        assertFalse(f.guard.accepts(frame))
        assertNull(f.guard.begin())
        f.guard.complete(frame)
        frame.deadline.run()
        assertEquals(1, f.timeouts)
        assertTrue(f.queued.isEmpty())
    }

    @Test fun pauseAndRotationInvalidateResultsWithoutOverlappingOrExtendingWork() {
        val f = Fixture()
        val old = f.guard.begin()!!
        f.now = 20_000
        repeat(3) { f.guard.invalidate() }
        assertFalse(f.guard.accepts(old))
        assertNull(f.guard.begin())
        assertEquals(30_000L, f.queued.values.single())
        f.now = 30_000
        f.dispatch()
        assertEquals(1, f.timeouts)
    }

    @Test fun invalidatedWorkCanFinishBeforeDeadlineAndFreshWorkGetsItsOwnDeadline() {
        val f = Fixture()
        val old = f.guard.begin()!!
        f.guard.invalidate()
        f.now = 10_000
        f.guard.complete(old)
        val fresh = f.guard.begin()!!
        assertTrue(f.guard.accepts(fresh))
        assertEquals(40_000L, f.queued.values.single())
        // A duplicate late callback/timer cannot clear or shorten the fresh frame.
        f.guard.complete(old)
        old.deadline.run()
        assertTrue(f.guard.busy)
        assertNull(f.guard.begin())
        f.now = 30_000
        f.dispatch()
        assertEquals(0, f.timeouts)
        assertTrue(f.guard.accepts(fresh))
    }

    @Test fun completionAtDeadlineCannotHideTimeoutBeforeTimerIsDispatched() {
        val f = Fixture()
        val frame = f.guard.begin()!!
        f.now = 30_000
        f.guard.complete(frame)
        assertEquals(1, f.timeouts)
        assertFalse(f.guard.accepts(frame))
        assertNull(f.guard.begin())
    }

    @Test fun overdueResultCannotBePublishedAheadOfDelayedTimer() {
        val f = Fixture()
        val frame = f.guard.begin()!!
        f.now = 35_000
        var published = false
        if (f.guard.accepts(frame)) published = true
        assertFalse(published)
        assertEquals(1, f.timeouts)
    }

    @Test fun normalCompletionCancelsDeadlineAndKeepsSessionOpen() {
        val f = Fixture()
        val frame = f.guard.begin()!!
        f.now = 2_000
        f.guard.complete(frame)
        assertTrue(f.queued.isEmpty())
        assertFalse(f.guard.busy)
        f.now = 60_000
        frame.deadline.run()
        assertEquals(0, f.timeouts)
        assertNotNull(f.guard.begin())
    }

    @Test fun stopCancelsDeadlineAndRejectsOldCallbacksPermanently() {
        val f = Fixture()
        val frame = f.guard.begin()!!
        f.guard.close()
        assertTrue(f.queued.isEmpty())
        assertFalse(f.guard.accepts(frame))
        assertNull(f.guard.begin())
        f.now = 90_000
        frame.deadline.run()
        f.guard.complete(frame)
        assertEquals(0, f.timeouts)
    }

    @Test fun earlyTimerRearmsForRemainingTimeWithoutChangingDeadline() {
        val f = Fixture()
        val frame = f.guard.begin()!!
        f.queued.clear()
        f.now = 7_000
        frame.deadline.run()
        assertEquals(30_000L, f.queued.values.single())
        assertEquals(0, f.timeouts)
        f.now = 30_000
        f.dispatch()
        assertEquals(1, f.timeouts)
    }
}
