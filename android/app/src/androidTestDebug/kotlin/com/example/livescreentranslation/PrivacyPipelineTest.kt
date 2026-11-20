package com.example.livescreentranslation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.ParcelFileDescriptor
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
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Debug integration test: only synthetic text; no MediaProjection or user screen. */
@RunWith(AndroidJUnit4::class)
class PrivacyPipelineTest {
    private fun processLog(): String {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("logcat -d --pid=${Process.myPid()} -v brief")
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
    }

    @Test fun sdkOcrAndTranslationWorkWithoutPayloadsInLogcat() {
        NativeLogPrivacy.requireProtection()
        val fixture = "The garden opens at nine in the morning."
        val logMarker = "SCREENLINGO_PRIVATE_LOG_PROBE_82741"
        // Both Java Log and the native translation SDK must cross the same boundary.
        Log.i("native", logMarker)
        Log.w("untrusted-$logMarker", logMarker)
        Log.e("native", logMarker, IllegalStateException(logMarker))

        val bitmap = Bitmap.createBitmap(1400, 180, Bitmap.Config.ARGB_8888)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val translator = ModelRepository.translator(CaptureConfig())
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            canvas.drawText(fixture, 30f, 100f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 52f
            })
            if (!Tasks.await(ModelRepository.ready(CaptureConfig()), 30, TimeUnit.SECONDS)) {
                // Explicit fixture setup: only the model is downloaded, never source text.
                Tasks.await(ModelRepository.prepare(CaptureConfig()), 180, TimeUnit.SECONDS)
            }
            val recognized = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0)), 30, TimeUnit.SECONDS).text
            assertTrue("Synthetic OCR did not recognize the fixture.", recognized.contains("garden"))
            val translated = Tasks.await(translator.translate(recognized), 60, TimeUnit.SECONDS)
            assertTrue("Translation did not produce target-language text.",
                translated.isNotBlank() && translated != recognized && translated.any { it in '\uAC00'..'\uD7A3' })

            val logs = processLog()
            assertFalse("A diagnostic probe leaked through the privacy boundary.", logs.contains(logMarker))
            assertFalse("The original fixture appeared in process logs.", logs.contains(fixture))
            assertFalse("The OCR result appeared in process logs.", logs.contains(recognized))
            assertFalse("The translated fixture appeared in process logs.", logs.contains(translated))
            assertFalse("The SDK emitted translation payload diagnostics.",
                logs.contains("Full translation:") || logs.contains("Query (after preprocess):") || logs.contains("Translation (after detokenizer):"))
            assertTrue("Redacted warnings should retain a safe diagnostic signal.",
                logs.contains("Runtime diagnostic redacted to protect screen text."))
        } finally {
            recognizer.close()
            translator.close()
            bitmap.recycle()
        }
    }
}
