package com.deenelife.purevison;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager; // নতুন ইমপোর্ট
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
import android.widget.TextView; // নতুন ইমপোর্ট
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

// Toolbar ইমপোর্ট
import androidx.appcompat.widget.Toolbar;

// Material Design 3 কম্পোনেন্ট ইমপোর্ট
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.slider.Slider;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

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
    public static final String KEY_CORNER_RADIUS = "CornerRadius"; // নতুন কী

    // UI কম্পোনেন্ট
    private SwitchMaterial serviceSwitch, switchBackgroundEnabled;
    private TextInputEditText editTextCustom;
    private TextInputLayout textLayoutCustom;
    private AutoCompleteTextView spinnerTemplates, spinnerFontColor, spinnerBackgroundColor;
    private Slider sliderFontSize, sliderTextOpacity, sliderBackgroundOpacity, sliderCornerRadius; // নতুন স্লাইডার
    private Toolbar toolbar;

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

    private AlertDialog helpDialog;


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
        sliderCornerRadius = findViewById(R.id.slider_corner_radius); // নতুন স্লাইডার

        registerOverlayPermissionLauncher();

        loadSettings();
        setupSpinners();
        setupListeners();

        checkFirstLaunch();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_language) {
            showLanguagePicker();
            return true;
        } else if (itemId == R.id.action_help) {
            showHelpDialog();
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
        templateArray = getResources().getStringArray(R.array.text_templates);
        ArrayAdapter<String> templateAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, templateArray);

        if (isLoadedTextTemplate) {
            spinnerTemplates.setText(loadedSavedText, false);
            textLayoutCustom.setVisibility(View.GONE);
        } else {
            spinnerTemplates.setText(loadedCustomTemplateString, false);
            textLayoutCustom.setVisibility(View.VISIBLE);
        }
        spinnerTemplates.setAdapter(templateAdapter);

        fontColorArray = getResources().getStringArray(R.array.font_colors);
        ArrayAdapter<String> fontColorAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, fontColorArray);

        spinnerFontColor.setText(fontColorArray[loadedFontColorPos], false);
        spinnerFontColor.setAdapter(fontColorAdapter);

        bgColorArray = getResources().getStringArray(R.array.background_colors);
        ArrayAdapter<String> backgroundColorAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, bgColorArray);

        spinnerBackgroundColor.setText(bgColorArray[loadedBgColorPos], false);
        spinnerBackgroundColor.setAdapter(backgroundColorAdapter);
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

        spinnerFontColor.setOnItemClickListener((parent, view, position, id) -> {
            sharedPreferences.edit().putInt(KEY_FONT_COLOR, position).apply();
            saveSettingsAndNotifyService();
        });

        switchBackgroundEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
            findViewById(R.id.text_layout_background_color).setEnabled(isChecked);
            findViewById(R.id.slider_background_opacity).setEnabled(isChecked);
            findViewById(R.id.label_bg_opacity_text).setEnabled(isChecked);
            findViewById(R.id.slider_corner_radius).setEnabled(isChecked); // নতুন
            findViewById(R.id.label_corner_radius_text).setEnabled(isChecked); // নতুন
            saveSettingsAndNotifyService();
        });

        spinnerBackgroundColor.setOnItemClickListener((parent, view, position, id) -> {
            sharedPreferences.edit().putInt(KEY_BACKGROUND_COLOR, position).apply();
            saveSettingsAndNotifyService();
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
        sliderCornerRadius.addOnSliderTouchListener(sliderSaveListener); // নতুন
    }

    private void saveSettingsAndNotifyService() {
        SharedPreferences.Editor editor = sharedPreferences.edit();

        editor.putString(KEY_OVERLAY_TEXT, editTextCustom.getText().toString());
        editor.putInt(KEY_FONT_SIZE, (int) sliderFontSize.getValue());
        editor.putInt(KEY_OPACITY, (int) sliderTextOpacity.getValue());
        editor.putBoolean(KEY_BACKGROUND_ENABLED, switchBackgroundEnabled.isChecked());
        editor.putInt(KEY_BACKGROUND_OPACITY, (int) sliderBackgroundOpacity.getValue());
        editor.putInt(KEY_CORNER_RADIUS, (int) sliderCornerRadius.getValue()); // নতুন

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

        loadedSavedText = sharedPreferences.getString(KEY_OVERLAY_TEXT, "Allah is watching me");
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

        sliderFontSize.setValue(sharedPreferences.getInt(KEY_FONT_SIZE, 14));
        sliderTextOpacity.setValue(sharedPreferences.getInt(KEY_OPACITY, 100));
        sliderBackgroundOpacity.setValue(sharedPreferences.getInt(KEY_BACKGROUND_OPACITY, 100));
        sliderCornerRadius.setValue(sharedPreferences.getInt(KEY_CORNER_RADIUS, 8)); // নতুন (ডিফল্ট 8)

        boolean bgEnabled = sharedPreferences.getBoolean(KEY_BACKGROUND_ENABLED, false);
        switchBackgroundEnabled.setChecked(bgEnabled);
        findViewById(R.id.text_layout_background_color).setEnabled(bgEnabled);
        findViewById(R.id.slider_background_opacity).setEnabled(bgEnabled);
        findViewById(R.id.label_bg_opacity_text).setEnabled(bgEnabled);
        findViewById(R.id.slider_corner_radius).setEnabled(bgEnabled); // নতুন
        findViewById(R.id.label_corner_radius_text).setEnabled(bgEnabled); // নতুন

        String[] fontColorArray = getResources().getStringArray(R.array.font_colors);
        try {
            loadedFontColorPos = sharedPreferences.getInt(KEY_FONT_COLOR, 0);
        } catch (ClassCastException e) {
            String oldColor = sharedPreferences.getString(KEY_FONT_COLOR, "White");
            loadedFontColorPos = Math.max(0, Arrays.asList(fontColorArray).indexOf(oldColor));
        }

        String[] bgColorArray = getResources().getStringArray(R.array.background_colors);
        try {
            loadedBgColorPos = sharedPreferences.getInt(KEY_BACKGROUND_COLOR, 0);
        } catch (ClassCastException e) {
            String oldColor = sharedPreferences.getString(KEY_BACKGROUND_COLOR, "Transparent");
            loadedBgColorPos = Math.max(0, Arrays.asList(bgColorArray).indexOf(oldColor));
        }

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
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveSettingsAndNotifyService();
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
            showHelpDialog();
            sharedPreferences.edit().putBoolean(KEY_IS_FIRST_LAUNCH, false).apply();
        }
    }

    // --- এই মেথডটি সম্পূর্ণ ঠিক করা হয়েছে ---
    private void showHelpDialog() {
        if (helpDialog != null && helpDialog.isShowing()) {
            helpDialog.dismiss();
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = this.getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_help_guide, null);
        builder.setView(dialogView);

        Button btnDisplay = dialogView.findViewById(R.id.btn_permission_display);
        Button btnBattery = dialogView.findViewById(R.id.btn_permission_battery);

        // --- পারমিশন চেক করার লজিক ---
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // ১. ডিসপ্লে পারমিশন চেক
            if (Settings.canDrawOverlays(this)) {
                btnDisplay.setText(getString(R.string.button_granted));
                btnDisplay.setEnabled(false);
            } else {
                btnDisplay.setText(getString(R.string.button_display_permission));
                btnDisplay.setEnabled(true);
            }

            // ২. ব্যাটারি পারমিশন চেক
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm.isIgnoringBatteryOptimizations(getPackageName())) {
                btnBattery.setText(getString(R.string.button_granted));
                btnBattery.setEnabled(false);
            } else {
                btnBattery.setText(getString(R.string.button_battery_permission));
                btnBattery.setEnabled(true);
            }
        }
        // --- পারমিশন চেক শেষ ---

        btnDisplay.setOnClickListener(v -> {
            // এই বাটনটি সব সময় ডিসপ্লে সেটিংসে যাবে
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            overlayPermissionLauncher.launch(intent); // আমরা registerOverlayPermissionLauncher ব্যবহার করি
            helpDialog.dismiss();
        });

        btnBattery.setOnClickListener(v -> {
            // ব্যাটারি অপ্টিমাইজেশন সেটিংসে পাঠাই
            try {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } catch (Exception e) {
                e.printStackTrace();
                // ফলব্যাক: App Info পেজে পাঠানো
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                Uri uri = Uri.fromParts("package", getPackageName(), null);
                intent.setData(uri);
                startActivity(intent);
            }
            helpDialog.dismiss();
        });

        builder.setNegativeButton(R.string.app_permission_dialog_button_cancel, (dialog, which) -> dialog.dismiss());

        helpDialog = builder.create();
        helpDialog.show();
    }
}