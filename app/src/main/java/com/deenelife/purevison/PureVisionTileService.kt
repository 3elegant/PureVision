package com.deenelife.purevison

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.deenelife.purevison.data.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PureVisionTileService : TileService() {

    private val preferences by lazy { AppPreferences.getInstance(this) }
    private val scope = CoroutineScope(Dispatchers.Main)

    companion object {
        fun requestTileUpdate(context: Context) {
            try {
                requestListeningState(
                    context,
                    ComponentName(context, PureVisionTileService::class.java)
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()

        val needsOverlay = preferences.isStaticTextEnabled() || preferences.isZikrModeEnabled()
        if (needsOverlay && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, getString(R.string.app_permission_denied_toast), Toast.LENGTH_SHORT).show()
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pendingIntent = android.app.PendingIntent.getActivity(
                    this, 0, intent,
                    android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
                )
                startActivityAndCollapse(pendingIntent)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
            return
        }

        val isCurrentlyActive = qsTile?.state == Tile.STATE_ACTIVE

        scope.launch {
            if (isCurrentlyActive) {
                // Stop service
                preferences.setServiceEnabled(false)
                val stopIntent = Intent(this@PureVisionTileService, OverlayService::class.java).apply {
                    action = OverlayService.ACTION_STOP_SERVICE
                }
                stopService(stopIntent)
                qsTile?.state = Tile.STATE_INACTIVE
            } else {
                // Start service
                preferences.setServiceEnabled(true)
                val startIntent = Intent(this@PureVisionTileService, OverlayService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(startIntent)
                } else {
                    startService(startIntent)
                }
                qsTile?.state = Tile.STATE_ACTIVE
            }
            qsTile?.updateTile()
        }
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val isEnabled = preferences.isServiceEnabled()
        val needsOverlay = preferences.isStaticTextEnabled() || preferences.isZikrModeEnabled()
        val hasRequiredPermission = if (needsOverlay && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }

        tile.state = if (isEnabled && hasRequiredPermission) {
            Tile.STATE_ACTIVE
        } else {
            Tile.STATE_INACTIVE
        }
        tile.label = getString(R.string.tile_label)
        tile.updateTile()
    }
}
