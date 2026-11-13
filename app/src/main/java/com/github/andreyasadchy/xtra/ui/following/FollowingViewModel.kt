package com.github.andreyasadchy.xtra.ui.following

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import com.github.andreyasadchy.xtra.XtraApp
import com.github.andreyasadchy.xtra.model.NotificationUser
import com.github.andreyasadchy.xtra.repository.BookmarksRepository
import com.github.andreyasadchy.xtra.repository.GraphQLRepository
import com.github.andreyasadchy.xtra.repository.HelixRepository
import com.github.andreyasadchy.xtra.repository.LocalChannelFollowsRepository
import com.github.andreyasadchy.xtra.repository.NotificationsRepository
import com.github.andreyasadchy.xtra.repository.OfflineVideosRepository
import com.github.andreyasadchy.xtra.repository.datasource.FollowedChannelsDataSource
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.prefs
import com.github.andreyasadchy.xtra.util.tokenPrefs
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Page state of the Compose Following page (U2) that the per-segment view models from v0.1 do not hold: the chosen
 * segment, the "recently live" list for the empty state, and unfollowing from a card's action menu.
 *
 * The lists themselves still come from FollowedStreamsViewModel, FollowedVideosViewModel, FollowedChannelsViewModel
 * and FollowedGamesViewModel, unchanged (plan 8: view models and requests stay as they are).
 */
class FollowingViewModel(
    private val applicationContext: Context,
    private val savedState: SavedStateHandle,
    private val localChannelFollowsRepository: LocalChannelFollowsRepository,
    private val notificationsRepository: NotificationsRepository,
    private val offlineVideosRepository: OfflineVideosRepository,
    private val bookmarksRepository: BookmarksRepository,
    private val graphQLRepository: GraphQLRepository,
    private val helixRepository: HelixRepository,
) : ViewModel() {

    private val _segment = MutableStateFlow(savedState.get<String>(KEY_SEGMENT))
    /** Key of the chosen segment, null until the page picks the default one. */
    val segment = _segment.asStateFlow()

    fun selectSegment(key: String) {
        savedState[KEY_SEGMENT] = key
        _segment.value = key
    }

    /** Followed channels by last broadcast, newest first: the "recently live" list shown when nobody is live. */
    val recentlyLive = Pager(PagingConfig(pageSize = 15, prefetchDistance = 5, initialLoadSize = 15)) {
        FollowedChannelsDataSource(
            sort = "last_broadcast",
            order = "desc",
            userId = applicationContext.tokenPrefs().getString(C.USER_ID, null),
            localChannelFollowsRepository = localChannelFollowsRepository,
            offlineVideosRepository = offlineVideosRepository,
            bookmarksRepository = bookmarksRepository,
            gqlHeaders = TwitchApiHelper.getGQLHeaders(applicationContext, true),
            graphQLRepository = graphQLRepository,
            helixHeaders = TwitchApiHelper.getHelixHeaders(applicationContext),
            helixRepository = helixRepository,
            enableIntegrity = applicationContext.prefs().getBoolean(C.ENABLE_INTEGRITY, false),
            networkLibrary = applicationContext.prefs().getString(C.NETWORK_LIBRARY, C.OKHTTP),
        )
    }.flow.cachedIn(viewModelScope)

    sealed interface Event {
        /** The unfollow went through: refresh the lists. */
        data object Unfollowed : Event
        data class UnfollowFailed(val message: String?) : Event
        /** Twitch asked for a fresh integrity token; retry once it is loaded. */
        data object NeedsIntegrity : Event
    }

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 4)
    val events = _events.asSharedFlow()

    private var pendingUnfollow: String? = null

    /**
     * Same as unfollowing on the channel page (ChannelPagerViewModel.deleteFollowChannel): an account follow through
     * GraphQL when the follow button setting is "account" and there is a web token, otherwise the local follow.
     */
    fun unfollow(channelId: String?) {
        if (channelId.isNullOrBlank()) return
        val prefs = applicationContext.prefs()
        val setting = prefs.getString(C.UI_FOLLOW_BUTTON, "0")?.toIntOrNull() ?: 0
        val userId = applicationContext.tokenPrefs().getString(C.USER_ID, null)
        val gqlHeaders = TwitchApiHelper.getGQLHeaders(applicationContext, true)
        val enableIntegrity = prefs.getBoolean(C.ENABLE_INTEGRITY, false)
        val networkLibrary = prefs.getString(C.NETWORK_LIBRARY, C.OKHTTP)
        viewModelScope.launch {
            try {
                if (setting == 0 && !gqlHeaders[C.HEADER_TOKEN].isNullOrBlank() && userId != channelId) {
                    val response = graphQLRepository.loadUnfollowUser(networkLibrary, gqlHeaders, channelId)
                    if (enableIntegrity && response.errors?.any { it.message == C.FAILED_INTEGRITY_CHECK } == true) {
                        pendingUnfollow = channelId
                        _events.emit(Event.NeedsIntegrity)
                        return@launch
                    }
                    val errorMessage = response.errors?.firstOrNull()?.message
                    _events.emit(if (errorMessage.isNullOrBlank()) Event.Unfollowed else Event.UnfollowFailed(errorMessage))
                } else {
                    localChannelFollowsRepository.getById(channelId)?.let { localChannelFollowsRepository.delete(it) }
                    notificationsRepository.deleteUser(NotificationUser(channelId))
                    _events.emit(Event.Unfollowed)
                }
            } catch (e: Exception) {
                _events.emit(Event.UnfollowFailed(e.message))
            }
        }
    }

    /** Runs the unfollow that was waiting for an integrity token, if any. */
    fun retryPendingUnfollow() {
        pendingUnfollow?.let {
            pendingUnfollow = null
            unfollow(it)
        }
    }

    companion object {
        private const val KEY_SEGMENT = "segment"

        val Factory = viewModelFactory {
            initializer {
                val application = (this[APPLICATION_KEY] as XtraApp)
                val m = application.xtraModule
                FollowingViewModel(
                    application.applicationContext, createSavedStateHandle(), m.localChannelFollowsRepository, m.notificationsRepository,
                    m.offlineVideosRepository, m.bookmarksRepository, m.graphQLRepository, m.helixRepository,
                )
            }
        }
    }
}
