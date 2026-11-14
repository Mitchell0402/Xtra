package com.github.andreyasadchy.xtra.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.ui.design.components.ActionItem
import com.github.andreyasadchy.xtra.ui.design.components.ActionSheet
import com.github.andreyasadchy.xtra.ui.design.components.Avatar
import com.github.andreyasadchy.xtra.ui.design.components.EmptyState
import com.github.andreyasadchy.xtra.ui.design.components.LagoonIconButton
import com.github.andreyasadchy.xtra.ui.design.components.LagoonTextField
import com.github.andreyasadchy.xtra.ui.design.components.LargeTopBar
import com.github.andreyasadchy.xtra.ui.design.components.SettingDependents
import com.github.andreyasadchy.xtra.ui.design.components.SettingIconTile
import com.github.andreyasadchy.xtra.ui.design.components.SettingRow
import com.github.andreyasadchy.xtra.ui.design.components.SettingTrailing
import com.github.andreyasadchy.xtra.ui.design.components.SettingsGroup
import com.github.andreyasadchy.xtra.ui.design.components.ToastHost
import com.github.andreyasadchy.xtra.ui.design.components.ToastHostState
import com.github.andreyasadchy.xtra.ui.design.components.pressable
import com.github.andreyasadchy.xtra.ui.design.components.rememberToastHostState
import com.github.andreyasadchy.xtra.ui.design.components.rememberTopBarState
import com.github.andreyasadchy.xtra.ui.design.icons.LucideIcons
import com.github.andreyasadchy.xtra.ui.design.theme.Lagoon
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonMotion
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonShapes
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonSpacing
import com.github.andreyasadchy.xtra.ui.settings.model.ActionRow
import com.github.andreyasadchy.xtra.ui.settings.model.ChoiceSetting
import com.github.andreyasadchy.xtra.ui.settings.model.ChoiceSource
import com.github.andreyasadchy.xtra.ui.settings.model.PageId
import com.github.andreyasadchy.xtra.ui.settings.model.PageLink
import com.github.andreyasadchy.xtra.ui.settings.model.SettingAction
import com.github.andreyasadchy.xtra.ui.settings.model.SettingItem
import com.github.andreyasadchy.xtra.ui.settings.model.SettingsCatalog
import com.github.andreyasadchy.xtra.ui.settings.model.SettingsGroupSpec
import com.github.andreyasadchy.xtra.ui.settings.model.SettingsPageSpec
import com.github.andreyasadchy.xtra.ui.settings.model.SliderSetting
import com.github.andreyasadchy.xtra.ui.settings.model.TabOrderSetting
import com.github.andreyasadchy.xtra.ui.settings.model.TextKind
import com.github.andreyasadchy.xtra.ui.settings.model.TextSetting
import com.github.andreyasadchy.xtra.ui.settings.model.ToggleSetting
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Widths at which settings shows categories and content side by side (plan 4 and 7.7: tablet landscape). */
const val SettingsTwoPaneDp = 840

const val SettingsHomeTag = "settingsHome"
const val SettingsSearchFieldTag = "settingsSearchField"
const val SettingsListTag = "settingsList"

/**
 * The settings back stack: the home screen and the pages opened from it. Survives rotation and process death as a
 * list of page names.
 */
@Stable
class SettingsNav(initial: List<String>) {
    val stack = mutableStateListOf<String>().apply { addAll(initial) }
    var forward by mutableStateOf(true)
        private set

    /** Row to scroll to and flash when its page opens (from search). */
    var highlight by mutableStateOf<String?>(null)
    var query by mutableStateOf("")

    val top: String get() = stack.last()

    fun open(page: PageId, highlightId: String? = null) {
        forward = true
        highlight = highlightId
        if (top != page.name) stack.add(page.name)
    }

    /** Opens a category from the home screen: the stack becomes home and that page. */
    fun openCategory(page: PageId, highlightId: String? = null) {
        forward = true
        highlight = highlightId
        val target = SettingsCatalog.page(page)
        val chain = generateSequence(target) { p -> p.parent?.let(SettingsCatalog::page) }.toList().asReversed().map { it.id.name }
        stack.clear()
        stack.add(HOME)
        stack.addAll(chain)
    }

    fun back(): Boolean {
        if (stack.size <= 1) return false
        forward = false
        stack.removeAt(stack.lastIndex)
        return true
    }

    companion object {
        const val HOME = "home"

        val Saver = listSaver<SettingsNav, String>(
            save = { it.stack.toList() },
            restore = { SettingsNav(it) },
        )
    }
}

@Composable
fun rememberSettingsNav(initialPage: PageId? = null): SettingsNav = rememberSaveable(saver = SettingsNav.Saver) {
    SettingsNav(listOfNotNull(SettingsNav.HOME, initialPage?.name))
}

/** A sheet a row asked for; the screen shows one at a time. */
sealed interface SheetRequest {
    data class Choice(val setting: ChoiceSetting) : SheetRequest
    data class Slider(val setting: SliderSetting) : SheetRequest
    data class Text(val setting: TextSetting) : SheetRequest
    data class Tabs(val setting: TabOrderSetting) : SheetRequest
    data class Confirm(val row: ActionRow) : SheetRequest
    data object AdLog : SheetRequest
    data object Languages : SheetRequest
    data object Account : SheetRequest
    data object ConfirmLogout : SheetRequest
}

/** The whole settings screen: home, pages, search, sheets, and two panes on wide windows. */
@Composable
fun SettingsApp(
    store: SettingsStore,
    host: SettingsHost,
    nav: SettingsNav = rememberSettingsNav(),
    toast: ToastHostState = rememberToastHostState(),
) {
    val colors = Lagoon.colors
    val wide = LocalConfiguration.current.screenWidthDp >= SettingsTwoPaneDp
    var sheet by remember { mutableStateOf<SheetRequest?>(null) }
    val context = LocalContext.current
    val searchIndex = remember(host.environment) { SettingsSearch.index(context, host.environment) }
    LaunchedEffect(host) { host.messages.collect { toast.show(it) } }
    // on a wide window the first page is always on the right, so back from it leaves settings
    BackHandler(enabled = if (wide) nav.stack.size > 2 else nav.stack.size > 1) { nav.back() }
    BackHandler(enabled = nav.query.isNotEmpty() && (nav.top == SettingsNav.HOME || wide)) { nav.query = "" }

    val actions = remember(store, host, nav) {
        PageActions(
            open = { page -> if (wide && SettingsCatalog.categories.any { it.id == page }) nav.openCategory(page) else nav.open(page) },
            sheet = { sheet = it },
            run = { row ->
                when {
                    row.action == SettingAction.AdLog -> sheet = SheetRequest.AdLog
                    row.action == SettingAction.DownloadedLanguages -> sheet = SheetRequest.Languages
                    row.destructive -> sheet = SheetRequest.Confirm(row)
                    else -> host.runAction(row.action)
                }
            },
        )
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.page)
            .windowInsetsPadding(WindowInsets.displayCutout.union(WindowInsets.navigationBars).only(WindowInsetsSides.Horizontal))
    ) {
        if (wide) {
            if (nav.stack.size < 2) LaunchedEffect(Unit) { nav.openCategory(SettingsCatalog.categories.first().id) }
            Row(Modifier.fillMaxSize()) {
                val current = nav.stack.getOrNull(1)
                HomeScreen(
                    store, host, nav, searchIndex, actions,
                    selected = current,
                    onAccount = { sheet = SheetRequest.Account },
                    modifier = Modifier.width(360.dp).fillMaxHeight(),
                    compact = true,
                )
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    PageHost(nav.top.takeIf { it != SettingsNav.HOME } ?: SettingsCatalog.categories.first().id.name, store, host, nav, actions, showBack = nav.stack.size > 2)
                }
            }
        } else {
            val motion = Lagoon.motion
            AnimatedContent(
                targetState = nav.top,
                transitionSpec = { if (nav.forward) motion.forward() else motion.back() },
                label = "settingsPage",
            ) { route ->
                if (route == SettingsNav.HOME) {
                    HomeScreen(store, host, nav, searchIndex, actions, selected = null, onAccount = { sheet = SheetRequest.Account })
                } else {
                    PageHost(route, store, host, nav, actions, showBack = true)
                }
            }
        }
        ToastHost(toast, Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 16.dp))
    }

    sheet?.let { request ->
        SettingsSheet(request, store, host, onDismiss = { sheet = null }, onRequest = { sheet = it })
    }
}

/** What rows and pages may ask for. */
@Stable
class PageActions(
    val open: (PageId) -> Unit,
    val sheet: (SheetRequest) -> Unit,
    val run: (ActionRow) -> Unit,
)

// ---- home ---------------------------------------------------------------------------------------------------------

@Composable
private fun HomeScreen(
    store: SettingsStore,
    host: SettingsHost,
    nav: SettingsNav,
    searchIndex: List<SearchEntry>,
    actions: PageActions,
    selected: String?,
    onAccount: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val topBar = rememberTopBarState()
    val query = nav.query
    Column(modifier.fillMaxSize().nestedScroll(topBar.nestedScrollConnection).testTag(SettingsHomeTag)) {
        LargeTopBar(
            title = stringResource(R.string.settings),
            state = topBar,
            navigationIcon = if (compact) null else {
                { LagoonIconButton(LucideIcons.ArrowLeft, stringResource(R.string.st_back), onClick = host::close, filled = false) }
            },
        )
        LazyColumn(
            Modifier.fillMaxSize().testTag(SettingsListTag),
            contentPadding = PaddingValues(start = LagoonSpacing.pageSide, end = LagoonSpacing.pageSide, bottom = bottomInset()),
            verticalArrangement = Arrangement.spacedBy(LagoonSpacing.m),
        ) {
            item(key = "search") {
                LagoonTextField(
                    value = query,
                    onValueChange = { nav.query = it },
                    placeholder = stringResource(R.string.st_search_hint),
                    modifier = Modifier.fillMaxWidth().testTag(SettingsSearchFieldTag),
                )
            }
            if (query.isNotBlank()) {
                val results = SettingsSearch.search(query, searchIndex)
                if (results.isEmpty()) {
                    item(key = "noResults") {
                        EmptyState(stringResource(R.string.st_search_empty, query.trim()), Modifier.fillMaxWidth().padding(top = 24.dp), icon = LucideIcons.Search)
                    }
                } else {
                    item(key = "results") {
                        SettingsGroup(title = stringResource(R.string.st_search_results, results.size)) {
                            results.take(60).forEach { entry ->
                                SettingRow(
                                    title = entry.title,
                                    description = entry.path.ifEmpty { null },
                                    icon = entry.icon,
                                    onClick = {
                                        if (entry.isPage) nav.openCategory(entry.page)
                                        else nav.openCategory(entry.page, highlightId = entry.id)
                                    },
                                )
                            }
                        }
                    }
                }
            } else {
                item(key = "account") { AccountCard(host.account, onAccount) }
                SettingsCatalog.homeGroups.forEach { (groupTitle, pages) ->
                    item(key = "home$groupTitle") {
                        SettingsGroup(title = stringResource(groupTitle)) {
                            pages.forEach { page ->
                                CategoryRow(page, store, host, selected = selected == page.id.name, showSummary = !compact) { nav.openCategory(page.id) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(page: SettingsPageSpec, store: SettingsStore, host: SettingsHost, selected: Boolean, showSummary: Boolean, onClick: () -> Unit) {
    val value = categoryValue(page, store, host)
    val background by animateColorAsState(if (selected) Lagoon.colors.control else Color.Transparent, Lagoon.motion.fade(), label = "categorySelected")
    SettingRow(
        title = stringResource(page.title),
        // the side list on wide windows is a list of names, as in the tablet design
        description = page.summary?.takeIf { showSummary }?.let { stringResource(it) },
        icon = page.icon,
        highlight = page.highlight || selected,
        trailing = if (value != null) SettingTrailing.Value(value) else SettingTrailing.Chevron,
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 4.dp).background(background, LagoonShapes.row),
    )
}

/** The current value a category shows on the home screen (design B-Settings: "均衡", "自动画质"). */
@Composable
private fun categoryValue(page: SettingsPageSpec, store: SettingsStore, host: SettingsHost): String? {
    store.observe()
    return when (page.id) {
        PageId.AdBlock -> adBlockPresetLabel(store)
        PageId.Playback -> choiceLabel(SettingsCatalog.playback.items.first() as ChoiceSetting, store, host)
        PageId.Appearance -> choiceLabel(SettingsCatalog.appearance.items.first() as ChoiceSetting, store, host)
        PageId.Notifications -> stringResource(
            if (store.bool(SettingsCatalog.notifications.items.first() as ToggleSetting)) R.string.st_on else R.string.st_off
        )
        PageId.About -> host.environment.versionName.ifEmpty { null }
        else -> null
    }
}

@Composable
private fun AccountCard(account: AccountInfo, onClick: () -> Unit) {
    val colors = Lagoon.colors
    val type = Lagoon.type
    val name = account.name?.takeIf { account.loggedIn && it.isNotBlank() }
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(onClick = onClick, shape = LagoonShapes.card)
            .background(colors.card)
            .padding(14.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LagoonSpacing.s),
    ) {
        if (name != null) {
            Avatar(null, name, size = 48.dp)
        } else {
            Box(Modifier.size(48.dp).background(colors.control, LagoonShapes.avatar(48.dp)), contentAlignment = Alignment.Center) {
                Icon(LucideIcons.User, null, tint = colors.textSecondary, modifier = Modifier.size(22.dp))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(name ?: stringResource(R.string.st_account_signed_out), style = type.bodyStrong, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(if (name != null) R.string.st_account_signed_in else R.string.st_account_sign_in_hint),
                style = type.label, color = colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(LucideIcons.ChevronRight, null, tint = colors.textHint, modifier = Modifier.size(16.dp))
    }
}

// ---- pages --------------------------------------------------------------------------------------------------------

@Composable
private fun PageHost(route: String, store: SettingsStore, host: SettingsHost, nav: SettingsNav, actions: PageActions, showBack: Boolean) {
    val id = runCatching { PageId.valueOf(route) }.getOrNull() ?: return
    val page = SettingsCatalog.page(id)
    val onBack: (() -> Unit)? = if (showBack) { { if (!nav.back()) host.close() } } else null
    when (id) {
        PageId.AdBlock -> AdBlockPage(page, store, host, nav, actions, onBack)
        PageId.CustomProxy -> CustomProxyPage(page, host.lists, onBack)
        PageId.StreamProxy -> StreamProxyPage(page, host.lists, onBack)
        PageId.VideoSwap -> VideoSwapPage(page, host.lists, onBack)
        PageId.Appearance -> SettingsPage(page, store, host, nav, actions, onBack, headerItems = 1) {
            item(key = "preview") { AppearancePreview(store) }
        }
        PageId.About -> SettingsPage(page, store, host, nav, actions, onBack, headerItems = 1) {
            item(key = "version") { AboutCard(host.environment.versionName) }
        }
        else -> SettingsPage(page, store, host, nav, actions, onBack)
    }
}

/** Page frame: collapsing title, back button, and a list that ends above the gesture bar. */
@Composable
fun SettingsPageFrame(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    listState: androidx.compose.foundation.lazy.LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit,
) {
    val topBar = rememberTopBarState()
    Column(modifier.fillMaxSize().background(Lagoon.colors.page).nestedScroll(topBar.nestedScrollConnection)) {
        LargeTopBar(
            title = title,
            state = topBar,
            navigationIcon = onBack?.let { back ->
                { LagoonIconButton(LucideIcons.ArrowLeft, stringResource(R.string.st_back), onClick = back, filled = false) }
            },
        )
        LazyColumn(
            Modifier.fillMaxSize().testTag(SettingsListTag),
            state = listState,
            contentPadding = PaddingValues(start = LagoonSpacing.pageSide, end = LagoonSpacing.pageSide, top = 4.dp, bottom = bottomInset()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            content = content,
        )
    }
}

@Composable
private fun bottomInset() = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp

/**
 * A page drawn from the catalogue: [header] first ([headerItems] lazy items), then the groups. Scrolls to and
 * flashes the row search opened it for.
 */
@Composable
fun SettingsPage(
    page: SettingsPageSpec,
    store: SettingsStore,
    host: SettingsHost,
    nav: SettingsNav,
    actions: PageActions,
    onBack: (() -> Unit)?,
    groups: List<SettingsGroupSpec> = page.groups,
    headerItems: Int = 0,
    header: LazyListScope.() -> Unit = {},
) {
    val listState = rememberLazyListState()
    val highlight = nav.highlight
    LaunchedEffect(highlight, page.id) {
        val target = highlight ?: return@LaunchedEffect
        val group = groups.indexOfFirst { g -> g.items.any { it.id == target } }
        if (group >= 0) listState.animateScrollToItem(headerItems + group)
        delay(1600)
        if (nav.highlight == target) nav.highlight = null
    }
    SettingsPageFrame(stringResource(page.title), onBack, listState = listState) {
        header()
        groups.forEach { group -> settingsGroup(group, page, store, host, actions, highlight) }
    }
}

fun LazyListScope.settingsGroup(
    group: SettingsGroupSpec,
    page: SettingsPageSpec,
    store: SettingsStore,
    host: SettingsHost,
    actions: PageActions,
    highlight: String?,
) {
    item(key = "group${group.title}${group.items.firstOrNull()?.id}") {
        val visible = group.items.filter { host.environment.allows(it.condition) }
        if (visible.isNotEmpty()) {
            SettingsGroup(
                title = group.title?.let { stringResource(it) },
                description = group.description?.let { stringResource(it) },
            ) {
                visible.forEach { item ->
                    val parent = item.dependsOn
                    if (parent != null) {
                        val switch = page.items.firstOrNull { it.id == parent } as? ToggleSetting
                        SettingDependents(visible = switch == null || store.bool(switch)) {
                            SettingItemRow(item, store, host, actions, highlight == item.id)
                        }
                    } else {
                        SettingItemRow(item, store, host, actions, highlight == item.id)
                    }
                }
            }
        }
    }
}

/** One catalogue row: the right trailing part for its type, and the sheet or action it opens. */
@Composable
fun SettingItemRow(item: SettingItem, store: SettingsStore, host: SettingsHost, actions: PageActions, highlighted: Boolean = false) {
    val colors = Lagoon.colors
    val flash by animateColorAsState(if (highlighted) colors.accentSoft else Color.Transparent, Lagoon.motion.fade(LagoonMotion.MEDIUM), label = "searchFlash")
    val modifier = Modifier.padding(horizontal = 4.dp).background(flash, LagoonShapes.row)
    val title = stringResource(item.title)
    val description = item.summary?.let { stringResource(it) }
    when (item) {
        is ToggleSetting -> SettingRow(
            title, modifier, description, item.icon,
            trailing = SettingTrailing.Toggle(store.bool(item)) { value ->
                store.put(item, value)
                host.afterChange(item, value)
            },
        )
        is ChoiceSetting -> SettingRow(
            title, modifier, description, item.icon,
            trailing = SettingTrailing.Value(choiceLabel(item, store, host)),
            onClick = { actions.sheet(SheetRequest.Choice(item)) },
        )
        is SliderSetting -> SettingRow(
            title, modifier, description, item.icon,
            trailing = SettingTrailing.Value(sliderLabel(item, store.int(item))),
            onClick = { actions.sheet(SheetRequest.Slider(item)) },
        )
        is TextSetting -> SettingRow(
            title, modifier, description, item.icon,
            trailing = SettingTrailing.Value(textLabel(item, store.string(item))),
            onClick = { actions.sheet(SheetRequest.Text(item)) },
        )
        is TabOrderSetting -> SettingRow(
            title, modifier, description, item.icon,
            trailing = SettingTrailing.Value(tabsLabel(item, store.string(item))),
            onClick = { actions.sheet(SheetRequest.Tabs(item)) },
        )
        is ActionRow -> SettingRow(
            title, modifier, description, item.icon,
            trailing = SettingTrailing.None,
            onClick = { actions.run(item) },
            titleColor = if (item.destructive) colors.danger else null,
        )
        is PageLink -> SettingRow(
            title, modifier, description, item.icon,
            trailing = SettingTrailing.Chevron,
            onClick = { actions.open(item.page) },
        )
    }
}

// ---- value labels -------------------------------------------------------------------------------------------------

@Composable
fun choiceOptions(setting: ChoiceSetting, host: SettingsHost): List<Pair<String, String>> = when (val source = setting.source) {
    is ChoiceSource.Arrays -> {
        val labels = stringArrayResource(source.entries)
        val values = stringArrayResource(source.values)
        values.zip(labels)
    }
    is ChoiceSource.Dynamic -> when (source.id) {
        SettingsCatalog.DYNAMIC_LANGUAGE -> stringArrayResource(R.array.language).zip(stringArrayResource(R.array.languageEntries))
        SettingsCatalog.DYNAMIC_NETWORK -> host.environment.networkLibraries.map { it to it }
        SettingsCatalog.DYNAMIC_TRANSLATE -> host.environment.translateLanguages
        else -> emptyList()
    }
}

@Composable
fun choiceValue(setting: ChoiceSetting, store: SettingsStore, host: SettingsHost): String {
    store.observe()
    return host.currentChoice(setting) ?: store.string(setting)
}

@Composable
fun choiceLabel(setting: ChoiceSetting, store: SettingsStore, host: SettingsHost): String {
    val value = choiceValue(setting, store, host)
    return choiceOptions(setting, host).firstOrNull { it.first == value }?.second ?: value
}

@Composable
fun sliderLabel(setting: SliderSetting, value: Int): String =
    setting.unit?.let { stringResource(it, value) } ?: value.toString()

@Composable
fun textLabel(setting: TextSetting, value: String?): String = when {
    setting.kind == TextKind.Secret -> stringResource(if (value.isNullOrEmpty()) R.string.st_not_set else R.string.st_set)
    value.isNullOrEmpty() -> stringResource(if (setting.default.isNullOrEmpty()) R.string.st_not_set else R.string.st_default)
    setting.kind == TextKind.Multiline -> value.lines().filter { it.isNotBlank() }.joinToString(", ")
    setting.unit != null -> stringResource(setting.unit, value)
    else -> value
}

@Composable
fun tabsLabel(setting: TabOrderSetting, value: String): String {
    val tabs = TabOrder.parse(value, setting.default)
    val labels = setting.tabs.toMap()
    val enabled = tabs.filter { it.enabled }
    if (enabled.isEmpty()) return stringResource(R.string.st_tabs_none)
    return enabled.mapNotNull { tab -> labels[tab.key]?.let { stringResource(it) } }.joinToString(stringResource(R.string.st_list_separator))
}

@Composable
private fun AboutCard(versionName: String) {
    val colors = Lagoon.colors
    Row(
        Modifier.fillMaxWidth().background(colors.card, LagoonShapes.card).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LagoonSpacing.s),
    ) {
        LagoonLogo(size = 56.dp)
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.app_name), style = Lagoon.type.title, color = colors.textPrimary)
            Text(stringResource(R.string.st_version, versionName), style = Lagoon.type.label, color = colors.textSecondary)
        }
    }
}

/** The settings group header of a custom page, styled like [SettingsGroup]'s title. */
@Composable
fun GroupTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text, style = Lagoon.type.labelStrong.copy(fontWeight = FontWeight.SemiBold), color = Lagoon.colors.textSecondary,
        modifier = modifier.padding(start = 6.dp, bottom = 8.dp).semantics { heading() },
    )
}

@Composable
internal fun AccountSheet(account: AccountInfo, host: SettingsHost, onDismiss: () -> Unit, onLogout: () -> Unit) {
    val actions = if (account.loggedIn) {
        listOf(
            ActionItem(stringResource(R.string.st_switch_account), LucideIcons.Users) { host.login() },
            ActionItem(stringResource(R.string.log_out), LucideIcons.LogOut, destructive = true, onClick = onLogout),
        )
    } else {
        listOf(ActionItem(stringResource(R.string.log_in), LucideIcons.LogIn) { host.login() })
    }
    ActionSheet(actions, onDismiss, title = account.name?.takeIf { account.loggedIn } ?: stringResource(R.string.st_account_signed_out))
}
