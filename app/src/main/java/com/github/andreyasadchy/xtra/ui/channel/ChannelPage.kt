package com.github.andreyasadchy.xtra.ui.channel

import com.github.andreyasadchy.xtra.ui.main.nav.ChannelArgs
import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.model.ui.Video
import com.github.andreyasadchy.xtra.ui.browse.BrowseFilter
import com.github.andreyasadchy.xtra.ui.browse.FilterKind
import com.github.andreyasadchy.xtra.ui.browse.FilterSaves
import com.github.andreyasadchy.xtra.ui.browse.PageBrowseActions
import com.github.andreyasadchy.xtra.ui.channel.about.ChannelAboutViewModel
import com.github.andreyasadchy.xtra.ui.channel.clips.ChannelClipsViewModel
import com.github.andreyasadchy.xtra.ui.channel.videos.ChannelVideosViewModel
import com.github.andreyasadchy.xtra.ui.main.LiveNotificationWorker
import com.github.andreyasadchy.xtra.ui.main.nav.ComposePage
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.prefs
import com.github.andreyasadchy.xtra.util.tokenPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * What v0.1's ChannelPagerFragment and its list fragments did on start, on sort changes, on follow, on notifications
 * and on the integrity check, call for call, for the Compose channel page (U5). The view models live in the page's
 * navigation entry.
 */
class ChannelPage(
    private val page: ComposePage,
    val args: ChannelArgs,
    val pager: ChannelPagerViewModel,
    val videos: ChannelVideosViewModel,
    val clips: ChannelClipsViewModel,
    val about: ChannelAboutViewModel,
) : PageBrowseActions(page), ChannelActions {

    private val context get() = page.requireContext()
    private val prefs get() = context.prefs()
    private val followSetting get() = prefs.getString(C.UI_FOLLOW_BUTTON, "0")?.toIntOrNull() ?: 0
    private val networkLibrary get() = prefs.getString(C.NETWORK_LIBRARY, C.OKHTTP)
    private val enableIntegrity get() = prefs.getBoolean(C.ENABLE_INTEGRITY, false)
    private val userId get() = context.tokenPrefs().getString(C.USER_ID, null)

    /** This channel has remembered sort choices (v0.1 showed "delete saved sort" then). */
    val remembered = MutableStateFlow(false)
    var refreshLists: () -> Unit = {}

    /** The id, login and name as known now: the link's, completed by the loaded channel (v0.1 wrote them into the arguments). */
    val channelId get() = args.channelId ?: pager.user.value?.id ?: pager.stream.value?.channelId
    val channelLogin get() = args.channelLogin ?: pager.user.value?.login ?: pager.stream.value?.channelLogin
    val channelName get() = pager.stream.value?.channelName ?: pager.user.value?.name ?: args.channelName

    /** v0.1's initialize() of the pager and of the videos, clips and about fragments. */
    fun start() {
        page.lifecycleScope.launch {
            if (videos.filter.value == null) {
                val saved = args.channelId?.let { videos.getChannelSort(it) } ?: videos.getChannelSort(ChannelFilterLogic.DEFAULT_ID)
                val filter = ChannelFilterLogic.initialVideos(saved)
                videos.setFilter(sort = filter.sort, type = filter.type)
            }
            if (clips.filter.value == null) {
                val saved = args.channelId?.let { clips.getChannelSort(it) } ?: clips.getChannelSort(ChannelFilterLogic.DEFAULT_ID)
                clips.setFilter(period = ChannelFilterLogic.initialClips(saved).period)
            }
            refreshRemembered()
        }
        loadChannel()
        pager.isFollowingChannel(
            userId, args.channelId, args.channelLogin, followSetting, networkLibrary,
            TwitchApiHelper.getGQLHeaders(context, true), TwitchApiHelper.getHelixHeaders(context),
        )
    }

    /** The header (stream and user) and the about data, which the header's description comes from. */
    fun loadChannel() {
        pager.loadStream(networkLibrary, TwitchApiHelper.getGQLHeaders(context), TwitchApiHelper.getHelixHeaders(context), enableIntegrity)
        about.loadAbout(args.channelId, args.channelLogin, networkLibrary, TwitchApiHelper.getGQLHeaders(context), enableIntegrity)
    }

    /** v0.1 updated the local copy of a followed channel once its user data arrived (link with `updateLocal`). */
    fun onUserLoaded() {
        val user = pager.user.value ?: return
        if (args.updateLocal) pager.updateLocalUser(networkLibrary, context.filesDir.path, user)
    }

    private suspend fun refreshRemembered() {
        remembered.value = args.channelId?.let { videos.getChannelSort(it) } != null
    }

    fun onIntegrityTokenLoaded(callback: String?) {
        when (callback) {
            CALLBACK_REFRESH -> {
                loadChannel()
                pager.isFollowingChannel(
                    userId, args.channelId, args.channelLogin, followSetting, networkLibrary,
                    TwitchApiHelper.getGQLHeaders(context, true), TwitchApiHelper.getHelixHeaders(context),
                )
                refreshLists()
            }
            CALLBACK_FOLLOW -> setFollowing(true)
            CALLBACK_UNFOLLOW -> setFollowing(false)
            CALLBACK_ENABLE_NOTIFICATIONS -> args.channelId?.let {
                pager.enableNotifications(
                    userId, it, followSetting, prefs.getBoolean(C.LIVE_NOTIFICATIONS_ENABLED, false), networkLibrary,
                    TwitchApiHelper.getGQLHeaders(context, true), enableIntegrity,
                )
            }
            CALLBACK_DISABLE_NOTIFICATIONS -> args.channelId?.let {
                pager.disableNotifications(userId, it, followSetting, networkLibrary, TwitchApiHelper.getGQLHeaders(context, true), enableIntegrity)
            }
        }
    }

    override fun setFollowing(follow: Boolean) {
        if (follow) {
            pager.saveFollowChannel(
                userId, args.channelId, args.channelLogin, args.channelName, followSetting,
                prefs.getBoolean(C.LIVE_NOTIFICATIONS_ENABLED, false),
                !prefs.getBoolean(C.UI_ACTIVATE_NOTIFICATIONS_WHEN_FOLLOWING, true),
                networkLibrary, TwitchApiHelper.getGQLHeaders(context, true), enableIntegrity,
            )
        } else {
            pager.deleteFollowChannel(userId, args.channelId, followSetting, networkLibrary, TwitchApiHelper.getGQLHeaders(context, true), enableIntegrity)
        }
    }

    /** The bell (v0.1's "toggle notifications" menu item), with the permission request and the background check. */
    override fun toggleNotifications() {
        val enabled = pager.notificationsEnabled.value ?: return
        if (enabled) {
            pager.disableNotifications(userId, args.channelId, followSetting, networkLibrary, TwitchApiHelper.getGQLHeaders(context, true), enableIntegrity)
        } else {
            val notificationsEnabled = prefs.getBoolean(C.LIVE_NOTIFICATIONS_ENABLED, false)
            pager.enableNotifications(userId, args.channelId, followSetting, notificationsEnabled, networkLibrary, TwitchApiHelper.getGQLHeaders(context, true), enableIntegrity)
            if (!args.channelId.isNullOrBlank() && !notificationsEnabled) {
                val activity = page.activity
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ActivityCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
                }
                pager.updateNotifications(networkLibrary, TwitchApiHelper.getGQLHeaders(context, true), TwitchApiHelper.getHelixHeaders(context))
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    "live_notifications",
                    ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
                    PeriodicWorkRequestBuilder<LiveNotificationWorker>(15, TimeUnit.MINUTES)
                        .setInitialDelay(1, TimeUnit.MINUTES)
                        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                        .build()
                )
                prefs.edit { putBoolean(C.LIVE_NOTIFICATIONS_ENABLED, true) }
            }
        }
    }

    /** v0.1's "watch live" button: the loaded stream, or one built from the link before it loads. */
    override fun watchLive() {
        page.activity.startStream(
            pager.stream.value?.takeIf { it.viewerCount != null } ?: Stream(
                id = args.streamId,
                channelId = args.channelId,
                channelLogin = args.channelLogin,
                channelName = args.channelName,
                channelImageURL = args.channelImage,
            )
        )
    }

    override fun shareChannel() {
        share("https://twitch.tv/$channelLogin", channelName)
    }

    override fun openTeam(name: String) {
        page.nav.openTeam(name)
    }

    /** About links and panel images open in the browser (v0.1: the same intent and the same message without one). */
    override fun openUrl(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).apply { addCategory(Intent.CATEGORY_BROWSABLE) })
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, R.string.no_browser_found, Toast.LENGTH_LONG).show()
        }
    }

    override fun openSettings() = settings()
    override fun openLogIn() = logIn()
    override fun openLogOut() = logOut()

    override fun toggleBookmark(video: Video) {
        videos.saveBookmark(context.filesDir.path, video, networkLibrary, TwitchApiHelper.getGQLHeaders(context), TwitchApiHelper.getHelixHeaders(context))
    }

    fun videosFilter() = BrowseFilter(sort = videos.sort, type = videos.type)
    fun clipsFilter() = BrowseFilter(period = clips.period)

    /** v0.1's onChange of the two lists: reload when something changed, then save what was asked for, in order. */
    override fun applyFilter(kind: FilterKind, filter: BrowseFilter, saves: FilterSaves) {
        val id = args.channelId
        page.lifecycleScope.launch {
            if (saves.forget && id != null) {
                videos.getChannelSort(id)?.let { videos.deleteChannelSort(it) }
            }
            when (kind) {
                FilterKind.Videos -> {
                    if (ChannelFilterLogic.videosChanged(videosFilter(), filter)) {
                        videos.setFilter(ChannelFilterLogic.effectiveVideoSort(filter), ChannelFilterLogic.effectiveType(filter))
                    }
                    if (saves.remember && id != null) videos.saveChannelSort(ChannelFilterLogic.saveVideos(videos.getChannelSort(id), id, filter))
                    if (saves.saveDefault) {
                        videos.saveChannelSort(ChannelFilterLogic.saveVideos(videos.getChannelSort(ChannelFilterLogic.DEFAULT_ID), ChannelFilterLogic.DEFAULT_ID, filter))
                    }
                }
                FilterKind.Clips -> {
                    if (ChannelFilterLogic.clipsChanged(clipsFilter(), filter)) {
                        clips.setFilter(ChannelFilterLogic.effectivePeriod(filter))
                    }
                    if (saves.remember && id != null) clips.saveChannelSort(ChannelFilterLogic.saveClips(clips.getChannelSort(id), id, filter))
                    if (saves.saveDefault) {
                        clips.saveChannelSort(ChannelFilterLogic.saveClips(clips.getChannelSort(ChannelFilterLogic.DEFAULT_ID), ChannelFilterLogic.DEFAULT_ID, filter))
                    }
                }
                else -> Unit
            }
            refreshRemembered()
        }
    }

    companion object {
        const val CALLBACK_FOLLOW = "follow"
        const val CALLBACK_UNFOLLOW = "unfollow"
        const val CALLBACK_ENABLE_NOTIFICATIONS = "enableNotifications"
        const val CALLBACK_DISABLE_NOTIFICATIONS = "disableNotifications"
    }
}
