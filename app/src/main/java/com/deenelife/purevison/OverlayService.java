package com.deenelife.purevison;

// অ্যানিমেশন ইমপোর্টগুলো সরিয়ে ফেলা হয়েছে
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
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.view.ViewCompat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

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

    // Lock Position ভেরিয়েবল
    private boolean isPositionLocked = false;

    // --- Zikr Mode ভেরিয়েবল ---
    private Handler zikrHandler;
    private Runnable zikrRunnable;
    private List<String> zikrList;
    private int currentZikrIndex = 0;
    private boolean isZikrModeEnabled = false;
    private int zikrDurationSeconds = 10;
    // --- Zikr Mode শেষ ---

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

        // --- Zikr Mode Handler ---
        zikrHandler = new Handler(Looper.getMainLooper());
        zikrList = new ArrayList<>();
        // --- Zikr Mode শেষ ---

        // onCreate এ লক স্টেট লোড করি
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
                0, // ফ্ল্যাগ নিচে সেট করা হবে
                PixelFormat.TRANSLUCENT);

        // ফ্ল্যাগ সেট করার জন্য নতুন মেথড কল করি
        updateWindowManagerFlags();

        int savedX = sharedPreferences.getInt(MainActivity.KEY_POS_X, 0);
        int savedY = sharedPreferences.getInt(MainActivity.KEY_POS_Y, 100);

        params.gravity = Gravity.TOP | Gravity.END; // ডিফল্ট পজিশন উপরে ডানদিকে

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
                stopZikrRotation(); // Zikr Mode বন্ধ
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
        // --- Zikr Mode সেটিংস লোড ---
        isZikrModeEnabled = sharedPreferences.getBoolean(MainActivity.KEY_ZIKR_MODE_ENABLED, false);
        zikrDurationSeconds = sharedPreferences.getInt(MainActivity.KEY_ZIKR_DURATION, 10);
        loadZikrList(); // তালিকা লোড (আপডেটেড)
        // --- Zikr Mode শেষ ---

        // সাধারণ সেটিংস
        int savedFontSize = sharedPreferences.getInt(MainActivity.KEY_FONT_SIZE, 16);
        int savedTextOpacity = sharedPreferences.getInt(MainActivity.KEY_OPACITY, 100);
        boolean isBackgroundEnabled = sharedPreferences.getBoolean(MainActivity.KEY_BACKGROUND_ENABLED, false);
        int savedBackgroundOpacity = sharedPreferences.getInt(MainActivity.KEY_BACKGROUND_OPACITY, 85);
        int savedCornerRadius = sharedPreferences.getInt(MainActivity.KEY_CORNER_RADIUS, 4);

        // Lock Position লোড
        boolean newLockState = sharedPreferences.getBoolean(MainActivity.KEY_POSITION_LOCKED, false);


        int fontColorPos;
        String[] fontColorArray = getResources().getStringArray(R.array.font_colors);
        try {
            fontColorPos = sharedPreferences.getInt(MainActivity.KEY_FONT_COLOR, 0);
        } catch (ClassCastException e) {
            String oldColor = sharedPreferences.getString(MainActivity.KEY_FONT_COLOR, "White");
            fontColorPos = Math.max(0, Arrays.asList(fontColorArray).indexOf(oldColor));
        }

        int bgColorPos;
        String[] bgColorArray = getResources().getStringArray(R.array.background_colors);
        try {
            bgColorPos = sharedPreferences.getInt(MainActivity.KEY_BACKGROUND_COLOR, 0);
        } catch (ClassCastException e) {
            String oldColor = sharedPreferences.getString(MainActivity.KEY_BACKGROUND_COLOR, "Transparent");
            bgColorPos = Math.max(0, Arrays.asList(bgColorArray).indexOf(oldColor));
        }

        if(fontColorPos >= fontColorArray.length) fontColorPos = 0;
        if(bgColorPos >= bgColorArray.length) bgColorPos = 0;


        // --- Zikr Mode লজিক (অ্যানিমেশন ছাড়া) ---
        stopZikrRotation(); // আগের টাইমার বন্ধ করি

        if (isZikrModeEnabled) {
            // যিকির মোড চালু থাকলে, টাইমার শুরু করি
            startZikrRotation();
        } else {
            // যিকির মোড বন্ধ থাকলে, সাধারণ টেক্সট দেখাই
            String savedText = sharedPreferences.getString(MainActivity.KEY_OVERLAY_TEXT, "Allah is watching me");
            overlayTextView.setText(savedText);
        }
        // --- Zikr Mode শেষ ---


        overlayTextView.setTextSize(TypedValue.COMPLEX_UNIT_SP, savedFontSize);

        // অপাসিটি সেট করি
        float textAlphaValue = savedTextOpacity / 100.0f;
        overlayTextView.setAlpha(textAlphaValue);

        overlayTextView.setTextColor(getFontColor(fontColorPos));
        overlayTextView.setRotation(0); // ডিফল্ট (0) সেট করা হলো


        try {
            Typeface customFont = ResourcesCompat.getFont(this, R.font.solaimanlipi);
            overlayTextView.setTypeface(customFont);
        } catch (Exception e) {
            e.printStackTrace();
            overlayTextView.setTypeface(Typeface.DEFAULT);
        }

        if (isBackgroundEnabled) {
            GradientDrawable backgroundDrawable = new GradientDrawable();
            backgroundDrawable.setShape(GradientDrawable.RECTANGLE);
            backgroundDrawable.setCornerRadius(dpToPx(savedCornerRadius));

            int backgroundColor = getBackgroundColor(bgColorPos);
            int alpha = (int) (savedBackgroundOpacity * 2.55);
            backgroundColor = (backgroundColor & 0x00FFFFFF) | (alpha << 24);
            backgroundDrawable.setColor(backgroundColor);

            overlayTextView.setBackground(backgroundDrawable);
            overlayTextView.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));
            overlayTextView.setShadowLayer(0,0,0, Color.TRANSPARENT);
        } else {
            overlayTextView.setBackgroundColor(getResources().getColor(android.R.color.transparent));
            overlayTextView.setPadding(0, 0, 0, 0);
            overlayTextView.setShadowLayer(5, 1, 1, ContextCompat.getColor(this, android.R.color.black));
        }

        if (newLockState != isPositionLocked) {
            isPositionLocked = newLockState;
            updateWindowManagerFlags();
        }
    }

    // --- Zikr Mode: তালিকা লোড করার মেথড (ক্র্যাশ ফিক্স) ---
    private void loadZikrList() {
        String zikrString;
        try {
            // 1. String হিসেবে পড়ার চেষ্টা করি
            zikrString = sharedPreferences.getString(MainActivity.KEY_ZIKR_LIST, "");
        } catch (ClassCastException e) {
            // 2. ClassCastException হলে, পুরনো HashSet হিসেবে পড়ি
            Set<String> oldZikrSet = sharedPreferences.getStringSet(MainActivity.KEY_ZIKR_LIST, null);

            if (oldZikrSet != null) {
                // 3. নতুন String ফরম্যাটে রূপান্তর করি
                StringBuilder sb = new StringBuilder();
                for (String zikr : oldZikrSet) {
                    sb.append(zikr).append(MainActivity.ZIKR_DELIMITER);
                }
                zikrString = sb.toString();
                if (zikrString.endsWith(MainActivity.ZIKR_DELIMITER)) {
                    zikrString = zikrString.substring(0, zikrString.length() - MainActivity.ZIKR_DELIMITER.length());
                }

                // 4. নতুন ফরম্যাটে সেভ করি (মাইগ্রেশন)
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
    // --- Zikr Mode শেষ ---

    // --- Zikr Mode: টাইমার শুরু করার মেথড (অ্যানিমেশন ছাড়া) ---
    private void startZikrRotation() {
        if (zikrHandler == null || zikrList == null || zikrList.isEmpty()) {
            return;
        }

        // রিস্টার্ট করার আগে পুরনোটা থামাই
        stopZikrRotation();

        zikrRunnable = new Runnable() {
            @Override
            public void run() {
                if (zikrList.isEmpty() || overlayTextView == null) {
                    return; // তালিকা খালি থাকলে বা ভিউ না থাকলে কিছু করি না
                }

                // ইনডেক্স ঠিক করি
                if (currentZikrIndex >= zikrList.size() || currentZikrIndex < 0) {
                    currentZikrIndex = 0;
                }

                // টেক্সট সেট করি
                overlayTextView.setText(zikrList.get(currentZikrIndex));

                // পরবর্তী ইনডেক্স রেডি করি
                currentZikrIndex = (currentZikrIndex + 1) % zikrList.size();

                // নির্দিষ্ট সময় পর আবার এই কোড রান করি
                long durationMillis = zikrDurationSeconds * 1000L;
                if (durationMillis < 1000) durationMillis = 1000; // সর্বনিম্ন ১ সেকেন্ড

                if (zikrHandler != null) {
                    zikrHandler.postDelayed(this, durationMillis);
                }
            }
        };

        // প্রথমবার সাথে সাথে রান করি
        zikrHandler.post(zikrRunnable);
    }
    // --- Zikr Mode শেষ ---

    // --- Zikr Mode: টাইমার বন্ধ করার মেথড (অ্যানিমেশন ছাড়া) ---
    private void stopZikrRotation() {
        if (zikrHandler != null && zikrRunnable != null) {
            zikrHandler.removeCallbacks(zikrRunnable);
        }
        zikrRunnable = null;
    }
    // --- Zikr Mode শেষ ---


    private void updateWindowManagerFlags() {
        if (isPositionLocked) {
            // লকড: টাচ ইগনোর করবে (Click-through)
            params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        } else {
            // আনলকড: টাচ গ্রহণ করবে (Movable)
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


    private int getFontColor(int position) {
        String[] colors = getResources().getStringArray(R.array.font_colors);
        String colorName = "White";
        if (position >= 0 && position < colors.length) {
            colorName = colors[position];
        }

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

    private int getBackgroundColor(int position) {
        String[] colors = getResources().getStringArray(R.array.background_colors);
        String colorName = "Transparent";
        if (position >= 0 && position < colors.length) {
            colorName = colors[position];
        }

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

        stopZikrRotation(); // Zikr Mode বন্ধ

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