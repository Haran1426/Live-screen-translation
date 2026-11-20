package com.example.livescreentranslation

import android.app.Activity
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.example.livescreentranslation.capture.CaptureConfig
import com.example.livescreentranslation.capture.ModelRepository
import com.example.livescreentranslation.capture.ScreenTranslationService
import com.example.livescreentranslation.capture.SessionStore
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    private var pendingStart: MethodChannel.Result? = null
    private var pendingConfig: CaptureConfig? = null
    private var listener: ((Map<String, Any>) -> Unit)? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "screenlingo/control")
            .setMethodCallHandler(::handleCall)
        EventChannel(flutterEngine.dartExecutor.binaryMessenger, "screenlingo/session")
            .setStreamHandler(object : EventChannel.StreamHandler {
                override fun onListen(arguments: Any?, events: EventChannel.EventSink) {
                    listener?.let(SessionStore::removeListener)
                    listener = { events.success(it) }
                    SessionStore.addListener(listener!!)
                }
                override fun onCancel(arguments: Any?) {
                    listener?.let(SessionStore::removeListener)
                    listener = null
                }
            })
    }

    private fun handleCall(call: MethodCall, result: MethodChannel.Result) {
        try {
            when (call.method) {
                "getSession" -> result.success(SessionStore.snapshot())
                "getDownloadNetwork" -> result.success(DownloadNetwork.snapshot(this))
                "setPracticeVisible" -> {
                    SessionStore.practiceVisible = call.arguments == true
                    result.success(null)
                }
                "openTranslationInfo" -> {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://cloud.google.com/translate")))
                    result.success(null)
                }
                "getSettings" -> result.success(CaptureConfig.load(this).toMap())
                "saveSettings" -> {
                    check(!SessionStore.active) { "번역을 종료한 뒤 설정을 바꿔 주세요." }
                    config(call).save(this)
                    result.success(null)
                }
                "hasOverlayPermission" -> result.success(Settings.canDrawOverlays(this))
                "requestOverlayPermission" -> {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                    result.success(null)
                }
                "modelsReady" -> ModelRepository.ready(config(call))
                    .addOnSuccessListener { result.success(it) }
                    .addOnFailureListener { result.error("models", "언어 모델 상태를 확인하지 못했어요.", null) }
                "prepareModels" -> ModelRepository.prepare(config(call))
                    .addOnSuccessListener { result.success(null) }
                    .addOnFailureListener { result.error("download", "언어 준비에 실패했어요. 네트워크와 저장 공간을 확인해 주세요. Wi-Fi 전용 설정이면 Wi-Fi가 필요해요.", null) }
                "start" -> requestCapture(config(call), result)
                "stop" -> {
                    stopService(Intent(this, ScreenTranslationService::class.java))
                    result.success(null)
                }
                "togglePause" -> {
                    if (SessionStore.active) startService(Intent(this, ScreenTranslationService::class.java).setAction(ScreenTranslationService.ACTION_PAUSE))
                    result.success(null)
                }
                else -> result.notImplemented()
            }
        } catch (error: Exception) {
            // Control arguments contain only language/settings values, never captured text.
            android.util.Log.e("ScreenLingo", "Native control failed: ${call.method}", error)
            result.error("unavailable", "작업을 시작하지 못했어요. 앱을 다시 열어 주세요.", null)
        }
    }

    private fun config(call: MethodCall) = CaptureConfig.fromMap(call.arguments as? Map<*, *> ?: emptyMap<Any, Any>())

    private fun requestCapture(config: CaptureConfig, result: MethodChannel.Result) {
        check(pendingStart == null && !SessionStore.active) { "이미 화면 공유를 준비하거나 번역하고 있어요." }
        check(Settings.canDrawOverlays(this)) { "먼저 다른 앱 위에 표시 권한을 허용해 주세요." }
        pendingStart = result
        pendingConfig = config
        ModelRepository.ready(config).addOnSuccessListener { ready ->
            if (pendingStart !== result) return@addOnSuccessListener
            if (!ready) {
                pendingStart = null
                pendingConfig = null
                result.error("models", "먼저 번역 언어를 준비해 주세요.", null)
                return@addOnSuccessListener
            }
            try {
                val preferences = getSharedPreferences("screenlingo_permissions", MODE_PRIVATE)
                if (Build.VERSION.SDK_INT >= 33 &&
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
                    !preferences.getBoolean("notificationAsked", false)) {
                    preferences.edit().putBoolean("notificationAsked", true).apply()
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_REQUEST)
                } else launchCaptureConsent()
            } catch (error: Exception) {
                pendingStart = null
                pendingConfig = null
                result.error("capture", "화면 공유 안내를 열지 못했어요.", null)
            }
        }.addOnFailureListener {
            if (pendingStart === result) {
                pendingStart = null
                pendingConfig = null
                result.error("models", "언어 모델 상태를 확인하지 못했어요.", null)
            }
        }
    }

    private fun launchCaptureConsent() {
        if (pendingStart == null) return
        try {
            val manager = getSystemService(MediaProjectionManager::class.java)
            val intent = if (Build.VERSION.SDK_INT >= 34) {
                manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
            } else manager.createScreenCaptureIntent()
            @Suppress("DEPRECATION")
            startActivityForResult(intent, CAPTURE_REQUEST)
        } catch (error: Exception) {
            pendingStart?.error("capture", "화면 공유 안내를 열지 못했어요.", null)
            pendingStart = null
            pendingConfig = null
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // Notification permission is optional; the overlay always provides a stop control.
        if (requestCode == NOTIFICATION_REQUEST) launchCaptureConsent()
    }

    @Deprecated("Legacy Flutter activity result bridge")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != CAPTURE_REQUEST) return
        val result = pendingStart
        val config = pendingConfig
        pendingStart = null
        pendingConfig = null
        if (resultCode != Activity.RESULT_OK || data == null || config == null) {
            result?.success(false)
            return
        }
        try {
            SessionStore.update("starting", "다른 앱이나 번역 연습 화면을 열어 주세요.", emptyList(), 0)
            val intent = Intent(this, ScreenTranslationService::class.java).apply {
                putExtra("captureResult", resultCode)
                putExtra("captureData", data)
                putExtra("source", config.source)
                putExtra("target", config.target)
                putExtra("intervalMs", config.intervalMs)
                putExtra("cardTextSp", config.cardTextSp)
                putExtra("cardHeightDp", config.cardHeightDp)
            }
            startForegroundService(intent)
            result?.success(true)
        } catch (error: Exception) {
            SessionStore.clear("화면 번역을 시작하지 못했어요. 다시 시도해 주세요.")
            result?.error("capture", "화면 번역을 시작하지 못했어요.", null)
        }
    }

    override fun onDestroy() {
        listener?.let(SessionStore::removeListener)
        listener = null
        pendingStart?.error("cancelled", "화면 공유 준비가 취소됐어요.", null)
        pendingStart = null
        SessionStore.practiceVisible = false
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        SessionStore.appVisible = true
    }

    override fun onPause() {
        SessionStore.appVisible = false
        super.onPause()
    }

    companion object {
        private const val CAPTURE_REQUEST = 6401
        private const val NOTIFICATION_REQUEST = 6403
    }
}
