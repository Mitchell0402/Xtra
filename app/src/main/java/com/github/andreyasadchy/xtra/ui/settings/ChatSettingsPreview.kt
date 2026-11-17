package com.github.andreyasadchy.xtra.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.chat.ChatMessage
import com.github.andreyasadchy.xtra.model.chat.Reply
import com.github.andreyasadchy.xtra.ui.chat.ChatAssets
import com.github.andreyasadchy.xtra.ui.chat.ChatLineBuilder
import com.github.andreyasadchy.xtra.ui.chat.ChatRow
import com.github.andreyasadchy.xtra.ui.chat.ChatStrings
import com.github.andreyasadchy.xtra.ui.chat.ChatStyle
import com.github.andreyasadchy.xtra.ui.design.theme.Lagoon
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonShapes
import com.github.andreyasadchy.xtra.ui.settings.model.PrefStore
import java.util.Random

/**
 * Top of the chat settings page (plan 7.7): a few sample lines drawn by the chat's own row, following text size,
 * timestamps, bold names, colours and first-message highlighting as they change (U7).
 */
@Composable
fun ChatSettingsPreview(store: SettingsStore) {
    val version = store.observe()
    val context = LocalContext.current
    val style = ChatStyle.from(store.prefs(PrefStore.App))
    val samples = sampleMessages(
        stringResource(R.string.cht_preview_m1), stringResource(R.string.cht_preview_m2),
        stringResource(R.string.cht_preview_m3), stringResource(R.string.cht_preview_m4),
    )
    val lines = remember(version, style, samples) {
        // text only: a picture that loads in the background would make the settings screenshots flaky
        val builder = ChatLineBuilder(style, ChatStrings.from(context), ChatAssets(), loggedInUser = "you", random = Random(7))
        samples.map { builder.build(it) }
    }
    Column(Modifier.fillMaxWidth().testTag("chatPreview")) {
        Text(
            stringResource(R.string.st_preview_label), style = Lagoon.type.labelStrong, color = Lagoon.colors.textSecondary,
            modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
        )
        Column(Modifier.fillMaxWidth().background(Lagoon.colors.page, LagoonShapes.card).padding(vertical = 10.dp)) {
            lines.forEach { ChatRow(it, style, onClick = null) }
        }
    }
}

/** A plain line, a reply, a mention of the viewer and a first message; nothing comes from the network. */
private fun sampleMessages(m1: String, m2: String, m3: String, m4: String): List<ChatMessage> {
    // a fixed afternoon, so the timestamps in the preview do not change between screenshots
    val now = 1_763_300_000_000L
    val first = ChatMessage(type = ChatMessage.USER_MESSAGE, id = "p1", userId = "1", userLogin = "lagoonfan", userName = "LagoonFan", message = m1, color = "#1E90FF", timestamp = now - 60_000)
    return listOf(
        first,
        ChatMessage(type = ChatMessage.REPLY_MESSAGE, reply = Reply("p1", "lagoonfan", "LagoonFan", m1)),
        ChatMessage(type = ChatMessage.USER_MESSAGE, id = "p2", userId = "2", userLogin = "tidepool", userName = "Tidepool", message = m2, color = "#8A2BE2", timestamp = now - 40_000,
            reply = Reply("p1", "lagoonfan", "LagoonFan", m1)),
        ChatMessage(type = ChatMessage.USER_MESSAGE, id = "p3", userId = "3", userLogin = "seaglass", userName = "SeaGlass", message = "@you $m3", timestamp = now - 20_000),
        ChatMessage(type = ChatMessage.USER_MESSAGE, id = "p4", userId = "4", userLogin = "newwave", userName = "NewWave", message = m4, isFirst = true, color = "#FF7F50", timestamp = now),
    )
}
