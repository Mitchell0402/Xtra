package com.github.andreyasadchy.xtra.ui.browse

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.github.andreyasadchy.xtra.model.ui.Clip
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.model.ui.Tag
import com.github.andreyasadchy.xtra.model.ui.Video
import com.github.andreyasadchy.xtra.ui.channel.ChannelPagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.download.DownloadDialog
import com.github.andreyasadchy.xtra.ui.game.GamePagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.games.GamesFragmentDirections
import com.github.andreyasadchy.xtra.ui.login.LoginActivity
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.ui.search.SearchPagerFragmentDirections
import com.github.andreyasadchy.xtra.ui.settings.SettingsActivity
import com.github.andreyasadchy.xtra.ui.top.TopStreamsFragmentDirections

/**
 * The calls the v0.1 browse fragments, adapters and menus made, call for call, for the Compose pages that replace
 * them (U4). Bookmarks and back differ per page and stay with the page.
 */
abstract class FragmentBrowseActions(private val fragment: Fragment) : BrowseActions {

    private val activity get() = fragment.requireActivity() as MainActivity

    override fun back() {
        fragment.findNavController().navigateUp()
    }

    override fun search() {
        fragment.findNavController().navigate(SearchPagerFragmentDirections.actionGlobalSearchPagerFragment())
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

    override fun openChannel(id: String?, login: String?, name: String?, image: String?, streamId: String?) {
        fragment.findNavController().navigate(
            ChannelPagerFragmentDirections.actionGlobalChannelPagerFragment(
                channelId = id,
                channelLogin = login,
                channelName = name,
                channelImage = image,
                streamId = streamId,
            )
        )
    }

    /** Both game destinations show the same Compose page now, so the `ui_gamepager` setting no longer picks one. */
    override fun openGame(id: String?, slug: String?, name: String?, boxArt: String?, updateLocal: Boolean) {
        fragment.findNavController().navigate(
            GamePagerFragmentDirections.actionGlobalGamePagerFragment(gameId = id, gameSlug = slug, gameName = name, boxArt = boxArt, updateLocal = updateLocal)
        )
    }

    override fun openStreamTag(tag: String) {
        fragment.findNavController().navigate(TopStreamsFragmentDirections.actionGlobalTopFragment(tags = arrayOf(tag)))
    }

    open fun openGameTag(tag: Tag) {
        fragment.findNavController().navigate(GamesFragmentDirections.actionGlobalGamesFragment(tags = arrayOf(tag)))
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
        ).show(fragment.childFragmentManager, null)
    }

    override fun share(url: String, title: String?) {
        fragment.startActivity(Intent.createChooser(Intent().apply {
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
        ).show(fragment.childFragmentManager, null)
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
        ).show(fragment.childFragmentManager, null)
    }

    override fun integrityCheckFailed() {
        (fragment.activity as? MainActivity)?.getNewIntegrityToken(CALLBACK_REFRESH, fragment.childFragmentManager)
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
