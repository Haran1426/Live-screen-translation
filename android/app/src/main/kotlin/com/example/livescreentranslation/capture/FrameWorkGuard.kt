package com.example.livescreentranslation.capture

/** Main-thread-only ownership and deadline for one capture → OCR → translation. */
internal class FrameWorkGuard(
    private val clock: () -> Long,
    private val schedule: (Runnable, Long) -> Unit,
    private val cancel: (Runnable) -> Unit,
    private val onTimeout: () -> Unit,
    private val timeoutMs: Long = 30_000,
) {
    class Ticket internal constructor(val startedAt: Long, internal val epoch: Long) {
        internal lateinit var deadline: Runnable
    }

    private var epoch = 0L
    private var active: Ticket? = null
    private var closed = false
    val busy: Boolean get() = active != null

    init { require(timeoutMs > 0) }

    fun begin(): Ticket? {
        if (expireIfNeeded() || closed || busy) return null
        val ticket = Ticket(clock(), epoch)
        ticket.deadline = Runnable {
            if (!closed && active === ticket && !expireIfNeeded()) {
                schedule(ticket.deadline, remaining(ticket))
            }
        }
        active = ticket
        schedule(ticket.deadline, timeoutMs)
        return ticket
    }

    fun accepts(ticket: Ticket): Boolean =
        !expireIfNeeded() && !closed && active === ticket && ticket.epoch == epoch

    fun complete(ticket: Ticket) {
        // A late SDK callback must not erase an overdue frame before its timer runs.
        if (expireIfNeeded() || active !== ticket) return
        cancel(ticket.deadline)
        active = null
    }

    fun invalidate() {
        epoch++
        // Pause/rotation discards results but cannot cancel ML Kit work. Keep ownership
        // and its original deadline until that work completes; never overlap a new frame.
    }

    fun close() {
        closed = true
        active?.let { cancel(it.deadline) }
        active = null
    }

    private fun remaining(ticket: Ticket): Long =
        (timeoutMs - (clock() - ticket.startedAt)).coerceAtLeast(0)

    private fun expireIfNeeded(): Boolean {
        val ticket = active ?: return false
        if (remaining(ticket) > 0) return false
        close()
        onTimeout()
        return true
    }
}
