package com.github.andreyasadchy.xtra.player.ads

/**
 * Ad state machine for one live stream. It has no Android or Media3 dependencies: the player
 * service feeds it events and carries out the [Action]s it returns, including the timers.
 *
 * Layers, in order: the ad sources from [AdSourceQueue] (secondary player) -> hide (video off,
 * muted). A layer that is unavailable, fails or has ads too falls through to the next one, so
 * the ad is never shown unless hiding is turned off.
 *
 * Ads show up in the media playlist a few seconds before the player reaches them. The secondary
 * player starts right away, silent and off screen, and takes over the screen only when the
 * primary player reaches the first ad segment (the "boundary"), so no content is cut and the
 * switch is usually seamless. The primary player keeps decoding video while it is covered, so
 * switching back after the ad is instant too.
 */
class AdController(
    private val queue: AdSourceQueue,
    private val hideAds: Boolean,
) {
    sealed class State {
        object Idle : State() {
            override fun toString() = "Idle"
        }

        /** A secondary player plays [source]; [shown] once the primary reached the ad. */
        data class Covering(val source: AdSource, val shown: Boolean) : State()

        /** No usable source: the primary's video is turned off and muted once [shown]. */
        data class Hidden(val shown: Boolean) : State()
    }

    sealed class Action {
        /** Start (or replace) the secondary player; on screen with sound when [show], else silent and off screen. */
        data class StartSecondary(val source: AdSource, val show: Boolean) : Action()
        /** Put the secondary player on screen and turn its sound on. */
        object ShowSecondary : Action()
        object StopSecondary : Action()
        /** Volume 0 on the primary player, its video keeps playing off screen. */
        object MutePrimary : Action()
        /** Video off and volume 0 on the primary player. */
        object HidePrimary : Action()
        /** Undo [MutePrimary] or, when [videoHidden], [HidePrimary]. */
        data class RestorePrimary(val videoHidden: Boolean) : Action()
        /** Call [onAdBoundary] after this many milliseconds; replaces a pending one. */
        data class ScheduleBoundary(val delayMs: Long) : Action()
        object CancelBoundary : Action()
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

    /** True while the primary player must not turn its video back on or change quality. */
    val isActive get() = state != State.Idle

    /** True while the primary player's video must stay off. */
    val isHidingPrimaryVideo get() = (state as? State.Hidden)?.shown == true

    /** True while the secondary player is on screen and the primary is muted. */
    val isCovered get() = (state as? State.Covering)?.shown == true

    val isCoveringWithProxy get() = (state as? State.Covering)?.source is AdSource.StreamProxy

    val isCoveringWithVideoSwap get() = (state as? State.Covering)?.source is AdSource.VideoSwap

    /**
     * The primary player's media playlist changed.
     * @param adStartDelayMs time until the primary player reaches the first ad segment
     * @param restoreDelayMs time until the last ad segment in the playlist has been played
     */
    fun onPrimaryPlaylist(ads: Boolean, adStartDelayMs: Long, restoreDelayMs: Long): List<Action> {
        playingAds = ads
        val actions = mutableListOf<Action>()
        if (ads) {
            if (restorePending) {
                restorePending = false
                actions.add(Action.CancelRestore)
            }
            if (state == State.Idle) {
                val next = queue.next()
                if (next != null) {
                    actions.add(startingNotice(next))
                    actions.add(Action.StartSecondary(next, show = false))
                    actions.add(Action.ScheduleBoundary(adStartDelayMs))
                    state = State.Covering(next, shown = false)
                } else if (hideAds) {
                    actions.add(Action.ScheduleBoundary(adStartDelayMs))
                    state = State.Hidden(shown = false)
                }
            }
        } else if (state != State.Idle && !restorePending) {
            restorePending = true
            actions.add(Action.ScheduleRestore(restoreDelayMs))
        }
        return actions
    }

    /** The primary player reached the first ad segment. */
    fun onAdBoundary(): List<Action> {
        return when (val state = state) {
            is State.Covering -> if (!state.shown) {
                this.state = state.copy(shown = true)
                listOf(Action.MutePrimary, Action.ShowSecondary)
            } else emptyList()
            is State.Hidden -> if (!state.shown) {
                this.state = State.Hidden(shown = true)
                listOf(Action.Notice(AdNotice.WAITING_FOR_ADS), Action.HidePrimary)
            } else emptyList()
            State.Idle -> emptyList()
        }
    }

    /** The secondary player's stream has ads too. */
    fun onSecondaryAds(): List<Action> = onSecondaryUnusable()

    /** The secondary player's stream could not be loaded or failed. */
    fun onSecondaryFailed(): List<Action> = onSecondaryUnusable()

    private fun onSecondaryUnusable(): List<Action> {
        val covering = state as? State.Covering ?: return emptyList()
        queue.markBad(covering.source)
        val actions = mutableListOf<Action>()
        val next = queue.next()
        if (next != null) {
            actions.add(startingNotice(next))
            actions.add(Action.StartSecondary(next, show = covering.shown))
            state = State.Covering(next, covering.shown)
            return actions
        }
        actions.add(stoppingNotice(covering.source))
        actions.add(Action.StopSecondary)
        if (playingAds && hideAds) {
            state = State.Hidden(covering.shown)
            if (covering.shown) {
                actions.add(Action.Notice(AdNotice.WAITING_FOR_ADS))
                actions.add(Action.HidePrimary)
            }
        } else {
            state = State.Idle
            queue.startNextBreak()
            if (restorePending) {
                restorePending = false
                actions.add(Action.CancelRestore)
            }
            if (covering.shown) {
                actions.add(Action.RestorePrimary(videoHidden = false))
            } else {
                actions.add(Action.CancelBoundary)
            }
        }
        return actions
    }

    /** The ad is over and the primary player has played past it. */
    fun onRestoreDelayElapsed(): List<Action> {
        restorePending = false
        val actions = mutableListOf<Action>()
        when (val state = state) {
            is State.Covering -> {
                actions.add(stoppingNotice(state.source))
                actions.add(Action.StopSecondary)
                if (state.shown) {
                    actions.add(Action.RestorePrimary(videoHidden = false))
                } else {
                    actions.add(Action.CancelBoundary)
                }
            }
            is State.Hidden -> {
                if (state.shown) {
                    actions.add(Action.RestorePrimary(videoHidden = true))
                } else {
                    actions.add(Action.CancelBoundary)
                }
            }
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
        when (val state = state) {
            is State.Covering -> {
                actions.add(Action.CancelBoundary)
                actions.add(Action.StopSecondary)
                actions.add(Action.RestorePrimary(videoHidden = false))
            }
            is State.Hidden -> {
                actions.add(Action.CancelBoundary)
                actions.add(Action.RestorePrimary(videoHidden = state.shown))
            }
            State.Idle -> {}
        }
        state = State.Idle
        playingAds = false
        queue.reset()
        return actions
    }

    private fun startingNotice(source: AdSource) = Action.Notice(
        when (source) {
            is AdSource.StreamProxy -> AdNotice.STARTING_PROXY
            is AdSource.VideoSwap -> AdNotice.STARTING_VIDEO_SWAP
        }
    )

    private fun stoppingNotice(source: AdSource) = Action.Notice(
        when (source) {
            is AdSource.StreamProxy -> AdNotice.STOPPING_PROXY
            is AdSource.VideoSwap -> AdNotice.STOPPING_VIDEO_SWAP
        }
    )
}
