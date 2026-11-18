package com.github.andreyasadchy.xtra.ui.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.ui.design.theme.Lagoon
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonShapes
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private val WheelRow = 44.dp

/**
 * A time of day as two scroll wheels (hours, minutes), plus an AM/PM segmented control in 12-hour mode (U9 A1,
 * replaces Material's clock picker). The value is minutes since midnight; [is24Hour] only changes how it is shown.
 *
 * @param visibleRows rows of each wheel on screen (odd); 3 on short windows so the whole page fits without scrolling.
 */
@Composable
fun LagoonTimeWheel(
    minuteOfDay: Int,
    onChange: (Int) -> Unit,
    is24Hour: Boolean,
    amPm: Pair<String, String>,
    modifier: Modifier = Modifier,
    visibleRows: Int = 5,
) {
    val hour24 = minuteOfDay / 60
    val minute = minuteOfDay % 60
    val latest by rememberUpdatedState(minuteOfDay)
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (is24Hour) {
                WheelColumn(
                    values = (0..23).map { "%02d".format(it) }, selected = hour24,
                    onSelect = { onChange(it * 60 + latest % 60) }, label = stringResource(R.string.pl_sleep_at_hours),
                    visibleRows = visibleRows, modifier = Modifier.testTag("wheelHours"),
                )
            } else {
                val hour12 = TimeWheelLogic.to12(hour24).first
                WheelColumn(
                    values = (1..12).map { it.toString() }, selected = hour12 - 1,
                    onSelect = { onChange(TimeWheelLogic.to24(it + 1, latest / 60 >= 12) * 60 + latest % 60) },
                    label = stringResource(R.string.pl_sleep_at_hours), visibleRows = visibleRows, modifier = Modifier.testTag("wheelHours"),
                )
            }
            Text(":", style = Lagoon.type.title, color = Lagoon.colors.textSecondary, modifier = Modifier.padding(horizontal = 8.dp))
            WheelColumn(
                values = (0..59).map { "%02d".format(it) }, selected = minute,
                onSelect = { onChange(latest / 60 * 60 + it) }, label = stringResource(R.string.pl_sleep_at_minutes),
                visibleRows = visibleRows, modifier = Modifier.testTag("wheelMinutes"),
            )
        }
        if (!is24Hour) {
            SegmentedControl(
                options = listOf(amPm.first, amPm.second),
                selectedIndex = if (hour24 >= 12) 1 else 0,
                onSelect = { pm -> onChange(TimeWheelLogic.to24(TimeWheelLogic.to12(latest / 60).first, pm == 1) * 60 + latest % 60) },
                modifier = Modifier.width(200.dp).testTag("wheelAmPm"),
            )
        }
    }
}

/** One wheel: a snapping list whose centre row is the value; tap a row to move to it. */
@Composable
private fun WheelColumn(
    values: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    label: String,
    visibleRows: Int,
    modifier: Modifier = Modifier,
) {
    val colors = Lagoon.colors
    val state = rememberLazyListState(initialFirstVisibleItemIndex = selected)
    val scope = rememberCoroutineScope()
    val onSelectNow by rememberUpdatedState(onSelect)
    val centre by remember {
        derivedStateOf {
            val info = state.layoutInfo
            val mid = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - mid) }?.index ?: selected
        }
    }
    // the wheel reports what is in the middle while it moves, so the summary line follows the finger
    LaunchedEffect(state) {
        snapshotFlow { centre }.distinctUntilChanged().collect { if (state.isScrollInProgress) onSelectNow(it) }
    }
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }.collect { scrolling -> if (!scrolling) onSelectNow(centre) }
    }
    // a change from outside (AM/PM switch, a test) moves the wheel
    LaunchedEffect(selected) {
        if (!state.isScrollInProgress && centre != selected) state.scrollToItem(selected)
    }
    val pad: Dp = WheelRow * (visibleRows / 2)
    val increase = stringResource(R.string.pl_wheel_increase)
    val decrease = stringResource(R.string.pl_wheel_decrease)
    Box(
        modifier
            .width(88.dp)
            .height(WheelRow * visibleRows)
            .clearAndSetSemantics {
                contentDescription = label
                stateDescription = values[selected]
                progressBarRangeInfo = ProgressBarRangeInfo(selected.toFloat(), 0f..(values.size - 1).toFloat(), values.size - 2)
                setProgress { v ->
                    val i = v.roundToInt().coerceIn(0, values.size - 1)
                    scope.launch { state.scrollToItem(i) }
                    onSelectNow(i)
                    true
                }
                customActions = listOf(
                    CustomAccessibilityAction(increase) {
                        val i = (selected + 1).coerceAtMost(values.size - 1)
                        scope.launch { state.scrollToItem(i) }; onSelectNow(i); true
                    },
                    CustomAccessibilityAction(decrease) {
                        val i = (selected - 1).coerceAtLeast(0)
                        scope.launch { state.scrollToItem(i) }; onSelectNow(i); true
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.fillMaxWidth().height(WheelRow).background(colors.control, LagoonShapes.input))
        LazyColumn(
            state = state,
            flingBehavior = rememberSnapFlingBehavior(state),
            contentPadding = PaddingValues(vertical = pad),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().height(WheelRow * visibleRows),
        ) {
            items(values.size) { i ->
                val isCentre = i == centre
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(WheelRow)
                        .clickable(remember { MutableInteractionSource() }, indication = null) {
                            scope.launch { state.animateScrollToItem(i) }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        values[i],
                        style = if (isCentre) Lagoon.type.title else Lagoon.type.body,
                        color = if (isCentre) colors.textPrimary else colors.textHint,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** The arithmetic of [LagoonTimeWheel] and the "at a set time" sleep timer, kept apart for tests. */
object TimeWheelLogic {
    /** 0-23 to 12-hour clock: (1-12, pm). Midnight is 12 AM, noon 12 PM. */
    fun to12(hour24: Int): Pair<Int, Boolean> = (if (hour24 % 12 == 0) 12 else hour24 % 12) to (hour24 >= 12)

    /** 12-hour clock (1-12) back to 0-23: 12 AM is 0, 12 PM is 12. */
    fun to24(hour12: Int, pm: Boolean): Int = hour12 % 12 + if (pm) 12 else 0

    /**
     * Whole minutes from [nowMinuteOfDay] to the next [targetMinuteOfDay], 0 when they are the same minute (as v0.1's
     * TwitchApiHelper.getMinutesLeft). 0 must not reach the timer, which reads it as "cancel".
     */
    fun minutesUntil(nowMinuteOfDay: Int, targetMinuteOfDay: Int): Int = ((targetMinuteOfDay - nowMinuteOfDay) % 1440 + 1440) % 1440
}
