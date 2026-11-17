package com.github.andreyasadchy.xtra.ui.design

import androidx.compose.ui.unit.dp
import com.github.andreyasadchy.xtra.ui.design.components.sheetMaxHeight
import org.junit.Assert.assertEquals
import org.junit.Test

/** U9 C2 (plan gate 4): the height a sheet may take, below the top safe area and above the keyboard. */
class SheetHeightTest {

    @Test
    fun phonePortrait() = assertEquals(859.dp, sheetMaxHeight(windowHeight = 915.dp, topInset = 48.dp, imeInset = 0.dp))

    @Test
    fun phoneLandscapeUnderTheStatusBar() = assertEquals(371.dp, sheetMaxHeight(windowHeight = 411.dp, topInset = 32.dp, imeInset = 0.dp))

    @Test
    fun tabletLandscape() = assertEquals(768.dp, sheetMaxHeight(windowHeight = 800.dp, topInset = 24.dp, imeInset = 0.dp))

    @Test
    fun keyboardOpen() = assertEquals(559.dp, sheetMaxHeight(windowHeight = 915.dp, topInset = 48.dp, imeInset = 300.dp))

    @Test
    fun neverCollapsesCompletely() = assertEquals(120.dp, sheetMaxHeight(windowHeight = 411.dp, topInset = 32.dp, imeInset = 300.dp))
}
