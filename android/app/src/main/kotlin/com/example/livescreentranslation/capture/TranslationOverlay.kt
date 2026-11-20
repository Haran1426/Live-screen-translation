package com.example.livescreentranslation.capture

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.input.InputManager
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.abs

/** Secure overlay windows prevent our translated text from feeding back into OCR. */
internal class TranslationOverlay(
    private val context: Context,
    private val onPause: () -> Unit,
    private val onStop: () -> Unit,
    private val cardTextSp: Int = 16,
    private val cardHeightDp: Int = 300,
    private val onPresentationChanged: () -> Unit,
) {
    private val manager = context.getSystemService(WindowManager::class.java)
    private val accent = Color.rgb(66, 99, 235)
    private val ink = Color.rgb(32, 43, 59)
    private val muted = Color.rgb(102, 112, 133)
    private val border = Color.rgb(231, 234, 241)
    private val positioned = PositionedTranslationOverlay(context)
    private var expanded = false
    private var attached = false
    private var compact = false
    private var paused = false
    private val panel = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(12), dp(18), dp(12))
        background = rounded(Color.WHITE, 16f).apply { setStroke(dp(1), border) }
        elevation = dp(4).toFloat()
    }
    private val heading = text("번역 중", 14f, accent).apply {
        setTypeface(typeface, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
    }
    private val status = text("다른 앱이나 연습 화면을 열어 주세요", 11f, muted)
    private val body = text("화면의 글자를 찾고 있어요…", cardTextSp.toFloat(), ink).apply {
        setLineSpacing(dp(3).toFloat(), 1f)
        setPadding(0, dp(8), 0, dp(8))
    }
    private val pause = button("멈춤") { onPause() }
    private val stop = button("종료") { onStop() }
    private val header = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
    private val actions = LinearLayout(context).apply { gravity = Gravity.END }
    // Overlay uses a platform Service theme and explicit colors, not AppCompat tinting.
    @android.annotation.SuppressLint("AppCompatCustomView")
    private val bubble = object : TextView(context) {
        override fun performClick(): Boolean = super.performClick()
    }.apply {
        text = "가"
        textSize = 22f
        setTextColor(accent)
        gravity = Gravity.CENTER
        setTypeface(typeface, Typeface.BOLD)
        background = rounded(Color.rgb(238, 242, 255), 28f).apply { setStroke(dp(1), Color.rgb(205, 215, 255)) }
        contentDescription = "원문 위 번역과 번역 카드 전환. 끌어서 이동"
        elevation = dp(6).toFloat()
        setOnClickListener { toggleExpanded() }
    }
    private val bubbleParams = params(dp(54), dp(54), Gravity.TOP or Gravity.START).apply {
        x = dp(12)
        y = dp(160)
    }
    private val panelParams = params(-1, dp(280), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
        y = dp(24)
    }

    init {
        header.apply {
            addView(heading)
            addView(button("접기") { toggleExpanded() })
        }
        panel.addView(header)
        panel.addView(status)
        panel.addView(ScrollView(context).apply { addView(body) }, LinearLayout.LayoutParams(-1, 0, 1f))
        panel.addView(ImageView(context).apply {
            setImageResource(com.example.livescreentranslation.R.drawable.google_translate)
            contentDescription = "powered by Google Translate"
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_START
        }, LinearLayout.LayoutParams(-2, dp(16)))
        panel.addView(actions.apply {
            addView(pause)
            addView(stop)
        })
        panel.visibility = View.GONE
        installDrag()
    }

    fun show() {
        resize()
        try {
            val positionedParams = params(-1, -1, Gravity.TOP or Gravity.START).apply {
                title = "ScreenLingo positioned translation"
                flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                setFitInsetsTypes(0)
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                // Android 12+ enforces its own touch obscuring limit for other apps.
                alpha = if (Build.VERSION.SDK_INT >= 31)
                    minOf(0.8f, context.getSystemService(InputManager::class.java).maximumObscuringOpacityForTouch)
                else 1f
            }
            manager.addView(positioned, positionedParams)
            manager.addView(panel, panelParams)
            manager.addView(bubble, bubbleParams)
            attached = true
        } catch (error: Exception) {
            runCatching { manager.removeView(positioned) }
            runCatching { manager.removeView(panel) }
            throw error
        }
    }

    val positionsEnabled: Boolean get() = !expanded

    fun clearPositions() { positioned.clear() }

    /** Secure windows can obscure the original in a capture. Hide before sampling. */
    fun hidePositionsForCapture(): Boolean {
        val visible = positioned.visibility == View.VISIBLE
        if (visible) clearPositions()
        return visible
    }

    fun renderPositions(translations: List<PositionedTranslation>, width: Int, height: Int) {
        if (!positionsEnabled || paused) return
        positioned.setTranslations(translations, width, height, exclusionRects() + systemBarRects(width, height))
    }

    fun resize() {
        val bounds = screenBounds()
        val density = context.resources.displayMetrics.density
        val layout = OverlayCardLayout.calculate(
            (bounds.width() / density).toInt().coerceAtLeast(1),
            (bounds.height() / density).toInt().coerceAtLeast(1),
            context.resources.configuration.fontScale,
            cardTextSp,
            cardHeightDp,
        )
        panelParams.width = dp(layout.width)
        panelParams.height = dp(layout.height)
        panelParams.gravity = Gravity.BOTTOM or if (layout.compact) Gravity.END else Gravity.CENTER_HORIZONTAL
        panelParams.x = if (layout.compact) dp(12) else 0
        if (compact != layout.compact) {
            compact = layout.compact
            (pause.parent as? ViewGroup)?.removeView(pause)
            (stop.parent as? ViewGroup)?.removeView(stop)
            val controls = if (compact) header else actions
            controls.addView(pause)
            controls.addView(stop)
            actions.visibility = if (compact) View.GONE else View.VISIBLE
        }
        updateHeading()
        bubbleParams.x = bubbleParams.x.coerceIn(0, (bounds.width() - dp(54)).coerceAtLeast(0))
        bubbleParams.y = bubbleParams.y.coerceIn(0, (bounds.height() - dp(100)).coerceAtLeast(0))
        if (attached) {
            manager.updateViewLayout(panel, panelParams)
            manager.updateViewLayout(bubble, bubbleParams)
        }
    }

    fun render(snapshot: Map<String, Any>) {
        paused = snapshot["phase"] == "paused"
        if (snapshot["phase"] != "running") clearPositions()
        updateHeading()
        pause.text = if (paused) "재개" else "멈춤"
        bubble.text = if (paused) "Ⅱ" else "가"
        status.text = snapshot["message"] as? String
        @Suppress("UNCHECKED_CAST")
        val blocks = snapshot["blocks"] as? List<Map<String, String>> ?: emptyList()
        val value = if (paused) "번역을 잠시 멈췄어요. 재개를 누르면 새 화면을 읽어요."
            else blocks.joinToString("\n\n") { it["translated"].orEmpty() }.ifBlank { "화면의 글자를 찾고 있어요…" }
        if (body.text.toString() != value) body.text = value
    }

    fun exclusionRects(): List<Rect> = listOf(panel, bubble).filter { it.visibility == View.VISIBLE && it.isAttachedToWindow }.map { view ->
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        Rect(location[0], location[1], location[0] + view.width, location[1] + view.height)
    }

    @Suppress("DEPRECATION")
    fun systemBarRects(width: Int, height: Int): List<Rect> {
        val insets = bubble.rootWindowInsets ?: return emptyList()
        val top: Int
        val bottom: Int
        val left: Int
        val right: Int
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            val bars = insets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            top = bars.top
            bottom = bars.bottom
            left = bars.left
            right = bars.right
        } else {
            top = insets.stableInsetTop
            bottom = insets.stableInsetBottom
            left = insets.stableInsetLeft
            right = insets.stableInsetRight
        }
        return listOf(
            Rect(0, 0, width, top), Rect(0, height - bottom, width, height),
            Rect(0, 0, left, height), Rect(width - right, 0, width, height),
        )
    }

    fun close() {
        if (!attached) return
        attached = false
        clearPositions()
        runCatching { manager.removeView(positioned) }
        runCatching { manager.removeView(panel) }
        runCatching { manager.removeView(bubble) }
        body.text = ""
    }

    private fun toggleExpanded() {
        expanded = !expanded
        clearPositions()
        panel.visibility = if (expanded) View.VISIBLE else View.GONE
        onPresentationChanged()
    }

    private fun updateHeading() {
        val state = if (paused) "일시정지" else "번역 중"
        heading.text = state
    }

    private fun installDrag() {
        var downX = 0f
        var downY = 0f
        var originX = 0
        var originY = 0
        var dragged = false
        val slop = ViewConfiguration.get(context).scaledTouchSlop
        bubble.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX; downY = event.rawY
                    originX = bubbleParams.x; originY = bubbleParams.y
                    dragged = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (abs(dx) > slop || abs(dy) > slop) dragged = true
                    if (dragged) {
                        val bounds = screenBounds()
                        bubbleParams.x = (originX + dx.toInt()).coerceIn(0, (bounds.width() - dp(54)).coerceAtLeast(0))
                        bubbleParams.y = (originY + dy.toInt()).coerceIn(0, (bounds.height() - dp(100)).coerceAtLeast(0))
                        runCatching { manager.updateViewLayout(bubble, bubbleParams) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> { if (!dragged) view.performClick(); true }
                MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }
    }

    private fun button(label: String, action: () -> Unit) = Button(context).apply {
        text = label
        textSize = 12f
        setTextColor(accent)
        setBackgroundColor(Color.TRANSPARENT)
        minWidth = dp(52)
        minimumWidth = dp(52)
        minHeight = dp(48)
        minimumHeight = dp(48)
        setPadding(dp(8), 0, dp(8), 0)
        setOnClickListener { action() }
    }

    private fun text(value: String, size: Float, color: Int) = TextView(context).apply {
        text = value; textSize = size; setTextColor(color)
    }
    private fun rounded(color: Int, radius: Float) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius.toInt()).toFloat()
    }
    private fun params(width: Int, height: Int, gravity: Int) = WindowManager.LayoutParams(
        width, height, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_SECURE,
        PixelFormat.TRANSLUCENT,
    ).apply { this.gravity = gravity }

    @Suppress("DEPRECATION")
    private fun screenBounds(): Rect = if (android.os.Build.VERSION.SDK_INT >= 30) manager.maximumWindowMetrics.bounds else {
        val metrics = android.util.DisplayMetrics()
        manager.defaultDisplay.getRealMetrics(metrics)
        Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
    }
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
}
