package com.deenelife.purevison

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.deenelife.purevison.data.AppPreferences
import com.deenelife.purevison.databinding.ActivityAboutBinding
import java.util.Locale

class AboutActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAboutBinding
    private val preferences by lazy { AppPreferences.getInstance(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        loadLocale()
        super.onCreate(savedInstanceState)
        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbarAbout.setNavigationOnClickListener {
            finish()
        }

        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            binding.tvAppVersion.text = "Version " + pInfo.versionName
        } catch (e: Exception) {
            e.printStackTrace()
        }

        binding.btnJoinTelegram.setOnClickListener {
            openUrl("https://t.me/purevisionapp")
        }

        binding.btnShareApp.setOnClickListener {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, getString(R.string.share_text))
            }
            startActivity(Intent.createChooser(shareIntent, getString(R.string.about_share_title)))
        }

        binding.btnVisitWebsite.setOnClickListener {
            openUrl("https://app.deenelife.com/purevision")
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

    private fun loadLocale() {
        val langCode = preferences.getLanguage()
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
}
