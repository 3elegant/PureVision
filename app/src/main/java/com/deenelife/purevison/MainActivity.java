package com.deenelife.purevison;

import android.app.Activity; // নতুন ইমপোর্ট
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

// Toolbar ইমপোর্ট
import androidx.appcompat.widget.Toolbar;

// Material Design 3 কম্পোনেন্ট ইমপোর্ট
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.slider.Slider;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

// নতুন ইমপোর্ট
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import yuku.ambilwarna.AmbilWarnaDialog;

public class MainActivity extends AppCompatActivity {

    // SharedPreferences কী (Keys)
    public static final String SHARED_PREFS_NAME = "PureVisionPrefs";
    public static final String KEY_SERVICE_ENABLED = "ServiceEnabled";
    public static final String KEY_OVERLAY_TEXT = "OverlayText";
    public static final String KEY_FONT_SIZE = "FontSize";
    public static final String KEY_OPACITY = "Opacity";
    public static final String KEY_FONT_COLOR = "FontColorPos";
    public static final String KEY_BACKGROUND_COLOR = "BackgroundColorPos";
    public static final String KEY_BACKGROUND_ENABLED = "BackgroundEnabled";
    public static final String KEY_BACKGROUND_OPACITY = "BackgroundOpacity";
    public static final String KEY_POS_X = "PosX";
    public static final String KEY_POS_Y = "PosY";
    public static final String KEY_LANGUAGE = "AppLanguage";
    public static final String KEY_IS_FIRST_LAUNCH = "IsFirstLaunch";
    public static final String KEY_CORNER_RADIUS = "CornerRadius";
    public static final String KEY_POSITION_LOCKED = "PositionLocked";

    // নতুন কালার Keys (সরাসরি কালার কোড সেভ করার জন্য)
    public static final String KEY_FONT_COLOR_INT = "FontColorInt";
    public static final String KEY_BACKGROUND_COLOR_INT = "BackgroundColorInt";

    // --- Zikr Mode Keys ---
    public static final String KEY_ZIKR_MODE_ENABLED = "ZikrModeEnabled";
    public static final String KEY_ZIKR_DURATION = "ZikrDuration";
    public static final String KEY_ZIKR_LIST = "ZikrList";
    public static final String ZIKR_DELIMITER = ";;;"; // যিকির আলাদা করার জন্য
    // --- Zikr Mode Keys শেষ ---


    // UI কম্পোনেন্ট
    private SwitchMaterial serviceSwitch, switchBackgroundEnabled, switchLockPosition;
    private TextInputEditText editTextCustom;
    private TextInputLayout textLayoutCustom;
    private AutoCompleteTextView spinnerTemplates, spinnerFontColor, spinnerBackgroundColor;
    private Slider sliderFontSize, sliderTextOpacity, sliderBackgroundOpacity, sliderCornerRadius;
    private Toolbar toolbar;
    private Button btnPickTextColor, btnPickBgColor; // কালার পিকার বাটন

    // --- Zikr Mode UI ---
    private SwitchMaterial switchZikrMode;
    private MaterialCardView cardZikrSettings;
    private Slider sliderZikrDuration;
    private TextView labelZikrDuration;
    private LinearLayout zikrListContainer; // যিকির তালিকার কন্টেইনার
    private Button btnAddNewZikr; // নতুন যিকির যোগ করার বাটন
    // --- Zikr Mode UI শেষ ---

    private ActivityResultLauncher<Intent> overlayPermissionLauncher;
    private SharedPreferences sharedPreferences;

    private boolean shouldShowPermissionDialog = false;

    private String[] templateArray;
    private String[] fontColorArray;
    private String[] bgColorArray;


    private int loadedFontColorPos;
    private int loadedBgColorPos;
    private String loadedSavedText;
    private boolean isLoadedTextTemplate;
    private String loadedCustomTemplateString;

    // বর্তমান সিলেক্টেড কালার (int)
    private int currentTextColor;
    private int currentBackgroundColor;


    private AlertDialog helpDialog;

    // ব্যাকআপ ও রিস্টোর Launcher
    private ActivityResultLauncher<Intent> backupLauncher;
    private ActivityResultLauncher<Intent> restoreLauncher;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        sharedPreferences = getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE);

        loadLocale();

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // UI এলিমেন্টগুলো খুঁজে বের করা
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        serviceSwitch = findViewById(R.id.switch_service);
        switchBackgroundEnabled = findViewById(R.id.switch_background_enabled);
        editTextCustom = findViewById(R.id.edit_text_custom);
        textLayoutCustom = findViewById(R.id.text_layout_custom);
        spinnerTemplates = findViewById(R.id.spinner_templates);
        spinnerFontColor = findViewById(R.id.spinner_font_color);
        spinnerBackgroundColor = findViewById(R.id.spinner_background_color);
        sliderFontSize = findViewById(R.id.slider_font_size);
        sliderTextOpacity = findViewById(R.id.slider_text_opacity);
        sliderBackgroundOpacity = findViewById(R.id.slider_background_opacity);
        sliderCornerRadius = findViewById(R.id.slider_corner_radius);

        // কালার পিকার বাটন
        btnPickTextColor = findViewById(R.id.btn_pick_text_color);
        btnPickBgColor = findViewById(R.id.btn_pick_bg_color);

        // Lock Position UI
        switchLockPosition = findViewById(R.id.switch_lock_position);

        // --- Zikr Mode UI ---
        switchZikrMode = findViewById(R.id.switch_zikr_mode);
        cardZikrSettings = findViewById(R.id.card_zikr_settings);
        sliderZikrDuration = findViewById(R.id.slider_zikr_duration);
        labelZikrDuration = findViewById(R.id.label_zikr_duration_text);
        zikrListContainer = findViewById(R.id.zikr_list_container); // নতুন
        btnAddNewZikr = findViewById(R.id.btn_add_new_zikr); // নতুন
        // --- Zikr Mode UI শেষ ---


        registerOverlayPermissionLauncher();
        // নতুন Launcher রেজিস্টার
        registerBackupRestoreLaunchers();

        // --- Zikr Mode ডিফল্ট তালিকা সেটআপ ---
        setupDefaultZikrList();
        // --- Zikr Mode শেষ ---

        loadAndDisplayZikrList(); // যিকির তালিকা UI-তে লোড করি
        loadSettings();
        setupSpinners();
        setupListeners();

        checkFirstLaunch();

        // --- Zikr Mode UI আপডেট ---
        updateZikrModeUI();
        // --- Zikr Mode শেষ ---
    }

    // --- Zikr Mode: ডিফল্ট তালিকা সেভ করার মেথড ---
    private void setupDefaultZikrList() {
        String currentList = sharedPreferences.getString(KEY_ZIKR_LIST, null);
        if (currentList == null || currentList.isEmpty()) {
            String delimiter = ZIKR_DELIMITER;
            StringBuilder sb = new StringBuilder();
            sb.append(getString(R.string.zikr_default_1)).append(delimiter);
            sb.append(getString(R.string.zikr_default_2)).append(delimiter);
            sb.append(getString(R.string.zikr_default_3)).append(delimiter);
            sb.append(getString(R.string.zikr_default_4)).append(delimiter);
            sb.append(getString(R.string.zikr_default_5));

            sharedPreferences.edit().putString(KEY_ZIKR_LIST, sb.toString()).apply();
        }
    }
    // --- Zikr Mode শেষ ---


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);

        MenuItem versionItem = menu.findItem(R.id.action_version);
        if (versionItem != null) {
            try {
                PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
                String version = "v" + pInfo.versionName;
                versionItem.setTitle(version);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_language) {
            showLanguagePicker();
            return true;
        } else if (itemId == R.id.action_share) {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_TEXT, getString(R.string.share_text));
            startActivity(Intent.createChooser(shareIntent, getString(R.string.action_share)));
            return true;
        } else if (itemId == R.id.action_help) {
            boolean hasDisplay = (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) || Settings.canDrawOverlays(this);
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            boolean hasBattery = (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) || pm.isIgnoringBatteryOptimizations(getPackageName());

            showHelpDialog(hasDisplay, hasBattery);
            return true;
        } else if (itemId == R.id.action_github) {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/purevisionapp"));
            startActivity(browserIntent);
            return true;
        } else if (itemId == R.id.action_backup_settings) { // নতুন
            launchBackup();
            return true;
        } else if (itemId == R.id.action_restore_settings) { // নতুন
            launchRestore();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }


    private void setLocale(String langCode) {
        if (langCode == null) return;
        Locale locale = new Locale(langCode);
        Locale.setDefault(locale);
        Resources res = getResources();
        Configuration config = res.getConfiguration();
        config.setLocale(locale);
        res.updateConfiguration(config, res.getDisplayMetrics());
    }

    private void loadLocale() {
        String language = sharedPreferences.getString(KEY_LANGUAGE, "bn");
        setLocale(language);
    }

    private void setupSpinners() {
        // Template Spinner
        templateArray = getResources().getStringArray(R.array.text_templates);
        ArrayAdapter<String> templateAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, templateArray);
        spinnerTemplates.setAdapter(templateAdapter);

        if (isLoadedTextTemplate) {
            spinnerTemplates.setText(loadedSavedText, false);
            textLayoutCustom.setVisibility(View.GONE);
        } else {
            spinnerTemplates.setText(loadedCustomTemplateString, false);
            textLayoutCustom.setVisibility(View.VISIBLE);
        }

        // Font Color Spinner
        fontColorArray = getResources().getStringArray(R.array.font_colors);
        ArrayAdapter<String> fontColorAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, fontColorArray);
        spinnerFontColor.setAdapter(fontColorAdapter);
        // যদি প্রিসেট কালার হয়, তাহলে নাম দেখাই, নইলে 'কাস্টম' দেখাই
        if (isPresetColor(currentTextColor, true)) {
            spinnerFontColor.setText(fontColorArray[loadedFontColorPos], false);
        } else {
            spinnerFontColor.setText(getString(R.string.template_custom), false);
        }


        // Background Color Spinner
        bgColorArray = getResources().getStringArray(R.array.background_colors);
        ArrayAdapter<String> backgroundColorAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, bgColorArray);
        spinnerBackgroundColor.setAdapter(backgroundColorAdapter);
        if (isPresetColor(currentBackgroundColor, false)) {
            spinnerBackgroundColor.setText(bgColorArray[loadedBgColorPos], false);
        } else {
            spinnerBackgroundColor.setText(getString(R.string.template_custom), false);
        }
    }

    // চেক করে কালারটি প্রিসেট লিস্টের কিনা
    private boolean isPresetColor(int color, boolean isText) {
        String[] arr = isText ? getResources().getStringArray(R.array.font_colors) : getResources().getStringArray(R.array.background_colors);
        for (int i = 0; i < arr.length; i++) {
            int c = isText ? getFontColorFromPosition(i) : getBackgroundColorFromPosition(i);
            if (c == color) return true;
        }
        return false;
    }


    private void setupListeners() {
        serviceSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                checkOverlayPermission();
            } else {
                stopOverlayService();
            }
            saveServiceState(isChecked);
        });

        // Lock Position Switch
        switchLockPosition.setOnCheckedChangeListener((buttonView, isChecked) -> {
            saveSettingsAndNotifyService();
        });

        // --- Zikr Mode Listeners ---
        switchZikrMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            updateZikrModeUI();
            saveSettingsAndNotifyService(); // যিকির মোড চালু/বন্ধ করলে সাথে সাথে সেভ
        });

        sliderZikrDuration.addOnChangeListener((slider, value, fromUser) -> {
            updateZikrDurationLabel(value);
        });

        btnAddNewZikr.setOnClickListener(v -> {
            addNewZikrItemView(""); // একটি খালি যিকির আইটেম যোগ করি
        });
        // --- Zikr Mode Listeners শেষ ---


        spinnerTemplates.setOnItemClickListener((parent, view, position, id) -> {
            String selectedTemplate = (String) parent.getItemAtPosition(position);
            String customTemplateString = getString(R.string.template_custom);

            if (selectedTemplate.equals(customTemplateString)) {
                textLayoutCustom.setVisibility(View.VISIBLE);
            } else {
                textLayoutCustom.setVisibility(View.GONE);
                editTextCustom.setText(selectedTemplate);
                saveSettingsAndNotifyService();
            }
        });


        // ফন্ট কালার ড্রপডাউন লিসেনার
        spinnerFontColor.setOnItemClickListener((parent, view, position, id) -> {
            sharedPreferences.edit().putInt(KEY_FONT_COLOR, position).apply();
            currentTextColor = getFontColorFromPosition(position);
            btnPickTextColor.setBackgroundColor(currentTextColor); // বাটন আপডেট
            saveSettingsAndNotifyService();
        });

        // ফন্ট কালার পিকার বাটন লিসেনার
        btnPickTextColor.setOnClickListener(v -> {
            openColorPicker(true);
        });


        switchBackgroundEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
            findViewById(R.id.text_layout_background_color).setEnabled(isChecked);
            findViewById(R.id.slider_background_opacity).setEnabled(isChecked);
            findViewById(R.id.label_bg_opacity_text).setEnabled(isChecked);
            findViewById(R.id.slider_corner_radius).setEnabled(isChecked);
            findViewById(R.id.label_corner_radius_text).setEnabled(isChecked);
            btnPickBgColor.setEnabled(isChecked); // বাটনও ডিজেবল হবে
            saveSettingsAndNotifyService();
        });

        // ব্যাকগ্রাউন্ড কালার ড্রপডাউন লিসেনার
        spinnerBackgroundColor.setOnItemClickListener((parent, view, position, id) -> {
            sharedPreferences.edit().putInt(KEY_BACKGROUND_COLOR, position).apply();
            currentBackgroundColor = getBackgroundColorFromPosition(position);
            btnPickBgColor.setBackgroundColor(currentBackgroundColor); // বাটন আপডেট
            saveSettingsAndNotifyService();
        });

        // ব্যাকগ্রাউন্ড কালার পিকার বাটন লিসেনার
        btnPickBgColor.setOnClickListener(v -> {
            openColorPicker(false);
        });

        Slider.OnSliderTouchListener sliderSaveListener = new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(@NonNull Slider slider) {}

            @Override
            public void onStopTrackingTouch(@NonNull Slider slider) {
                saveSettingsAndNotifyService();
            }
        };

        sliderFontSize.addOnSliderTouchListener(sliderSaveListener);
        sliderTextOpacity.addOnSliderTouchListener(sliderSaveListener);
        sliderBackgroundOpacity.addOnSliderTouchListener(sliderSaveListener);
        sliderCornerRadius.addOnSliderTouchListener(sliderSaveListener);
        sliderZikrDuration.addOnSliderTouchListener(sliderSaveListener); // Zikr স্লাইডার সেভ
    }

    // কালার পিকার ওপেন করার মেথড
    private void openColorPicker(boolean isTextColor) {
        int initialColor = isTextColor ? currentTextColor : currentBackgroundColor;

        new AmbilWarnaDialog(this, initialColor, new AmbilWarnaDialog.OnAmbilWarnaListener() {
            @Override
            public void onOk(AmbilWarnaDialog dialog, int color) {
                if (isTextColor) {
                    currentTextColor = color;
                    btnPickTextColor.setBackgroundColor(color);
                    spinnerFontColor.setText(getString(R.string.template_custom), false); // কাস্টম টেক্সট দেখান
                } else {
                    currentBackgroundColor = color;
                    btnPickBgColor.setBackgroundColor(color);
                    spinnerBackgroundColor.setText(getString(R.string.template_custom), false);
                }
                saveSettingsAndNotifyService();
            }

            @Override
            public void onCancel(AmbilWarnaDialog dialog) {
                // Cancelled
            }
        }).show();
    }

    // --- Zikr Mode: UI দেখানোর মেথড ---
    private void updateZikrModeUI() {
        boolean isZikrEnabled = switchZikrMode.isChecked();

        // যিকির মোড চালু হলে, সাধারণ টেক্সট সেটিংস কার্ডটি হাইড করুন
        findViewById(R.id.card_text_settings).setVisibility(isZikrEnabled ? View.GONE : View.VISIBLE);
        // এবং যিকির সেটিংস কার্ড দেখান
        cardZikrSettings.setVisibility(isZikrEnabled ? View.VISIBLE : View.GONE);
    }
    // --- Zikr Mode শেষ ---

    // --- Zikr Mode: স্লাইডার লেবেল আপডেটের মেথড ---
    private void updateZikrDurationLabel(float value) {
        String label = getString(R.string.label_zikr_duration) + ": " + (int) value + " " + getString(R.string.label_seconds);
        labelZikrDuration.setText(label);
    }
    // --- Zikr Mode শেষ ---

    // --- Zikr Mode: নতুন যিকির আইটেম UI-তে যোগ করার মেথড ---
    private void addNewZikrItemView(String zikrText) {
        LayoutInflater inflater = LayoutInflater.from(this);
        // নতুন লেআউট ফাইলটি ইনফ্ল্যাট করি
        View zikrItemView = inflater.inflate(R.layout.item_zikr_edit, zikrListContainer, false);

        TextInputEditText editText = zikrItemView.findViewById(R.id.edit_text_zikr_item);
        Button deleteButton = zikrItemView.findViewById(R.id.btn_delete_zikr_item);

        editText.setText(zikrText);

        // *** বাগ ফিক্স: সিস্টেমকে এই EditText-এর অবস্থা সেভ করা থেকে বিরত রাখা ***
        editText.setSaveEnabled(false);

        deleteButton.setOnClickListener(v -> {
            // অ্যানিমেশন দিয়ে ভিউটি রিমুভ করি
            zikrItemView.animate().alpha(0).setDuration(300).withEndAction(() -> {
                zikrListContainer.removeView(zikrItemView);
                // নোট: সেভ হবে শুধু 'onPause' বা 'onStopTrackingTouch' এ
            }).start();
        });

        // কন্টেইনারে নতুন ভিউটি যোগ করি
        zikrListContainer.addView(zikrItemView);
    }
    // --- Zikr Mode শেষ ---

    // --- Zikr Mode: সেভ করা তালিকা লোড করে UI-তে দেখানোর মেথড ---
    private void loadAndDisplayZikrList() {
        zikrListContainer.removeAllViews(); // শুরু করার আগে পুরনো ভিউগুলো পরিষ্কার করি
        String zikrString;

        try {
            zikrString = sharedPreferences.getString(KEY_ZIKR_LIST, "");
        } catch (ClassCastException e) {
            Set<String> oldZikrSet = sharedPreferences.getStringSet(KEY_ZIKR_LIST, null);
            if (oldZikrSet != null) {
                StringBuilder sb = new StringBuilder();
                for (String zikr : oldZikrSet) {
                    sb.append(zikr).append(ZIKR_DELIMITER);
                }
                zikrString = sb.toString();
                if (zikrString.endsWith(ZIKR_DELIMITER)) {
                    zikrString = zikrString.substring(0, zikrString.length() - ZIKR_DELIMITER.length());
                }
                sharedPreferences.edit().putString(KEY_ZIKR_LIST, zikrString).apply();
            } else {
                zikrString = "";
            }
        }

        if (zikrString == null || zikrString.isEmpty()) {
            setupDefaultZikrList();
            zikrString = sharedPreferences.getString(KEY_ZIKR_LIST, "");
        }

        String[] zikrArray = zikrString.split(ZIKR_DELIMITER);
        for (String zikr : zikrArray) {
            if (zikr != null && !zikr.trim().isEmpty()) {
                addNewZikrItemView(zikr);
            }
        }
    }
    // --- Zikr Mode শেষ ---


    private void saveSettingsAndNotifyService() {
        SharedPreferences.Editor editor = sharedPreferences.edit();

        // --- Zikr Mode সেভ ---
        boolean isZikrEnabled = switchZikrMode.isChecked();
        editor.putBoolean(KEY_ZIKR_MODE_ENABLED, isZikrEnabled);
        editor.putInt(KEY_ZIKR_DURATION, (int) sliderZikrDuration.getValue());

        // --- যিকির তালিকা UI থেকে পড়ে সেভ করি ---
        StringBuilder zikrBuilder = new StringBuilder();
        int childCount = zikrListContainer.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View zikrItemView = zikrListContainer.getChildAt(i);
            TextInputEditText editText = zikrItemView.findViewById(R.id.edit_text_zikr_item);
            if (editText != null) {
                String zikrText = editText.getText().toString().trim();

                if (!zikrText.isEmpty()) {
                    zikrBuilder.append(zikrText);
                    zikrBuilder.append(ZIKR_DELIMITER);
                }
            }
        }

        String zikrListString = zikrBuilder.toString();
        if (zikrListString.endsWith(ZIKR_DELIMITER)) {
            zikrListString = zikrListString.substring(0, zikrListString.length() - ZIKR_DELIMITER.length());
        }
        editor.putString(KEY_ZIKR_LIST, zikrListString);
        // --- যিকির তালিকা সেভ শেষ ---

        if (!isZikrEnabled) {
            editor.putString(KEY_OVERLAY_TEXT, editTextCustom.getText().toString());
        }

        editor.putInt(KEY_FONT_SIZE, (int) sliderFontSize.getValue());
        editor.putInt(KEY_OPACITY, (int) sliderTextOpacity.getValue());
        editor.putBoolean(KEY_BACKGROUND_ENABLED, switchBackgroundEnabled.isChecked());
        editor.putInt(KEY_BACKGROUND_OPACITY, (int) sliderBackgroundOpacity.getValue());
        editor.putInt(KEY_CORNER_RADIUS, (int) sliderCornerRadius.getValue());

        // কালার সেভ (Integer হিসেবে)
        editor.putInt(KEY_FONT_COLOR_INT, currentTextColor);
        editor.putInt(KEY_BACKGROUND_COLOR_INT, currentBackgroundColor);

        // Lock Position সেভ
        editor.putBoolean(KEY_POSITION_LOCKED, switchLockPosition.isChecked());

        editor.apply();

        if (serviceSwitch.isChecked()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                return;
            }

            Intent intent = new Intent(this, OverlayService.class);
            intent.setAction(OverlayService.ACTION_UPDATE_SETTINGS);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        }
    }

    private void loadSettings() {
        serviceSwitch.setChecked(sharedPreferences.getBoolean(KEY_SERVICE_ENABLED, false));

        // Lock Position লোড
        switchLockPosition.setChecked(sharedPreferences.getBoolean(KEY_POSITION_LOCKED, false));

        // --- Zikr Mode লোড ---
        switchZikrMode.setChecked(sharedPreferences.getBoolean(KEY_ZIKR_MODE_ENABLED, false));
        float zikrDuration = sharedPreferences.getInt(KEY_ZIKR_DURATION, 5); // ডিফল্ট ১০ সেকেন্ড
        sliderZikrDuration.setValue(zikrDuration);
        updateZikrDurationLabel(zikrDuration); // লেবেল আপডেট
        // --- Zikr Mode শেষ ---

        loadedSavedText = sharedPreferences.getString(KEY_OVERLAY_TEXT, "আল্লাহ (ﷲ) আমাকে দেখছেন");
        editTextCustom.setText(loadedSavedText);

        List<String> templates = Arrays.asList(getResources().getStringArray(R.array.text_templates));
        loadedCustomTemplateString = getString(R.string.template_custom);

        isLoadedTextTemplate = false;
        for (String template : templates) {
            if (template.equals(loadedSavedText) && !template.equals(loadedCustomTemplateString)) {
                isLoadedTextTemplate = true;
                break;
            }
        }

        sliderFontSize.setValue(sharedPreferences.getInt(KEY_FONT_SIZE, 16));
        sliderTextOpacity.setValue(sharedPreferences.getInt(KEY_OPACITY, 100));
        sliderBackgroundOpacity.setValue(sharedPreferences.getInt(KEY_BACKGROUND_OPACITY, 93));
        sliderCornerRadius.setValue(sharedPreferences.getInt(KEY_CORNER_RADIUS, 4));

        boolean bgEnabled = sharedPreferences.getBoolean(KEY_BACKGROUND_ENABLED, true);
        switchBackgroundEnabled.setChecked(bgEnabled);
        findViewById(R.id.text_layout_background_color).setEnabled(bgEnabled);
        findViewById(R.id.slider_background_opacity).setEnabled(bgEnabled);
        findViewById(R.id.label_bg_opacity_text).setEnabled(bgEnabled);
        findViewById(R.id.slider_corner_radius).setEnabled(bgEnabled);
        findViewById(R.id.label_corner_radius_text).setEnabled(bgEnabled);
        btnPickBgColor.setEnabled(bgEnabled);

        // কালার লোড করুন (ইন্টিজার হিসেবে), যদি না থাকে তবে পজিশন থেকে ডিফল্ট নিন
        String[] fontColorArray = getResources().getStringArray(R.array.font_colors);
        try {
            loadedFontColorPos = sharedPreferences.getInt(KEY_FONT_COLOR, 0);
        } catch (ClassCastException e) {
            String oldColor = sharedPreferences.getString(KEY_FONT_COLOR, "White");
            loadedFontColorPos = Math.max(0, Arrays.asList(fontColorArray).indexOf(oldColor));
        }
        // ডিফল্ট টেক্সট কালার সাদা
        currentTextColor = sharedPreferences.getInt(KEY_FONT_COLOR_INT, getFontColorFromPosition(loadedFontColorPos));
        btnPickTextColor.setBackgroundColor(currentTextColor);


        String[] bgColorArray = getResources().getStringArray(R.array.background_colors);
        try {
            loadedBgColorPos = sharedPreferences.getInt(KEY_BACKGROUND_COLOR, 0);
        } catch (ClassCastException e) {
            String oldColor = sharedPreferences.getString(KEY_BACKGROUND_COLOR, "Transparent");
            loadedBgColorPos = Math.max(0, Arrays.asList(bgColorArray).indexOf(oldColor));
        }
        // ডিফল্ট ব্যাকগ্রাউন্ড কালার ট্রান্সপারেন্ট
        currentBackgroundColor = sharedPreferences.getInt(KEY_BACKGROUND_COLOR_INT, getBackgroundColorFromPosition(loadedBgColorPos));
        btnPickBgColor.setBackgroundColor(currentBackgroundColor);

        if(loadedFontColorPos >= fontColorArray.length) loadedFontColorPos = 0;
        if(loadedBgColorPos >= bgColorArray.length) loadedBgColorPos = 0;
    }

    @Override
    protected void onResume() {
        super.onResume();
        serviceSwitch.setChecked(sharedPreferences.getBoolean(KEY_SERVICE_ENABLED, false));

        if (shouldShowPermissionDialog) {
            showRestrictedPermissionGuideDialog();
            shouldShowPermissionDialog = false;
        } else {
            checkAndShowPermissionGuide();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveSettingsAndNotifyService(); // অ্যাপ Pause হলেই সব সেটিংস সেভ করি
    }


    private void startOverlayService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            serviceSwitch.setChecked(false);
            return;
        }
        saveServiceState(true);
        serviceSwitch.setChecked(true);

        Intent intent = new Intent(this, OverlayService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void stopOverlayService() {
        saveServiceState(false);
        serviceSwitch.setChecked(false);

        Intent intent = new Intent(this, OverlayService.class);
        stopService(intent);
    }

    private void saveServiceState(boolean isEnabled) {
        sharedPreferences.edit().putBoolean(KEY_SERVICE_ENABLED, isEnabled).apply();
    }

    private void checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                overlayPermissionLauncher.launch(intent);
            } else {
                startOverlayService();
            }
        } else {
            startOverlayService();
        }
    }

    private void registerOverlayPermissionLauncher() {
        overlayPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        if (Settings.canDrawOverlays(this)) {
                            startOverlayService();
                        } else {
                            serviceSwitch.setChecked(false);
                            saveServiceState(false);

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                shouldShowPermissionDialog = true;
                            } else {
                                Toast.makeText(this, getString(R.string.app_permission_denied_toast), Toast.LENGTH_LONG).show();
                            }
                        }
                    }
                }
        );
    }

    private void showRestrictedPermissionGuideDialog() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.app_permission_dialog_title))
                .setMessage(getString(R.string.app_permission_dialog_message))
                .setPositiveButton(getString(R.string.app_permission_dialog_button_ok), (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    Uri uri = Uri.fromParts("package", getPackageName(), null);
                    intent.setData(uri);
                    startActivity(intent);
                })
                .setNegativeButton(getString(R.string.app_permission_dialog_button_cancel), (dialog, which) -> {
                    dialog.dismiss();
                    Toast.makeText(this, getString(R.string.app_permission_denied_toast), Toast.LENGTH_LONG).show();
                })
                .show();
    }

    private void showLanguagePicker() {
        final String[] languages = {"English", "বাংলা"};
        String currentLang = sharedPreferences.getString(KEY_LANGUAGE, "bn");
        int currentLangIndex = currentLang.equals("bn") ? 1 : 0;

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.label_language))
                .setSingleChoiceItems(languages, currentLangIndex, (dialog, which) -> {
                    String lang = (which == 1) ? "bn" : "en";

                    String currentLangCheck = sharedPreferences.getString(KEY_LANGUAGE, "bn");
                    if (!currentLangCheck.equals(lang)) {
                        sharedPreferences.edit().putString(KEY_LANGUAGE, lang).apply();
                        setLocale(lang);
                        recreate();
                    }
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.app_permission_dialog_button_cancel, (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void checkFirstLaunch() {
        boolean isFirstLaunch = sharedPreferences.getBoolean(KEY_IS_FIRST_LAUNCH, true);
        if (isFirstLaunch) {
            showHelpDialog(false, false);
            sharedPreferences.edit().putBoolean(KEY_IS_FIRST_LAUNCH, false).apply();
        }
    }

    private void checkAndShowPermissionGuide() {
        if (helpDialog != null && helpDialog.isShowing()) {
            return;
        }

        boolean hasDisplayPerm = true;
        boolean hasBatteryPerm = true;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            hasDisplayPerm = Settings.canDrawOverlays(this);
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            hasBatteryPerm = pm.isIgnoringBatteryOptimizations(getPackageName());
        }

        if (hasDisplayPerm && hasBatteryPerm) {
            return;
        }

        showHelpDialog(hasDisplayPerm, hasBatteryPerm);
    }

    private void showHelpDialog(boolean hasDisplayPerm, boolean hasBatteryPerm) {
        if (helpDialog != null && helpDialog.isShowing()) {
            helpDialog.dismiss();
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = this.getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_help_guide, null);
        builder.setView(dialogView);

        Button btnDisplay = dialogView.findViewById(R.id.btn_permission_display);
        Button btnBattery = dialogView.findViewById(R.id.btn_permission_battery);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (hasDisplayPerm) {
                btnDisplay.setText(getString(R.string.button_granted));
                btnDisplay.setEnabled(false);
            } else {
                btnDisplay.setText(getString(R.string.button_display_permission));
                btnDisplay.setEnabled(true);
            }

            if (hasBatteryPerm) {
                btnBattery.setText(getString(R.string.button_granted));
                btnBattery.setEnabled(false);
            } else {
                btnBattery.setText(getString(R.string.button_battery_permission));
                btnDisplay.setEnabled(true);
            }
        }

        btnDisplay.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            overlayPermissionLauncher.launch(intent);
            helpDialog.dismiss();
        });

        btnBattery.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } catch (Exception e) {
                e.printStackTrace();
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                Uri uri = Uri.fromParts("package", getPackageName(), null);
                intent.setData(uri);
                startActivity(intent);
            }
            helpDialog.dismiss();
        });

        builder.setNegativeButton(R.string.app_permission_dialog_button_cancel, (dialog, which) -> dialog.dismiss());
        builder.setNeutralButton(R.string.button_video_tutorial, (dialog, which) -> {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/purevisionapp/8"));
            startActivity(browserIntent);
        });

        helpDialog = builder.create();
        helpDialog.show();
    }

    // =================================================================
    // নতুন: ব্যাকআপ ও রিস্টোর মেথড
    // =================================================================

    private void registerBackupRestoreLaunchers() {
        backupLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) {
                            if (performBackup(uri)) {
                                Toast.makeText(this, getString(R.string.backup_success), Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(this, getString(R.string.backup_failed), Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                }
        );

        restoreLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) {
                            if (performRestore(uri)) {
                                Toast.makeText(this, getString(R.string.restore_success), Toast.LENGTH_LONG).show();
                                // রিস্টোর সফল হলে Activity রিক্রিয়েট করি
                                recreate();
                            } else {
                                Toast.makeText(this, getString(R.string.restore_failed), Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                }
        );
    }

    private void launchBackup() {
        // ব্যাকআপ নেওয়ার আগে বর্তমান সেটিংস সেভ করি
        saveSettingsAndNotifyService();

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/xml"); // আমরা XML ফাইল সেভ করছি
        intent.putExtra(Intent.EXTRA_TITLE, getString(R.string.backup_file_name));

        try {
            backupLauncher.launch(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Cannot launch file picker", Toast.LENGTH_SHORT).show();
        }
    }

    private void launchRestore() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/xml"); // শুধু XML ফাইল খুঁজতে

        try {
            restoreLauncher.launch(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Cannot launch file picker", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean performBackup(Uri uri) {
        File prefsFile = new File(getFilesDir().getParent(), "shared_prefs/" + SHARED_PREFS_NAME + ".xml");
        if (!prefsFile.exists()) {
            Toast.makeText(this, "No settings to backup.", Toast.LENGTH_SHORT).show();
            return false;
        }

        try (InputStream in = new FileInputStream(prefsFile);
             OutputStream out = getContentResolver().openOutputStream(uri)) {

            if (out == null) return false;

            byte[] buf = new byte[1024];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean performRestore(Uri uri) {
        // রিস্টোর করার আগে সার্ভিস বন্ধ করি যেন ফাইল কনফ্লিক্ট না হয়
        stopOverlayService();

        File prefsFile = new File(getFilesDir().getParent(), "shared_prefs/" + SHARED_PREFS_NAME + ".xml");

        try (InputStream in = getContentResolver().openInputStream(uri);
             OutputStream out = new FileOutputStream(prefsFile)) {

            if (in == null) return false;

            byte[] buf = new byte[1024];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        } catch (Exception e) {
            // ফাইল ইনভ্যালিড হলেও এক্সেপশন হতে পারে
            e.printStackTrace();
            return false;
        }
    }

    // =================================================================
    // Helper Methods (আগের কোড)
    // =================================================================

    // Helper method: পজিশন থেকে টেক্সট কালার কোড পাওয়া
    private int getFontColorFromPosition(int position) {
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

    // Helper method: পজিশন থেকে ব্যাকগ্রাউন্ড কালার কোড পাওয়া
    private int getBackgroundColorFromPosition(int position) {
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
}