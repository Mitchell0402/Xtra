package com.github.andreyasadchy.xtra.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.ui.design.theme.Lagoon
import com.github.andreyasadchy.xtra.ui.player.PlayerControlsPreview
import com.github.andreyasadchy.xtra.ui.player.PlayerOptions
import com.github.andreyasadchy.xtra.ui.settings.model.PrefStore

/** Top of the player buttons page: the controls as the switches below draw them, updated as they change (U6). */
@Composable
fun PlayerButtonsPreview(store: SettingsStore) {
    store.observe()
    val options = PlayerOptions.from(store.prefs(PrefStore.App), loggedIn = true)
    Column {
        PlayerControlsPreview(options)
        Text(
            stringResource(R.string.pl_quick_buttons_desc),
            style = Lagoon.type.label,
            color = Lagoon.colors.textHint,
            modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 8.dp),
        )
    }
}
