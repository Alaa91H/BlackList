package com.blacklist.app.service

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.blacklist.app.R
import com.blacklist.app.di.ServiceLocator
import com.blacklist.app.domain.engine.TemporaryFirewall
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * User-triggered Quick Settings toggle for a one-hour local block-all override.
 * It never starts background work by itself and can always be switched off directly.
 */
class TemporaryBlockTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()
        scope.launch {
            val repository = ServiceLocator.provideRepository(applicationContext)
            if (repository.isTemporaryBlockAllActive()) {
                repository.cancelTemporaryBlockAll()
            } else {
                repository.enableTemporaryBlockAll(TemporaryFirewall.HOUR_1)
            }
            updateTile(repository.isTemporaryBlockAllActive())
        }
    }

    private fun refreshTile() {
        scope.launch {
            val active = ServiceLocator.provideRepository(applicationContext).isTemporaryBlockAllActive()
            updateTile(active)
        }
    }

    private suspend fun updateTile(active: Boolean) = withContext(Dispatchers.Main.immediate) {
        qsTile?.apply {
            state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = getString(if (active) R.string.tile_temporary_block_active else R.string.tile_temporary_block)
            updateTile()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
