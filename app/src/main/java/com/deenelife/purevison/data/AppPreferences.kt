package com.deenelife.purevison.data

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.io.InputStream
import android.util.Xml
import org.xmlpull.v1.XmlPullParser

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "pure_vision_datastore",
    produceMigrations = { context ->
        listOf(SharedPreferencesMigration(context, AppPreferences.SHARED_PREFS_NAME))
    }
)

class AppPreferences private constructor(private val context: Context) {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val legacyPrefs: SharedPreferences =
        context.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        const val SHARED_PREFS_NAME = "PureVisionPrefs"
        const val ZIKR_DELIMITER = ";;;"

        // Preference Keys
        val PREF_SERVICE_ENABLED = booleanPreferencesKey("ServiceEnabled")
        val PREF_STATIC_TEXT_ENABLED = booleanPreferencesKey("StaticTextEnabled")
        val PREF_ZIKR_MODE_ENABLED = booleanPreferencesKey("ZikrModeEnabled")
        val PREF_POSITION_LOCKED = booleanPreferencesKey("PositionLocked")
        val PREF_BACKGROUND_ENABLED = booleanPreferencesKey("BackgroundEnabled")
        val PREF_IS_FIRST_LAUNCH = booleanPreferencesKey("IsFirstLaunch")

        const val NOTCH_STYLE_SIDE = 0
        const val NOTCH_STYLE_ISLAND = 1

        val PREF_STATUS_BAR_PIN_ENABLED = booleanPreferencesKey("StatusBarPinEnabled")
        val PREF_STATUS_BAR_PIN_PERCENT = floatPreferencesKey("StatusBarPinPercent")
        val PREF_STATUS_BAR_PIN_OFFSET_Y = intPreferencesKey("StatusBarPinOffsetY")
        val PREF_NOTCH_STYLE = intPreferencesKey("NotchStyle")
        val PREF_NOTCH_DROP_OFFSET_Y = intPreferencesKey("NotchDropOffsetY")
        val PREF_NOTCH_OFFSET_X = intPreferencesKey("NotchOffsetX")
        val PREF_NOTIFICATION_ZIKR_ENABLED = booleanPreferencesKey("NotificationZikrEnabled")
        val PREF_DYNAMIC_STATUS_ICON_ENABLED = booleanPreferencesKey("DynamicStatusIconEnabled")

        val PREF_OVERLAY_TEXT = stringPreferencesKey("OverlayText")
        val PREF_LANGUAGE = stringPreferencesKey("AppLanguage")
        val PREF_ZIKR_LIST = stringPreferencesKey("ZikrList")
        val PREF_SELECTED_FONT = stringPreferencesKey("SelectedFont")
        val PREF_CUSTOM_FONT_NAME = stringPreferencesKey("CustomFontName")

        val PREF_FONT_SIZE = intPreferencesKey("FontSize")
        val PREF_OPACITY = intPreferencesKey("Opacity")
        val PREF_FONT_COLOR_INT = intPreferencesKey("FontColorInt")
        val PREF_BACKGROUND_COLOR_INT = intPreferencesKey("BackgroundColorInt")
        val PREF_BACKGROUND_OPACITY = intPreferencesKey("BackgroundOpacity")
        val PREF_CORNER_RADIUS = intPreferencesKey("CornerRadius")
        val PREF_ZIKR_DURATION = intPreferencesKey("ZikrDuration")
        val PREF_POS_X = intPreferencesKey("PosX")
        val PREF_POS_Y = intPreferencesKey("PosY")

        @Volatile
        private var INSTANCE: AppPreferences? = null

        fun getInstance(context: Context): AppPreferences {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppPreferences(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val preferencesFlow: Flow<Preferences> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }

    // Reactive Flows
    val serviceEnabledFlow: Flow<Boolean> = preferencesFlow.map { it[PREF_SERVICE_ENABLED] ?: legacyPrefs.getBoolean("ServiceEnabled", false) }
    val staticTextEnabledFlow: Flow<Boolean> = preferencesFlow.map { it[PREF_STATIC_TEXT_ENABLED] ?: legacyPrefs.getBoolean("StaticTextEnabled", true) }
    val zikrModeEnabledFlow: Flow<Boolean> = preferencesFlow.map { it[PREF_ZIKR_MODE_ENABLED] ?: legacyPrefs.getBoolean("ZikrModeEnabled", false) }
    val positionLockedFlow: Flow<Boolean> = preferencesFlow.map { it[PREF_POSITION_LOCKED] ?: legacyPrefs.getBoolean("PositionLocked", false) }
    val backgroundEnabledFlow: Flow<Boolean> = preferencesFlow.map { it[PREF_BACKGROUND_ENABLED] ?: legacyPrefs.getBoolean("BackgroundEnabled", true) }

    val statusBarPinEnabledFlow: Flow<Boolean> = preferencesFlow.map { it[PREF_STATUS_BAR_PIN_ENABLED] ?: legacyPrefs.getBoolean("StatusBarPinEnabled", false) }
    val statusBarPinPercentFlow: Flow<Float> = preferencesFlow.map { it[PREF_STATUS_BAR_PIN_PERCENT] ?: legacyPrefs.getFloat("StatusBarPinPercent", 50.0f) }
    val statusBarPinOffsetYFlow: Flow<Int> = preferencesFlow.map { it[PREF_STATUS_BAR_PIN_OFFSET_Y] ?: legacyPrefs.getInt("StatusBarPinOffsetY", 0) }
    val notchStyleFlow: Flow<Int> = preferencesFlow.map { it[PREF_NOTCH_STYLE] ?: legacyPrefs.getInt("NotchStyle", NOTCH_STYLE_ISLAND) }
    val notchDropOffsetYFlow: Flow<Int> = preferencesFlow.map { it[PREF_NOTCH_DROP_OFFSET_Y] ?: legacyPrefs.getInt("NotchDropOffsetY", 32) }
    val notchOffsetXFlow: Flow<Int> = preferencesFlow.map { it[PREF_NOTCH_OFFSET_X] ?: legacyPrefs.getInt("NotchOffsetX", 0) }
    val notificationZikrEnabledFlow: Flow<Boolean> = preferencesFlow.map { it[PREF_NOTIFICATION_ZIKR_ENABLED] ?: legacyPrefs.getBoolean("NotificationZikrEnabled", false) }
    val dynamicStatusIconEnabledFlow: Flow<Boolean> = preferencesFlow.map { it[PREF_DYNAMIC_STATUS_ICON_ENABLED] ?: legacyPrefs.getBoolean("DynamicStatusIconEnabled", false) }

    val overlayTextFlow: Flow<String> = preferencesFlow.map { it[PREF_OVERLAY_TEXT] ?: legacyPrefs.getString("OverlayText", "আল্লাহ (ﷲ) আমাকে দেখছেন") ?: "আল্লাহ (ﷲ) আমাকে দেখছেন" }
    val selectedFontFlow: Flow<String> = preferencesFlow.map {
        val stored = it[PREF_SELECTED_FONT] ?: legacyPrefs.getString("SelectedFont", null)
        val lang = it[PREF_LANGUAGE] ?: legacyPrefs.getString("AppLanguage", "bn") ?: "bn"
        stored ?: FontManager.getDefaultFontId(lang)
    }
    val customFontNameFlow: Flow<String> = preferencesFlow.map { it[PREF_CUSTOM_FONT_NAME] ?: legacyPrefs.getString("CustomFontName", "") ?: "" }
    val appLanguageFlow: Flow<String> = preferencesFlow.map { it[PREF_LANGUAGE] ?: legacyPrefs.getString("AppLanguage", "bn") ?: "bn" }

    val fontSizeFlow: Flow<Int> = preferencesFlow.map { it[PREF_FONT_SIZE] ?: legacyPrefs.getInt("FontSize", 16) }
    val textOpacityFlow: Flow<Int> = preferencesFlow.map { it[PREF_OPACITY] ?: legacyPrefs.getInt("Opacity", 100) }
    val backgroundOpacityFlow: Flow<Int> = preferencesFlow.map { it[PREF_BACKGROUND_OPACITY] ?: legacyPrefs.getInt("BackgroundOpacity", 93) }
    val cornerRadiusFlow: Flow<Int> = preferencesFlow.map { it[PREF_CORNER_RADIUS] ?: legacyPrefs.getInt("CornerRadius", 4) }
    val zikrDurationFlow: Flow<Int> = preferencesFlow.map { it[PREF_ZIKR_DURATION] ?: legacyPrefs.getInt("ZikrDuration", 5) }

    val fontColorIntFlow: Flow<Int> = preferencesFlow.map {
        it[PREF_FONT_COLOR_INT] ?: legacyPrefs.getInt("FontColorInt", ContextCompat.getColor(context, android.R.color.white))
    }
    val backgroundColorIntFlow: Flow<Int> = preferencesFlow.map {
        it[PREF_BACKGROUND_COLOR_INT] ?: legacyPrefs.getInt("BackgroundColorInt", Color.TRANSPARENT)
    }

    // Synchronous snapshot getters for fast Service access
    fun isServiceEnabled(): Boolean = runBlocking { serviceEnabledFlow.first() }
    fun isStaticTextEnabled(): Boolean = runBlocking { staticTextEnabledFlow.first() }
    fun isZikrModeEnabled(): Boolean = runBlocking { zikrModeEnabledFlow.first() }
    fun isPositionLocked(): Boolean = runBlocking { positionLockedFlow.first() }
    fun isBackgroundEnabled(): Boolean = runBlocking { backgroundEnabledFlow.first() }

    fun isStatusBarPinEnabled(): Boolean = legacyPrefs.getBoolean("StatusBarPinEnabled", runBlocking { statusBarPinEnabledFlow.first() })
    fun getStatusBarPinPercent(): Float = legacyPrefs.getFloat("StatusBarPinPercent", runBlocking { statusBarPinPercentFlow.first() })
    fun getStatusBarPinOffsetY(): Int = legacyPrefs.getInt("StatusBarPinOffsetY", runBlocking { statusBarPinOffsetYFlow.first() })
    fun getNotchStyle(): Int = legacyPrefs.getInt("NotchStyle", runBlocking { notchStyleFlow.first() })
    fun getNotchDropOffsetY(): Int = legacyPrefs.getInt("NotchDropOffsetY", runBlocking { notchDropOffsetYFlow.first() })
    fun getNotchOffsetX(): Int = legacyPrefs.getInt("NotchOffsetX", runBlocking { notchOffsetXFlow.first() })
    fun isNotificationZikrEnabled(): Boolean = legacyPrefs.getBoolean("NotificationZikrEnabled", runBlocking { notificationZikrEnabledFlow.first() })
    fun isDynamicStatusIconEnabled(): Boolean = legacyPrefs.getBoolean("DynamicStatusIconEnabled", runBlocking { dynamicStatusIconEnabledFlow.first() })

    fun getOverlayText(): String = runBlocking { overlayTextFlow.first() }
    fun getSelectedFont(): String = runBlocking { selectedFontFlow.first() }
    fun getCustomFontName(): String = runBlocking { customFontNameFlow.first() }
    fun getFontSize(): Int = runBlocking { fontSizeFlow.first() }
    fun getTextOpacity(): Int = runBlocking { textOpacityFlow.first() }
    fun getBackgroundOpacity(): Int = runBlocking { backgroundOpacityFlow.first() }
    fun getCornerRadius(): Int = runBlocking { cornerRadiusFlow.first() }
    fun getZikrDuration(): Int = runBlocking { zikrDurationFlow.first() }
    fun getFontColorInt(): Int = runBlocking { fontColorIntFlow.first() }
    fun getBackgroundColorInt(): Int = runBlocking { backgroundColorIntFlow.first() }
    fun getLanguage(): String = runBlocking { appLanguageFlow.first() }

    fun getPosX(): Int = runBlocking {
        preferencesFlow.first()[PREF_POS_X] ?: legacyPrefs.getInt("PosX", 0)
    }

    fun getPosY(): Int = runBlocking {
        preferencesFlow.first()[PREF_POS_Y] ?: legacyPrefs.getInt("PosY", 100)
    }

    fun isFirstLaunch(): Boolean = runBlocking {
        preferencesFlow.first()[PREF_IS_FIRST_LAUNCH] ?: legacyPrefs.getBoolean("IsFirstLaunch", true)
    }

    fun getDefaultZikrList(): List<String> {
        return listOf(
            "সুবহানাল্লাহ",
            "আলহামদুলিল্লাহ",
            "আল্লাহু আকবার",
            "লা ইলাহা ইল্লাল্লাহ",
            "আস্তাগফিরুল্লাহ"
        )
    }

    fun getZikrList(): List<String> {
        val raw = legacyPrefs.getString("ZikrList", null)
            ?: runBlocking { preferencesFlow.first()[PREF_ZIKR_LIST] }

        if (raw.isNullOrBlank()) {
            return getDefaultZikrList()
        }

        val items = raw.split(ZIKR_DELIMITER).map { it.trim() }.filter { it.isNotEmpty() }
            .filterNot {
                it == "সুবহানাল্লাহি ওয়া বিহামদিহি, সুবহানাল্লাহিল আজিম" ||
                it == "লা হাওলা ওয়ালা কুওয়াতা ইল্লা বিল্লাহ" ||
                it == "আল্লাহুম্মা সাল্লি আলা মুহাম্মাদ"
            }
        return if (items.isEmpty()) {
            getDefaultZikrList()
        } else {
            items
        }
    }

    // Async Update functions (with dual sync to legacy SharedPreferences for backup XML compatibility)
    suspend fun setServiceEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PREF_SERVICE_ENABLED] = enabled }
        legacyPrefs.edit().putBoolean("ServiceEnabled", enabled).apply()
    }

    suspend fun setStaticTextEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PREF_STATIC_TEXT_ENABLED] = enabled }
        legacyPrefs.edit().putBoolean("StaticTextEnabled", enabled).apply()
    }

    suspend fun setZikrModeEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PREF_ZIKR_MODE_ENABLED] = enabled }
        legacyPrefs.edit().putBoolean("ZikrModeEnabled", enabled).apply()
    }

    suspend fun setPositionLocked(locked: Boolean) {
        context.dataStore.edit { it[PREF_POSITION_LOCKED] = locked }
        legacyPrefs.edit().putBoolean("PositionLocked", locked).apply()
    }

    suspend fun setBackgroundEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PREF_BACKGROUND_ENABLED] = enabled }
        legacyPrefs.edit().putBoolean("BackgroundEnabled", enabled).apply()
    }

    suspend fun setStatusBarPinEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PREF_STATUS_BAR_PIN_ENABLED] = enabled }
        legacyPrefs.edit().putBoolean("StatusBarPinEnabled", enabled).commit()
    }

    suspend fun setStatusBarPinPercent(percent: Float) {
        context.dataStore.edit { it[PREF_STATUS_BAR_PIN_PERCENT] = percent }
        legacyPrefs.edit().putFloat("StatusBarPinPercent", percent).commit()
    }

    fun updateStatusBarPinPercentAsync(percent: Float) {
        applicationScope.launch { setStatusBarPinPercent(percent) }
    }

    suspend fun setStatusBarPinOffsetY(offsetY: Int) {
        context.dataStore.edit { it[PREF_STATUS_BAR_PIN_OFFSET_Y] = offsetY }
        legacyPrefs.edit().putInt("StatusBarPinOffsetY", offsetY).commit()
    }

    fun updateStatusBarPinOffsetYAsync(offsetY: Int) {
        applicationScope.launch { setStatusBarPinOffsetY(offsetY) }
    }

    suspend fun setNotchStyle(style: Int) {
        context.dataStore.edit { it[PREF_NOTCH_STYLE] = style }
        legacyPrefs.edit().putInt("NotchStyle", style).commit()
    }

    suspend fun setNotchDropOffsetY(offsetY: Int) {
        context.dataStore.edit { it[PREF_NOTCH_DROP_OFFSET_Y] = offsetY }
        legacyPrefs.edit().putInt("NotchDropOffsetY", offsetY).commit()
    }

    fun updateNotchDropOffsetYAsync(offsetY: Int) {
        applicationScope.launch { setNotchDropOffsetY(offsetY) }
    }

    suspend fun setNotchOffsetX(offsetX: Int) {
        context.dataStore.edit { it[PREF_NOTCH_OFFSET_X] = offsetX }
        legacyPrefs.edit().putInt("NotchOffsetX", offsetX).commit()
    }

    fun updateNotchOffsetXAsync(offsetX: Int) {
        applicationScope.launch { setNotchOffsetX(offsetX) }
    }

    suspend fun setNotificationZikrEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PREF_NOTIFICATION_ZIKR_ENABLED] = enabled }
        legacyPrefs.edit().putBoolean("NotificationZikrEnabled", enabled).commit()
    }

    suspend fun setDynamicStatusIconEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PREF_DYNAMIC_STATUS_ICON_ENABLED] = enabled }
        legacyPrefs.edit().putBoolean("DynamicStatusIconEnabled", enabled).apply()
    }

    suspend fun setOverlayText(text: String) {
        context.dataStore.edit { it[PREF_OVERLAY_TEXT] = text }
        legacyPrefs.edit().putString("OverlayText", text).apply()
    }

    suspend fun setSelectedFont(fontId: String) {
        context.dataStore.edit { it[PREF_SELECTED_FONT] = fontId }
        legacyPrefs.edit().putString("SelectedFont", fontId).apply()
    }

    suspend fun setCustomFontName(name: String) {
        context.dataStore.edit { it[PREF_CUSTOM_FONT_NAME] = name }
        legacyPrefs.edit().putString("CustomFontName", name).apply()
    }

    suspend fun setFontSize(size: Int) {
        context.dataStore.edit { it[PREF_FONT_SIZE] = size }
        legacyPrefs.edit().putInt("FontSize", size).apply()
    }

    suspend fun setTextOpacity(opacity: Int) {
        context.dataStore.edit { it[PREF_OPACITY] = opacity }
        legacyPrefs.edit().putInt("Opacity", opacity).apply()
    }

    suspend fun setBackgroundOpacity(opacity: Int) {
        context.dataStore.edit { it[PREF_BACKGROUND_OPACITY] = opacity }
        legacyPrefs.edit().putInt("BackgroundOpacity", opacity).apply()
    }

    suspend fun setCornerRadius(radius: Int) {
        context.dataStore.edit { it[PREF_CORNER_RADIUS] = radius }
        legacyPrefs.edit().putInt("CornerRadius", radius).apply()
    }

    suspend fun setFontColorInt(color: Int) {
        context.dataStore.edit { it[PREF_FONT_COLOR_INT] = color }
        legacyPrefs.edit().putInt("FontColorInt", color).apply()
    }

    suspend fun setBackgroundColorInt(color: Int) {
        context.dataStore.edit { it[PREF_BACKGROUND_COLOR_INT] = color }
        legacyPrefs.edit().putInt("BackgroundColorInt", color).apply()
    }

    suspend fun setZikrDuration(duration: Int) {
        context.dataStore.edit { it[PREF_ZIKR_DURATION] = duration }
        legacyPrefs.edit().putInt("ZikrDuration", duration).apply()
    }

    suspend fun setPosition(x: Int, y: Int) {
        context.dataStore.edit {
            it[PREF_POS_X] = x
            it[PREF_POS_Y] = y
        }
        legacyPrefs.edit().putInt("PosX", x).putInt("PosY", y).apply()
    }

    suspend fun setLanguage(language: String) {
        context.dataStore.edit { it[PREF_LANGUAGE] = language }
        legacyPrefs.edit().putString("AppLanguage", language).apply()
    }

    suspend fun setFirstLaunchDone() {
        context.dataStore.edit { it[PREF_IS_FIRST_LAUNCH] = false }
        legacyPrefs.edit().putBoolean("IsFirstLaunch", false).apply()
    }

    suspend fun setZikrList(list: List<String>) {
        val stringJoined = list.joinToString(ZIKR_DELIMITER)
        context.dataStore.edit { it[PREF_ZIKR_LIST] = stringJoined }
        legacyPrefs.edit().putString("ZikrList", stringJoined).apply()
    }

    fun updatePositionAsync(x: Int, y: Int) {
        applicationScope.launch { setPosition(x, y) }
    }

    fun updateServiceEnabledAsync(enabled: Boolean) {
        applicationScope.launch { setServiceEnabled(enabled) }
    }

    suspend fun reloadFromLegacyPreferences() {
        val sp = context.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE)
        context.dataStore.edit { prefs ->
            prefs.clear()
            if (sp.contains("ServiceEnabled")) prefs[PREF_SERVICE_ENABLED] = sp.getBoolean("ServiceEnabled", false)
            if (sp.contains("StaticTextEnabled")) prefs[PREF_STATIC_TEXT_ENABLED] = sp.getBoolean("StaticTextEnabled", true)
            if (sp.contains("ZikrModeEnabled")) prefs[PREF_ZIKR_MODE_ENABLED] = sp.getBoolean("ZikrModeEnabled", false)
            if (sp.contains("PositionLocked")) prefs[PREF_POSITION_LOCKED] = sp.getBoolean("PositionLocked", false)
            if (sp.contains("BackgroundEnabled")) prefs[PREF_BACKGROUND_ENABLED] = sp.getBoolean("BackgroundEnabled", true)
            if (sp.contains("IsFirstLaunch")) prefs[PREF_IS_FIRST_LAUNCH] = sp.getBoolean("IsFirstLaunch", true)

            if (sp.contains("StatusBarPinEnabled")) prefs[PREF_STATUS_BAR_PIN_ENABLED] = sp.getBoolean("StatusBarPinEnabled", false)
            if (sp.contains("StatusBarPinPercent")) prefs[PREF_STATUS_BAR_PIN_PERCENT] = sp.getFloat("StatusBarPinPercent", 50.0f)
            if (sp.contains("StatusBarPinOffsetY")) prefs[PREF_STATUS_BAR_PIN_OFFSET_Y] = sp.getInt("StatusBarPinOffsetY", 0)
            if (sp.contains("NotchStyle")) prefs[PREF_NOTCH_STYLE] = sp.getInt("NotchStyle", NOTCH_STYLE_ISLAND)
            if (sp.contains("NotchDropOffsetY")) prefs[PREF_NOTCH_DROP_OFFSET_Y] = sp.getInt("NotchDropOffsetY", 32)
            if (sp.contains("NotchOffsetX")) prefs[PREF_NOTCH_OFFSET_X] = sp.getInt("NotchOffsetX", 0)
            if (sp.contains("NotificationZikrEnabled")) prefs[PREF_NOTIFICATION_ZIKR_ENABLED] = sp.getBoolean("NotificationZikrEnabled", false)

            if (sp.contains("OverlayText")) prefs[PREF_OVERLAY_TEXT] = sp.getString("OverlayText", "") ?: ""
            if (sp.contains("AppLanguage")) prefs[PREF_LANGUAGE] = sp.getString("AppLanguage", "bn") ?: "bn"
            if (sp.contains("ZikrList")) prefs[PREF_ZIKR_LIST] = sp.getString("ZikrList", "") ?: ""
            if (sp.contains("SelectedFont")) prefs[PREF_SELECTED_FONT] = sp.getString("SelectedFont", "") ?: ""
            if (sp.contains("CustomFontName")) prefs[PREF_CUSTOM_FONT_NAME] = sp.getString("CustomFontName", "") ?: ""

            if (sp.contains("FontSize")) prefs[PREF_FONT_SIZE] = sp.getInt("FontSize", 16)
            if (sp.contains("Opacity")) prefs[PREF_OPACITY] = sp.getInt("Opacity", 100)
            if (sp.contains("FontColorInt")) prefs[PREF_FONT_COLOR_INT] = sp.getInt("FontColorInt", ContextCompat.getColor(context, android.R.color.white))
            if (sp.contains("BackgroundColorInt")) prefs[PREF_BACKGROUND_COLOR_INT] = sp.getInt("BackgroundColorInt", Color.TRANSPARENT)
            if (sp.contains("BackgroundOpacity")) prefs[PREF_BACKGROUND_OPACITY] = sp.getInt("BackgroundOpacity", 93)
            if (sp.contains("CornerRadius")) prefs[PREF_CORNER_RADIUS] = sp.getInt("CornerRadius", 4)
            if (sp.contains("ZikrDuration")) prefs[PREF_ZIKR_DURATION] = sp.getInt("ZikrDuration", 5)
            if (sp.contains("PosX")) prefs[PREF_POS_X] = sp.getInt("PosX", 0)
            if (sp.contains("PosY")) prefs[PREF_POS_Y] = sp.getInt("PosY", 100)
        }
    }

    fun flushToLegacyPreferences() {
        val editor = legacyPrefs.edit()
        editor.putBoolean("ServiceEnabled", isServiceEnabled())
        editor.putBoolean("StaticTextEnabled", isStaticTextEnabled())
        editor.putBoolean("ZikrModeEnabled", isZikrModeEnabled())
        editor.putBoolean("PositionLocked", isPositionLocked())
        editor.putBoolean("BackgroundEnabled", isBackgroundEnabled())
        editor.putBoolean("IsFirstLaunch", isFirstLaunch())

        editor.putBoolean("StatusBarPinEnabled", isStatusBarPinEnabled())
        editor.putFloat("StatusBarPinPercent", getStatusBarPinPercent())
        editor.putInt("StatusBarPinOffsetY", getStatusBarPinOffsetY())
        editor.putInt("NotchStyle", getNotchStyle())
        editor.putInt("NotchDropOffsetY", getNotchDropOffsetY())
        editor.putInt("NotchOffsetX", getNotchOffsetX())
        editor.putBoolean("NotificationZikrEnabled", isNotificationZikrEnabled())
        editor.putBoolean("DynamicStatusIconEnabled", isDynamicStatusIconEnabled())

        editor.putString("OverlayText", getOverlayText())
        editor.putString("AppLanguage", getLanguage())
        editor.putString("ZikrList", getZikrList().joinToString(ZIKR_DELIMITER))
        editor.putString("SelectedFont", getSelectedFont())
        editor.putString("CustomFontName", getCustomFontName())

        editor.putInt("FontSize", getFontSize())
        editor.putInt("Opacity", getTextOpacity())
        editor.putInt("FontColorInt", getFontColorInt())
        editor.putInt("BackgroundColorInt", getBackgroundColorInt())
        editor.putInt("BackgroundOpacity", getBackgroundOpacity())
        editor.putInt("CornerRadius", getCornerRadius())
        editor.putInt("ZikrDuration", getZikrDuration())
        editor.putInt("PosX", getPosX())
        editor.putInt("PosY", getPosY())
        editor.commit()
    }

    suspend fun restoreFromXmlStream(inputStream: InputStream): Boolean {
        return try {
            val parser = Xml.newPullParser()
            parser.setInput(inputStream, "utf-8")
            var eventType = parser.eventType
            val map = mutableMapOf<String, Any>()

            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    val tag = parser.name
                    val name = parser.getAttributeValue(null, "name")
                    if (name != null) {
                        when (tag) {
                            "boolean" -> {
                                val value = parser.getAttributeValue(null, "value")?.toBoolean() ?: false
                                map[name] = value
                            }
                            "int" -> {
                                val value = parser.getAttributeValue(null, "value")?.toIntOrNull() ?: 0
                                map[name] = value
                            }
                            "float" -> {
                                val value = parser.getAttributeValue(null, "value")?.toFloatOrNull() ?: 0f
                                map[name] = value
                            }
                            "string" -> {
                                val value = parser.getAttributeValue(null, "value") ?: parser.nextText() ?: ""
                                map[name] = value
                            }
                        }
                    }
                }
                eventType = parser.next()
            }

            if (map.isEmpty()) return false

            val editor = legacyPrefs.edit()
            editor.clear()
            for ((key, value) in map) {
                when (value) {
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Float -> editor.putFloat(key, value)
                    is String -> editor.putString(key, value)
                }
            }
            editor.commit()

            reloadFromLegacyPreferences()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
