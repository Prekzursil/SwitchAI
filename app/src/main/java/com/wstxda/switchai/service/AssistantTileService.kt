package com.wstxda.switchai.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.graphics.drawable.IconCompat
import androidx.core.service.quicksettings.PendingIntentActivityWrapper
import androidx.core.service.quicksettings.TileServiceCompat
import com.wstxda.switchai.R
import com.wstxda.switchai.logic.DigitalAssistantChecker
import com.wstxda.switchai.logic.PreferenceHelper
import com.wstxda.switchai.ui.utils.AssistantResourcesManager
import com.wstxda.switchai.utils.Constants

class AssistantTileService : TileService() {

    private val assistantResourcesManager by lazy { AssistantResourcesManager(this) }
    private val preferenceHelper by lazy { PreferenceHelper(this) }

    override fun onStartListening() {
        super.onStartListening()
        refreshTileContent()
    }

    override fun onTileAdded() {
        super.onTileAdded()
        refreshTileContent()
    }

    override fun onClick() {
        val intent = Intent(this, DigitalAssistantService::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        TileServiceCompat.startActivityAndCollapse(
            this,
            PendingIntentActivityWrapper(
                this, 0, intent, PendingIntent.FLAG_IMMUTABLE, false
            )
        )
    }

    private fun refreshTileContent() {
        val tile = qsTile ?: return

        if (!DigitalAssistantChecker.isSetupDone(this)) {
            tile.state = Tile.STATE_UNAVAILABLE
            tile.updateTile()
            return
        }

        val isSelectorEnabled =
            preferenceHelper.getBoolean(Constants.SELECTOR_PREF_KEY, false)

        val assistantValue =
            preferenceHelper.getString(Constants.DIGITAL_ASSISTANT_SELECT_PREF_KEY, null)

        tile.state = if (isSelectorEnabled || assistantValue != null) {
            Tile.STATE_ACTIVE
        } else {
            Tile.STATE_INACTIVE
        }

        if (isSelectorEnabled) {
            applyTileResources(
                icon = R.drawable.ic_assistant,
                title = getString(R.string.assistant_label),
                subtitle = getString(R.string.assistant_label_select),
            )
        } else {
            val iconRes = assistantResourcesManager.getAssistantIcon(assistantValue)
            val name = assistantResourcesManager.getAssistantName(assistantValue)

            applyTileResources(
                icon = iconRes,
                title = name,
                subtitle = getString(R.string.assistant_label_open),
            )
        }

        tile.updateTile()
    }

    private fun applyTileResources(icon: Int, title: String, subtitle: String) {
        val tile = qsTile ?: return
        tile.icon = IconCompat.createWithResource(this, icon).toIcon(this)
        tile.label = title
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = subtitle
        }
    }
}