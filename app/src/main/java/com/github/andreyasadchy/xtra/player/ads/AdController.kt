package com.github.andreyasadchy.xtra.player.ads

/**
 * Ad state machine for one live stream. It has no Android or Media3 dependencies: the player
 * service feeds it events and carries out the [Action]s it returns, including the timers.
 *
 * Layers, in order: the ad sources from [AdSourceQueue] (secondary player) -> hide (video off,
 * muted). A layer that is unavailable, fails or has ads too falls through to the next one, so
 * the ad is never shown unless hiding is turned off.
 */
class AdController(
    private val queue: AdSourceQueue,
    private val hideAds: Boolean,
) {
    sealed class State {
        object Idle : State()
        data class Covering(val source: AdSource) : State()
        object Hidden : State()
    }

    sealed class Action {
        /** Video off and volume 0 on the primary player. */
        object MutePrimary : Action()
        /** Undo [MutePrimary] and apply the selected quality again. */
        object RestorePrimary : Action()
        /** Start (or replace) the secondary player with this source. */
        data class StartSecondary(val source: AdSource) : Action()
        object StopSecondary : Action()
        /** Call [onSecondaryTimeout] after this many milliseconds; replaces a pending timeout. */
        data class StartSecondaryTimeout(val delayMs: Long) : Action()
        object CancelSecondaryTimeout : Action()
        /** Call [onRestoreDelayElapsed] after this many milliseconds. */
        data class ScheduleRestore(val delayMs: Long) : Action()
        object CancelRestore : Action()
        data class Notice(val notice: AdNotice) : Action()
    }

    enum class AdNotice {
        STARTING_PROXY, STOPPING_PROXY, STARTING_VIDEO_SWAP, STOPPING_VIDEO_SWAP, WAITING_FOR_ADS
    }

    var state: State = State.Idle
        private set

    /** The primary player's latest playlist had ads. */
    var playingAds = false
        private set

    private var restorePending = false

    /** True while the primary player must not turn its video back on. */
    val isActive get() = state != State.Idle

    val isCoveringWithProxy get() = (state as? State.Covering)?.source is AdSource.StreamProxy

    val isCoveringWithVideoSwap get() = (state as? State.Covering)?.source is AdSource.VideoSwap

    /**
     * The primary player's media playlist changed.
     * @param restoreDelayMs time until the last ad segment in the playlist has been played
     */
    fun onPrimaryPlaylist(ads: Boolean, restoreDelayMs: Long): List<Action> {
        playingAds = ads
        val actions = mutableListOf<Action>()
        if (ads) {
            if (restorePending) {
                restorePending = false
                actions.add(Action.CancelRestore)
            }
            if (state == State.Idle) {
                actions.addAll(coverWithNextSource(fromIdle = true))
            }
        } else if (state != State.Idle && !restorePending) {
            restorePending = true
            actions.add(Action.ScheduleRestore(restoreDelayMs))
        }
        return actions
    }

    /** The secondary player's stream has ads too. */
    fun onSecondaryAds(): List<Action> = onSecondaryUnusable()

    /** The secondary player's stream could not be loaded. */
    fun onSecondaryFailed(): List<Action> = onSecondaryUnusable()

    private fun onSecondaryUnusable(): List<Action> {
        val current = (state as? State.Covering)?.source ?: return emptyList()
        queue.markBad(current)
        return coverWithNextSource(fromIdle = false)
    }

    /** The secondary player ran for the maximum time. */
    fun onSecondaryTimeout(): List<Action> {
        val covering = state as? State.Covering ?: return emptyList()
        return listOf(stoppingNotice(covering.source), Action.StopSecondary) + hideOrRestore()
    }

    /** The ad is over and the primary player has played past it. */
    fun onRestoreDelayElapsed(): List<Action> {
        restorePending = false
        val actions = mutableListOf<Action>()
        when (val state = state) {
            is State.Covering -> {
                actions.add(stoppingNotice(state.source))
                actions.add(Action.CancelSecondaryTimeout)
                actions.add(Action.StopSecondary)
                actions.add(Action.RestorePrimary)
            }
            State.Hidden -> actions.add(Action.RestorePrimary)
            State.Idle -> {}
        }
        this.state = State.Idle
        queue.startNextBreak()
        return actions
    }

    /** A new stream or video is loaded: drop everything without showing an ad. */
    fun reset(): List<Action> {
        val actions = mutableListOf<Action>()
        if (restorePending) {
            restorePending = false
            actions.add(Action.CancelRestore)
        }
        when (state) {
            is State.Covering -> {
                actions.add(Action.CancelSecondaryTimeout)
                actions.add(Action.StopSecondary)
                actions.add(Action.RestorePrimary)
            }
            State.Hidden -> actions.add(Action.RestorePrimary)
            State.Idle -> {}
        }
        state = State.Idle
        playingAds = false
        queue.reset()
        return actions
    }

    private fun coverWithNextSource(fromIdle: Boolean): List<Action> {
        val actions = mutableListOf<Action>()
        val next = queue.next()
        if (next != null) {
            actions.add(
                Action.Notice(
                    when (next) {
                        is AdSource.StreamProxy -> AdNotice.STARTING_PROXY
                        is AdSource.VideoSwap -> AdNotice.STARTING_VIDEO_SWAP
                    }
                )
            )
            if (fromIdle) {
                actions.add(Action.MutePrimary)
            }
            actions.add(Action.StartSecondaryTimeout(SECONDARY_TIMEOUT_MS))
            actions.add(Action.StartSecondary(next))
            state = State.Covering(next)
        } else {
            (state as? State.Covering)?.let {
                actions.add(stoppingNotice(it.source))
                actions.add(Action.CancelSecondaryTimeout)
                actions.add(Action.StopSecondary)
            }
            if (fromIdle) {
                if (hideAds) {
                    actions.add(Action.Notice(AdNotice.WAITING_FOR_ADS))
                    actions.add(Action.MutePrimary)
                    state = State.Hidden
                }
            } else {
                actions.addAll(hideOrRestore())
            }
        }
        return actions
    }

    /** No secondary player any more: keep the primary hidden while it still plays ads. */
    private fun hideOrRestore(): List<Action> {
        return if (playingAds && hideAds) {
            val wasHidden = state == State.Hidden
            state = State.Hidden
            if (wasHidden) emptyList() else listOf(Action.Notice(AdNotice.WAITING_FOR_ADS))
        } else {
            state = State.Idle
            queue.startNextBreak()
            if (restorePending) {
                restorePending = false
                listOf(Action.CancelRestore, Action.RestorePrimary)
            } else {
                listOf(Action.RestorePrimary)
            }
        }
    }

    private fun stoppingNotice(source: AdSource) = Action.Notice(
        when (source) {
            is AdSource.StreamProxy -> AdNotice.STOPPING_PROXY
            is AdSource.VideoSwap -> AdNotice.STOPPING_VIDEO_SWAP
        }
    )

    companion object {
        const val SECONDARY_TIMEOUT_MS = 180_000L
    }
}
