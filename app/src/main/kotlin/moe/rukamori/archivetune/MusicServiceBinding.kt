package moe.rukamori.archivetune

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import moe.rukamori.archivetune.constants.StopMusicOnTaskClearKey
import moe.rukamori.archivetune.playback.MusicService
import moe.rukamori.archivetune.playback.MusicService.MusicBinder
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.get
import moe.rukamori.archivetune.utils.reportException

class MusicServiceBinding(
    private val activity: MainActivity,
) {
    var playerConnection by mutableStateOf<PlayerConnection?>(null)
        internal set

    var isMusicServiceBound: Boolean = false
        private set

    var intentRouter: MainIntentRouter? = null

    private val serviceConnection =
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                isMusicServiceBound = true
                if (service is MusicBinder) {
                    val conn = PlayerConnection(activity, service, activity.database, activity.lifecycleScope)
                    playerConnection = conn
                    activity.playerConnectionHolder.connection.value = conn

                    intentRouter?.onServiceConnected(conn)
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                isMusicServiceBound = false
                intentRouter?.onServiceDisconnected()
                playerConnection?.dispose()
                playerConnection = null
                activity.playerConnectionHolder.connection.value = null
            }
        }

    fun onStart() {
        isMusicServiceBound =
            activity.bindService(
                Intent(activity, MusicService::class.java),
                serviceConnection,
                Context.BIND_AUTO_CREATE,
            )
        intentRouter?.onStart()
    }

    fun safeUnbindMusicService() {
        if (!isMusicServiceBound) return
        try {
            activity.unbindService(serviceConnection)
        } catch (e: IllegalArgumentException) {
        } catch (e: Exception) {
            reportException(e)
        } finally {
            isMusicServiceBound = false
        }
    }

    fun onStop() {
        safeUnbindMusicService()
    }

    fun onDestroy() {
        val shouldStopOnTaskClear =
            if (!activity.isFinishing) {
                false
            } else {
                activity.dataStore.get(StopMusicOnTaskClearKey, false)
            }

        if (shouldStopOnTaskClear) {
            playerConnection?.service?.stopAndClearPlayback(clearPersistentState = true)
            safeUnbindMusicService()
            activity.stopService(Intent(activity, MusicService::class.java))
            playerConnection = null
        }
    }
}
