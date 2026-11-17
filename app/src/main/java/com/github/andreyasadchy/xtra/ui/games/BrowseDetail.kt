package com.github.andreyasadchy.xtra.ui.games

import android.os.Parcelable
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.github.andreyasadchy.xtra.ui.design.theme.Lagoon
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonMotion
import com.github.andreyasadchy.xtra.ui.main.nav.GameArgs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize

/** A game opened from the Browse grid, kept across rotation and process death. */
@Parcelize
data class GameRef(
    val id: String?,
    val slug: String?,
    val name: String?,
    val boxArt: String?,
) : Parcelable {
    val key: String get() = id ?: name.orEmpty()

    fun args() = GameArgs(gameId = id, gameSlug = slug, gameName = name, boxArt = boxArt)
}

/**
 * Which game is open over the Browse grid, and the view models of that game page. They live in a store of their
 * own so they go away when the page closes, instead of piling up in the Browse fragment.
 */
class BrowseDetailViewModel(private val handle: SavedStateHandle) : ViewModel() {
    val open: StateFlow<GameRef?> = handle.getStateFlow(KEY_OPEN, null)

    private val stores = mutableMapOf<String, ViewModelStore>()

    fun open(ref: GameRef) {
        handle[KEY_OPEN] = ref
    }

    fun close() {
        handle[KEY_OPEN] = null
    }

    fun owner(key: String): ViewModelStoreOwner {
        val store = stores.getOrPut(key) { ViewModelStore() }
        return object : ViewModelStoreOwner {
            override val viewModelStore = store
        }
    }

    /** Clears every page's view models except [keep]'s, once the closing animation is over. */
    fun release(keep: String?) {
        stores.keys.filter { it != keep }.forEach { stores.remove(it)?.clear() }
    }

    override fun onCleared() {
        stores.values.forEach { it.clear() }
        stores.clear()
    }

    companion object {
        private const val KEY_OPEN = "open"

        val Factory = viewModelFactory {
            initializer { BrowseDetailViewModel(createSavedStateHandle()) }
        }
    }
}

/** What a page needs to mark its box art as the shared element. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Immutable
class BoxArtShare(val transition: SharedTransitionScope, val visibility: AnimatedVisibilityScope)

/**
 * Marks a box art as the shared element of the poster-to-page transition (plan 5, "card to detail"): it grows from
 * the poster to the page header on the long duration and standard curve, and back. Off when motion is reduced.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedBoxArt(share: BoxArtShare?, key: String): Modifier {
    if (share == null || Lagoon.motion.reduced) return this
    return with(share.transition) {
        this@sharedBoxArt.sharedElement(
            sharedContentState = rememberSharedContentState("boxArt:$key"),
            animatedVisibilityScope = share.visibility,
            boundsTransform = { _, _ -> tween(LagoonMotion.LONG, easing = LagoonMotion.Standard) },
        )
    }
}

/**
 * The Browse grid with a game page opened over it in the same composition, so the poster's box art can fly into the
 * page header and back. The back gesture scrubs the transition with the finger (predictive back) and either
 * finishes it or springs back; the grid stays opaque underneath the whole time, so nothing flashes through.
 *
 * [backEnabled] is false while something else owns back, such as the maximized player above the page.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun BrowseDetailHost(
    open: GameRef?,
    onClose: () -> Unit,
    onSettled: (GameRef?) -> Unit,
    backEnabled: Boolean,
    grid: @Composable (BoxArtShare) -> Unit,
    detail: @Composable (GameRef, BoxArtShare) -> Unit,
) {
    val seekable = remember { SeekableTransitionState(open) }
    val scope = rememberCoroutineScope()
    val fade = LagoonMotion.MEDIUM
    LaunchedEffect(open) {
        if (seekable.currentState != open || seekable.targetState != open) seekable.animateTo(open)
        onSettled(open)
    }
    PredictiveBackHandler(enabled = open != null && backEnabled) { progress ->
        val from = open
        try {
            progress.collect { event -> seekable.seekTo(event.progress, targetState = null) }
            onClose()
        } catch (e: CancellationException) {
            scope.launch { seekable.animateTo(from) }
        }
    }
    val transition = rememberTransition(seekable, label = "browseDetail")
    SharedTransitionLayout {
        transition.AnimatedContent(
            transitionSpec = {
                if (targetState != null) {
                    // the page fades in over the grid, which stays as it is underneath
                    (fadeIn(tween(fade, easing = LagoonMotion.Standard)) togetherWith ExitTransition.KeepUntilTransitionsFinished)
                        .apply { targetContentZIndex = 1f }
                } else {
                    // back: the grid is already there underneath, the page fades away over it
                    (EnterTransition.None togetherWith fadeOut(tween(fade, easing = LagoonMotion.Exit)))
                        .apply { targetContentZIndex = -1f }
                }
            },
            contentKey = { it?.key },
        ) { ref ->
            val share = BoxArtShare(this@SharedTransitionLayout, this)
            if (ref == null) grid(share) else detail(ref, share)
        }
    }
}
