package com.deenelife.purevison

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.deenelife.purevison.data.AppPreferences
import com.deenelife.purevison.data.FontManager
import com.deenelife.purevison.databinding.ActivitySettingsBinding
import com.deenelife.purevison.databinding.DialogHelpGuideBinding
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val preferences by lazy { AppPreferences.getInstance(this) }

    private lateinit var backupLauncher: ActivityResultLauncher<Intent>
    private lateinit var restoreLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        loadLocale()
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbarSettings.setNavigationOnClickListener {
            finish()
        }

        registerLaunchers()
        updateLanguageDisplay()
        setupClickListeners()
    }

    private fun updateLanguageDisplay() {
        val lang = preferences.getLanguage()
        binding.tvCurrentLanguage.text = if (lang == "bn") "বাংলা" else "English"
    }

    private fun registerLaunchers() {
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
                        Toast.makeText(this@SettingsActivity, getString(R.string.restore_success), Toast.LENGTH_LONG).show()
                        notifyOverlayService()
                        recreate()
                    }
                } else {
                    Toast.makeText(this, getString(R.string.restore_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.itemSettingLanguage.setOnClickListener {
            showLanguagePicker()
        }

        binding.itemSettingNotifications.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.title_hide_notification_setting))
                .setMessage(getString(R.string.desc_hide_notification_dialog))
                .setPositiveButton(getString(R.string.button_open_notification_settings)) { _, _ ->
                    openNotificationSettings()
                }
                .setNegativeButton(getString(R.string.action_got_it), null)
                .show()
        }

        binding.itemSettingBackup.setOnClickListener {
            launchBackup()
        }

        binding.itemSettingRestore.setOnClickListener {
            launchRestore()
        }

        binding.itemSettingShare.setOnClickListener {
            shareApp()
        }

        binding.itemSettingFaq.setOnClickListener {
            startActivity(Intent(this, FaqActivity::class.java))
        }

        binding.itemSettingHelp.setOnClickListener {
            showHelpGuide()
        }

        binding.itemSettingAbout.setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }

        binding.itemSettingUpdate.setOnClickListener {
            openUrl("https://github.com/3elegant/PureVision/releases/latest")
        }
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
                        notifyOverlayService()
                        recreate()
                    }
                }
                dialog.dismiss()
            }
            .setNegativeButton(R.string.app_permission_dialog_button_cancel) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun launchBackup() {
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

    private fun notifyOverlayService() {
        if (preferences.isServiceEnabled()) {
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

    private fun shareApp() {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, getString(R.string.share_text))
        }
        startActivity(Intent.createChooser(shareIntent, getString(R.string.title_settings_share)))
    }

    private fun showHelpGuide() {
        val hasDisplay = (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) || Settings.canDrawOverlays(this)
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val hasBattery = (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) || pm.isIgnoringBatteryOptimizations(packageName)

        val guideBinding = DialogHelpGuideBinding.inflate(layoutInflater)
        val builder = AlertDialog.Builder(this)
            .setView(guideBinding.root)
            .setNegativeButton(R.string.app_permission_dialog_button_cancel) { d, _ -> d.dismiss() }

        val dialog = builder.create()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (hasDisplay) {
                guideBinding.btnPermissionDisplay.text = getString(R.string.button_granted)
                guideBinding.btnPermissionDisplay.isEnabled = false
            } else {
                guideBinding.btnPermissionDisplay.text = getString(R.string.button_display_permission)
                guideBinding.btnPermissionDisplay.isEnabled = true
            }

            if (hasBattery) {
                guideBinding.btnPermissionBattery.text = getString(R.string.button_granted)
                guideBinding.btnPermissionBattery.isEnabled = false
            } else {
                guideBinding.btnPermissionBattery.text = getString(R.string.button_battery_permission)
                guideBinding.btnPermissionBattery.isEnabled = true
            }
        }

        guideBinding.btnPermissionDisplay.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
            dialog.dismiss()
        }

        guideBinding.btnPermissionBattery.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
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
            }
            dialog.dismiss()
        }

        dialog.show()
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
        }
    }

    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
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
        val langCode = preferences.getLanguage()
        setLocale(langCode)
    }
}
