package com.example.livescreentranslation.capture

import android.content.Context

data class CaptureConfig(
    val source: String = "en",
    val target: String = "ko",
    val wifiOnly: Boolean = true,
    val intervalMs: Int = 2000,
    val cardTextSp: Int = 16,
    val cardHeightDp: Int = 300,
) {
    fun toMap(): Map<String, Any> = mapOf(
        "source" to source, "target" to target,
        "wifiOnly" to wifiOnly, "intervalMs" to intervalMs,
        "cardTextSp" to cardTextSp, "cardHeightDp" to cardHeightDp,
    )

    fun save(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("source", source).putString("target", target)
            .putBoolean("wifiOnly", wifiOnly).putInt("intervalMs", intervalMs)
            .putInt("cardTextSp", cardTextSp).putInt("cardHeightDp", cardHeightDp).apply()
    }

    companion object {
        private const val PREFS = "screenlingo_settings"
        private val languages = setOf("en", "ko", "ja", "zh")

        fun fromMap(map: Map<*, *>): CaptureConfig {
            val source = map["source"] as? String ?: "en"
            val target = map["target"] as? String ?: "ko"
            require(source in languages && target in languages && source != target) {
                "서로 다른 원문 언어와 번역 언어를 선택해 주세요."
            }
            val interval = (map["intervalMs"] as? Number)?.toInt() ?: 2000
            require(interval in setOf(1000, 2000, 4000)) { "지원하지 않는 화면 확인 간격이에요." }
            val textSp = (map["cardTextSp"] as? Number)?.toInt() ?: 16
            val heightDp = (map["cardHeightDp"] as? Number)?.toInt() ?: 300
            require(textSp in setOf(16, 20, 24) && heightDp in setOf(300, 420)) { "지원하지 않는 번역 카드 크기예요." }
            return CaptureConfig(source, target, map["wifiOnly"] as? Boolean ?: true, interval, textSp, heightDp)
        }

        fun load(context: Context): CaptureConfig {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return runCatching {
                fromMap(mapOf(
                    "source" to prefs.getString("source", "en"),
                    "target" to prefs.getString("target", "ko"),
                    "wifiOnly" to prefs.getBoolean("wifiOnly", true),
                    "intervalMs" to prefs.getInt("intervalMs", 2000),
                    "cardTextSp" to prefs.getInt("cardTextSp", 16).takeIf { it in setOf(16, 20, 24) },
                    "cardHeightDp" to prefs.getInt("cardHeightDp", 300).takeIf { it in setOf(300, 420) },
                ))
            }.getOrDefault(CaptureConfig())
        }
    }
}
