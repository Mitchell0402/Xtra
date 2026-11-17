package com.github.andreyasadchy.xtra.ui.browse

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.github.andreyasadchy.xtra.model.ui.Clip
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.model.ui.Tag
import com.github.andreyasadchy.xtra.model.ui.Video
import com.github.andreyasadchy.xtra.ui.download.DownloadDialog
import com.github.andreyasadchy.xtra.ui.login.LoginActivity
import com.github.andreyasadchy.xtra.ui.main.nav.ComposePage
import com.github.andreyasadchy.xtra.ui.settings.SettingsActivity

/**
 * The calls the v0.1 browse fragments, adapters and menus made, call for call, for the Compose pages that replace
 * them (U4; from U8 on through the page's navigation instead of the fragment's). Bookmarks and back differ per page
 * and stay with the page.
 */
abstract class PageBrowseActions(private val page: ComposePage) : BrowseActions {

    private val activity get() = page.activity
    private val nav get() = page.nav

    override fun back() {
        nav.back()
    }

    override fun search() {
        nav.openSearch()
    }

    fun settings() {
        activity.settingsResultLauncher?.launch(Intent(activity, SettingsActivity::class.java))
    }

    fun logIn() {
        activity.loginResultLauncher?.launch(Intent(activity, LoginActivity::class.java))
    }

    fun logOut() {
        activity.logoutResultLauncher?.launch(Intent(activity, LoginActivity::class.java))
    }

    override fun openStream(stream: Stream) {
        activity.startStream(stream)
    }

    override fun listen(stream: Stream) {
        activity.startStream(stream, audioOnly = true)
    }

    override fun openChannel(id: String?, login: String?, name: String?, image: String?, streamId: String?) {
        nav.openChannel(id = id, login = login, name = name, image = image, streamId = streamId)
    }

    /** Both game destinations show the same Compose page now, so the `ui_gamepager` setting no longer picks one. */
    override fun openGame(id: String?, slug: String?, name: String?, boxArt: String?, updateLocal: Boolean) {
        nav.openGame(id = id, slug = slug, name = name, boxArt = boxArt, updateLocal = updateLocal)
    }

    override fun openStreamTag(tag: String) {
        nav.openTopStreams(tags = listOf(tag))
    }

    open fun openGameTag(tag: Tag) {
        nav.openGames(listOf(tag))
    }

    /** Same as "Download" on a channel page that is live: the download dialog loads the stream's qualities itself. */
    override fun record(stream: Stream) {
        DownloadDialog.newStreamInstance(
            id = stream.id,
            channelId = stream.channelId,
            channelLogin = stream.channelLogin,
            channelName = stream.channelName,
            channelImage = stream.channelImage,
            gameId = stream.gameId,
            gameSlug = stream.gameSlug,
            gameName = stream.gameName,
            title = stream.title,
            thumbnail = stream.thumbnail,
            createdAt = stream.createdAt,
        ).show(page.childFragmentManager, null)
    }

    override fun share(url: String, title: String?) {
        page.startActivity(Intent.createChooser(Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, url)
            title?.let { putExtra(Intent.EXTRA_TITLE, it) }
            type = "text/plain"
        }, null))
    }

    /** VideosAdapter's click: resume where the video was left, from the start when it was watched to the end. */
    override fun openVideo(video: Video, position: Long?) {
        val duration = video.durationSeconds
        val startFromBeginning = position != null && duration != null && duration > 0 && position >= duration * 1000L &&
                video.thumbnail.let { it != null && !it.startsWith("https://vod-secure.twitch.tv/_404/404_processing") }
        activity.startVideo(video, if (startFromBeginning) 0 else position, startFromBeginning)
    }

    override fun downloadVideo(video: Video) {
        DownloadDialog.newVideoInstance(
            id = video.id,
            channelId = video.channelId,
            channelLogin = video.channelLogin,
            channelName = video.channelName,
            channelImage = video.channelImage,
            gameId = video.gameId,
            gameSlug = video.gameSlug,
            gameName = video.gameName,
            title = video.title,
            thumbnail = video.thumbnail,
            createdAt = video.createdAt,
            durationSeconds = video.durationSeconds,
            type = video.type,
            animatedPreviewUrl = video.animatedPreviewURL,
        ).show(page.childFragmentManager, null)
    }

    override fun openClip(clip: Clip) {
        activity.startClip(clip)
    }

    override fun downloadClip(clip: Clip) {
        DownloadDialog.newClipInstance(
            id = clip.id,
            channelId = clip.channelId,
            channelLogin = clip.channelLogin,
            channelName = clip.channelName,
            channelImage = clip.channelImage,
            gameId = clip.gameId,
            gameSlug = clip.gameSlug,
            gameName = clip.gameName,
            title = clip.title,
            thumbnail = clip.thumbnail,
            createdAt = clip.createdAt,
            durationSeconds = clip.durationSeconds,
            videoId = clip.videoId,
            videoOffsetSeconds = clip.videoOffsetSeconds,
            videoCreatedAt = clip.videoCreatedAt,
        ).show(page.childFragmentManager, null)
    }

    override fun integrityCheckFailed() {
        activity.getNewIntegrityToken(CALLBACK_REFRESH, page.childFragmentManager)
    }

    companion object {
        const val CALLBACK_REFRESH = "refresh"
    }
}

/** Whether the device has a working connection now (MainActivity fills its own flag only once its callback fires). */
fun Context.hasValidatedNetwork(): Boolean {
    val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
    return capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
