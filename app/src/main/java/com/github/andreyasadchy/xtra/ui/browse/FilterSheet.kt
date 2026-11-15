package com.github.andreyasadchy.xtra.ui.browse

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.model.ui.Tag
import com.github.andreyasadchy.xtra.ui.common.StreamSorts
import com.github.andreyasadchy.xtra.ui.common.VideoFilters
import com.github.andreyasadchy.xtra.ui.design.components.ButtonStyle
import com.github.andreyasadchy.xtra.ui.design.components.ChoiceOption
import com.github.andreyasadchy.xtra.ui.design.components.FilterChip
import com.github.andreyasadchy.xtra.ui.design.components.FilterChipItem
import com.github.andreyasadchy.xtra.ui.design.components.LagoonBottomSheet
import com.github.andreyasadchy.xtra.ui.design.components.LagoonButton
import com.github.andreyasadchy.xtra.ui.design.components.LagoonIconButton
import com.github.andreyasadchy.xtra.ui.design.components.LagoonTextField
import com.github.andreyasadchy.xtra.ui.design.components.MultiChoiceList
import com.github.andreyasadchy.xtra.ui.design.components.SettingRow
import com.github.andreyasadchy.xtra.ui.design.components.SettingTrailing
import com.github.andreyasadchy.xtra.ui.design.components.SingleChoiceList
import com.github.andreyasadchy.xtra.ui.design.components.pressable
import com.github.andreyasadchy.xtra.ui.design.components.shimmer
import com.github.andreyasadchy.xtra.ui.design.icons.LucideIcons
import com.github.andreyasadchy.xtra.ui.design.theme.Lagoon
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonShapes
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonSpacing
import kotlinx.coroutines.delay

/** Test tags of the filter sheet. */
const val FilterSheetTag = "browseFilterSheet"
const val FilterApplyTag = "browseFilterApply"
const val TagSearchFieldTag = "browseTagSearch"

private val StreamSortOptions = listOf(StreamSorts.SORT_VIEWERS, StreamSorts.SORT_VIEWERS_ASC, StreamSorts.RECENT)
private val VideoSorts = listOf(VideoFilters.SORT_TIME, VideoFilters.SORT_VIEWS)
private val Periods = listOf(VideoFilters.PERIOD_DAY, VideoFilters.PERIOD_WEEK, VideoFilters.PERIOD_MONTH, VideoFilters.PERIOD_ALL)
private val VideoTypes = listOf(VideoFilters.VIDEO_TYPE_ALL, VideoFilters.VIDEO_TYPE_ARCHIVE, VideoFilters.VIDEO_TYPE_HIGHLIGHT, VideoFilters.VIDEO_TYPE_UPLOAD)

fun streamSortLabel(sort: String) = when (sort) {
    StreamSorts.SORT_VIEWERS_ASC -> R.string.viewers_low
    StreamSorts.RECENT -> R.string.recent
    else -> R.string.viewers_high
}

fun videoSortLabel(sort: String) = if (sort == VideoFilters.SORT_TIME) R.string.upload_date else R.string.view_count

fun periodLabel(period: String) = when (period) {
    VideoFilters.PERIOD_DAY -> R.string.today
    VideoFilters.PERIOD_MONTH -> R.string.this_month
    VideoFilters.PERIOD_ALL -> R.string.all_time
    else -> R.string.this_week
}

fun typeLabel(type: String) = when (type) {
    VideoFilters.VIDEO_TYPE_ARCHIVE -> R.string.video_type_archive
    VideoFilters.VIDEO_TYPE_HIGHLIGHT -> R.string.video_type_highlight
    VideoFilters.VIDEO_TYPE_UPLOAD -> R.string.video_type_upload
    else -> R.string.all
}

/** Language names for the codes the lists filter by (the v0.1 language dialog's two arrays). */
@Composable
fun languageNames(): Map<String, String> {
    val names = stringArrayResource(R.array.gqlUserLanguageEntries)
    val codes = stringArrayResource(R.array.gqlUserLanguageValues)
    return remember(names, codes) { codes.zip(names).toMap() }
}

@Composable
private fun languagesSummary(languages: List<String>): String {
    val names = languageNames()
    return when (languages.size) {
        0 -> stringResource(R.string.browse_filter_any_language)
        1 -> names[languages[0]] ?: languages[0]
        else -> stringResource(R.string.browse_filter_more, names[languages[0]] ?: languages[0], languages.size - 1)
    }
}

/**
 * The chips above a list that show what it is sorted and filtered by (plan 6, filter strip). Every chip opens the
 * one filter sheet; the chosen tags show as filled chips and a tap removes them.
 */
@Composable
fun filterChips(
    kind: FilterKind,
    filter: BrowseFilter,
    config: FilterSheetConfig,
    onOpen: () -> Unit,
    onRemoveTag: (FilterTag) -> Unit,
): List<FilterChipItem> = buildList {
    when (kind) {
        FilterKind.Streams -> {
            add(FilterChipItem(stringResource(streamSortLabel(FilterLogic.effectiveStreamSort(filter))), icon = LucideIcons.ArrowUpDown, dropdown = true, onClick = onOpen))
            add(FilterChipItem(languagesSummary(filter.languages), icon = LucideIcons.Languages, dropdown = true, onClick = onOpen))
            add(FilterChipItem(stringResource(R.string.browse_filter_tags), icon = LucideIcons.Tag, dropdown = true, onClick = onOpen))
        }
        FilterKind.Videos -> {
            add(FilterChipItem(stringResource(videoSortLabel(FilterLogic.effectiveVideoSort(filter))), icon = LucideIcons.ArrowUpDown, dropdown = true, onClick = onOpen))
            if (config.showPeriod) add(FilterChipItem(stringResource(periodLabel(FilterLogic.effectivePeriod(filter))), icon = LucideIcons.Clock, dropdown = true, onClick = onOpen))
            add(FilterChipItem(stringResource(typeLabel(FilterLogic.effectiveType(filter))), icon = LucideIcons.Film, dropdown = true, onClick = onOpen))
            if (config.showLanguages) add(FilterChipItem(languagesSummary(filter.languages), icon = LucideIcons.Languages, dropdown = true, onClick = onOpen))
        }
        FilterKind.Clips -> {
            add(FilterChipItem(stringResource(periodLabel(FilterLogic.effectivePeriod(filter))), icon = LucideIcons.Clock, dropdown = true, onClick = onOpen))
            if (config.showLanguages) add(FilterChipItem(languagesSummary(filter.languages), icon = LucideIcons.Languages, dropdown = true, onClick = onOpen))
        }
        FilterKind.Games -> {
            add(FilterChipItem(stringResource(R.string.browse_filter_tags), icon = LucideIcons.Tag, dropdown = true, onClick = onOpen))
        }
    }
    filter.tags.forEach { tag ->
        add(FilterChipItem(tag.name, selected = true, icon = LucideIcons.X, onClick = { onRemoveTag(tag) }))
    }
}

private enum class FilterPage { Main, Languages, Tags }

/**
 * The one filter sheet (plan 7.2) that replaces the sort, language and tag dialogs: sort, period and type as
 * choices, languages and tags on a second page inside the same sheet, the save options as switches, and one
 * button to apply. [tagResults] are the tag search results for [onTagQuery]; null hides tag search.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterSheet(
    config: FilterSheetConfig,
    current: BrowseFilter,
    onApply: (BrowseFilter, FilterSaves) -> Unit,
    onDismiss: () -> Unit,
    tagResults: LazyPagingItems<Tag>? = null,
    onTagQuery: (String) -> Unit = {},
) {
    LagoonBottomSheet(onDismiss = onDismiss, modifier = Modifier.testTag(FilterSheetTag), skipHalfHeight = true) {
        FilterSheetContent(config, current, onApply, onDismiss, tagResults, onTagQuery)
    }
}

/** What the filter sheet shows, without the sheet's window (the sheet, the screenshot tests). */
@Composable
internal fun FilterSheetContent(
    config: FilterSheetConfig,
    current: BrowseFilter,
    onApply: (BrowseFilter, FilterSaves) -> Unit,
    onDismiss: () -> Unit,
    tagResults: LazyPagingItems<Tag>? = null,
    onTagQuery: (String) -> Unit = {},
) {
    var draft by remember { mutableStateOf(current) }
    var page by remember { mutableStateOf(FilterPage.Main) }
    var remember by remember { mutableStateOf(config.remembered) }
    var saveDefault by remember { mutableStateOf(false) }
    var saveFilters by remember { mutableStateOf(false) }
    val motion = Lagoon.motion
    // inside the sheet's own window, so back returns to the first page before it closes the sheet
    BackHandler(enabled = page != FilterPage.Main) { page = FilterPage.Main }
    AnimatedContent(
        targetState = page,
        transitionSpec = { if (targetState == FilterPage.Main) motion.back() else motion.forward() },
        label = "filterPage",
    ) { shown ->
        when (shown) {
            FilterPage.Main -> MainPage(
                config = config,
                draft = draft,
                onDraft = { draft = it },
                remember = remember,
                onRemember = { remember = it },
                saveDefault = saveDefault,
                onSaveDefault = { saveDefault = it },
                saveFilters = saveFilters,
                onSaveFilters = { saveFilters = it },
                onOpen = { page = it },
                onApply = {
                    val saves = FilterSaves(
                        remember = config.canRemember && remember,
                        forget = config.canRemember && config.remembered && !remember,
                        saveDefault = config.canSaveDefault && saveDefault,
                        saveFilters = config.canSaveFilters && saveFilters && (draft.tags.isNotEmpty() || draft.languages.isNotEmpty()),
                    )
                    onDismiss()
                    onApply(draft, saves)
                },
            )
            FilterPage.Languages -> LanguagesPage(
                selected = draft.languages,
                onChange = { draft = draft.copy(languages = it) },
                onBack = { page = FilterPage.Main },
            )
            FilterPage.Tags -> TagsPage(
                gameTags = config.kind == FilterKind.Games,
                selected = draft.tags,
                onChange = { draft = draft.copy(tags = it) },
                results = tagResults,
                onQuery = onTagQuery,
                onBack = { page = FilterPage.Main },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MainPage(
    config: FilterSheetConfig,
    draft: BrowseFilter,
    onDraft: (BrowseFilter) -> Unit,
    remember: Boolean,
    onRemember: (Boolean) -> Unit,
    saveDefault: Boolean,
    onSaveDefault: (Boolean) -> Unit,
    saveFilters: Boolean,
    onSaveFilters: (Boolean) -> Unit,
    onOpen: (FilterPage) -> Unit,
    onApply: () -> Unit,
) {
    val kind = config.kind
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.62f).dp
    Column {
        Text(
            stringResource(R.string.browse_filter_title), style = Lagoon.type.headline, color = Lagoon.colors.textPrimary,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp).semantics { heading() },
        )
        Column(Modifier.heightIn(max = maxHeight).verticalScroll(rememberScrollState())) {
            when (kind) {
                FilterKind.Streams -> {
                    SheetSection(stringResource(R.string.sort))
                    val sort = FilterLogic.effectiveStreamSort(draft)
                    SingleChoiceList(
                        StreamSortOptions.map { ChoiceOption(stringResource(streamSortLabel(it))) },
                        selected = StreamSortOptions.indexOf(sort).coerceAtLeast(0),
                        onSelect = { onDraft(draft.copy(sort = StreamSortOptions[it])) },
                    )
                }
                FilterKind.Videos -> {
                    SheetSection(stringResource(R.string.sort))
                    val sort = FilterLogic.effectiveVideoSort(draft)
                    SingleChoiceList(
                        VideoSorts.map { ChoiceOption(stringResource(videoSortLabel(it))) },
                        selected = VideoSorts.indexOf(sort).coerceAtLeast(0),
                        onSelect = { onDraft(draft.copy(sort = VideoSorts[it])) },
                    )
                }
                else -> Unit
            }
            if ((kind == FilterKind.Videos && config.showPeriod) || kind == FilterKind.Clips) {
                SheetSection(stringResource(R.string.period))
                val period = FilterLogic.effectivePeriod(draft)
                SingleChoiceList(
                    Periods.map { ChoiceOption(stringResource(periodLabel(it))) },
                    selected = Periods.indexOf(period).coerceAtLeast(0),
                    onSelect = { onDraft(draft.copy(period = Periods[it])) },
                )
            }
            if (kind == FilterKind.Videos) {
                SheetSection(stringResource(R.string.type))
                val type = FilterLogic.effectiveType(draft)
                SingleChoiceList(
                    VideoTypes.map { ChoiceOption(stringResource(typeLabel(it))) },
                    selected = VideoTypes.indexOf(type).coerceAtLeast(0),
                    onSelect = { onDraft(draft.copy(type = VideoTypes[it])) },
                )
            }
            Column(Modifier.padding(horizontal = 6.dp, vertical = 6.dp)) {
                if (kind != FilterKind.Games && config.showLanguages) {
                    SettingRow(
                        stringResource(R.string.languages),
                        icon = LucideIcons.Languages,
                        trailing = SettingTrailing.Value(languagesSummary(draft.languages)),
                        onClick = { onOpen(FilterPage.Languages) },
                    )
                }
                if (kind == FilterKind.Streams || kind == FilterKind.Games) {
                    SettingRow(
                        stringResource(R.string.browse_filter_tags),
                        icon = LucideIcons.Tag,
                        trailing = SettingTrailing.Value(
                            if (draft.tags.isEmpty()) stringResource(R.string.browse_filter_no_tags) else draft.tags.size.toString()
                        ),
                        onClick = { onOpen(FilterPage.Tags) },
                    )
                    if (draft.tags.isNotEmpty()) {
                        FlowRow(
                            Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(LagoonSpacing.xs),
                        ) {
                            draft.tags.forEach { tag ->
                                FilterChip(FilterChipItem(tag.name, selected = true, icon = LucideIcons.X, onClick = {
                                    onDraft(draft.copy(tags = draft.tags - tag))
                                }))
                            }
                        }
                    }
                }
                if (config.canRemember) {
                    SettingRow(
                        stringResource(if (config.rememberForChannel) R.string.save_sort_channel else R.string.save_sort_game),
                        description = stringResource(
                            if (config.rememberForChannel) R.string.channel_filter_remember_description else R.string.browse_filter_remember_description
                        ),
                        icon = LucideIcons.Pin,
                        trailing = SettingTrailing.Toggle(remember, onRemember),
                    )
                }
                if (config.canSaveDefault) {
                    SettingRow(
                        stringResource(R.string.save_default),
                        description = stringResource(
                            if (kind == FilterKind.Streams && !config.canRemember) R.string.browse_filter_default_top_description
                            else if (config.rememberForChannel) R.string.channel_filter_default_description
                            else R.string.browse_filter_default_description
                        ),
                        icon = LucideIcons.Star,
                        trailing = SettingTrailing.Toggle(saveDefault, onSaveDefault),
                    )
                }
                if (config.canSaveFilters) {
                    val hasFilters = draft.tags.isNotEmpty() || draft.languages.isNotEmpty()
                    SettingRow(
                        stringResource(R.string.save_filters),
                        description = stringResource(R.string.browse_filter_save_filters_description),
                        icon = LucideIcons.Bookmark,
                        enabled = hasFilters,
                        trailing = SettingTrailing.Toggle(saveFilters && hasFilters, onSaveFilters),
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(LagoonSpacing.s),
        ) {
            LagoonButton(
                stringResource(R.string.browse_filter_reset),
                { onDraft(BrowseFilter()) },
                Modifier.weight(1f),
                style = ButtonStyle.Secondary,
            )
            LagoonButton(stringResource(R.string.apply), onApply, Modifier.weight(1f).testTag(FilterApplyTag))
        }
    }
}

@Composable
private fun SubPageHeader(title: String, onBack: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        LagoonIconButton(LucideIcons.ArrowLeft, stringResource(R.string.browse_back), onBack, filled = false)
        Text(
            title, style = Lagoon.type.headline, color = Lagoon.colors.textPrimary,
            modifier = Modifier.weight(1f).padding(start = 4.dp).semantics { heading() },
        )
        trailing?.invoke()
    }
}

@Composable
private fun LanguagesPage(selected: List<String>, onChange: (List<String>) -> Unit, onBack: () -> Unit) {
    val names = stringArrayResource(R.array.gqlUserLanguageEntries)
    val codes = stringArrayResource(R.array.gqlUserLanguageValues)
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.7f).dp
    Column {
        SubPageHeader(stringResource(R.string.languages), onBack) {
            if (selected.isNotEmpty()) {
                LagoonButton(stringResource(R.string.browse_filter_clear), { onChange(emptyList()) }, style = ButtonStyle.Secondary)
            }
        }
        MultiChoiceList(
            options = names.map { ChoiceOption(it) },
            selected = codes.indices.filter { codes[it] in selected }.toSet(),
            onToggle = { index, on ->
                val code = codes[index]
                onChange(if (on) (selected + code).distinct().sorted() else selected - code)
            },
            modifier = Modifier.heightIn(max = maxHeight).verticalScroll(rememberScrollState()),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagsPage(
    gameTags: Boolean,
    selected: List<FilterTag>,
    onChange: (List<FilterTag>) -> Unit,
    results: LazyPagingItems<Tag>?,
    onQuery: (String) -> Unit,
    onBack: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    // v0.1 searched 750 ms after the last key; the sheet answers sooner
    LaunchedEffect(query) {
        if (query.isNotBlank()) delay(300)
        onQuery(query)
    }
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.5f).dp
    Column(Modifier.imePadding()) {
        SubPageHeader(stringResource(R.string.browse_filter_tags), onBack)
        LagoonTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = stringResource(R.string.search_tags),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).focusRequester(focus).testTag(TagSearchFieldTag),
        )
        if (selected.isNotEmpty()) {
            FlowRow(
                Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(LagoonSpacing.xs),
            ) {
                selected.forEach { tag ->
                    FilterChip(FilterChipItem(tag.name, selected = true, icon = LucideIcons.X, onClick = { onChange(selected - tag) }))
                }
            }
        }
        val list = results
        when {
            list == null || query.isBlank() -> Text(
                stringResource(R.string.browse_filter_tags_hint), style = Lagoon.type.label, color = Lagoon.colors.textSecondary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            )
            list.loadState.refresh is LoadState.Loading && list.itemCount == 0 -> Column(
                Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                repeat(4) { Box(Modifier.fillMaxWidth().heightIn(min = 44.dp).shimmer(LagoonShapes.row)) }
            }
            list.loadState.refresh is LoadState.Error && list.itemCount == 0 -> Text(
                stringResource(R.string.browse_filter_tags_error), style = Lagoon.type.label, color = Lagoon.colors.danger,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            )
            list.itemCount == 0 -> Text(
                stringResource(R.string.browse_filter_tags_none), style = Lagoon.type.label, color = Lagoon.colors.textSecondary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            )
            else -> LazyColumn(
                Modifier.heightIn(max = maxHeight),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            ) {
                items(list.itemCount) { index ->
                    val tag = list[index] ?: return@items
                    val name = tag.name ?: return@items
                    // game tags filter by id, stream tags by name (v0.1 SearchTagsDialog)
                    if (gameTags && tag.id == null) return@items
                    val item = FilterTag(if (gameTags) tag.id else null, name)
                    val chosen = selected.any { if (gameTags) it.id == item.id else it.name == item.name }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .pressable(onClick = {
                                onChange(if (chosen) selected.filterNot { if (gameTags) it.id == item.id else it.name == item.name } else selected + item)
                            }, shape = LagoonShapes.row, scaleOnPress = false)
                            .heightIn(min = 48.dp)
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(LucideIcons.Hash, contentDescription = null, tint = Lagoon.colors.textSecondary, modifier = Modifier.padding(end = 12.dp))
                        Text(name, style = Lagoon.type.body, color = Lagoon.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        if (chosen) Icon(LucideIcons.Check, contentDescription = null, tint = Lagoon.colors.accent)
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetSection(text: String) {
    Text(
        text, style = Lagoon.type.labelStrong, color = Lagoon.colors.textSecondary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp).semantics { heading() },
    )
}
