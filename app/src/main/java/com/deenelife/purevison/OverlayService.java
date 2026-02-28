package com.deenelife.purevison;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.view.ViewCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class OverlayService extends Service implements View.OnTouchListener {

    private WindowManager windowManager;
    private View mOverlayView;
    private LinearLayout overlayContainer;
    private TextView staticTextView;
    private View dividerView;
    private TextView zikrTextView;
    private WindowManager.LayoutParams params;
    private SharedPreferences sharedPreferences;

    private int initialX;
    private int initialY;
    private float initialTouchX;
    private float initialTouchY;

    private boolean isPositionLocked = false;
    private boolean isStaticTextEnabled = true;

    private Handler zikrHandler;
    private Runnable zikrRunnable;
    private List<String> zikrList;
    private int currentZikrIndex = 0;
    private boolean isZikrModeEnabled = false;
    private int zikrDurationSeconds = 5;

    private static final String CHANNEL_ID = "OverlayServiceChannel";
    private static final int NOTIFICATION_ID = 1;

    public static final String ACTION_STOP_SERVICE = "ACTION_STOP_SERVICE";
    public static final String ACTION_UPDATE_SETTINGS = "ACTION_UPDATE_SETTINGS";

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sharedPreferences = getSharedPreferences(MainActivity.SHARED_PREFS_NAME, MODE_PRIVATE);
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);

        mOverlayView = inflater.inflate(R.layout.overlay_layout, null);
        overlayContainer = mOverlayView.findViewById(R.id.overlay_container);
        staticTextView = mOverlayView.findViewById(R.id.overlay_static_text);
        dividerView = mOverlayView.findViewById(R.id.overlay_divider);
        zikrTextView = mOverlayView.findViewById(R.id.overlay_zikr_text);

        mOverlayView.setOnTouchListener(this);

        zikrHandler = new Handler(Looper.getMainLooper());
        zikrList = new ArrayList<>();

        isPositionLocked = sharedPreferences.getBoolean(MainActivity.KEY_POSITION_LOCKED, false);

        int layoutFlag;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutFlag = WindowManager.LayoutParams.TYPE_PHONE;
        }

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                0,
                PixelFormat.TRANSLUCENT);

        updateWindowManagerFlags();

        int savedX = sharedPreferences.getInt(MainActivity.KEY_POS_X, 0);
        int savedY = sharedPreferences.getInt(MainActivity.KEY_POS_Y, 100);

        params.gravity = Gravity.TOP | Gravity.END;

        params.x = savedX;
        params.y = savedY;

        try {
            windowManager.addView(mOverlayView, params);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;

        if (ACTION_STOP_SERVICE.equals(action)) {
            saveServiceState(false);
            stopZikrRotation();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        createNotificationChannel();

        Intent stopSelfIntent = new Intent(this, OverlayService.class);
        stopSelfIntent.setAction(ACTION_STOP_SERVICE);

        int pendingIntentFlag = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            pendingIntentFlag = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;
        }

        PendingIntent pendingStopIntent = PendingIntent.getService(this, 0, stopSelfIntent, pendingIntentFlag);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.service_notification_title))
                .setContentText(getString(R.string.service_notification_text))
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.service_notification_action_stop), pendingStopIntent)
                .build();

        startForeground(NOTIFICATION_ID, notification);

        if (!ACTION_UPDATE_SETTINGS.equals(action)) {
            saveServiceState(true);
        }

        loadAndUpdateSettings();

        return START_STICKY;
    }

    private void loadAndUpdateSettings() {
        isStaticTextEnabled = sharedPreferences.getBoolean(MainActivity.KEY_STATIC_TEXT_ENABLED, true);
        isZikrModeEnabled = sharedPreferences.getBoolean(MainActivity.KEY_ZIKR_MODE_ENABLED, false);
        zikrDurationSeconds = sharedPreferences.getInt(MainActivity.KEY_ZIKR_DURATION, 5);
        loadZikrList();

        int savedFontSize = sharedPreferences.getInt(MainActivity.KEY_FONT_SIZE, 16);
        int savedTextOpacity = sharedPreferences.getInt(MainActivity.KEY_OPACITY, 100);
        boolean isBackgroundEnabled = sharedPreferences.getBoolean(MainActivity.KEY_BACKGROUND_ENABLED, false);
        int savedBackgroundOpacity = sharedPreferences.getInt(MainActivity.KEY_BACKGROUND_OPACITY, 85);
        int savedCornerRadius = sharedPreferences.getInt(MainActivity.KEY_CORNER_RADIUS, 4);

        boolean newLockState = sharedPreferences.getBoolean(MainActivity.KEY_POSITION_LOCKED, false);

        int textColor = sharedPreferences.getInt(MainActivity.KEY_FONT_COLOR_INT, ContextCompat.getColor(this, android.R.color.white));
        int backgroundColorRaw = sharedPreferences.getInt(MainActivity.KEY_BACKGROUND_COLOR_INT, Color.TRANSPARENT);

        stopZikrRotation();

        if (isZikrModeEnabled) {
            startZikrRotation();
        } else {
            updateOverlayText();
        }

        staticTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, savedFontSize);
        zikrTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, savedFontSize);

        float textAlphaValue = savedTextOpacity / 100.0f;
        staticTextView.setAlpha(textAlphaValue);
        zikrTextView.setAlpha(textAlphaValue);

        staticTextView.setTextColor(textColor);
        zikrTextView.setTextColor(textColor);

        int dividerAlpha = (int) (textAlphaValue * 70);
        int dividerColor = Color.argb(dividerAlpha, Color.red(textColor), Color.green(textColor), Color.blue(textColor));
        dividerView.setBackgroundColor(dividerColor);

        try {
            Typeface customFont = ResourcesCompat.getFont(this, R.font.solaimanlipi);
            staticTextView.setTypeface(customFont);
            zikrTextView.setTypeface(customFont);
        } catch (Exception e) {
            e.printStackTrace();
            staticTextView.setTypeface(Typeface.DEFAULT);
            zikrTextView.setTypeface(Typeface.DEFAULT);
        }

        if (isBackgroundEnabled) {
            GradientDrawable backgroundDrawable = new GradientDrawable();
            backgroundDrawable.setShape(GradientDrawable.RECTANGLE);
            backgroundDrawable.setCornerRadius(dpToPx(savedCornerRadius));

            int alpha = (int) (savedBackgroundOpacity * 2.55);
            int finalBackgroundColor = (backgroundColorRaw & 0x00FFFFFF) | (alpha << 24);

            backgroundDrawable.setColor(finalBackgroundColor);

            overlayContainer.setBackground(backgroundDrawable);
            overlayContainer.setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8));
            staticTextView.setShadowLayer(0,0,0, Color.TRANSPARENT);
            zikrTextView.setShadowLayer(0,0,0, Color.TRANSPARENT);
        } else {
            overlayContainer.setBackgroundColor(Color.TRANSPARENT);
            overlayContainer.setPadding(0, 0, 0, 0);
            staticTextView.setShadowLayer(5, 1, 1, ContextCompat.getColor(this, android.R.color.black));
            zikrTextView.setShadowLayer(5, 1, 1, ContextCompat.getColor(this, android.R.color.black));
        }

        if (newLockState != isPositionLocked) {
            isPositionLocked = newLockState;
            updateWindowManagerFlags();
        }
    }

    private void updateOverlayText() {
        if (staticTextView == null || zikrTextView == null || dividerView == null) return;

        boolean hasStaticText = false;
        boolean hasZikrText = false;

        if (isStaticTextEnabled) {
            String savedText = sharedPreferences.getString(MainActivity.KEY_OVERLAY_TEXT, "Allah is watching me");
            if (savedText != null && !savedText.trim().isEmpty()) {
                staticTextView.setText(savedText.trim());
                staticTextView.setVisibility(View.VISIBLE);
                hasStaticText = true;
            } else {
                staticTextView.setVisibility(View.GONE);
            }
        } else {
            staticTextView.setVisibility(View.GONE);
        }

        if (isZikrModeEnabled && zikrList != null && !zikrList.isEmpty()) {
            if (currentZikrIndex >= 0 && currentZikrIndex < zikrList.size()) {
                zikrTextView.setText(zikrList.get(currentZikrIndex));
                zikrTextView.setVisibility(View.VISIBLE);
                hasZikrText = true;
            } else {
                zikrTextView.setVisibility(View.GONE);
            }
        } else {
            zikrTextView.setVisibility(View.GONE);
        }

        if (hasStaticText && hasZikrText) {
            dividerView.setVisibility(View.VISIBLE);
        } else {
            dividerView.setVisibility(View.GONE);
        }

        if (!hasStaticText && !hasZikrText) {
            overlayContainer.setVisibility(View.GONE);
        } else {
            overlayContainer.setVisibility(View.VISIBLE);
        }
    }

    private void loadZikrList() {
        String zikrString;
        try {
            zikrString = sharedPreferences.getString(MainActivity.KEY_ZIKR_LIST, "");
        } catch (ClassCastException e) {
            Set<String> oldZikrSet = sharedPreferences.getStringSet(MainActivity.KEY_ZIKR_LIST, null);
            if (oldZikrSet != null) {
                StringBuilder sb = new StringBuilder();
                for (String zikr : oldZikrSet) {
                    sb.append(zikr).append(MainActivity.ZIKR_DELIMITER);
                }
                zikrString = sb.toString();
                if (zikrString.endsWith(MainActivity.ZIKR_DELIMITER)) {
                    zikrString = zikrString.substring(0, zikrString.length() - MainActivity.ZIKR_DELIMITER.length());
                }
                sharedPreferences.edit().putString(MainActivity.KEY_ZIKR_LIST, zikrString).apply();
            } else {
                zikrString = "";
            }
        }

        zikrList.clear();

        if (zikrString == null || zikrString.isEmpty()) {
            zikrList.add(getString(R.string.zikr_default_1));
            return;
        }

        String[] zikrArray = zikrString.split(MainActivity.ZIKR_DELIMITER);

        for (String zikr : zikrArray) {
            if (zikr != null && !zikr.trim().isEmpty()) {
                zikrList.add(zikr.trim());
            }
        }

        if (zikrList.isEmpty()) {
            zikrList.add(getString(R.string.zikr_default_1));
        }
    }

    private void startZikrRotation() {
        if (zikrHandler == null || zikrList == null || zikrList.isEmpty()) {
            updateOverlayText();
            return;
        }

        stopZikrRotation();

        zikrRunnable = new Runnable() {
            @Override
            public void run() {
                if (zikrTextView == null) return;

                if (zikrList.isEmpty()) {
                    updateOverlayText();
                    return;
                }

                if (currentZikrIndex >= zikrList.size() || currentZikrIndex < 0) {
                    currentZikrIndex = 0;
                }

                updateOverlayText();

                currentZikrIndex = (currentZikrIndex + 1) % zikrList.size();

                long durationMillis = zikrDurationSeconds * 1000L;
                if (durationMillis < 1000) durationMillis = 1000;

                if (zikrHandler != null) {
                    zikrHandler.postDelayed(this, durationMillis);
                }
            }
        };

        zikrHandler.post(zikrRunnable);
    }

    private void stopZikrRotation() {
        if (zikrHandler != null && zikrRunnable != null) {
            zikrHandler.removeCallbacks(zikrRunnable);
        }
        zikrRunnable = null;
    }

    private void updateWindowManagerFlags() {
        if (isPositionLocked) {
            params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        } else {
            params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        }

        if (windowManager != null && mOverlayView != null && ViewCompat.isAttachedToWindow(mOverlayView)) {
            try {
                windowManager.updateViewLayout(mOverlayView, params);
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            }
        }
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        saveServiceState(false);

        stopZikrRotation();

        if (mOverlayView != null && windowManager != null && ViewCompat.isAttachedToWindow(mOverlayView)) {
            try {
                windowManager.removeView(mOverlayView);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        mOverlayView = null;

        stopForeground(true);
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Intent broadcastIntent = new Intent(this, RestartReceiver.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this,
                0,
                broadcastIntent,
                flags
        );

        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + 1000,
                    pendingIntent
            );
        }

        super.onTaskRemoved(rootIntent);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.app_name),
                    NotificationManager.IMPORTANCE_LOW
            );
            serviceChannel.setDescription(getString(R.string.service_notification_text));

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (isPositionLocked) {
            return false;
        }

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                initialX = params.x;
                initialY = params.y;
                initialTouchX = event.getRawX();
                initialTouchY = event.getRawY();
                return true;
            case MotionEvent.ACTION_MOVE:
                params.y = initialY + (int) (event.getRawY() - initialTouchY);
                params.x = initialX + (int) (initialTouchX - event.getRawX());

                windowManager.updateViewLayout(mOverlayView, params);
                return true;
            case MotionEvent.ACTION_UP:
                savePosition(params.x, params.y);
                return true;
        }
        return false;
    }

    private void savePosition(int x, int y) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(MainActivity.KEY_POS_X, x);
        editor.putInt(MainActivity.KEY_POS_Y, y);
        editor.apply();
    }

    private void saveServiceState(boolean isEnabled) {
        if (sharedPreferences == null) {
            sharedPreferences = getSharedPreferences(MainActivity.SHARED_PREFS_NAME, MODE_PRIVATE);
        }
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(MainActivity.KEY_SERVICE_ENABLED, isEnabled);
        editor.apply();
    }
}