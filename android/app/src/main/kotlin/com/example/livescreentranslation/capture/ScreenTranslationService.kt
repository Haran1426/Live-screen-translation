package com.example.livescreentranslation.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.LruCache
import android.view.WindowManager
import com.example.livescreentranslation.MainActivity
import com.example.livescreentranslation.R
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.math.max

class ScreenTranslationService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var workerThread: HandlerThread
    private lateinit var worker: Handler
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var overlay: TranslationOverlay? = null
    private var recognizer: TextRecognizer? = null
    private var translator: Translator? = null
    private var config = CaptureConfig()
    private var width = 0
    private var height = 0
    private var density = 0
    private val frames = FrameWorkGuard(
        clock = SystemClock::elapsedRealtime,
        schedule = { callback, delay -> main.postDelayed(callback, delay); Unit },
        cancel = { main.removeCallbacks(it) },
        onTimeout = { fail("화면 처리 응답이 30초 동안 없어 번역을 종료했어요. 화면 번역을 다시 시작해 주세요.") },
    )
    private var stopped = false
    private var paused = false
    private var failureMessage: String? = null
    private var consecutiveErrors = 0
    private val cache = LruCache<String, String>(160)
    private var previousSelection: FrameTextSelection? = null
    private val stateListener: (Map<String, Any>) -> Unit = { overlay?.render(it) }

    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { stopSelf() }
    }

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() { stopSelf() }
        override fun onCapturedContentResize(width: Int, height: Int) {
            if (Build.VERSION.SDK_INT >= 34 && display != null) resizeCapture(width, height)
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            if (stopped) return
            if (!Settings.canDrawOverlays(this@ScreenTranslationService)) {
                fail("다른 앱 위에 표시 권한이 해제되어 번역을 종료했어요.")
                return
            }
            if (!paused && !frames.busy && (!SessionStore.appVisible || SessionStore.practiceVisible)) captureFrame()
            main.postDelayed(this, config.intervalMs.toLong())
        }
    }

    override fun onCreate() {
        super.onCreate()
        workerThread = HandlerThread("screenlingo-frames").apply { start() }
        worker = Handler(workerThread.looper)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF), RECEIVER_NOT_EXPORTED)
        else registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF))
        SessionStore.addListener(stateListener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        if (intent?.action == ACTION_PAUSE) { togglePause(); return START_NOT_STICKY }
        if (projection != null) return START_NOT_STICKY
        if (intent == null || !intent.hasExtra("captureData")) { stopSelf(); return START_NOT_STICKY }
        try {
            startNotification()
            check(Settings.canDrawOverlays(this))
            config = CaptureConfig.fromMap(mapOf(
                "source" to intent.getStringExtra("source"),
                "target" to intent.getStringExtra("target"),
                "intervalMs" to intent.getIntExtra("intervalMs", 2000),
                "cardTextSp" to intent.getIntExtra("cardTextSp", 16),
                "cardHeightDp" to intent.getIntExtra("cardHeightDp", 300),
            ))
            translator = ModelRepository.translator(config)
            recognizer = when (config.source) {
                "ja" -> TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
                "zh" -> TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
                "ko" -> TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
                else -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            }
            overlay = TranslationOverlay(this, ::togglePause, ::stopSelf, config.cardTextSp, config.cardHeightDp).also { it.show() }
            @Suppress("DEPRECATION")
            val data = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra("captureData", Intent::class.java)
                else intent.getParcelableExtra<Intent>("captureData")
            checkNotNull(data)
            projection = getSystemService(MediaProjectionManager::class.java)
                .getMediaProjection(intent.getIntExtra("captureResult", 0), data)
            checkNotNull(projection).registerCallback(projectionCallback, main)
            val bounds = screenBounds()
            width = bounds.width()
            height = bounds.height()
            density = resources.displayMetrics.densityDpi
            reader = newReader(width, height)
            display = projection!!.createVirtualDisplay(
                "ScreenLingo", width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader!!.surface, null, main,
            )
            SessionStore.update("running", "다른 앱이나 번역 연습 화면을 열어 주세요.", emptyList(), 0)
            main.postDelayed(tick, 600)
        } catch (error: Exception) {
            fail("화면 번역을 시작하지 못했어요. 권한을 확인하고 다시 시작해 주세요.")
        }
        return START_NOT_STICKY
    }

    private fun startNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "화면 번역", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, javaClass).setAction(ACTION_STOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val pause = PendingIntent.getService(this, 2, Intent(this, javaClass).setAction(ACTION_PAUSE), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_translate)
            .setContentTitle("ScreenLingo 화면 번역")
            .setContentText("화면의 글자를 기기 안에서 번역하고 있어요")
            .setContentIntent(open).setOngoing(true)
            .addAction(Notification.Action.Builder(null, "멈춤 / 재개", pause).build())
            .addAction(Notification.Action.Builder(null, "종료", stop).build())
            .build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        else startForeground(NOTIFICATION_ID, notification)
    }

    private fun captureFrame() {
        val sourceReader = reader ?: return
        val ticket = frames.begin() ?: return
        val frameWidth = width
        val frameHeight = height
        val exclusions = overlay?.exclusionRects().orEmpty() + overlay?.systemBarRects(width, height).orEmpty()
        worker.post {
            var bitmap: Bitmap? = null
            try {
                sourceReader.acquireLatestImage()?.use { image ->
                    val plane = image.planes[0]
                    val paddedWidth = plane.rowStride / plane.pixelStride
                    val padded = Bitmap.createBitmap(paddedWidth, frameHeight, Bitmap.Config.ARGB_8888)
                    try {
                        padded.copyPixelsFromBuffer(plane.buffer)
                        val cropped = Bitmap.createBitmap(padded, 0, 0, frameWidth, frameHeight)
                        val scale = minOf(1f, 1920f / max(frameWidth, frameHeight))
                        bitmap = if (scale < 1f) Bitmap.createScaledBitmap(cropped, (frameWidth * scale).toInt(), (frameHeight * scale).toInt(), true) else cropped
                        if (cropped !== bitmap && cropped !== padded) cropped.recycle()
                    } finally {
                        if (padded !== bitmap) padded.recycle()
                    }
                }
                val frame = bitmap
                main.post {
                    if (!valid(ticket) || frame == null) {
                        frame?.recycle()
                        frames.complete(ticket)
                    } else recognize(frame, exclusions, frameWidth.toFloat() / frame.width, ticket)
                }
            } catch (error: Exception) {
                bitmap?.recycle()
                main.post {
                    if (valid(ticket)) frameError()
                    frames.complete(ticket)
                }
            }
        }
    }

    private fun recognize(bitmap: Bitmap, exclusions: List<Rect>, scale: Float, ticket: FrameWorkGuard.Ticket) {
        val engine = recognizer
        if (engine == null || !valid(ticket)) { bitmap.recycle(); frames.complete(ticket); return }
        val task = try {
            engine.process(InputImage.fromBitmap(bitmap, 0))
        } catch (error: Exception) {
            bitmap.recycle()
            if (valid(ticket)) frameError()
            frames.complete(ticket)
            return
        }
        task
            .addOnSuccessListener { result ->
                if (!valid(ticket)) { frames.complete(ticket); return@addOnSuccessListener }
                val selection = FrameTextSelection.select(result.textBlocks.filter { block ->
                    val box = block.boundingBox ?: return@filter false
                    val originalBox = Rect((box.left * scale).toInt(), (box.top * scale).toInt(), (box.right * scale).toInt(), (box.bottom * scale).toInt())
                    exclusions.none { Rect.intersects(it, originalBox) }
                }.map { it.text })
                if (selection.texts.isEmpty()) {
                    previousSelection = null
                    consecutiveErrors = 0
                    SessionStore.update("running", "읽을 글자를 찾고 있어요. 카드에 가려졌다면 접어 주세요.", emptyList(), 0)
                    frames.complete(ticket)
                } else if (selection.texts == previousSelection?.texts) {
                    if (selection != previousSelection) {
                        previousSelection = selection
                        SessionStore.update(message = selection.message(config.source, config.target))
                    }
                    frames.complete(ticket)
                } else translate(selection, ticket)
            }
            .addOnFailureListener {
                if (valid(ticket)) frameError()
                frames.complete(ticket)
            }
            .addOnCompleteListener {
                // A timeout/stop does not mean the SDK has released its input image.
                bitmap.recycle()
                if (it.isCanceled) frames.complete(ticket)
            }
    }

    private fun translate(selection: FrameTextSelection, ticket: FrameWorkGuard.Ticket) {
        val texts = selection.texts
        val engine = translator ?: run { frames.complete(ticket); return }
        val tasks = try {
            texts.map { text ->
                val cached = cache.get(text)
                if (cached != null) Tasks.forResult(cached)
                else engine.translate(text).addOnSuccessListener { translation ->
                    if (valid(ticket)) cache.put(text, translation)
                }
            }
        } catch (error: Exception) {
            // Some requests may already be running. End the session rather than
            // releasing ownership and overlapping another frame with those requests.
            fail("화면을 처리하지 못해 번역을 종료했어요. 언어 준비와 화면 공유를 확인해 주세요.")
            return
        }
        Tasks.whenAllSuccess<String>(tasks).addOnSuccessListener { translations ->
            if (!valid(ticket)) return@addOnSuccessListener
            previousSelection = selection
            consecutiveErrors = 0
            val blocks = texts.zip(translations).map { (original, translated) -> mapOf("original" to original, "translated" to translated) }
            SessionStore.update("running", selection.message(config.source, config.target), blocks, SystemClock.elapsedRealtime() - ticket.startedAt)
        }.addOnFailureListener {
            if (valid(ticket)) frameError()
        }.addOnCompleteListener { frames.complete(ticket) }
    }

    private fun frameError() {
        consecutiveErrors++
        previousSelection = null
        if (consecutiveErrors >= 3) fail("화면을 처리하지 못해 번역을 종료했어요. 언어 준비와 화면 공유를 확인해 주세요.")
        else SessionStore.update(message = "화면을 읽지 못했어요. 잠시 후 다시 확인해요.", blocks = emptyList())
    }

    private fun togglePause() {
        if (projection == null || stopped) return
        paused = !paused
        frames.invalidate()
        previousSelection = null
        cache.evictAll()
        SessionStore.update(
            if (paused) "paused" else "running",
            if (paused) "화면 읽기를 멈췄어요." else "새 화면의 글자를 찾고 있어요.",
            emptyList(), 0,
        )
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val bounds = screenBounds()
        resizeCapture(bounds.width(), bounds.height())
    }

    private fun resizeCapture(newWidth: Int, newHeight: Int) {
        if (stopped || newWidth <= 0 || newHeight <= 0 || (width == newWidth && height == newHeight)) return
        if (display == null) return
        try {
            frames.invalidate()
            previousSelection = null
            val old = reader
            val replacement = newReader(newWidth, newHeight)
            reader = replacement
            width = newWidth
            height = newHeight
            display!!.resize(width, height, density)
            display!!.surface = replacement.surface
            worker.post { old?.close() }
            overlay?.resize()
            SessionStore.update(message = "화면 크기가 바뀌었어요. 새 화면을 읽고 있어요.", blocks = emptyList())
        } catch (error: Exception) {
            fail("화면 크기를 변경하지 못했어요. 화면 번역을 다시 시작해 주세요.")
        }
    }

    private fun valid(ticket: FrameWorkGuard.Ticket) = frames.accepts(ticket) && !stopped && !paused

    private fun newReader(width: Int, height: Int) = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

    @Suppress("DEPRECATION")
    private fun screenBounds(): Rect {
        val manager = getSystemService(WindowManager::class.java)
        return if (Build.VERSION.SDK_INT >= 30) manager.maximumWindowMetrics.bounds else {
            val metrics = android.util.DisplayMetrics()
            manager.defaultDisplay.getRealMetrics(metrics)
            Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
        }
    }

    private fun fail(message: String) {
        failureMessage = message
        stopped = true
        frames.close()
        main.removeCallbacks(tick)
        stopSelf()
    }

    override fun onDestroy() {
        stopped = true
        frames.close()
        main.removeCallbacksAndMessages(null)
        runCatching { unregisterReceiver(screenOff) }
        SessionStore.removeListener(stateListener)
        overlay?.close()
        overlay = null
        projection?.unregisterCallback(projectionCallback)
        display?.release()
        display = null
        projection?.stop()
        projection = null
        val oldReader = reader
        reader = null
        worker.post { oldReader?.close() }
        workerThread.quitSafely()
        recognizer?.close()
        recognizer = null
        translator?.close()
        translator = null
        cache.evictAll()
        previousSelection = null
        SessionStore.clear(failureMessage)
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_STOP = "screenlingo.STOP"
        const val ACTION_PAUSE = "screenlingo.PAUSE"
        private const val CHANNEL = "screenlingo_translation"
        private const val NOTIFICATION_ID = 6402
    }
}
