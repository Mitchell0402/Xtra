package com.github.andreyasadchy.xtra.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.chat.ChatMessage
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.ui.chat.ChatViewModel.Companion.ChatViewModelFactory
import com.github.andreyasadchy.xtra.ui.chat.ImageClickedViewModel.Companion.ImageClickedViewModelFactory
import com.github.andreyasadchy.xtra.ui.chat.MessageClickedViewModel.Companion.MessageClickedViewModelFactory
import com.github.andreyasadchy.xtra.ui.common.BaseNetworkFragment
import com.github.andreyasadchy.xtra.ui.common.IntegrityDialog
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonTheme
import com.github.andreyasadchy.xtra.ui.main.MainActivity
import com.github.andreyasadchy.xtra.ui.main.shell.ShellHost
import com.github.andreyasadchy.xtra.ui.player.PlayerFragment
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper
import com.github.andreyasadchy.xtra.util.prefs
import com.github.andreyasadchy.xtra.util.tokenPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Instant

/**
 * The chat (U7): a Compose page over the unchanged [ChatViewModel]. The fragment keeps v0.1's arguments and the
 * methods the player and the channel page call; it turns the view model's message events into prepared lines
 * ([ChatLineBuilder] on a background thread, in order) and handles the actions of the page.
 */
class ChatFragment : BaseNetworkFragment(), ChatActions, ChatSheetActions, IntegrityDialog.Listener {

    private val viewModel: ChatViewModel by viewModels { ChatViewModelFactory }
    val state = ChatState()
    override val sheetData = ChatSheetData()

    private var builder: ChatLineBuilder? = null
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val buildDispatcher = Dispatchers.Default.limitedParallelism(1)
    private val events = Channel<ChatEvent>(Channel.UNLIMITED)
    private val recentArrivals = ArrayDeque<Long>()
    private var recentEmoteNames = emptyList<String>()
    private var cardRequests = 0
    private var userJob: Job? = null
    private var emoteJob: Job? = null
    private var layoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null

    private sealed interface ChatEvent {
        class New(val message: ChatMessage) : ChatEvent
        class AddFront(val messages: List<ChatMessage>) : ChatEvent
        class Remove(val count: Int) : ChatEvent
        class Reset(val messages: List<ChatMessage>) : ChatEvent
        data object ReloadAll : ChatEvent
        class UpdateUser(val userId: String) : ChatEvent
    }

    private val backPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            toggleEmoteMenu(false)
        }
    }

    private val args get() = requireArguments()
    private val isLive get() = args.getBoolean(KEY_IS_LIVE)
    private val channelId get() = args.getString(KEY_CHANNEL_ID)
    private val channelLogin get() = args.getString(KEY_CHANNEL_LOGIN)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent { LagoonTheme { ChatScreen(state, this@ChatFragment) } }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val context = requireContext()
        val prefs = context.prefs()
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.integrity.collect {
                    (requireActivity() as? MainActivity)?.getNewIntegrityToken(it, childFragmentManager)
                }
            }
        }
        state.disabled = prefs.getBoolean(C.CHAT_DISABLE, false)
        state.nameDisplay = prefs.getString(C.UI_NAME_DISPLAY, "0")
        state.isLive = isLive
        if (!state.disabled) {
            val accountLogin = context.tokenPrefs().getString(C.USERNAME, null)
            val isLoggedIn = !accountLogin.isNullOrBlank() &&
                    (!TwitchApiHelper.getGQLHeaders(context, true)[C.HEADER_TOKEN].isNullOrBlank() ||
                            !TwitchApiHelper.getHelixHeaders(context)[C.HEADER_TOKEN].isNullOrBlank())
            val chatUrl = args.getString(KEY_CHAT_URL)
            if (isLive || (args.getString(KEY_VIDEO_ID) != null && args.getInt(KEY_START_TIME) != -1) || chatUrl != null) {
                val enableMessaging = isLive && isLoggedIn
                val style = ChatStyle.from(prefs)
                state.style = style
                builder = ChatLineBuilder(
                    style = style,
                    strings = ChatStrings.from(context),
                    assets = assets(),
                    loggedInUser = if (enableMessaging) accountLogin else null,
                    chatUrl = chatUrl,
                    getEmoteBytes = viewModel::getEmoteBytes,
                )
                startPipeline()
                events.trySend(ChatEvent.Reset(synchronized(viewModel.chatMessages) { viewModel.chatMessages.toList() }))
                state.messagingEnabled = enableMessaging
                state.chatBarVisible = parentFragment !is PlayerFragment || prefs.getBoolean(C.KEY_CHAT_BAR_VISIBLE, true)
                if (enableMessaging) {
                    viewModel.loadRecentEmotes()
                    collectEmotes()
                    trackKeyboard(view)
                }
                collectEvents()
                if (chatUrl != null) {
                    viewModel.startReplay(
                        channelId = channelId,
                        channelLogin = channelLogin,
                        chatUrl = chatUrl,
                        createdAt = args.getString(KEY_CREATED_AT),
                        getCurrentPosition = (parentFragment as PlayerFragment)::getCurrentPosition,
                        getCurrentSpeed = (parentFragment as PlayerFragment)::getCurrentSpeed
                    )
                }
            } else {
                state.replayUnavailable = true
            }
        }
        if (parentFragment !is PlayerFragment) {
            ViewCompat.setOnApplyWindowInsetsListener(view) { _, windowInsets ->
                if ((activity as? ShellHost)?.shell?.hasNavigation == false) {
                    val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
                    view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                        bottomMargin = insets.bottom
                    }
                }
                WindowInsetsCompat.CONSUMED
            }
        }
    }

    private fun assets() = ChatAssets.of(
        viewModel.localTwitchEmotes, viewModel.thirdPartyEmotes, viewModel.globalBadges, viewModel.channelBadges, viewModel.cheerEmotes,
        viewModel.stvUsers, viewModel.stvBadges, viewModel.namePaints, viewModel.personalEmoteSets,
    )

    // ---- message pipeline ------------------------------------------------------------------------------------------

    /** Applies the view model's events in order: lines are built on one background thread, then added in one go. */
    private fun startPipeline() {
        viewLifecycleOwner.lifecycleScope.launch {
            val batch = ArrayList<ChatEvent>()
            for (first in events) {
                batch.add(first)
                while (true) batch.add(events.tryReceive().getOrNull() ?: break)
                apply(batch)
                batch.clear()
            }
        }
    }

    private suspend fun apply(batch: List<ChatEvent>) {
        val b = builder ?: return
        val current = state.lines.toList()
        val limit = state.style.messageLimit
        // build off the main thread against a copy, then swap the result in
        val result = withContext(buildDispatcher) {
            var lines = current
            for (event in batch) {
                lines = when (event) {
                    is ChatEvent.New -> lines + b.build(event.message)
                    is ChatEvent.AddFront -> event.messages.map { b.build(it) } + lines
                    is ChatEvent.Remove -> lines.drop(event.count)
                    is ChatEvent.Reset -> event.messages.map { b.build(it) }
                    ChatEvent.ReloadAll -> {
                        b.assets = assets()
                        lines.map { b.build(it.message, it.key) }
                    }
                    is ChatEvent.UpdateUser -> lines.map { if (it.message.userId == event.userId) b.build(it.message, it.key) else it }
                }
            }
            if (lines.size > limit) lines.takeLast(limit) else lines
        }
        val now = System.currentTimeMillis()
        repeat(batch.count { it is ChatEvent.New }) { recentArrivals.addLast(now) }
        while (recentArrivals.isNotEmpty() && now - recentArrivals.first() > 1000) recentArrivals.removeFirst()
        state.fast = recentArrivals.size > FAST_RATE
        replaceLines(result)
    }

    /** Updates the observable list with the fewest changes, so the list keeps its items and scroll position. */
    private fun replaceLines(result: List<ChatLine>) {
        val lines = state.lines
        if (result.isEmpty()) {
            lines.clear()
            return
        }
        val firstKey = result.first().key
        val drop = lines.indexOfFirst { it.key == firstKey }
        if (drop > 0) lines.removeRange(0, drop)
        if (drop >= 0 && lines.size <= result.size && lines.indices.all { lines[it] === result[it] }) {
            if (result.size > lines.size) lines.addAll(result.subList(lines.size, result.size))
            return
        }
        // the start or the middle changed (recent messages, rebuilt lines): replace what differs
        var i = 0
        while (i < lines.size && i < result.size) {
            if (lines[i] !== result[i]) lines[i] = result[i]
            i++
        }
        if (lines.size > result.size) lines.removeRange(result.size, lines.size)
        if (result.size > lines.size) lines.addAll(result.subList(lines.size, result.size))
    }

    private fun collectEvents() {
        val owner = viewLifecycleOwner
        fun launch(block: suspend () -> Unit) = owner.lifecycleScope.launch { owner.repeatOnLifecycle(Lifecycle.State.STARTED) { block() } }
        launch {
            viewModel.newMessage.collect { (message, _, removeCount) ->
                if (removeCount > 0) {
                    synchronized(viewModel.chatMessages) { repeat(removeCount) { viewModel.chatMessages.removeAt(0) } }
                }
                events.trySend(ChatEvent.New(message))
            }
        }
        launch { viewModel.addMessages.collect { events.trySend(ChatEvent.AddFront(it.first)) } }
        launch { viewModel.removeMessages.collect { events.trySend(ChatEvent.Remove(it)) } }
        launch {
            viewModel.reloadMessages.collectLatest {
                if (it) {
                    events.trySend(ChatEvent.ReloadAll)
                    viewModel.reloadMessages.value = false
                }
            }
        }
        launch { viewModel.updateUserMessages.collect { events.trySend(ChatEvent.UpdateUser(it)) } }
        launch {
            viewModel.roomState.collectLatest { roomState ->
                if (roomState != null) {
                    state.modes = state.modes.merge(roomState)
                    viewModel.roomState.value = null
                }
            }
        }
        launch { viewModel.channelPoints.collect { state.points = it } }
        launch {
            viewModel.hideRaid.collectLatest {
                if (it) {
                    state.raid = null
                    viewModel.raidClosed = true
                    viewModel.hideRaid.value = false
                }
            }
        }
        launch {
            viewModel.raid.collectLatest { raid ->
                if (raid != null) {
                    if (!viewModel.raidClosed) {
                        if (raid.openStream) {
                            if (requireContext().prefs().getBoolean(C.CHAT_RAIDS_AUTO_SWITCH, true) && parentFragment is PlayerFragment) {
                                (requireActivity() as? MainActivity)?.startStream(
                                    Stream(channelId = raid.targetId, channelLogin = raid.targetLogin, channelName = raid.targetName, channelImageURL = raid.targetImageURL)
                                )
                            }
                            state.raid = null
                            viewModel.raidClosed = true
                        } else {
                            state.raid = raid
                            state.raidImage = raid.targetImage
                        }
                    }
                    viewModel.raid.value = null
                }
            }
        }
        launch {
            viewModel.raidClicked.collectLatest {
                if (it != null) {
                    (requireActivity() as? MainActivity)?.startStream(
                        Stream(channelId = it.targetId, channelLogin = it.targetLogin, channelName = it.targetName, channelImageURL = it.targetImageURL)
                    )
                    viewModel.raidClicked.value = null
                }
            }
        }
        launch {
            viewModel.hidePoll.collectLatest {
                if (it) {
                    closePoll()
                    viewModel.hidePoll.value = false
                }
            }
        }
        launch {
            viewModel.poll.collectLatest { poll ->
                if (poll != null) {
                    if (!viewModel.pollClosed) {
                        when (poll.status) {
                            "ACTIVE" -> state.poll = poll
                            "COMPLETED", "TERMINATED" -> {
                                state.poll = poll
                                viewModel.pollSecondsLeft.value = null
                                viewModel.pollTimer?.cancel()
                                viewModel.startPollTimeout { state.poll = null }
                            }
                            else -> closePoll()
                        }
                    }
                    viewModel.poll.value = null
                }
            }
        }
        launch { viewModel.pollSecondsLeft.collect { state.pollSecondsLeft = it } }
        launch {
            viewModel.hidePrediction.collectLatest {
                if (it) {
                    closePrediction()
                    viewModel.hidePrediction.value = false
                }
            }
        }
        launch {
            viewModel.prediction.collectLatest { prediction ->
                if (prediction != null) {
                    if (!viewModel.predictionClosed) {
                        when (prediction.status) {
                            "ACTIVE" -> state.prediction = prediction
                            "LOCKED", "CANCELED", "CANCEL_PENDING", "RESOLVED", "RESOLVE_PENDING" -> {
                                state.prediction = prediction
                                viewModel.predictionSecondsLeft.value = null
                                viewModel.predictionTimer?.cancel()
                                viewModel.startPredictionTimeout { state.prediction = null }
                            }
                            else -> closePrediction()
                        }
                    }
                    viewModel.prediction.value = null
                }
            }
        }
        launch { viewModel.predictionSecondsLeft.collect { state.predictionSecondsLeft = it } }
        launch {
            viewModel.playbackMessage.collectLatest {
                if (it != null) {
                    if (it.live != null) {
                        (parentFragment as? PlayerFragment)?.updateLiveStatus(it.live, it.serverTime, channelLogin)
                    }
                    (parentFragment as? PlayerFragment)?.updateViewerCount(it.viewers)
                }
            }
        }
        launch {
            viewModel.streamInfo.collectLatest {
                if (it != null) {
                    (parentFragment as? PlayerFragment)?.updateStreamInfo(it.title, it.gameId, null, it.gameName)
                }
            }
        }
    }

    private fun collectEmotes() {
        val owner = viewLifecycleOwner
        fun launch(block: suspend () -> Unit) = owner.lifecycleScope.launch { owner.repeatOnLifecycle(Lifecycle.State.STARTED) { block() } }
        launch {
            viewModel.recentEmotes.collectLatest { list ->
                recentEmoteNames = list.sortedByDescending { it.usedAt }.map { it.name }
                updateEmoteSections()
            }
        }
        launch { viewModel.userEmotesUpdated.collect { updateEmoteSections() } }
        launch { viewModel.thirdPartyEmotesUpdated.collect { updateEmoteSections() } }
        updateEmoteSections()
    }

    private fun updateEmoteSections() {
        val personal = viewModel.userSTVEmoteSetId?.let { id -> synchronized(viewModel.personalEmoteSets) { viewModel.personalEmoteSets[id] } } ?: emptyList()
        state.emoteSections = EmoteSections.build(
            recent = recentEmoteNames,
            twitch = synchronized(viewModel.userEmotes) { viewModel.userEmotes.toList() },
            channelTwitch = synchronized(viewModel.channelTwitchEmoteNames) { viewModel.channelTwitchEmoteNames.toSet() },
            personal = personal,
            thirdParty = synchronized(viewModel.thirdPartyEmotes) { viewModel.thirdPartyEmotes.toList() },
        )
    }

    /** The keyboard's height, for the emote panel that takes its place; the chat's own insets are consumed by its host. */
    private fun trackKeyboard(view: View) {
        var imeShown = false
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            val ime = ViewCompat.getRootWindowInsets(view)?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0
            val nav = ViewCompat.getRootWindowInsets(view)?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0
            val shown = ime > nav
            if (shown) state.keyboardHeight = ime - nav
            // only when the keyboard comes up: while it goes down after the emote button, the panel must stay
            if (shown && !imeShown && state.emotePanel) toggleEmoteMenu(false)
            imeShown = shown
        }
        layoutListener = listener
        view.viewTreeObserver.addOnGlobalLayoutListener(listener)
    }

    // ---- v0.1 methods the player and channel page call ---------------------------------------------------------------

    override fun initialize() {
        if (!requireContext().prefs().getBoolean(C.CHAT_DISABLE, false)) {
            if (isLive) {
                viewModel.startLive(
                    requireContext().prefs().getString(C.NETWORK_LIBRARY, C.OKHTTP),
                    requireContext().prefs().getString(C.CHAT_RECENT_MESSAGES_URL, "https://recent-messages.robotty.de/api/v2/recent-messages/\$channel"),
                    channelId,
                    channelLogin,
                    args.getString(KEY_CHANNEL_NAME),
                    args.getString(KEY_STREAM_ID)
                )
            } else {
                val videoId = args.getString(KEY_VIDEO_ID)
                val startTime = args.getInt(KEY_START_TIME)
                if (videoId != null && startTime != -1) {
                    viewModel.startReplay(
                        channelId = channelId,
                        channelLogin = channelLogin,
                        videoId = videoId,
                        createdAt = args.getString(KEY_CREATED_AT),
                        startTime = startTime,
                        getCurrentPosition = (parentFragment as PlayerFragment)::getCurrentPosition,
                        getCurrentSpeed = (parentFragment as PlayerFragment)::getCurrentSpeed
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resume()
    }

    private fun resume() {
        if (isLive) {
            viewModel.resumeLive(channelId, channelLogin)
        } else {
            viewModel.resumeReplay(
                channelId = channelId,
                channelLogin = channelLogin,
                chatUrl = args.getString(KEY_CHAT_URL),
                videoId = args.getString(KEY_VIDEO_ID),
                createdAt = args.getString(KEY_CREATED_AT),
                startTime = args.getInt(KEY_START_TIME),
                getCurrentPosition = (parentFragment as PlayerFragment)::getCurrentPosition,
                getCurrentSpeed = (parentFragment as PlayerFragment)::getCurrentSpeed
            )
        }
    }

    fun isActive(): Boolean? = viewModel.isActive()

    fun disconnect() = viewModel.disconnect()

    fun reconnect() {
        val login = channelLogin
        if (login != null) {
            viewModel.startLiveChat(channelId, login)
            if (requireContext().prefs().getBoolean(C.CHAT_RECENT, true)) {
                viewModel.loadRecentMessages(
                    requireContext().prefs().getString(C.NETWORK_LIBRARY, C.OKHTTP),
                    requireContext().prefs().getString(C.CHAT_RECENT_MESSAGES_URL, "https://recent-messages.robotty.de/api/v2/recent-messages/\$channel"),
                    login,
                )
            }
        }
        viewModel.autoReconnect = true
    }

    fun reloadEmotes() = viewModel.reloadEmotes(channelId, channelLogin)

    fun startReplayChatLoad() = viewModel.startReplayChatLoad()

    fun updatePosition(position: Long) = viewModel.updatePosition(position)

    fun updateSpeed(speed: Float) = viewModel.updateSpeed(speed)

    fun updateStreamId(id: String?) {
        viewModel.streamId = id
    }

    fun emoteMenuIsVisible() = state.emotePanel

    fun toggleEmoteMenu(enable: Boolean) {
        state.emotePanel = enable
        toggleBackPressedCallback(enable)
    }

    fun toggleBackPressedCallback(enable: Boolean) {
        if (enable) {
            requireActivity().onBackPressedDispatcher.addCallback(this, backPressedCallback)
        } else {
            backPressedCallback.remove()
        }
    }

    /** The player's "chat input" switch (v0.1 hid the message bar view). */
    fun setChatBarVisible(visible: Boolean) {
        if (!visible) {
            hideKeyboard()
            if (state.emotePanel) toggleEmoteMenu(false)
        }
        state.chatBarVisible = visible
    }

    fun chatBarVisible() = state.chatBarVisible

    /** Picture-in-picture closes the chat's sheets, like v0.1 closed its dialogs. */
    fun closeSheets() {
        state.sheet = null
    }

    // ---- ChatActions -----------------------------------------------------------------------------------------------

    override fun openUserCard(message: ChatMessage) {
        hideKeyboard()
        openSheet(ChatSheet.UserCard(message))
    }

    override fun openSheet(sheet: ChatSheet) {
        state.sheet = sheet
        when (sheet) {
            is ChatSheet.UserCard -> loadUser(sheet.message)
            is ChatSheet.EmoteCard -> loadEmote(sheet.info)
            else -> Unit
        }
    }

    override fun closeSheet() {
        state.sheet = null
    }

    private fun loadUser(message: ChatMessage) {
        userJob?.cancel()
        sheetData.user = null
        sheetData.userFailed = false
        if (message.userId == null && message.userLogin == null) return
        val targetId = channelId
        val vm = ViewModelProvider(this, MessageClickedViewModelFactory)["user${cardRequests++}", MessageClickedViewModel::class.java]
        val context = requireContext()
        vm.loadUser(
            channelId = message.userId,
            channelLogin = message.userLogin,
            targetId = if (message.userId != targetId) targetId else null,
            networkLibrary = context.prefs().getString(C.NETWORK_LIBRARY, C.OKHTTP),
            gqlHeaders = TwitchApiHelper.getGQLHeaders(context, true),
            helixHeaders = TwitchApiHelper.getHelixHeaders(context),
            enableIntegrity = context.prefs().getBoolean(C.ENABLE_INTEGRITY, false),
        )
        userJob = viewLifecycleOwner.lifecycleScope.launch {
            launch { vm.integrity.collect { (requireActivity() as? MainActivity)?.getNewIntegrityToken(it, childFragmentManager) } }
            val (user, _) = vm.user.first { it != null }!!
            sheetData.userFailed = user == null
            sheetData.user = user?.let {
                ChatUserInfo(
                    id = it.id, login = it.login, name = it.name, image = it.profileImage, banner = it.bannerImageURL,
                    created = it.createdAt?.let { d -> formatDate(d) },
                    followed = it.followedAt?.let { d -> formatDate(d) },
                )
            }
            userJob?.cancel()
        }
    }

    private fun formatDate(text: String): String? =
        Instant.parseOrNull(text)?.toEpochMilliseconds()?.takeIf { it > 0 }?.let { TwitchApiHelper.formatDate(requireContext(), it) }

    private fun loadEmote(info: EmoteInfo) {
        emoteJob?.cancel()
        sheetData.emoteSource = null
        val id = info.emoteId ?: return
        val vm = ViewModelProvider(this, ImageClickedViewModelFactory)["emote${cardRequests++}", ImageClickedViewModel::class.java]
        val context = requireContext()
        vm.loadEmoteCard(id, context.prefs().getString(C.NETWORK_LIBRARY, C.OKHTTP), TwitchApiHelper.getGQLHeaders(context), context.prefs().getBoolean(C.ENABLE_INTEGRITY, false))
        emoteJob = viewLifecycleOwner.lifecycleScope.launch {
            launch { vm.integrity.collect { (requireActivity() as? MainActivity)?.getNewIntegrityToken(it, childFragmentManager) } }
            val card = vm.emoteCard.first { it != null }!!
            val name = ChatEvents.displayName(card.channelName, card.channelLogin, state.nameDisplay)
            sheetData.emoteSource = when (card.type) {
                "SUBSCRIPTIONS" -> getString(R.string.channel_sub_emote, name, when (card.subTier) {
                    "TIER_1" -> "1"
                    "TIER_2" -> "2"
                    "TIER_3" -> "3"
                    else -> card.subTier
                })
                "FOLLOWER" -> getString(R.string.channel_follower_emote, name)
                "BITS_BADGE_TIERS" -> getString(R.string.bits_reward_emote, card.bitThreshold)
                else -> null
            }
            emoteJob?.cancel()
        }
    }

    override fun onIntegrityTokenLoaded(callback: String?) {
        if (callback == "refresh") {
            when (val sheet = state.sheet) {
                is ChatSheet.UserCard -> loadUser(sheet.message)
                is ChatSheet.EmoteCard -> loadEmote(sheet.info)
                else -> Unit
            }
        }
    }

    override fun send() {
        val text = state.input.text.trim()
        if (text.isEmpty()) return
        val context = requireContext()
        val replyId = state.reply?.id
        viewModel.send(
            message = text,
            replyId = replyId,
            networkLibrary = context.prefs().getString(C.NETWORK_LIBRARY, C.OKHTTP),
            gqlHeaders = TwitchApiHelper.getGQLHeaders(context, true),
            helixHeaders = TwitchApiHelper.getHelixHeaders(context),
            accountId = context.tokenPrefs().getString(C.USER_ID, null),
            channelId = channelId,
            channelLogin = channelLogin,
            useApiCommands = context.prefs().getBoolean(C.DEBUG_API_COMMANDS, true),
            useApiChatMessages = context.prefs().getBoolean(C.DEBUG_API_CHAT_MESSAGES, true),
            enableIntegrity = context.prefs().getBoolean(C.ENABLE_INTEGRITY, false),
        )
        state.input = TextFieldValue("")
        state.reply = null
        state.suggestions = emptyList()
        if (state.emotePanel) toggleEmoteMenu(false)
        state.scrollToLatest++
    }

    override fun onInputChange(value: TextFieldValue) {
        state.input = value
        val token = ChatSuggestions.tokenAt(value)
        state.suggestions = if (token != null) {
            ChatSuggestions.find(token.second, synchronized(viewModel.autoCompleteList) { viewModel.autoCompleteList.toList() })
        } else emptyList()
    }

    override fun deleteWord() {
        val text = ChatSuggestions.deleteWord(state.input.text)
        onInputChange(TextFieldValue(text, TextRange(text.length)))
    }

    override fun clearInput() = onInputChange(TextFieldValue(""))

    override fun cancelReply() {
        state.reply = null
    }

    override fun toggleEmotePanel() {
        if (!state.emotePanel) {
            hideKeyboard()
            updateEmoteSections()
        }
        toggleEmoteMenu(!state.emotePanel)
    }

    override fun onInputFocused() {
        if (state.emotePanel) toggleEmoteMenu(false)
    }

    override fun insert(text: String) {
        val v = state.input
        val cursor = v.selection.end.coerceIn(0, v.text.length)
        val before = v.text.substring(0, cursor)
        val prefix = if (before.isNotEmpty() && !before.endsWith(' ')) " " else ""
        val inserted = "$prefix$text "
        onInputChange(TextFieldValue(before + inserted + v.text.substring(cursor), TextRange(cursor + inserted.length)))
    }

    override fun applySuggestion(s: Suggestion) {
        state.input = ChatSuggestions.apply(state.input, s.text)
        state.suggestions = emptyList()
    }

    override fun openPoints() {
        sheetData.notifyPoints = requireContext().prefs().getBoolean(C.CHAT_POINTS_NOTIFY, false)
        openSheet(ChatSheet.Points)
    }

    override fun claimPoints() = viewModel.claimPoints()

    override fun openRaid() {
        state.raid?.let { viewModel.raidClicked.value = it }
    }

    override fun closeRaid() {
        state.raid = null
        viewModel.raidClosed = true
    }

    override fun closePoll() {
        state.poll = null
        viewModel.pollSecondsLeft.value = null
        viewModel.pollTimer?.cancel()
        viewModel.pollClosed = true
        if (state.sheet == ChatSheet.Poll) state.sheet = null
    }

    override fun closePrediction() {
        state.prediction = null
        viewModel.predictionSecondsLeft.value = null
        viewModel.predictionTimer?.cancel()
        viewModel.predictionClosed = true
        if (state.sheet == ChatSheet.Prediction) state.sheet = null
    }

    // ---- ChatSheetActions ------------------------------------------------------------------------------------------

    override fun reply(message: ChatMessage) {
        val id = message.id ?: return
        state.sheet = null
        state.reply = ReplyTarget(id, message.userLogin, message.userName ?: sheetData.user?.name, message.message)
        state.focusRequests++
    }

    override fun mention(message: ChatMessage) {
        state.sheet = null
        insert("@" + (message.userName ?: message.userLogin ?: return))
        state.focusRequests++
    }

    override fun copy(message: ChatMessage) {
        ContextCompat.getSystemService(requireContext(), ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("label", message.message))
        state.sheet = null
    }

    override fun copyToInput(message: ChatMessage) {
        state.sheet = null
        val text = message.message ?: return
        onInputChange(TextFieldValue(text, TextRange(text.length)))
        state.focusRequests++
    }

    override fun copyFull(message: ChatMessage) {
        ContextCompat.getSystemService(requireContext(), ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("label", message.fullMsg))
        state.sheet = null
    }

    override fun viewChannel(id: String?, login: String?, name: String?, image: String?) {
        state.sheet = null
        (activity as? MainActivity)?.nav?.openChannel(id = id, login = login, name = name, image = image)
        (parentFragment as? PlayerFragment)?.minimize()
    }

    override fun setAutoClaim(on: Boolean) {
        requireContext().prefs().edit { putBoolean(C.CHAT_POINTS_COLLECT, on) }
        viewModel.setAutoClaim(on)
    }

    override fun setNotifyPoints(on: Boolean) {
        requireContext().prefs().edit { putBoolean(C.CHAT_POINTS_NOTIFY, on) }
        viewModel.notifyPoints = on
        sheetData.notifyPoints = on
    }

    override val showFullMessage: Boolean get() = requireContext().prefs().getBoolean(C.DEBUG_CHAT_FULL_MSG, false)

    private fun hideKeyboard() {
        val view = view ?: return
        (requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(view.windowToken, 0)
        view.clearFocus()
    }

    override fun onNetworkRestored() {
        if (isResumed) resume()
    }

    override fun onStop() {
        super.onStop()
        if (!isLive || !requireContext().prefs().getBoolean(C.PLAYER_KEEP_CHAT_OPEN, false)) {
            viewModel.stopLiveChat()
            viewModel.stopReplayChat()
        }
    }

    override fun onDestroyView() {
        layoutListener?.let { view?.viewTreeObserver?.removeOnGlobalLayoutListener(it) }
        layoutListener = null
        super.onDestroyView()
    }

    companion object {
        private const val KEY_IS_LIVE = "isLive"
        private const val KEY_CHANNEL_ID = "channel_id"
        private const val KEY_CHANNEL_LOGIN = "channel_login"
        private const val KEY_CHANNEL_NAME = "channel_name"
        private const val KEY_STREAM_ID = "streamId"
        private const val KEY_VIDEO_ID = "videoId"
        private const val KEY_CREATED_AT = "createdAt"
        private const val KEY_CHAT_URL = "chatUrl"
        private const val KEY_START_TIME = "startTime"

        /** More messages a second than this and new lines appear without animation (plan 5). */
        const val FAST_RATE = 4

        fun newInstance(channelId: String?, channelLogin: String?, channelName: String?, streamId: String?): ChatFragment {
            return ChatFragment().apply {
                arguments = Bundle().apply {
                    putBoolean(KEY_IS_LIVE, true)
                    putString(KEY_CHANNEL_ID, channelId)
                    putString(KEY_CHANNEL_LOGIN, channelLogin)
                    putString(KEY_CHANNEL_NAME, channelName)
                    putString(KEY_STREAM_ID, streamId)
                }
            }
        }

        fun newInstance(channelId: String?, channelLogin: String?, videoId: String?, createdAt: String?, startTime: Int?): ChatFragment {
            return ChatFragment().apply {
                arguments = Bundle().apply {
                    putBoolean(KEY_IS_LIVE, false)
                    putString(KEY_CHANNEL_ID, channelId)
                    putString(KEY_CHANNEL_LOGIN, channelLogin)
                    putString(KEY_VIDEO_ID, videoId)
                    putString(KEY_CREATED_AT, createdAt)
                    putInt(KEY_START_TIME, (startTime ?: -1))
                }
            }
        }

        fun newLocalInstance(channelId: String?, channelLogin: String?, createdAt: String?, chatUrl: String?): ChatFragment {
            return ChatFragment().apply {
                arguments = Bundle().apply {
                    putString(KEY_CHANNEL_ID, channelId)
                    putString(KEY_CHANNEL_LOGIN, channelLogin)
                    putString(KEY_CREATED_AT, createdAt)
                    putString(KEY_CHAT_URL, chatUrl)
                }
            }
        }
    }
}
