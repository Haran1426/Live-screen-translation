package com.example.livescreentranslation

import android.app.UiAutomation
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.ParcelFileDescriptor
import android.os.Build
import android.os.Process
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.livescreentranslation.capture.CaptureConfig
import com.example.livescreentranslation.capture.ModelRepository
import com.example.livescreentranslation.privacy.NativeLogPrivacy
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.text.Normalizer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

/** Authored in-memory fixtures only: 4 OCR scripts and all 12 directed language pairs. */
@RunWith(AndroidJUnit4::class)
class MultilingualPipelineTest {
    companion object {
        private lateinit var automation: UiAutomation

        @JvmStatic @BeforeClass fun prepareFixtureModelsOnce() {
            var language = "test-shell"
            try {
                // This suite needs shell log reads, never an accessibility/view tree.
                // API30 keeps the public default path; the no-accessibility flag is API31+.
                val flags = if (Build.VERSION.SDK_INT >= 31) UiAutomation.FLAG_DONT_USE_ACCESSIBILITY else 0
                automation = InstrumentationRegistry.getInstrumentation().getUiAutomation(flags)
                language = "protection"
                NativeLogPrivacy.requireProtection()
                Log.w("ScreenLingoFixture", "Synthetic multilingual diagnostic probe.")
                for (target in listOf("ko", "ja", "zh")) {
                    language = target
                    val config = CaptureConfig(source = "en", target = target, wifiOnly = true)
                    if (!Tasks.await(ModelRepository.ready(config), 30, TimeUnit.SECONDS)) {
                        Tasks.await(ModelRepository.prepare(config), 180, TimeUnit.SECONDS)
                    }
                }
            } catch (failure: Throwable) {
                // Fail setup once; do not repeat a stuck download in each source test.
                throw AssertionError("Fixture model setup failed at $language (${failure.javaClass.simpleName}).")
            }
        }
    }

    @Test fun englishToKorean() = verify("en", "ko")
    @Test fun englishToJapanese() = verify("en", "ja")
    @Test fun englishToChinese() = verify("en", "zh")
    @Test fun japaneseToEnglish() = verify("ja", "en")
    @Test fun japaneseToKorean() = verify("ja", "ko")
    @Test fun japaneseToChinese() = verify("ja", "zh")
    @Test fun chineseToEnglish() = verify("zh", "en")
    @Test fun chineseToKorean() = verify("zh", "ko")
    @Test fun chineseToJapanese() = verify("zh", "ja")
    @Test fun koreanToEnglish() = verify("ko", "en")
    @Test fun koreanToJapanese() = verify("ko", "ja")
    @Test fun koreanToChinese() = verify("ko", "zh")

    private fun verify(source: String, target: String) {
        val fixture = when (source) {
            "en" -> "The library opens at 9 in the morning."
            "ja" -> "図書館は午前9時に開きます。"
            "zh" -> "图书馆上午9点开放。"
            else -> "도서관은 오전 9시에 문을 엽니다."
        }
        var stage = "protection"
        try {
            NativeLogPrivacy.requireProtection()
            stage = "ocr"
            val recognizer = when (source) {
                "ja" -> TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
                "zh" -> TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
                "ko" -> TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
                else -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            }
            val recognized = try { recognize(recognizer, fixture) } finally { recognizer.close() }
            stage = "source-library"
            assertTrue("The OCR fixture lost its library anchor.", hasLibrary(source, recognized))
            stage = "source-time"
            assertTrue("The OCR fixture lost its time anchor.", hasNine(recognized))
            assertNoPayloads(fixture, recognized)
            val config = CaptureConfig(source = source, target = target, wifiOnly = true)
            stage = "models-$target"
            assertTrue("The fixture models are not ready.", Tasks.await(ModelRepository.ready(config), 30, TimeUnit.SECONDS))
            stage = "translate-$target"
            val translator = ModelRepository.translator(config)
            val translated = try {
                Tasks.await(translator.translate(recognized), 60, TimeUnit.SECONDS)
            } finally { translator.close() }
            stage = "log-$target"
            assertNoPayloads(fixture, recognized, translated)
            stage = "target-library"
            assertTrue("The translation fixture lost its library anchor.", hasLibrary(target, translated))
            stage = "target-time"
            assertTrue("The translation fixture lost its time anchor.", hasNine(translated))
            assertFalse("Translation returned the unchanged source.", translated == recognized)
        } catch (failure: Throwable) {
            // JUnit status must not dump SDK exception messages or recognized/translated text.
            throw AssertionError("Multilingual fixture $source-$target failed at $stage (${failure.javaClass.simpleName}).")
        }
    }

    private fun recognize(recognizer: TextRecognizer, fixture: String): String {
        val bitmap = Bitmap.createBitmap(1500, 180, Bitmap.Config.ARGB_8888)
        var submitted = false
        try {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 52f }
            if (paint.measureText(fixture) > 1440f) paint.textSize *= 1440f / paint.measureText(fixture)
            Canvas(bitmap).apply { drawColor(Color.WHITE); drawText(fixture, 30f, 110f, paint) }
            val task = recognizer.process(InputImage.fromBitmap(bitmap, 0))
            submitted = true
            // Await timing out does not release SDK ownership of the input bitmap.
            task.addOnCompleteListener(Executor { it.run() }) { bitmap.recycle() }
            return Tasks.await(task, 45, TimeUnit.SECONDS).text
        } finally {
            if (!submitted) bitmap.recycle()
        }
    }

    private fun normalize(value: String) = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase().filterNot { it.isWhitespace() }

    private fun hasLibrary(language: String, value: String): Boolean {
        val normalized = normalize(value)
        return when (language) {
            "en" -> normalized.contains("library")
            "ko" -> normalized.contains("도서관")
            // Both the native noun and its loanword mean library; style is separate QA.
            "ja" -> normalized.contains("図書館") || normalized.contains("ライブラリ")
            else -> normalized.contains("图书馆") || normalized.contains("圖書館")
        }
    }

    private fun hasNine(value: String): Boolean {
        val normalized = normalize(value)
        return normalized.contains('9') || normalized.contains("nine") ||
            normalized.contains("아홉") || normalized.contains('九')
    }

    private fun assertNoPayloads(vararg texts: String) {
        val descriptor = automation.executeShellCommand("logcat -d --pid=${Process.myPid()} -v brief")
        val logs = ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
        assertTrue("The safe diagnostic probe is missing; log visibility is unverified.",
            logs.contains("Runtime diagnostic redacted to protect screen text."))
        assertTrue("A fixture payload appeared in process logs.", texts.all { it.isNotBlank() && !logs.contains(it) })
        assertFalse("Translation SDK payload diagnostics appeared in process logs.",
            logs.contains("Full translation:") || logs.contains("Query (after preprocess):") ||
                logs.contains("Translation (after detokenizer):"))
    }
}
