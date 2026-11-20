package com.example.livescreentranslation.capture

/** Main-thread-only session state. No screenshots or translation history are persisted. */
object SessionStore {
    var appVisible = false
        set(value) {
            val wasAllowed = captureAllowed
            field = value
            if (wasAllowed != captureAllowed) onCaptureVisibilityChanged?.invoke()
        }
    var practiceVisible = false
        set(value) {
            val wasAllowed = captureAllowed
            field = value
            if (wasAllowed != captureAllowed) onCaptureVisibilityChanged?.invoke()
        }
    val captureAllowed: Boolean get() = !appVisible || practiceVisible
    var onCaptureVisibilityChanged: (() -> Unit)? = null
    private val listeners = mutableSetOf<(Map<String, Any>) -> Unit>()
    var phase = "idle"
        private set
    private var message = "번역할 화면을 준비해 주세요"
    private var blocks = emptyList<Map<String, String>>()
    private var elapsedMs = 0L

    val active: Boolean get() = phase in setOf("starting", "running", "paused")

    fun snapshot(): Map<String, Any> = mapOf(
        "phase" to phase, "message" to message,
        "blocks" to blocks, "elapsedMs" to elapsedMs,
    )

    fun update(
        phase: String = this.phase,
        message: String = this.message,
        blocks: List<Map<String, String>> = this.blocks,
        elapsedMs: Long = this.elapsedMs,
    ) {
        this.phase = phase
        this.message = message
        this.blocks = blocks
        this.elapsedMs = elapsedMs
        val value = snapshot()
        listeners.toList().forEach { it(value) }
    }

    fun clear(error: String? = null) = update(
        phase = if (error == null) "idle" else "error",
        message = error ?: "번역을 종료했어요. 화면과 번역문을 지웠어요.",
        blocks = emptyList(), elapsedMs = 0,
    )

    fun addListener(listener: (Map<String, Any>) -> Unit) {
        listeners.add(listener)
        listener(snapshot())
    }

    fun removeListener(listener: (Map<String, Any>) -> Unit) { listeners.remove(listener) }
}
