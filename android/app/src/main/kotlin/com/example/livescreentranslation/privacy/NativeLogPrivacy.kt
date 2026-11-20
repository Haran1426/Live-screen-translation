package com.example.livescreentranslation.privacy

/** Public Android NDK logging policy; no ML Kit reflection or global OS changes. */
object NativeLogPrivacy {
    private val loaded = runCatching { System.loadLibrary("screenlingo_privacy") }.isSuccess

    fun requireProtection() {
        check(loaded) { "텍스트 로그 보호를 준비하지 못해 번역을 중지했어요." }
        // Reinstall before each SDK client is constructed, including model preparation.
        install()
    }

    private external fun install()
}
