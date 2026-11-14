package com.github.andreyasadchy.xtra.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.ui.design.components.LiveCard
import com.github.andreyasadchy.xtra.ui.design.components.LiveCardCompact
import com.github.andreyasadchy.xtra.ui.design.theme.Lagoon
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonShapes
import com.github.andreyasadchy.xtra.ui.following.displayName
import com.github.andreyasadchy.xtra.ui.settings.model.ChoiceSetting
import com.github.andreyasadchy.xtra.ui.settings.model.SettingsCatalog
import com.github.andreyasadchy.xtra.ui.settings.model.ToggleSetting
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.TwitchApiHelper

/**
 * The live preview at the top of the appearance page (plan 7.7): a sample stream card that follows the card
 * settings as they change (stream previews, name display, uptime, tags, number formatting). Nothing is loaded from
 * the network; the card has no image.
 */
@Composable
fun AppearancePreview(store: SettingsStore) {
    val colors = Lagoon.colors
    val motion = Lagoon.motion
    val density = LocalDensity.current
    store.observe()
    val items = SettingsCatalog.appearance.items
    fun toggle(key: String) = (items.first { it.id == key } as ToggleSetting).let(store::bool)
    fun choice(key: String) = (items.first { it.id == key } as ChoiceSetting).let(store::string)
    val compact = choice(C.COMPACT_STREAMS) != "disabled"
    val name = displayName(stringResource(R.string.st_preview_channel), "lagoonfm", choice(C.UI_NAME_DISPLAY))
    val viewers = TwitchApiHelper.formatCount(12_345, toggle(C.UI_TRUNCATE_VIEW_COUNT))
    val uptime = if (toggle(C.UI_UPTIME)) "2:14:05" else null
    val tags = if (toggle(C.UI_TAGS)) listOf(stringResource(R.string.st_preview_tag1), stringResource(R.string.st_preview_tag2)) else emptyList()
    val title = stringResource(R.string.st_preview_title)
    val game = stringResource(R.string.st_preview_game)
    Column(
        Modifier.fillMaxWidth().background(colors.card, LagoonShapes.card).padding(12.dp).semantics(mergeDescendants = true) {},
    ) {
        Text(
            stringResource(R.string.st_preview_label), style = Lagoon.type.labelStrong, color = colors.textSecondary,
            modifier = Modifier.padding(start = 6.dp, bottom = 10.dp),
        )
        AnimatedContent(compact, transitionSpec = { motion.sibling(density, targetState) }, label = "previewCard") { small ->
            if (small) {
                LiveCardCompact(title, name, viewers, onClick = {}, gameName = game, uptime = uptime, modifier = Modifier.fillMaxWidth())
            } else {
                LiveCard(title, name, viewers, onClick = {}, gameName = game, uptime = uptime, tags = tags, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
