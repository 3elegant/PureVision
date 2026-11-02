package com.deenelife.purevison

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.deenelife.purevison.data.AppPreferences
import com.deenelife.purevison.data.FontManager
import com.deenelife.purevison.databinding.OverlayLayoutBinding

class OverlayService : Service(), View.OnTouchListener {

    private lateinit var windowManager: WindowManager
    private lateinit var binding: OverlayLayoutBinding
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var preferences: AppPreferences

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    private var isPositionLocked = false
    private var isStaticTextEnabled = true
    private var isZikrModeEnabled = false
    private var zikrDurationSeconds = 5

    private val zikrHandler = Handler(Looper.getMainLooper())
    private var zikrRunnable: Runnable? = null
    private val zikrList = ArrayList<String>()
    private var currentZikrIndex = 0

    companion object {
        private const val CHANNEL_SILENT_ID = "PureVision_Silent_Channel_v1"
        private const val CHANNEL_ZIKR_ID = "PureVision_Zikr_Channel_v1"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_STOP_SERVICE = "ACTION_STOP_SERVICE"
        const val ACTION_UPDATE_SETTINGS = "ACTION_UPDATE_SETTINGS"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        preferences = AppPreferences.getInstance(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        binding = OverlayLayoutBinding.inflate(LayoutInflater.from(this))
        binding.root.setOnTouchListener(this)

        isPositionLocked = preferences.isPositionLocked()

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            0,
            PixelFormat.TRANSLUCENT
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        updateWindowManagerFlags()

        val hasOverlayPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)
        val needsOverlay = preferences.isStaticTextEnabled() || preferences.isZikrModeEnabled()
        if (hasOverlayPermission && needsOverlay) {
            try {
                windowManager.addView(binding.root, params)
                binding.root.post {
                    if (binding.root.isAttachedToWindow) {
                        updateWindowManagerFlags()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        PureVisionTileService.requestTileUpdate(this)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        loadAndUpdateSettings()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        if (ACTION_STOP_SERVICE == action) {
            preferences.updateServiceEnabledAsync(false)
            stopZikrRotation()
            removeOverlayView()
            stopForegroundCompat()
            stopSelf()
            PureVisionTileService.requestTileUpdate(this)
            return START_NOT_STICKY
        }

        createNotificationChannel()

        loadZikrList()
        val activeText = getCurrentActiveText()
        val initialNotification = buildNotification(activeText)
        startForeground(NOTIFICATION_ID, initialNotification)

        if (ACTION_UPDATE_SETTINGS != action) {
            preferences.updateServiceEnabledAsync(true)
        }

        loadAndUpdateSettings()
        PureVisionTileService.requestTileUpdate(this)

        return START_STICKY
    }

    private fun getCurrentActiveText(): String {
        val isZikrActive = preferences.isZikrModeEnabled() ||
                preferences.isNotificationZikrEnabled()

        return if (isZikrActive && zikrList.isNotEmpty()) {
            if (currentZikrIndex in zikrList.indices) zikrList[currentZikrIndex] else zikrList[0]
        } else {
            preferences.getOverlayText()
        }
    }

    private fun buildNotification(activeText: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            this.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val stopSelfIntent = Intent(this, OverlayService::class.java).apply {
            this.action = ACTION_STOP_SERVICE
        }

        val pendingIntentFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val contentPendingIntent = PendingIntent.getActivity(this, 0, openAppIntent, pendingIntentFlag)
        val stopPendingIntent = PendingIntent.getService(this, 1, stopSelfIntent, pendingIntentFlag)

        val isNotifZikr = preferences.isNotificationZikrEnabled()
        val channelId = if (isNotifZikr) CHANNEL_ZIKR_ID else CHANNEL_SILENT_ID

        val title = if (isNotifZikr && activeText.isNotBlank()) activeText else getString(R.string.service_notification_title)
        val text = if (isNotifZikr) getString(R.string.app_name) else getString(R.string.service_notification_text)
        val priority = if (isNotifZikr) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_MIN
        val visibility = if (isNotifZikr) NotificationCompat.VISIBILITY_PUBLIC else NotificationCompat.VISIBILITY_SECRET

        val builder = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentPendingIntent)
            .setPriority(priority)
            .setVisibility(visibility)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setSmallIcon(R.drawable.ic_stat_purevision)

        if (isNotifZikr) {
            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.service_notification_action_stop),
                stopPendingIntent
            )
        }

        return builder.build()
    }

    private fun updateNotification(activeText: String) {
        try {
            val notification = buildNotification(activeText)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadAndUpdateSettings() {
        isStaticTextEnabled = preferences.isStaticTextEnabled()
        isZikrModeEnabled = preferences.isZikrModeEnabled()
        val isNotifZikr = preferences.isNotificationZikrEnabled()
        zikrDurationSeconds = preferences.getZikrDuration()
        loadZikrList()

        val savedFontSize = preferences.getFontSize()
        val savedTextOpacity = preferences.getTextOpacity()
        val isBackgroundEnabled = preferences.isBackgroundEnabled()
        val savedBackgroundOpacity = preferences.getBackgroundOpacity()
        val savedCornerRadius = preferences.getCornerRadius()
        val newLockState = preferences.isPositionLocked()
        val selectedFont = preferences.getSelectedFont()

        val textColor = preferences.getFontColorInt()
        val backgroundColorRaw = preferences.getBackgroundColorInt()

        stopZikrRotation()

        val needsZikrRotation = isZikrModeEnabled || isNotifZikr

        if (needsZikrRotation) {
            startZikrRotation()
        } else {
            updateOverlayText()
        }

        binding.overlayStaticText.setTextSize(TypedValue.COMPLEX_UNIT_SP, savedFontSize.toFloat())
        binding.overlayZikrText.setTextSize(TypedValue.COMPLEX_UNIT_SP, savedFontSize.toFloat())

        val textAlphaValue = savedTextOpacity / 100.0f
        binding.overlayStaticText.alpha = textAlphaValue
        binding.overlayZikrText.alpha = textAlphaValue

        binding.overlayStaticText.setTextColor(textColor)
        binding.overlayZikrText.setTextColor(textColor)

        val dividerAlpha = (textAlphaValue * 70).toInt()
        val dividerColor = Color.argb(
            dividerAlpha,
            Color.red(textColor),
            Color.green(textColor),
            Color.blue(textColor)
        )
        binding.overlayDivider.setBackgroundColor(dividerColor)

        val customFont = FontManager.getTypeface(this, selectedFont)
        binding.overlayStaticText.typeface = customFont
        binding.overlayZikrText.typeface = customFont

        val isStatusBarPin = preferences.isStatusBarPinEnabled()
        val isNotchIsland = isStatusBarPin && preferences.getNotchStyle() == AppPreferences.NOTCH_STYLE_ISLAND

        if (isNotchIsland) {
            val backgroundDrawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(24).toFloat()
                val alpha = if (isBackgroundEnabled) (savedBackgroundOpacity * 2.55).toInt() else 210
                val baseBg = if (isBackgroundEnabled) backgroundColorRaw else Color.BLACK
                val finalBackgroundColor = (baseBg and 0x00FFFFFF) or (alpha shl 24)
                setColor(finalBackgroundColor)
                setStroke(dpToPx(1), Color.argb(60, 255, 255, 255))
            }

            binding.overlayContainer.background = backgroundDrawable
            binding.overlayContainer.setPadding(dpToPx(16), dpToPx(6), dpToPx(16), dpToPx(6))
            binding.overlayStaticText.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
            binding.overlayZikrText.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
        } else if (isBackgroundEnabled) {
            val backgroundDrawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(savedCornerRadius).toFloat()
                val alpha = (savedBackgroundOpacity * 2.55).toInt()
                val finalBackgroundColor = (backgroundColorRaw and 0x00FFFFFF) or (alpha shl 24)
                setColor(finalBackgroundColor)
            }

            binding.overlayContainer.background = backgroundDrawable
            binding.overlayContainer.setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8))
            binding.overlayStaticText.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
            binding.overlayZikrText.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
        } else {
            binding.overlayContainer.setBackgroundColor(Color.TRANSPARENT)
            binding.overlayContainer.setPadding(0, 0, 0, 0)
            val shadowColor = ContextCompat.getColor(this, android.R.color.black)
            binding.overlayStaticText.setShadowLayer(5f, 1f, 1f, shadowColor)
            binding.overlayZikrText.setShadowLayer(5f, 1f, 1f, shadowColor)
        }

        isPositionLocked = newLockState
        updateWindowManagerFlags()

        val hasOverlayPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)
        val needsOverlay = isStaticTextEnabled || isZikrModeEnabled

        if (hasOverlayPermission && needsOverlay) {
            if (!binding.root.isAttachedToWindow) {
                try {
                    windowManager.addView(binding.root, params)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        binding.root.post {
            if (binding.root.isAttachedToWindow) {
                updateWindowManagerFlags()
            }
        }
    }

    private fun updateOverlayText(withFade: Boolean = false) {
        var hasStaticText = false
        var hasZikrText = false
        var activeStatusText = ""

        if (isStaticTextEnabled) {
            val savedText = preferences.getOverlayText()
            if (savedText.isNotBlank()) {
                binding.overlayStaticText.text = savedText.trim()
                binding.overlayStaticText.visibility = View.VISIBLE
                hasStaticText = true
                activeStatusText = savedText.trim()
            } else {
                binding.overlayStaticText.visibility = View.GONE
            }
        } else {
            binding.overlayStaticText.visibility = View.GONE
        }

        val isStatusZikrActive = preferences.isNotificationZikrEnabled()

        if (isZikrModeEnabled && zikrList.isNotEmpty()) {
            if (currentZikrIndex in zikrList.indices) {
                val nextText = zikrList[currentZikrIndex]
                activeStatusText = nextText
                if (withFade) {
                    val targetAlpha = preferences.getTextOpacity() / 100.0f
                    binding.overlayZikrText.animate()
                        .alpha(0f)
                        .setDuration(250)
                        .withEndAction {
                            binding.overlayZikrText.text = nextText
                            binding.overlayZikrText.animate()
                                .alpha(targetAlpha)
                                .setDuration(250)
                                .start()
                        }.start()
                } else {
                    binding.overlayZikrText.text = nextText
                }
                binding.overlayZikrText.visibility = View.VISIBLE
                hasZikrText = true
            } else {
                binding.overlayZikrText.visibility = View.GONE
            }
        } else {
            binding.overlayZikrText.visibility = View.GONE
            // If screen overlay zikr is disabled, but status bar rotation is active:
            if (isStatusZikrActive && zikrList.isNotEmpty() && currentZikrIndex in zikrList.indices) {
                activeStatusText = zikrList[currentZikrIndex]
            }
        }

        binding.overlayDivider.visibility = if (hasStaticText && hasZikrText) View.VISIBLE else View.GONE

        // If user does not want any screen overlay, completely hide the container and root
        if (!hasStaticText && !hasZikrText) {
            binding.overlayContainer.visibility = View.GONE
            binding.root.visibility = View.GONE
        } else {
            binding.root.visibility = View.VISIBLE
            binding.overlayContainer.visibility = View.VISIBLE
        }

        updateNotification(activeStatusText)
    }

    private fun loadZikrList() {
        zikrList.clear()
        val loaded = preferences.getZikrList()
        if (loaded.isNotEmpty()) {
            zikrList.addAll(loaded)
        } else {
            zikrList.addAll(preferences.getDefaultZikrList())
        }
    }

    private fun startZikrRotation() {
        if (zikrList.isEmpty()) {
            updateOverlayText()
            return
        }

        stopZikrRotation()

        zikrRunnable = object : Runnable {
            override fun run() {
                if (zikrList.isEmpty()) {
                    updateOverlayText()
                    return
                }

                if (currentZikrIndex !in zikrList.indices) {
                    currentZikrIndex = 0
                }

                updateOverlayText(withFade = true)

                currentZikrIndex = (currentZikrIndex + 1) % zikrList.size

                val durationMillis = (zikrDurationSeconds * 1000L).coerceAtLeast(1000L)
                zikrHandler.postDelayed(this, durationMillis)
            }
        }

        zikrHandler.post(zikrRunnable!!)
    }

    private fun stopZikrRotation() {
        zikrRunnable?.let { zikrHandler.removeCallbacks(it) }
        zikrRunnable = null
    }

    private fun updateWindowManagerFlags() {
        val isStatusBarPin = preferences.isStatusBarPinEnabled()

        if (isStatusBarPin) {
            if (preferences.getNotchStyle() == AppPreferences.NOTCH_STYLE_ISLAND) {
                params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                params.x = dpToPx(preferences.getNotchOffsetX())
                params.y = dpToPx(preferences.getNotchDropOffsetY())
            } else {
                params.gravity = Gravity.TOP or Gravity.START
                val displayMetrics = resources.displayMetrics
                val viewWidth = if (binding.root.width > 0) binding.root.width else dpToPx(160)
                val maxX = (displayMetrics.widthPixels - viewWidth).coerceAtLeast(0)
                val pinPercent = preferences.getStatusBarPinPercent().coerceIn(0f, 100f)
                params.x = ((pinPercent / 100f) * maxX).toInt()
                params.y = dpToPx(preferences.getStatusBarPinOffsetY())
            }

            params.flags = if (isPositionLocked) {
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            } else {
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            }
        } else {
            params.gravity = Gravity.TOP or Gravity.END
            params.x = preferences.getPosX()
            params.y = preferences.getPosY()
            params.flags = if (isPositionLocked) {
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            } else {
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            }
        }

        if (binding.root.isAttachedToWindow) {
            try {
                windowManager.updateViewLayout(binding.root, params)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun removeOverlayView() {
        if (::binding.isInitialized && binding.root.isAttachedToWindow) {
            try {
                windowManager.removeView(binding.root)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        preferences.updateServiceEnabledAsync(false)
        stopZikrRotation()
        removeOverlayView()
        stopForegroundCompat()
        PureVisionTileService.requestTileUpdate(this)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!preferences.isServiceEnabled()) {
            super.onTaskRemoved(rootIntent)
            return
        }

        val broadcastIntent = Intent(this, RestartReceiver::class.java)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getBroadcast(this, 0, broadcastIntent, flags)
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        try {
            alarmManager?.set(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + 1000,
                pendingIntent
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }

        super.onTaskRemoved(rootIntent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java) ?: return

            // Delete old channels
            try {
                manager.deleteNotificationChannel("OverlayServiceChannel")
                manager.deleteNotificationChannel("OverlayServiceChannel_v2")
                manager.deleteNotificationChannel("OverlayServiceChannel_v3")
                manager.deleteNotificationChannel("PureVision_Foreground_Channel_v4")
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 1. Silent Channel for Background Service (No status bar icon, completely quiet & minimized)
            val silentChannel = NotificationChannel(
                CHANNEL_SILENT_ID,
                getString(R.string.notification_channel_service),
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = getString(R.string.notification_channel_service_desc)
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
            manager.createNotificationChannel(silentChannel)

            // 2. Active Channel for Live Zikr on Notification & Lock Screen
            val zikrChannel = NotificationChannel(
                CHANNEL_ZIKR_ID,
                getString(R.string.notification_channel_zikr),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.desc_notification_zikr)
                setSound(null, null)
                enableVibration(false)
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(zikrChannel)
        }
    }

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        if (isPositionLocked) {
            return false
        }

        val isStatusBarPin = preferences.isStatusBarPinEnabled()
        val isNotchIsland = isStatusBarPin && preferences.getNotchStyle() == AppPreferences.NOTCH_STYLE_ISLAND

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = params.x
                initialY = params.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val displayMetrics = resources.displayMetrics

                if (isNotchIsland) {
                    val newX = initialX + (event.rawX - initialTouchX).toInt()
                    val newY = initialY + (event.rawY - initialTouchY).toInt()
                    val maxOffsetPx = dpToPx(60)
                    val maxDropPx = dpToPx(120)
                    params.x = newX.coerceIn(-maxOffsetPx, maxOffsetPx)
                    params.y = newY.coerceIn(0, maxDropPx)
                } else if (isStatusBarPin) {
                    val maxX = (displayMetrics.widthPixels - binding.root.width).coerceAtLeast(0)
                    val maxVerticalOffsetPx = dpToPx(80)
                    val newX = initialX + (event.rawX - initialTouchX).toInt()
                    val newY = initialY + (event.rawY - initialTouchY).toInt()
                    params.x = newX.coerceIn(0, maxX)
                    params.y = newY.coerceIn(0, maxVerticalOffsetPx)
                } else {
                    val maxX = (displayMetrics.widthPixels - binding.root.width).coerceAtLeast(0)
                    val maxY = (displayMetrics.heightPixels - binding.root.height).coerceAtLeast(0)
                    val newY = initialY + (event.rawY - initialTouchY).toInt()
                    val newX = initialX + (initialTouchX - event.rawX).toInt()
                    params.x = newX.coerceIn(0, maxX)
                    params.y = newY.coerceIn(0, maxY)
                }

                try {
                    windowManager.updateViewLayout(binding.root, params)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (isNotchIsland) {
                    preferences.updateNotchOffsetXAsync(pxToDp(params.x))
                    preferences.updateNotchDropOffsetYAsync(pxToDp(params.y))
                } else if (isStatusBarPin) {
                    val displayMetrics = resources.displayMetrics
                    val maxX = (displayMetrics.widthPixels - binding.root.width).coerceAtLeast(0)
                    val percent = if (maxX > 0) (params.x.toFloat() / maxX * 100f).coerceIn(0f, 100f) else 50f
                    preferences.updateStatusBarPinPercentAsync(percent)
                    preferences.updateStatusBarPinOffsetYAsync(pxToDp(params.y))
                } else {
                    preferences.updatePositionAsync(params.x, params.y)
                }
                return true
            }
        }
        return false
    }

    private fun pxToDp(px: Int): Int {
        val density = resources.displayMetrics.density
        return if (density > 0) (px / density).toInt() else px
    }
}
