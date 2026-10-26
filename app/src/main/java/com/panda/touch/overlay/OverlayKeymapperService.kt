package com.panda.touch.overlay

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.panda.touch.R
import com.panda.touch.model.ActionType
import com.panda.touch.model.GameProfile
import com.panda.touch.model.KeyMapping
import com.panda.touch.model.PointF
import com.panda.touch.service.RootConnectionManager
import kotlinx.coroutines.*

/**
 * Service quản lý lớp phủ nổi (Floating HUD Keymapper) trên màn hình game.
 * Chuyển đổi linh hoạt giữa EDIT MODE (chỉnh sửa, kéo thả, vẽ dây nối)
 * và PLAY MODE (khoá nút, FLAG_NOT_TOUCHABLE cho Target, bắt sự kiện Trigger gọi sang RootService).
 */
class OverlayKeymapperService : Service() {

    private lateinit var windowManager: WindowManager
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    private var isEditMode = true
    private val profile = GameProfile()

    // Overlay Views
    private var canvasOverlayView: OverlayCanvasView? = null
    private val triggerViews = mutableMapOf<String, View>()
    private val targetViews = mutableMapOf<String, View>()

    // Coroutine Jobs cho chế độ Rapid Fire
    private val rapidFireJobs = mutableMapOf<String, Job>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startForegroundServiceNotification()

        // Kết nối sang RootService nếu chưa kết nối
        RootConnectionManager.bind(this)

        initDefaultProfile()
        setupOverlayViews()
    }

    private fun startForegroundServiceNotification() {
        val channelId = "panda_touch_overlay_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Panda Touch Overlay",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Panda Touch Pro (ROOT)")
            .setContentText("Keymapper Overlay is Active")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()

        startForeground(1001, notification)
    }

    private fun initDefaultProfile() {
        // Thêm 1 mapping mặc định (ví dụ nút Bắn: Trigger ở góc phải trên, Target ở nút bắn trong game)
        profile.mappings.add(
            KeyMapping(
                name = "Fire",
                actionType = ActionType.SINGLE_TAP,
                triggerPos = PointF(300f, 600f),
                targetPos = PointF(1850f, 750f),
                assignedSlot = 5
            )
        )
        // Thêm nút Nhảy
        profile.mappings.add(
            KeyMapping(
                name = "Jump",
                actionType = ActionType.SINGLE_TAP,
                triggerPos = PointF(300f, 450f),
                targetPos = PointF(2050f, 600f),
                assignedSlot = 6
            )
        )
    }

    private fun setupOverlayViews() {
        // 1. Thêm Canvas View toàn màn hình vẽ đường nối giữa Trigger và Target
        canvasOverlayView = OverlayCanvasView(this)
        val canvasParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        windowManager.addView(canvasOverlayView, canvasParams)

        // 2. Render các mapping
        for (mapping in profile.mappings) {
            createTriggerView(mapping)
            createTargetView(mapping)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createTriggerView(mapping: KeyMapping) {
        val trigger = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#8000E676")) // Xanh lá mờ
            val text = TextView(context).apply {
                text = mapping.name
                setTextColor(Color.WHITE)
                textSize = 12f
                gravity = Gravity.CENTER
            }
            addView(text, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }

        val sizePx = (mapping.sizeDp * resources.displayMetrics.density).toInt()
        val params = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = mapping.triggerPos.x.toInt()
            y = mapping.triggerPos.y.toInt()
        }

        trigger.setOnTouchListener { view, event ->
            if (isEditMode) {
                // Kéo thả nút Trigger trong Edit Mode
                handleDrag(view, params, event) { newX, newY ->
                    mapping.triggerPos.x = newX
                    mapping.triggerPos.y = newY
                    canvasOverlayView?.invalidate()
                }
            } else {
                // Bơm cảm ứng sang RootService trong Play Mode
                handlePlayTouch(mapping, event)
            }
            true
        }

        windowManager.addView(trigger, params)
        triggerViews[mapping.id] = trigger
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createTargetView(mapping: KeyMapping) {
        val target = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#80FF1744")) // Đỏ mờ
            val text = TextView(context).apply {
                text = "T:${mapping.name}"
                setTextColor(Color.WHITE)
                textSize = 10f
                gravity = Gravity.CENTER
            }
            addView(text, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }

        val sizePx = ((mapping.sizeDp * 0.8f) * resources.displayMetrics.density).toInt()
        val flags = if (isEditMode) {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        } else {
            // Khi ở Play Mode, TARGET POINT BẮT BUỘC PHẢI LÀ NOT_TOUCHABLE
            // để mọi thao tác xuyên thấu xuống Game Engine bên dưới!
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        }

        val params = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = mapping.targetPos.x.toInt()
            y = mapping.targetPos.y.toInt()
        }

        target.setOnTouchListener { view, event ->
            if (isEditMode) {
                handleDrag(view, params, event) { newX, newY ->
                    mapping.targetPos.x = newX
                    mapping.targetPos.y = newY
                    canvasOverlayView?.invalidate()
                }
            }
            true
        }

        windowManager.addView(target, params)
        targetViews[mapping.id] = target
    }

    /**
     * Chuyển đổi giữa Edit Mode và Play Mode
     */
    fun toggleMode() {
        isEditMode = !isEditMode
        updateModeState()
    }

    private fun updateModeState() {
        for (mapping in profile.mappings) {
            val target = targetViews[mapping.id] ?: continue
            val params = target.layoutParams as WindowManager.LayoutParams

            if (isEditMode) {
                target.visibility = View.VISIBLE
                params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            } else {
                // Play Mode: ẩn hoặc làm trong suốt, gán FLAG_NOT_TOUCHABLE
                params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            }
            windowManager.updateViewLayout(target, params)
        }
        canvasOverlayView?.invalidate()
    }

    private fun handlePlayTouch(mapping: KeyMapping, event: MotionEvent) {
        val rootService = RootConnectionManager.getService() ?: return

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                when (mapping.actionType) {
                    ActionType.SINGLE_TAP -> {
                        // Bơm sự kiện DOWN tại tọa độ Target Point
                        rootService.injectTouchDown(mapping.assignedSlot, mapping.targetPos.x, mapping.targetPos.y)
                    }
                    ActionType.RAPID_FIRE -> {
                        // Khởi động Coroutine lặp lại tap liên thanh
                        startRapidFire(mapping)
                    }
                    ActionType.SWIPE -> {
                        val end = mapping.targetEndPoint ?: mapping.targetPos
                        rootService.injectSwipe(
                            mapping.assignedSlot,
                            mapping.targetPos.x,
                            mapping.targetPos.y,
                            end.x,
                            end.y,
                            mapping.swipeDurationMs,
                            15
                        )
                    }
                    else -> {}
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (mapping.actionType == ActionType.SINGLE_TAP) {
                    rootService.injectTouchMove(mapping.assignedSlot, mapping.targetPos.x, mapping.targetPos.y)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (mapping.actionType == ActionType.RAPID_FIRE) {
                    stopRapidFire(mapping)
                } else if (mapping.actionType == ActionType.SINGLE_TAP) {
                    // Bơm sự kiện UP giải phóng slot
                    rootService.injectTouchUp(mapping.assignedSlot)
                }
            }
        }
    }

    private fun startRapidFire(mapping: KeyMapping) {
        stopRapidFire(mapping)
        rapidFireJobs[mapping.id] = serviceScope.launch(Dispatchers.IO) {
            val rootService = RootConnectionManager.getService() ?: return@launch
            val delayMs = (1000L / mapping.rapidFireClicksPerSec).coerceAtLeast(20L)
            while (isActive) {
                rootService.injectTap(mapping.assignedSlot, mapping.targetPos.x, mapping.targetPos.y, 25)
                delay(delayMs)
            }
        }
    }

    private fun stopRapidFire(mapping: KeyMapping) {
        rapidFireJobs[mapping.id]?.cancel()
        rapidFireJobs.remove(mapping.id)
        RootConnectionManager.getService()?.injectTouchUp(mapping.assignedSlot)
    }

    private fun handleDrag(
        view: View,
        params: WindowManager.LayoutParams,
        event: MotionEvent,
        onMoved: (Float, Float) -> Unit
    ) {
        var startX = 0f
        var startY = 0f
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.rawX
                startY = event.rawY
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - startX
                val dy = event.rawY - startY
                params.x += dx.toInt()
                params.y += dy.toInt()
                windowManager.updateViewLayout(view, params)
                onMoved(params.x.toFloat(), params.y.toFloat())
                startX = event.rawX
                startY = event.rawY
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        canvasOverlayView?.let { windowManager.removeView(it) }
        triggerViews.values.forEach { windowManager.removeView(it) }
        targetViews.values.forEach { windowManager.removeView(it) }
        RootConnectionManager.unbind()
    }

    /**
     * Canvas vẽ đường chỉ dẫn kết nối Trigger -> Target trong Edit Mode
     */
    inner class OverlayCanvasView(context: Context) : View(context) {
        private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.CYAN
            strokeWidth = 4f
            style = Paint.Style.STROKE
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (!isEditMode) return // Ở Play Mode không vẽ dây nối

            for (mapping in profile.mappings) {
                canvas.drawLine(
                    mapping.triggerPos.x + 50f,
                    mapping.triggerPos.y + 50f,
                    mapping.targetPos.x + 40f,
                    mapping.targetPos.y + 40f,
                    linePaint
                )
            }
        }
    }
}
