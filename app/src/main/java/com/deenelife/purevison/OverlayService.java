package com.deenelife.purevison;

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
import android.os.Build;
import android.os.IBinder;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.view.ViewCompat;

public class OverlayService extends Service implements View.OnTouchListener {

    private WindowManager windowManager;
    private View mOverlayView;
    private TextView overlayTextView;
    private WindowManager.LayoutParams params;
    private SharedPreferences sharedPreferences;

    private int initialX;
    private int initialY;
    private float initialTouchX;
    private float initialTouchY;

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
        overlayTextView = mOverlayView.findViewById(R.id.overlay_text_view);
        mOverlayView.setOnTouchListener(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    PixelFormat.TRANSLUCENT);
        } else {
            params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    PixelFormat.TRANSLUCENT);
        }

        int savedX = sharedPreferences.getInt(MainActivity.KEY_POS_X, 0);
        int savedY = sharedPreferences.getInt(MainActivity.KEY_POS_Y, 100);

        params.gravity = Gravity.TOP | Gravity.START;
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

        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_STOP_SERVICE.equals(action)) {
                saveServiceState(false);
                stopForeground(true);
                stopSelf();
                return START_NOT_STICKY;
            } else if (ACTION_UPDATE_SETTINGS.equals(action)) {
                loadAndUpdateSettings();
                return START_STICKY;
            }
        }

        saveServiceState(true);
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
        loadAndUpdateSettings();

        return START_STICKY;
    }

    private void loadAndUpdateSettings() {
        String savedText = sharedPreferences.getString(MainActivity.KEY_OVERLAY_TEXT, "Allah is watching me");
        int savedFontSize = sharedPreferences.getInt(MainActivity.KEY_FONT_SIZE, 14);
        int savedTextOpacity = sharedPreferences.getInt(MainActivity.KEY_OPACITY, 100);
        boolean isBackgroundEnabled = sharedPreferences.getBoolean(MainActivity.KEY_BACKGROUND_ENABLED, false);
        int savedBackgroundOpacity = sharedPreferences.getInt(MainActivity.KEY_BACKGROUND_OPACITY, 100);

        String savedFontColorName;
        String savedBackgroundColorName;
        // savedFontStyleName মুছে ফেলা হয়েছে

        try {
            savedFontColorName = sharedPreferences.getString(MainActivity.KEY_FONT_COLOR, "White");
        } catch (ClassCastException e) {
            int oldPosition = sharedPreferences.getInt(MainActivity.KEY_FONT_COLOR, 0);
            savedFontColorName = getResources().getStringArray(R.array.font_colors)[oldPosition];
        }

        try {
            savedBackgroundColorName = sharedPreferences.getString(MainActivity.KEY_BACKGROUND_COLOR, "Transparent");
        } catch (ClassCastException e) {
            int oldPosition = sharedPreferences.getInt(MainActivity.KEY_BACKGROUND_COLOR, 0);
            savedBackgroundColorName = getResources().getStringArray(R.array.background_colors)[oldPosition];
        }

        // ফন্ট স্টাইল চেক করার কোড মুছে ফেলা হয়েছে

        overlayTextView.setText(savedText);
        overlayTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, savedFontSize);

        float textAlphaValue = savedTextOpacity / 100.0f;
        overlayTextView.setAlpha(textAlphaValue);

        overlayTextView.setTextColor(getFontColor(savedFontColorName));

        // --- ফন্ট স্টাইল প্রয়োগ করা (SolaimanLipi হার্ডকোড করা হয়েছে) ---
        try {
            Typeface customFont = ResourcesCompat.getFont(this, R.font.solaimanlipi);
            overlayTextView.setTypeface(customFont);
        } catch (Exception e) {
            e.printStackTrace();
            overlayTextView.setTypeface(Typeface.DEFAULT);
        }

        if (isBackgroundEnabled) {
            int backgroundColor = getBackgroundColor(savedBackgroundColorName);
            int alpha = (int) (savedBackgroundOpacity * 2.55);
            backgroundColor = (backgroundColor & 0x00FFFFFF) | (alpha << 24);
            overlayTextView.setBackgroundColor(backgroundColor);
            overlayTextView.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));
            overlayTextView.setShadowLayer(0,0,0, Color.TRANSPARENT);
        } else {
            overlayTextView.setBackgroundColor(getResources().getColor(android.R.color.transparent));
            overlayTextView.setPadding(0, 0, 0, 0);
            overlayTextView.setShadowLayer(5, 1, 1, ContextCompat.getColor(this, android.R.color.black));
        }
    }

    private int getFontColor(String colorName) {
        switch (colorName) {
            case "White":
            case "সাদা":
                return ContextCompat.getColor(this, android.R.color.white);
            case "Black":
            case "কালো":
                return ContextCompat.getColor(this, android.R.color.black);
            case "Red":
            case "লাল":
                return ContextCompat.getColor(this, android.R.color.holo_red_light);
            case "Green":
            case "সবুজ":
                return ContextCompat.getColor(this, android.R.color.holo_green_light);
            case "Blue":
            case "নীল":
                return ContextCompat.getColor(this, android.R.color.holo_blue_light);
            case "Yellow":
            case "হলুদ":
                return ContextCompat.getColor(this, android.R.color.holo_orange_light);
            default:
                return ContextCompat.getColor(this, android.R.color.white);
        }
    }

    private int getBackgroundColor(String colorName) {
        switch (colorName) {
            case "Transparent":
            case "স্বচ্ছ":
                return Color.TRANSPARENT;
            case "Black":
            case "কালো":
                return ContextCompat.getColor(this, android.R.color.black);
            case "White":
            case "সাদা":
                return ContextCompat.getColor(this, android.R.color.white);
            case "Grey":
            case "ধূসর":
                return ContextCompat.getColor(this, android.R.color.darker_gray);
            case "Red":
            case "লাল":
                return ContextCompat.getColor(this, android.R.color.holo_red_dark);
            case "Blue":
            case "নীল":
                return ContextCompat.getColor(this, android.R.color.holo_blue_dark);
            default:
                return Color.TRANSPARENT;
        }
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        saveServiceState(false);

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
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                initialX = params.x;
                initialY = params.y;
                initialTouchX = event.getRawX();
                initialTouchY = event.getRawY();
                return true;
            case MotionEvent.ACTION_MOVE:
                params.x = initialX + (int) (event.getRawX() - initialTouchX);
                params.y = initialY + (int) (event.getRawY() - initialTouchY);
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