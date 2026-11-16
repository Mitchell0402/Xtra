package com.github.andreyasadchy.xtra.ui.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.github.andreyasadchy.xtra.ui.main.MainActivity

/**
 * Binds the player page to [ExoPlayerService] (v0.1 ExoPlayerFragment's onStart, onStop, close and audio only,
 * unchanged in order and effect). Everything else, including what the page does with the service's callbacks, is in
 * [PlayerFragment] and [PlayerConnection].
 */
@OptIn(UnstableApi::class)
class ExoPlayerFragment : PlayerFragment() {

    private var serviceConnection: ServiceConnection? = null

    override fun onStart() {
        super.onStart()
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                if (view != null) {
                    val backend = ExoPlaybackBackend((service as ExoPlayerService.ServiceBinder).getService())
                    this@ExoPlayerFragment.connection.attach(backend)
                    val endTime = backend.setSleepTimer(-1)
                    if (endTime > 0L) {
                        val duration = endTime - System.currentTimeMillis()
                        if (duration > 0L) {
                            (activity as? MainActivity)?.setSleepTimer(duration)
                            state.sleepTimerEnd = endTime
                        } else {
                            minimize()
                            close()
                            (activity as? MainActivity)?.closePlayer()
                        }
                    }
                    backend.setStopServiceTimer(false)
                    backend.player?.let { player ->
                        setPipActions(player.playbackState != Player.STATE_ENDED && player.playbackState != Player.STATE_IDLE && player.playWhenReady)
                    }
                    if (backend.loaded) applyAudioOnlyStart()
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                this@ExoPlayerFragment.connection.detach()
            }
        }
        val intent = Intent(requireContext(), ExoPlayerService::class.java).apply {
            action = ExoPlayerService.INTENT_START
        }
        requireContext().startService(intent)
        requireContext().bindService(intent, connection, Context.BIND_AUTO_CREATE)
        serviceConnection = connection
    }

    private fun unbind() {
        connection.detach()
        serviceConnection?.let { requireContext().unbindService(it) }
        serviceConnection = null
    }

    override fun startAudioOnly() {
        backend?.let {
            it.startAudioOnly()
            it.setSleepTimer((activity as? MainActivity)?.getSleepTimerTimeLeft() ?: 0)
            it.setStopServiceTimer(true)
        }
        unbind()
    }

    override fun close(deleteStates: Boolean) {
        val backend = backend
        backend?.pause()
        backend?.stop()
        // isAdded: a deep link can close a player fragment that was restored but not attached yet
        if (deleteStates && isAdded) {
            viewModel.deletePlaybackStates()
        }
        unbind()
        backend?.stopSelf()
    }

    override fun onStop() {
        super.onStop()
        backend?.let {
            // v0.1: before Android 11 the flag is unreliable in onStop, the page's own picture-in-picture state is used
            val isInPIPMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                requireActivity().isInPictureInPictureMode
            } else {
                state.inPictureInPicture && isMaximized
            }
            it.stop(isInPIPMode)
            it.setSleepTimer((activity as? MainActivity)?.getSleepTimerTimeLeft() ?: 0)
            it.setStopServiceTimer(true)
        }
        unbind()
    }

    override fun onNetworkRestored() {
        if (isResumed) {
            if (backend?.type == BasePlaybackService.STREAM) {
                backend?.restartPlayer()
            } else {
                backend?.player?.prepare()
            }
        }
    }

    override fun onNetworkLost() {
        if (backend?.type != BasePlaybackService.STREAM && isResumed) {
            backend?.stop()
        }
    }
}
