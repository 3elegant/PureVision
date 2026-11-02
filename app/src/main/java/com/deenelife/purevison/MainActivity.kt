package com.deenelife.purevison

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.OpenableColumns
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.deenelife.purevison.data.AppPreferences
import com.deenelife.purevison.data.FontManager
import com.deenelife.purevison.databinding.ActivityMainBinding
import com.deenelife.purevison.databinding.DialogHelpGuideBinding
import com.deenelife.purevison.databinding.ItemZikrEditBinding
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import yuku.ambilwarna.AmbilWarnaDialog
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val preferences by lazy { AppPreferences.getInstance(this) }

    private var currentTextColor: Int = Color.WHITE
    private var currentBackgroundColor: Int = Color.TRANSPARENT

    private var loadedFontColorPos: Int = 0
    private var loadedBgColorPos: Int = 0
    private var isLoadedTextTemplate: Boolean = false
    private var loadedCustomTemplateString: String = ""

    private var shouldShowPermissionDialog = false
    private var helpDialog: AlertDialog? = null

    private var textDebounceJob: Job? = null

    private lateinit var overlayPermissionLauncher: ActivityResultLauncher<Intent>
    private lateinit var notificationPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var customFontPickerLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var backupLauncher: ActivityResultLauncher<Intent>
    private lateinit var restoreLauncher: ActivityResultLauncher<Intent>
    private var lastLoadedLanguage: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        loadLocale()
        lastLoadedLanguage = preferences.getLanguage()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        registerLaunchers()
        loadAndDisplayZikrList()
        loadSettings()
        setupSpinners()
        setupFontSelector()
        setupListeners()

        checkFirstLaunch()
        checkNotificationPermission()
        updateVisibility()
    }

    private fun registerLaunchers() {
        overlayPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Settings.canDrawOverlays(this)) {
                    startOverlayService()
                } else {
                    val isStatusOnly = binding.switchNotificationZikr.isChecked
                    if (isStatusOnly) {
                        binding.switchStaticText.isChecked = false
                        binding.switchZikrMode.isChecked = false
                        lifecycleScope.launch {
                            preferences.setStaticTextEnabled(false)
                            preferences.setZikrModeEnabled(false)
                        }
                        updateVisibility()
                        startOverlayService()
                        Toast.makeText(this, getString(R.string.toast_status_bar_only_mode), Toast.LENGTH_SHORT).show()
                    } else {
                        binding.switchService.isChecked = false
                        lifecycleScope.launch { preferences.setServiceEnabled(false) }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            shouldShowPermissionDialog = true
                        } else {
                            Toast.makeText(this, getString(R.string.app_permission_denied_toast), Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }

        notificationPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                notifyService()
            }
        }

        customFontPickerLauncher = registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) {
                handleCustomFontSelected(uri)
            }
        }

        backupLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data?.data != null) {
                val uri = result.data!!.data!!
                if (performBackup(uri)) {
                    Toast.makeText(this, getString(R.string.backup_success), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, getString(R.string.backup_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }

        restoreLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data?.data != null) {
                val uri = result.data!!.data!!
                if (performRestore(uri)) {
                    lifecycleScope.launch {
                        preferences.reloadFromLegacyPreferences()
                        Toast.makeText(this@MainActivity, getString(R.string.restore_success), Toast.LENGTH_LONG).show()
                        recreate()
                    }
                } else {
                    Toast.makeText(this, getString(R.string.restore_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun checkAndRequestLockScreenNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }

        val areNotifsEnabled = androidx.core.app.NotificationManagerCompat.from(this).areNotificationsEnabled()
        if (!areNotifsEnabled) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.title_lockscreen_permission))
                .setMessage(getString(R.string.desc_lockscreen_permission_blocked))
                .setPositiveButton(getString(R.string.button_open_notification_settings)) { _, _ ->
                    openNotificationSettings()
                }
                .setNegativeButton(getString(R.string.app_permission_dialog_button_cancel), null)
                .show()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.title_lockscreen_permission))
                .setMessage(getString(R.string.desc_lockscreen_permission))
                .setPositiveButton(getString(R.string.button_open_notification_settings)) { _, _ ->
                    openNotificationSettings()
                }
                .setNegativeButton(getString(R.string.action_got_it), null)
                .show()
        }
    }

    private fun openNotificationSettings() {
        try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                }
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
            }
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
            } catch (ignored: Exception) {}
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_help -> {
                val hasDisplay = (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) || Settings.canDrawOverlays(this)
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                val hasBattery = (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) || pm.isIgnoringBatteryOptimizations(packageName)
                showHelpDialog(hasDisplay, hasBattery)
                true
            }
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setLocale(langCode: String?) {
        if (langCode == null) return
        val locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Locale.forLanguageTag(langCode)
        } else {
            @Suppress("DEPRECATION")
            Locale(langCode)
        }
        Locale.setDefault(locale)
        val res = resources
        val config = res.configuration
        config.setLocale(locale)
        @Suppress("DEPRECATION")
        res.updateConfiguration(config, res.displayMetrics)
        @Suppress("DEPRECATION")
        baseContext.resources.updateConfiguration(config, res.displayMetrics)
        @Suppress("DEPRECATION")
        applicationContext.resources.updateConfiguration(config, res.displayMetrics)
    }

    private fun loadLocale() {
        val language = preferences.getLanguage()
        setLocale(language)
    }

    private fun setupSpinners() {
        val templateArray = resources.getStringArray(R.array.text_templates)
        val templateAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, templateArray)
        binding.spinnerTemplates.setAdapter(templateAdapter)

        val savedText = preferences.getOverlayText()
        if (isLoadedTextTemplate) {
            binding.spinnerTemplates.setText(savedText, false)
            binding.textLayoutCustom.visibility = View.GONE
        } else {
            binding.spinnerTemplates.setText(loadedCustomTemplateString, false)
            binding.textLayoutCustom.visibility = View.VISIBLE
        }

        val fontColorArray = resources.getStringArray(R.array.font_colors)
        val fontColorAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, fontColorArray)
        binding.spinnerFontColor.setAdapter(fontColorAdapter)
        if (isPresetColor(currentTextColor, true)) {
            val safePos = loadedFontColorPos.coerceIn(fontColorArray.indices)
            binding.spinnerFontColor.setText(fontColorArray[safePos], false)
        } else {
            binding.spinnerFontColor.setText(getString(R.string.template_custom), false)
        }

        val bgColorArray = resources.getStringArray(R.array.background_colors)
        val backgroundColorAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, bgColorArray)
        binding.spinnerBackgroundColor.setAdapter(backgroundColorAdapter)
        if (isPresetColor(currentBackgroundColor, false)) {
            val safePos = loadedBgColorPos.coerceIn(bgColorArray.indices)
            binding.spinnerBackgroundColor.setText(bgColorArray[safePos], false)
        } else {
            binding.spinnerBackgroundColor.setText(getString(R.string.template_custom), false)
        }
    }

    private fun setupFontSelector() {
        val customName = preferences.getCustomFontName()
        val fontNames = FontManager.supportedFonts.map {
            if (it.id == FontManager.FONT_CUSTOM && customName.isNotBlank()) {
                "${getString(it.displayNameRes)} ($customName)"
            } else {
                getString(it.displayNameRes)
            }
        }
        val fontAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, fontNames)
        binding.spinnerFontFamily.setAdapter(fontAdapter)

        val selectedFontId = preferences.getSelectedFont()
        val currentIndex = FontManager.getFontIndex(selectedFontId)
        binding.spinnerFontFamily.setText(fontNames[currentIndex], false)

        binding.spinnerFontFamily.setOnItemClickListener { _, _, position, _ ->
            if (position in FontManager.supportedFonts.indices) {
                val chosenFont = FontManager.supportedFonts[position]
                if (chosenFont.id == FontManager.FONT_CUSTOM && !FontManager.getCustomFontFile(this).exists()) {
                    openCustomFontPicker()
                } else {
                    lifecycleScope.launch {
                        preferences.setSelectedFont(chosenFont.id)
                        notifyService()
                    }
                }
            }
        }
    }

    private fun openCustomFontPicker() {
        try {
            customFontPickerLauncher.launch(arrayOf("font/*", "application/octet-stream", "*/*"))
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot launch file picker", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleCustomFontSelected(uri: Uri) {
        try {
            var fileName = "CustomFont"
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    fileName = cursor.getString(nameIndex)
                }
            }

            val targetFile = FontManager.getCustomFontFile(this)
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(1024)
                    var len: Int
                    while (input.read(buffer).also { len = it } > 0) {
                        output.write(buffer, 0, len)
                    }
                }
            }

            val testTypeface = Typeface.createFromFile(targetFile)
            if (testTypeface != null) {
                lifecycleScope.launch {
                    preferences.setSelectedFont(FontManager.FONT_CUSTOM)
                    preferences.setCustomFontName(fileName)
                    setupFontSelector()
                    notifyService()
                }
                Toast.makeText(this, getString(R.string.custom_font_success), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, getString(R.string.custom_font_failed), Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, getString(R.string.custom_font_failed), Toast.LENGTH_LONG).show()
        }
    }

    private fun isPresetColor(color: Int, isText: Boolean): Boolean {
        val arr = if (isText) resources.getStringArray(R.array.font_colors) else resources.getStringArray(R.array.background_colors)
        for (i in arr.indices) {
            val c = if (isText) getFontColorFromPosition(i) else getBackgroundColorFromPosition(i)
            if (c == color) return true
        }
        return false
    }

    private fun setupListeners() {
        binding.switchService.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                checkOverlayPermission()
            } else {
                stopOverlayService()
            }
            lifecycleScope.launch { preferences.setServiceEnabled(isChecked) }
            PureVisionTileService.requestTileUpdate(this)
        }


        binding.switchLockPosition.setOnCheckedChangeListener { _, _ ->
            saveSettingsAndNotifyService()
        }

        binding.switchStaticText.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && binding.switchService.isChecked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                checkOverlayPermission()
            }
            updateVisibility()
            saveSettingsAndNotifyService()
        }

        binding.switchZikrMode.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && binding.switchService.isChecked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                checkOverlayPermission()
            }
            updateVisibility()
            saveSettingsAndNotifyService()
        }

        // Status Bar / Notch Options Listeners
        binding.switchStatusBarPin.setOnCheckedChangeListener { _, isChecked ->
            binding.layoutStatusBarPinControls.visibility = if (isChecked) View.VISIBLE else View.GONE
            lifecycleScope.launch {
                preferences.setStatusBarPinEnabled(isChecked)
                notifyService()
            }
        }

        binding.toggleNotchStyle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val isIsland = checkedId == R.id.btn_style_island
                binding.layoutIslandControls.visibility = if (isIsland) View.VISIBLE else View.GONE
                binding.layoutSideControls.visibility = if (isIsland) View.GONE else View.VISIBLE
                val newStyle = if (isIsland) AppPreferences.NOTCH_STYLE_ISLAND else AppPreferences.NOTCH_STYLE_SIDE
                lifecycleScope.launch {
                    preferences.setNotchStyle(newStyle)
                    notifyService()
                }
            }
        }

        // Notch Drop Controls
        binding.sliderNotchDrop.addOnChangeListener { _, value, _ ->
            updateNotchDropLabel(value)
            preferences.updateNotchDropOffsetYAsync(value.toInt())
        }

        binding.sliderNotchDrop.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}
            override fun onStopTrackingTouch(slider: Slider) {
                lifecycleScope.launch {
                    preferences.setNotchDropOffsetY(slider.value.toInt())
                    notifyService()
                }
            }
        })

        binding.btnDropClose.setOnClickListener {
            binding.sliderNotchDrop.value = 24.0f
            updateNotchDropLabel(24.0f)
            lifecycleScope.launch {
                preferences.setNotchDropOffsetY(24)
                notifyService()
            }
        }

        binding.btnDropMedium.setOnClickListener {
            binding.sliderNotchDrop.value = 32.0f
            updateNotchDropLabel(32.0f)
            lifecycleScope.launch {
                preferences.setNotchDropOffsetY(32)
                notifyService()
            }
        }

        binding.btnDropFar.setOnClickListener {
            binding.sliderNotchDrop.value = 44.0f
            updateNotchDropLabel(44.0f)
            lifecycleScope.launch {
                preferences.setNotchDropOffsetY(44)
                notifyService()
            }
        }

        binding.btnResetNotchDrop.setOnClickListener {
            binding.sliderNotchDrop.value = 32.0f
            updateNotchDropLabel(32.0f)
            lifecycleScope.launch {
                preferences.setNotchDropOffsetY(32)
                notifyService()
            }
        }

        // Island Horizontal Fine-Tune
        binding.sliderIslandOffsetX.addOnChangeListener { _, value, _ ->
            updateIslandOffsetXLabel(value)
            preferences.updateNotchOffsetXAsync(value.toInt())
        }

        binding.sliderIslandOffsetX.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}
            override fun onStopTrackingTouch(slider: Slider) {
                lifecycleScope.launch {
                    preferences.setNotchOffsetX(slider.value.toInt())
                    notifyService()
                }
            }
        })

        binding.btnResetIslandOffsetX.setOnClickListener {
            binding.sliderIslandOffsetX.value = 0.0f
            updateIslandOffsetXLabel(0.0f)
            lifecycleScope.launch {
                preferences.setNotchOffsetX(0)
                notifyService()
            }
        }

        // Side Notch Position Controls
        binding.btnNotchLeft.setOnClickListener {
            binding.sliderNotchPosition.value = 10.0f
            updateNotchSliderLabel(10.0f)
            lifecycleScope.launch {
                preferences.setStatusBarPinPercent(10.0f)
                notifyService()
            }
        }

        binding.btnNotchCenter.setOnClickListener {
            binding.sliderNotchPosition.value = 50.0f
            updateNotchSliderLabel(50.0f)
            lifecycleScope.launch {
                preferences.setStatusBarPinPercent(50.0f)
                notifyService()
            }
        }

        binding.btnNotchRight.setOnClickListener {
            binding.sliderNotchPosition.value = 90.0f
            updateNotchSliderLabel(90.0f)
            lifecycleScope.launch {
                preferences.setStatusBarPinPercent(90.0f)
                notifyService()
            }
        }

        binding.sliderNotchPosition.addOnChangeListener { _, value, _ ->
            updateNotchSliderLabel(value)
            preferences.updateStatusBarPinPercentAsync(value)
        }

        binding.sliderNotchPosition.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}
            override fun onStopTrackingTouch(slider: Slider) {
                lifecycleScope.launch {
                    preferences.setStatusBarPinPercent(slider.value)
                    notifyService()
                }
            }
        })

        binding.btnResetNotchPosition.setOnClickListener {
            binding.sliderNotchPosition.value = 50.0f
            updateNotchSliderLabel(50.0f)
            lifecycleScope.launch {
                preferences.setStatusBarPinPercent(50.0f)
                notifyService()
            }
        }

        // Side Notch Vertical Controls
        binding.sliderNotchSideVertical.addOnChangeListener { _, value, _ ->
            updateNotchSideVerticalLabel(value)
            preferences.updateStatusBarPinOffsetYAsync(value.toInt())
        }

        binding.sliderNotchSideVertical.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}
            override fun onStopTrackingTouch(slider: Slider) {
                lifecycleScope.launch {
                    preferences.setStatusBarPinOffsetY(slider.value.toInt())
                    notifyService()
                }
            }
        })

        binding.btnResetNotchSideVertical.setOnClickListener {
            binding.sliderNotchSideVertical.value = 0.0f
            updateNotchSideVerticalLabel(0.0f)
            lifecycleScope.launch {
                preferences.setStatusBarPinOffsetY(0)
                notifyService()
            }
        }

        binding.switchNotificationZikr.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                checkAndRequestLockScreenNotificationPermission()
            }
            updateVisibility()
            lifecycleScope.launch {
                preferences.setNotificationZikrEnabled(isChecked)
                notifyService()
            }
        }

        binding.btnPickCustomFont.setOnClickListener {
            openCustomFontPicker()
        }

        binding.sliderZikrDuration.addOnChangeListener { _, value, _ ->
            updateZikrDurationLabel(value)
        }

        binding.btnAddNewZikr.setOnClickListener {
            addNewZikrItemView("")
        }

        binding.btnResetDefaultZikr.setOnClickListener {
            binding.zikrListContainer.removeAllViews()
            val defaults = preferences.getDefaultZikrList()
            for (item in defaults) {
                addNewZikrItemView(item)
            }
            saveSettingsAndNotifyService()
            Toast.makeText(this, getString(R.string.button_reset_default_zikr), Toast.LENGTH_SHORT).show()
        }

        binding.spinnerTemplates.setOnItemClickListener { parent, _, position, _ ->
            val selectedTemplate = parent.getItemAtPosition(position) as String
            val customTemplateString = getString(R.string.template_custom)

            if (selectedTemplate == customTemplateString) {
                binding.textLayoutCustom.visibility = View.VISIBLE
            } else {
                binding.textLayoutCustom.visibility = View.GONE
                binding.editTextCustom.setText(selectedTemplate)
                saveSettingsAndNotifyService()
            }
        }

        binding.editTextCustom.doAfterTextChanged { editable ->
            textDebounceJob?.cancel()
            textDebounceJob = lifecycleScope.launch {
                delay(400)
                preferences.setOverlayText(editable?.toString() ?: "")
                notifyService()
            }
        }

        binding.spinnerFontColor.setOnItemClickListener { _, _, position, _ ->
            loadedFontColorPos = position
            currentTextColor = getFontColorFromPosition(position)
            binding.btnPickTextColor.setBackgroundColor(currentTextColor)
            saveSettingsAndNotifyService()
        }

        binding.btnPickTextColor.setOnClickListener {
            openColorPicker(true)
        }

        binding.switchBackgroundEnabled.setOnCheckedChangeListener { _, isChecked ->
            binding.textLayoutBackgroundColor.isEnabled = isChecked
            binding.sliderBackgroundOpacity.isEnabled = isChecked
            binding.labelBgOpacityText.isEnabled = isChecked
            binding.sliderCornerRadius.isEnabled = isChecked
            binding.labelCornerRadiusText.isEnabled = isChecked
            binding.btnResetBgOpacity.isEnabled = isChecked
            binding.btnResetCornerRadius.isEnabled = isChecked
            binding.btnPickBgColor.isEnabled = isChecked
            saveSettingsAndNotifyService()
        }

        binding.spinnerBackgroundColor.setOnItemClickListener { _, _, position, _ ->
            loadedBgColorPos = position
            currentBackgroundColor = getBackgroundColorFromPosition(position)
            binding.btnPickBgColor.setBackgroundColor(currentBackgroundColor)
            saveSettingsAndNotifyService()
        }

        binding.btnPickBgColor.setOnClickListener {
            openColorPicker(false)
        }

        val sliderSaveListener = object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}
            override fun onStopTrackingTouch(slider: Slider) {
                saveSettingsAndNotifyService()
            }
        }

        binding.sliderFontSize.addOnSliderTouchListener(sliderSaveListener)
        binding.sliderTextOpacity.addOnSliderTouchListener(sliderSaveListener)
        binding.sliderBackgroundOpacity.addOnSliderTouchListener(sliderSaveListener)
        binding.sliderCornerRadius.addOnSliderTouchListener(sliderSaveListener)
        binding.sliderZikrDuration.addOnSliderTouchListener(sliderSaveListener)

        binding.btnResetFontSize.setOnClickListener {
            binding.sliderFontSize.value = 16.0f
            saveSettingsAndNotifyService()
        }

        binding.btnResetTextOpacity.setOnClickListener {
            binding.sliderTextOpacity.value = 100.0f
            saveSettingsAndNotifyService()
        }

        binding.btnResetBgOpacity.setOnClickListener {
            binding.sliderBackgroundOpacity.value = 93.0f
            saveSettingsAndNotifyService()
        }

        binding.btnResetCornerRadius.setOnClickListener {
            binding.sliderCornerRadius.value = 4.0f
            saveSettingsAndNotifyService()
        }

        binding.btnResetZikrDuration.setOnClickListener {
            binding.sliderZikrDuration.value = 5.0f
            saveSettingsAndNotifyService()
        }
    }

    private fun openColorPicker(isTextColor: Boolean) {
        val initialColor = if (isTextColor) currentTextColor else currentBackgroundColor
        AmbilWarnaDialog(this, initialColor, object : AmbilWarnaDialog.OnAmbilWarnaListener {
            override fun onOk(dialog: AmbilWarnaDialog, color: Int) {
                if (isTextColor) {
                    currentTextColor = color
                    binding.btnPickTextColor.setBackgroundColor(color)
                    binding.spinnerFontColor.setText(getString(R.string.template_custom), false)
                } else {
                    currentBackgroundColor = color
                    binding.btnPickBgColor.setBackgroundColor(color)
                    binding.spinnerBackgroundColor.setText(getString(R.string.template_custom), false)
                }
                saveSettingsAndNotifyService()
            }

            override fun onCancel(dialog: AmbilWarnaDialog) {}
        }).show()
    }

    private fun updateVisibility() {
        val isStaticEnabled = binding.switchStaticText.isChecked
        val isZikrEnabled = binding.switchZikrMode.isChecked
        val isNotifZikr = binding.switchNotificationZikr.isChecked

        binding.cardNormalTextSettings.visibility = if (isStaticEnabled) View.VISIBLE else View.GONE
        val needsZikrSettings = isZikrEnabled || isNotifZikr
        binding.cardZikrSettings.visibility = if (needsZikrSettings) View.VISIBLE else View.GONE
        binding.cardTextAppearance.visibility = if (isStaticEnabled || isZikrEnabled) View.VISIBLE else View.GONE
    }

    private fun updateNotchSliderLabel(value: Float) {
        val side = when {
            value < 35f -> getString(R.string.action_notch_left)
            value > 65f -> getString(R.string.action_notch_right)
            else -> getString(R.string.action_notch_center)
        }
        binding.labelNotchSliderText.text = "${getString(R.string.label_notch_slider)}: ${value.toInt()}% ($side)"
    }

    private fun updateNotchSideVerticalLabel(value: Float) {
        binding.labelNotchSideVerticalText.text = "${getString(R.string.label_notch_side_vertical)}: ${value.toInt()} dp"
    }

    private fun updateNotchDropLabel(value: Float) {
        binding.labelNotchDropText.text = "${getString(R.string.label_notch_drop_distance)}: ${value.toInt()} dp"
    }

    private fun updateIslandOffsetXLabel(value: Float) {
        val direction = when {
            value < -5f -> " (${getString(R.string.action_notch_left)})"
            value > 5f -> " (${getString(R.string.action_notch_right)})"
            else -> " (${getString(R.string.action_notch_center)})"
        }
        binding.labelIslandOffsetXText.text = "${getString(R.string.label_notch_offset_x)}: ${value.toInt()} dp$direction"
    }

    private fun updateZikrDurationLabel(value: Float) {
        val label = getString(R.string.label_zikr_duration) + ": " + value.toInt() + " " + getString(R.string.label_seconds)
        binding.labelZikrDurationText.text = label
    }

    private fun addNewZikrItemView(zikrText: String) {
        val itemBinding = ItemZikrEditBinding.inflate(layoutInflater, binding.zikrListContainer, false)
        itemBinding.editTextZikrItem.setText(zikrText)
        itemBinding.editTextZikrItem.isSaveEnabled = false

        itemBinding.btnDeleteZikrItem.setOnClickListener {
            itemBinding.root.animate().alpha(0f).setDuration(250).withEndAction {
                binding.zikrListContainer.removeView(itemBinding.root)
                saveSettingsAndNotifyService()
            }.start()
        }

        binding.zikrListContainer.addView(itemBinding.root)
    }

    private fun loadAndDisplayZikrList() {
        binding.zikrListContainer.removeAllViews()
        val list = preferences.getZikrList()
        for (zikr in list) {
            if (zikr.isNotBlank()) {
                addNewZikrItemView(zikr)
            }
        }
    }

    private fun saveSettingsAndNotifyService() {
        val isStaticEnabled = binding.switchStaticText.isChecked
        val isZikrEnabled = binding.switchZikrMode.isChecked

        val zikrList = ArrayList<String>()
        val childCount = binding.zikrListContainer.childCount
        for (i in 0 until childCount) {
            val child = binding.zikrListContainer.getChildAt(i)
            val editText = child.findViewById<TextInputEditText>(R.id.edit_text_zikr_item)
            if (editText != null) {
                val text = editText.text.toString().trim()
                if (text.isNotEmpty()) {
                    zikrList.add(text)
                }
            }
        }

        if (zikrList.isEmpty()) {
            zikrList.addAll(preferences.getDefaultZikrList())
        }

        lifecycleScope.launch {
            preferences.setStaticTextEnabled(isStaticEnabled)
            preferences.setZikrModeEnabled(isZikrEnabled)
            preferences.setZikrDuration(binding.sliderZikrDuration.value.toInt())
            preferences.setZikrList(zikrList)
            preferences.setOverlayText(binding.editTextCustom.text.toString())
            preferences.setFontSize(binding.sliderFontSize.value.toInt())
            preferences.setTextOpacity(binding.sliderTextOpacity.value.toInt())
            preferences.setBackgroundEnabled(binding.switchBackgroundEnabled.isChecked)
            preferences.setBackgroundOpacity(binding.sliderBackgroundOpacity.value.toInt())
            preferences.setCornerRadius(binding.sliderCornerRadius.value.toInt())
            preferences.setFontColorInt(currentTextColor)
            preferences.setBackgroundColorInt(currentBackgroundColor)
            preferences.setPositionLocked(binding.switchLockPosition.isChecked)

            preferences.setStatusBarPinEnabled(binding.switchStatusBarPin.isChecked)
            preferences.setStatusBarPinPercent(binding.sliderNotchPosition.value)
            preferences.setStatusBarPinOffsetY(binding.sliderNotchSideVertical.value.toInt())
            preferences.setNotchDropOffsetY(binding.sliderNotchDrop.value.toInt())
            preferences.setNotchOffsetX(binding.sliderIslandOffsetX.value.toInt())
            preferences.setNotificationZikrEnabled(binding.switchNotificationZikr.isChecked)

            notifyService()
        }
    }

    private fun notifyService() {
        if (binding.switchService.isChecked) {
            val needsOverlay = binding.switchStaticText.isChecked || binding.switchZikrMode.isChecked
            if (needsOverlay && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                return
            }
            val intent = Intent(this, OverlayService::class.java).apply {
                action = OverlayService.ACTION_UPDATE_SETTINGS
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }
    }

    private fun loadSettings() {
        binding.switchService.isChecked = preferences.isServiceEnabled()
        binding.switchLockPosition.isChecked = preferences.isPositionLocked()

        binding.switchStaticText.isChecked = preferences.isStaticTextEnabled()
        binding.switchZikrMode.isChecked = preferences.isZikrModeEnabled()

        val isPinEnabled = preferences.isStatusBarPinEnabled()
        binding.switchStatusBarPin.isChecked = isPinEnabled
        binding.layoutStatusBarPinControls.visibility = if (isPinEnabled) View.VISIBLE else View.GONE

        val notchStyle = preferences.getNotchStyle()
        val isIsland = notchStyle == AppPreferences.NOTCH_STYLE_ISLAND
        binding.toggleNotchStyle.check(if (isIsland) R.id.btn_style_island else R.id.btn_style_side)
        binding.layoutIslandControls.visibility = if (isIsland) View.VISIBLE else View.GONE
        binding.layoutSideControls.visibility = if (isIsland) View.GONE else View.VISIBLE

        val dropOffsetY = preferences.getNotchDropOffsetY().toFloat().coerceIn(0f, 80f)
        binding.sliderNotchDrop.value = dropOffsetY
        updateNotchDropLabel(dropOffsetY)

        val offsetX = preferences.getNotchOffsetX().toFloat().coerceIn(-50f, 50f)
        binding.sliderIslandOffsetX.value = offsetX
        updateIslandOffsetXLabel(offsetX)

        val pinPercent = preferences.getStatusBarPinPercent().coerceIn(0f, 100f)
        binding.sliderNotchPosition.value = pinPercent
        updateNotchSliderLabel(pinPercent)

        val sideOffsetY = preferences.getStatusBarPinOffsetY().toFloat().coerceIn(0f, 80f)
        binding.sliderNotchSideVertical.value = sideOffsetY
        updateNotchSideVerticalLabel(sideOffsetY)

        binding.switchNotificationZikr.isChecked = preferences.isNotificationZikrEnabled()

        var zikrDuration = preferences.getZikrDuration().toFloat()
        if (zikrDuration > 300) zikrDuration = 300f
        binding.sliderZikrDuration.value = zikrDuration.coerceIn(1f, 300f)
        updateZikrDurationLabel(zikrDuration)

        val savedText = preferences.getOverlayText()
        binding.editTextCustom.setText(savedText)

        val templates = resources.getStringArray(R.array.text_templates)
        loadedCustomTemplateString = getString(R.string.template_custom)

        isLoadedTextTemplate = templates.any { it == savedText && it != loadedCustomTemplateString }

        var savedFontSize = preferences.getFontSize().toFloat()
        if (savedFontSize > 200) savedFontSize = 200f
        binding.sliderFontSize.value = savedFontSize.coerceIn(1f, 200f)

        binding.sliderTextOpacity.value = preferences.getTextOpacity().toFloat().coerceIn(10f, 100f)
        binding.sliderBackgroundOpacity.value = preferences.getBackgroundOpacity().toFloat().coerceIn(0f, 100f)
        binding.sliderCornerRadius.value = preferences.getCornerRadius().toFloat().coerceIn(0f, 50f)

        val bgEnabled = preferences.isBackgroundEnabled()
        binding.switchBackgroundEnabled.isChecked = bgEnabled
        binding.textLayoutBackgroundColor.isEnabled = bgEnabled
        binding.sliderBackgroundOpacity.isEnabled = bgEnabled
        binding.labelBgOpacityText.isEnabled = bgEnabled
        binding.sliderCornerRadius.isEnabled = bgEnabled
        binding.labelCornerRadiusText.isEnabled = bgEnabled
        binding.btnResetBgOpacity.isEnabled = bgEnabled
        binding.btnResetCornerRadius.isEnabled = bgEnabled
        binding.btnPickBgColor.isEnabled = bgEnabled

        currentTextColor = preferences.getFontColorInt()
        binding.btnPickTextColor.setBackgroundColor(currentTextColor)

        currentBackgroundColor = preferences.getBackgroundColorInt()
        binding.btnPickBgColor.setBackgroundColor(currentBackgroundColor)
    }

    override fun onResume() {
        super.onResume()
        val currentLang = preferences.getLanguage()
        if (lastLoadedLanguage != null && lastLoadedLanguage != currentLang) {
            lastLoadedLanguage = currentLang
            loadLocale()
            recreate()
            return
        }
        loadSettings()
        binding.switchService.isChecked = preferences.isServiceEnabled()

        if (shouldShowPermissionDialog) {
            showRestrictedPermissionGuideDialog()
            shouldShowPermissionDialog = false
        } else {
            checkAndShowPermissionGuide()
        }
        PureVisionTileService.requestTileUpdate(this)
    }

    override fun onPause() {
        super.onPause()
        saveSettingsAndNotifyService()
    }

    private fun startOverlayService() {
        val needsOverlay = binding.switchStaticText.isChecked || binding.switchZikrMode.isChecked
        if (needsOverlay && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            binding.switchService.isChecked = false
            return
        }
        lifecycleScope.launch { preferences.setServiceEnabled(true) }
        binding.switchService.isChecked = true

        val intent = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        PureVisionTileService.requestTileUpdate(this)
    }

    private fun stopOverlayService() {
        lifecycleScope.launch { preferences.setServiceEnabled(false) }
        binding.switchService.isChecked = false

        val intent = Intent(this, OverlayService::class.java)
        stopService(intent)
        PureVisionTileService.requestTileUpdate(this)
    }

    private fun checkOverlayPermission() {
        val needsOverlay = binding.switchStaticText.isChecked || binding.switchZikrMode.isChecked
        if (needsOverlay && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                overlayPermissionLauncher.launch(intent)
            } else {
                startOverlayService()
            }
        } else {
            startOverlayService()
        }
    }

    private fun showRestrictedPermissionGuideDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.app_permission_dialog_title))
            .setMessage(getString(R.string.app_permission_dialog_message))
            .setPositiveButton(getString(R.string.app_permission_dialog_button_ok)) { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
            }
            .setNegativeButton(getString(R.string.app_permission_dialog_button_cancel)) { dialog, _ ->
                dialog.dismiss()
                Toast.makeText(this, getString(R.string.app_permission_denied_toast), Toast.LENGTH_LONG).show()
            }
            .show()
    }

    private fun showLanguagePicker() {
        val languages = arrayOf("English", "বাংলা")
        val currentLang = preferences.getLanguage()
        val currentLangIndex = if (currentLang == "bn") 1 else 0

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.label_language))
            .setSingleChoiceItems(languages, currentLangIndex) { dialog, which ->
                val lang = if (which == 1) "bn" else "en"
                if (preferences.getLanguage() != lang) {
                    lifecycleScope.launch {
                        preferences.setLanguage(lang)
                        val currentFont = preferences.getSelectedFont()
                        if (currentFont == FontManager.FONT_SOLAIMANLIPI || currentFont == FontManager.FONT_SYSTEM) {
                            val newFont = if (lang == "bn") FontManager.FONT_SOLAIMANLIPI else FontManager.FONT_SYSTEM
                            preferences.setSelectedFont(newFont)
                        }
                        setLocale(lang)
                        recreate()
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton(R.string.app_permission_dialog_button_cancel) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun checkFirstLaunch() {
        if (preferences.isFirstLaunch()) {
            showHelpDialog(hasDisplayPerm = false, hasBatteryPerm = false)
            lifecycleScope.launch { preferences.setFirstLaunchDone() }
        }
    }

    private fun checkAndShowPermissionGuide() {
        if (helpDialog != null && helpDialog!!.isShowing) {
            return
        }

        var hasDisplayPerm = true
        var hasBatteryPerm = true

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            hasDisplayPerm = Settings.canDrawOverlays(this)
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            hasBatteryPerm = pm.isIgnoringBatteryOptimizations(packageName)
        }

        if (hasDisplayPerm && hasBatteryPerm) {
            return
        }

        showHelpDialog(hasDisplayPerm, hasBatteryPerm)
    }

    private fun showHelpDialog(hasDisplayPerm: Boolean, hasBatteryPerm: Boolean) {
        helpDialog?.dismiss()

        val guideBinding = DialogHelpGuideBinding.inflate(layoutInflater)
        val builder = AlertDialog.Builder(this).setView(guideBinding.root)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (hasDisplayPerm) {
                guideBinding.btnPermissionDisplay.text = getString(R.string.button_granted)
                guideBinding.btnPermissionDisplay.isEnabled = false
            } else {
                guideBinding.btnPermissionDisplay.text = getString(R.string.button_display_permission)
                guideBinding.btnPermissionDisplay.isEnabled = true
            }

            if (hasBatteryPerm) {
                guideBinding.btnPermissionBattery.text = getString(R.string.button_granted)
                guideBinding.btnPermissionBattery.isEnabled = false
            } else {
                guideBinding.btnPermissionBattery.text = getString(R.string.button_battery_permission)
                guideBinding.btnPermissionBattery.isEnabled = true
            }
        }

        guideBinding.btnPermissionDisplay.setOnClickListener {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
            helpDialog?.dismiss()
        }

        guideBinding.btnPermissionBattery.setOnClickListener {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
            }
            helpDialog?.dismiss()
        }

        builder.setNegativeButton(R.string.app_permission_dialog_button_cancel) { dialog, _ -> dialog.dismiss() }
        builder.setNeutralButton(R.string.button_video_tutorial) { _, _ ->
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/purevisionapp/8")))
        }

        helpDialog = builder.create().apply { show() }
    }

    private fun launchBackup() {
        saveSettingsAndNotifyService()

        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/xml"
            putExtra(Intent.EXTRA_TITLE, getString(R.string.backup_file_name))
        }

        try {
            backupLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot launch file picker", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchRestore() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/xml"
        }

        try {
            restoreLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot launch file picker", Toast.LENGTH_SHORT).show()
        }
    }

    private fun performBackup(uri: Uri): Boolean {
        preferences.flushToLegacyPreferences()
        val prefsFile = File(filesDir.parent, "shared_prefs/${AppPreferences.SHARED_PREFS_NAME}.xml")
        if (!prefsFile.exists()) {
            Toast.makeText(this, "No settings to backup.", Toast.LENGTH_SHORT).show()
            return false
        }

        return try {
            contentResolver.openOutputStream(uri)?.use { out ->
                FileInputStream(prefsFile).use { `in` ->
                    val buf = ByteArray(1024)
                    var len: Int
                    while (`in`.read(buf).also { len = it } > 0) {
                        out.write(buf, 0, len)
                    }
                }
            }
            true
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }

    private fun performRestore(uri: Uri): Boolean {
        stopOverlayService()
        val prefsFile = File(filesDir.parent, "shared_prefs/${AppPreferences.SHARED_PREFS_NAME}.xml")

        return try {
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return false
            FileOutputStream(prefsFile).use { out ->
                out.write(bytes)
            }
            runBlocking {
                preferences.restoreFromXmlStream(ByteArrayInputStream(bytes))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun getFontColorFromPosition(position: Int): Int {
        val colors = resources.getStringArray(R.array.font_colors)
        val colorName = if (position in colors.indices) colors[position] else "White"

        return when (colorName) {
            "White", "সাদা" -> ContextCompat.getColor(this, android.R.color.white)
            "Black", "কালো" -> ContextCompat.getColor(this, android.R.color.black)
            "Red", "লাল" -> ContextCompat.getColor(this, android.R.color.holo_red_light)
            "Green", "সবুজ" -> ContextCompat.getColor(this, android.R.color.holo_green_light)
            "Blue", "নীল" -> ContextCompat.getColor(this, android.R.color.holo_blue_light)
            "Yellow", "হলুদ" -> ContextCompat.getColor(this, android.R.color.holo_orange_light)
            else -> ContextCompat.getColor(this, android.R.color.white)
        }
    }

    private fun getBackgroundColorFromPosition(position: Int): Int {
        val colors = resources.getStringArray(R.array.background_colors)
        val colorName = if (position in colors.indices) colors[position] else "Transparent"

        return when (colorName) {
            "Transparent", "স্বচ্ছ" -> Color.TRANSPARENT
            "Black", "কালো" -> ContextCompat.getColor(this, android.R.color.black)
            "White", "সাদা" -> ContextCompat.getColor(this, android.R.color.white)
            "Grey", "ধূসর" -> ContextCompat.getColor(this, android.R.color.darker_gray)
            "Red", "লাল" -> ContextCompat.getColor(this, android.R.color.holo_red_dark)
            "Blue", "নীল" -> ContextCompat.getColor(this, android.R.color.holo_blue_dark)
            else -> Color.TRANSPARENT
        }
    }
}
