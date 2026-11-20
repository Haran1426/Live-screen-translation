package com.example.livescreentranslation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.app.UiAutomation
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.Process
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.livescreentranslation.privacy.NativeLogPrivacy
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import java.text.Normalizer
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Authored mixed-script bitmap only. Coverage flags are not accuracy scores. */
@RunWith(AndroidJUnit4::class)
class MixedReadingTest {
    @Test fun englishSelection() = check("en")
    @Test fun koreanSelection() = check("ko")
    @Test fun japaneseSelection() = check("ja")
    @Test fun chineseSelection() = check("zh")

    private fun check(source: String) {
        var stage = "setup"
        val bitmap = Bitmap.createBitmap(1500, 500, Bitmap.Config.ARGB_8888)
        var submitted = false
        var engine: TextRecognizer? = null
        try {
            NativeLogPrivacy.requireProtection()
            val recognizer = when (source) {
                "ja" -> TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
                "zh" -> TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
                "ko" -> TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
                else -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            }
            engine = recognizer
            val lines = listOf(
                "A17 The library opens at 9 in the morning.",
                "A17 図書館は午前9時に開きます。",
                "A17 도서관은 오전 9시에 문을 엽니다.",
                "A17 图书馆上午9点开放。",
            )
            Canvas(bitmap).apply {
                drawColor(Color.WHITE)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 48f }
                lines.forEachIndexed { index, line -> drawText(line, 30f, 90f + index * 110f, paint) }
            }
            stage = "ocr"
            val task = recognizer.process(InputImage.fromBitmap(bitmap, 0))
            submitted = true
            task.addOnCompleteListener(Executor { it.run() }) { bitmap.recycle() }
            val recognized = Tasks.await(task, 45, TimeUnit.SECONDS).text
            val normalized = Normalizer.normalize(recognized, Normalizer.Form.NFKC).lowercase().filterNot { it.isWhitespace() }
            val coverage = linkedMapOf(
                "en" to normalized.contains("library"),
                "ko" to normalized.contains("도서관"),
                "ja" to normalized.contains("図書館"),
                "zh" to (normalized.contains("图书馆") || normalized.contains("圖書館")),
            )
            stage = "selected-script"
            assertTrue("The selected fixture anchor was lost.", coverage[source] == true)
            assertTrue("The ASCII fixture identifier was lost.", normalized.contains("a17"))
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            instrumentation.sendStatus(2, Bundle().apply {
                putString("stream", "Mixed OCR source=$source: " + coverage.entries.joinToString { "${it.key}=${it.value}" })
            })
            stage = "log-check"
            Log.w("ScreenLingoMixedFixture", "Fixed authored mixed reading diagnostic.")
            val automation = if (Build.VERSION.SDK_INT >= 31) instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_USE_ACCESSIBILITY) else instrumentation.uiAutomation
            val descriptor = automation.executeShellCommand("logcat -d --pid=${Process.myPid()} -v brief")
            val logs = ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
            assertTrue("The safe diagnostic probe is missing.", logs.contains("Runtime diagnostic redacted to protect screen text."))
            assertTrue("An authored OCR payload appeared in logs.", lines.none { logs.contains(it) } && !logs.contains(recognized))
        } catch (failure: Throwable) {
            throw AssertionError("Mixed OCR $source failed at $stage (${failure.javaClass.simpleName}).")
        } finally {
            engine?.close()
            if (!submitted) bitmap.recycle()
        }
    }
}
