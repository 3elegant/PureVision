package com.deenelife.purevison;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

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
    public static final String KEY_FONT_COLOR = "FontColor";
    public static final String KEY_BACKGROUND_ENABLED = "BackgroundEnabled";
    public static final String KEY_BACKGROUND_COLOR = "BackgroundColor";
    public static final String KEY_BACKGROUND_OPACITY = "BackgroundOpacity";
    public static final String KEY_POS_X = "PosX";
    public static final String KEY_POS_Y = "PosY";
    public static final String KEY_LANGUAGE = "AppLanguage";

    // UI কম্পোনেন্ট
    private SwitchMaterial serviceSwitch, switchBackgroundEnabled;
    private TextInputEditText editTextCustom;
    private TextInputLayout textLayoutCustom;
    private AutoCompleteTextView spinnerTemplates, spinnerFontColor, spinnerBackgroundColor;
    private Slider sliderFontSize, sliderTextOpacity, sliderBackgroundOpacity;
    private MaterialButtonToggleGroup toggleLanguage;

    private ActivityResultLauncher<Intent> overlayPermissionLauncher;
    private SharedPreferences sharedPreferences;

    private boolean shouldShowPermissionDialog = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        sharedPreferences = getSharedPreferences(SHARED_PREFS_NAME, MODE_PRIVATE);

        loadLocale();

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // UI এলিমেন্টগুলো খুঁজে বের করা
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
        toggleLanguage = findViewById(R.id.toggle_language);

        registerOverlayPermissionLauncher();
        setupSpinners();

        loadSettings();
        setupListeners();
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
        // টেমপ্লেট স্পিনার
        ArrayAdapter<CharSequence> templateAdapter = ArrayAdapter.createFromResource(this,
                R.array.text_templates, android.R.layout.simple_dropdown_item_1line);
        spinnerTemplates.setAdapter(templateAdapter);

        // ফন্ট কালার স্পিনার
        ArrayAdapter<CharSequence> fontColorAdapter = ArrayAdapter.createFromResource(this,
                R.array.font_colors, android.R.layout.simple_dropdown_item_1line);
        spinnerFontColor.setAdapter(fontColorAdapter);

        // ব্যাকগ্রাউন্ড কালার স্পিনার
        ArrayAdapter<CharSequence> backgroundColorAdapter = ArrayAdapter.createFromResource(this,
                R.array.background_colors, android.R.layout.simple_dropdown_item_1line);
        spinnerBackgroundColor.setAdapter(backgroundColorAdapter);
    }

    private void setupListeners() {
        // ভাষা পরিবর্তন বাটন
        toggleLanguage.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                String lang = "en";
                if (checkedId == R.id.btn_lang_bn) {
                    lang = "bn";
                }

                String currentLang = sharedPreferences.getString(KEY_LANGUAGE, "bn");
                if (!currentLang.equals(lang)) {
                    sharedPreferences.edit().putString(KEY_LANGUAGE, lang).apply();
                    setLocale(lang);
                    recreate();
                }
            }
        });

        // সার্ভিস সুইচ
        serviceSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                checkOverlayPermission();
            } else {
                stopOverlayService();
            }
            saveServiceState(isChecked);
        });

        // টেমপ্লেট স্পিনার
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

        // ফন্ট কালার স্পিনার
        spinnerFontColor.setOnItemClickListener((parent, view, position, id) -> saveSettingsAndNotifyService());

        // ব্যাকগ্রাউন্ড সুইচ
        switchBackgroundEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
            findViewById(R.id.text_layout_background_color).setEnabled(isChecked);
            findViewById(R.id.slider_background_opacity).setEnabled(isChecked);
            saveSettingsAndNotifyService();
        });

        // ব্যাকগ্রাউন্ড কালার স্পিনার
        spinnerBackgroundColor.setOnItemClickListener((parent, view, position, id) -> saveSettingsAndNotifyService());

        // সব স্লাইডার
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
    }

    private void saveSettingsAndNotifyService() {
        SharedPreferences.Editor editor = sharedPreferences.edit();

        editor.putString(KEY_OVERLAY_TEXT, editTextCustom.getText().toString());
        editor.putInt(KEY_FONT_SIZE, (int) sliderFontSize.getValue());
        editor.putInt(KEY_OPACITY, (int) sliderTextOpacity.getValue());
        editor.putBoolean(KEY_BACKGROUND_ENABLED, switchBackgroundEnabled.isChecked());
        editor.putInt(KEY_BACKGROUND_OPACITY, (int) sliderBackgroundOpacity.getValue());
        editor.putString(KEY_FONT_COLOR, spinnerFontColor.getText().toString());
        editor.putString(KEY_BACKGROUND_COLOR, spinnerBackgroundColor.getText().toString());

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

    // --- এই মেথডটি আপডেট করা হয়েছে ---
    private void loadSettings() {
        String language = sharedPreferences.getString(KEY_LANGUAGE, "bn");
        toggleLanguage.check(language.equals("bn") ? R.id.btn_lang_bn : R.id.btn_lang_en);

        serviceSwitch.setChecked(sharedPreferences.getBoolean(KEY_SERVICE_ENABLED, false));

        // --- এই লাইনটি পরিবর্তন করা হয়েছে ---
        // ডিফল্ট টেক্সট হিসেবে "Allah is watching me" সেট করা হলো
        String savedText = sharedPreferences.getString(KEY_OVERLAY_TEXT, "Allah (ﷲ) is watching me");
        editTextCustom.setText(savedText);

        List<String> templates = Arrays.asList(getResources().getStringArray(R.array.text_templates));
        String customTemplateString = getString(R.string.template_custom);

        boolean isTemplate = false;
        for (String template : templates) {
            if (template.equals(savedText) && !template.equals(customTemplateString)) {
                isTemplate = true;
                break;
            }
        }

        if (isTemplate) {
            spinnerTemplates.setText(savedText, false);
            textLayoutCustom.setVisibility(View.GONE);
        } else {
            spinnerTemplates.setText(customTemplateString, false);
            textLayoutCustom.setVisibility(View.VISIBLE);
        }


        sliderFontSize.setValue(sharedPreferences.getInt(KEY_FONT_SIZE, 14));
        sliderTextOpacity.setValue(sharedPreferences.getInt(KEY_OPACITY, 100));
        sliderBackgroundOpacity.setValue(sharedPreferences.getInt(KEY_BACKGROUND_OPACITY, 100));

        boolean bgEnabled = sharedPreferences.getBoolean(KEY_BACKGROUND_ENABLED, false);
        switchBackgroundEnabled.setChecked(bgEnabled);
        findViewById(R.id.text_layout_background_color).setEnabled(bgEnabled);
        findViewById(R.id.slider_background_opacity).setEnabled(bgEnabled);

        String fontColor = sharedPreferences.getString(KEY_FONT_COLOR, getString(R.array.font_colors, 0));
        spinnerFontColor.setText(fontColor, false);

        String bgColor = sharedPreferences.getString(KEY_BACKGROUND_COLOR, getString(R.array.background_colors, 0));
        spinnerBackgroundColor.setText(bgColor, false);
    }

    private String getString(int arrayResId, int index) {
        try {
            return getResources().getStringArray(arrayResId)[index];
        } catch (Exception e) {
            return "";
        }
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
}