package com.github.andreyasadchy.xtra.ui.browse

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.assertIsDisplayed
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.andreyasadchy.xtra.model.ui.Game
import com.github.andreyasadchy.xtra.model.ui.Stream
import com.github.andreyasadchy.xtra.ui.design.theme.LagoonTheme
import com.github.andreyasadchy.xtra.ui.following.FollowingFixtures
import com.github.andreyasadchy.xtra.ui.games.GamesGridTag
import com.github.andreyasadchy.xtra.ui.games.GamesScreen
import com.github.andreyasadchy.xtra.ui.top.TopStreamsListTag
import com.github.andreyasadchy.xtra.ui.top.TopStreamsScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Plan 10.6 U4: "paging to the third page loads no duplicates and misses nothing". The pages get the same paging
 * configs as the view models (30 a page) from a source with three pages and then the end, and are scrolled down
 * until the end; every item must be there once and in order.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h900dp")
class BrowsePagingTest {
    @get:Rule
    val compose = createComposeRule()

    private class ThreePages<T : Any>(private val make: (Int) -> T) : PagingSource<Int, T>() {
        val loads = mutableListOf<Int>()
        override fun getRefreshKey(state: PagingState<Int, T>): Int? = null
        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, T> {
            val page = params.key ?: 0
            loads += page
            return LoadResult.Page(
                data = (0 until 30).map { make(page * 30 + it) },
                prevKey = null,
                nextKey = if (page < 2) page + 1 else null,
            )
        }
    }

    private fun scrollToEnd(tag: String, state: () -> LazyGridState, items: () -> LazyPagingItems<*>) {
        for (attempt in 0 until 40) {
            compose.waitForIdle()
            val last = state().layoutInfo.totalItemsCount - 1
            compose.onNodeWithTag(tag).performScrollToIndex(last.coerceAtLeast(0))
            compose.waitForIdle()
            if (items().itemCount == 90 && items().loadState.append.endOfPaginationReached) break
        }
        compose.onNodeWithTag(tag).performScrollToIndex(state().layoutInfo.totalItemsCount - 1)
        compose.waitForIdle()
    }

    @Test
    fun theGamesGridLoadsThreePagesWithoutGapsOrRepeats() {
        val source = ThreePages { Game(id = "g$it", name = "Game $it") }
        lateinit var items: LazyPagingItems<Game>
        lateinit var grid: LazyGridState
        compose.setContent {
            LagoonTheme(reducedMotion = true) {
                // GamesViewModel's config
                items = remember { Pager(PagingConfig(pageSize = 30, prefetchDistance = 10, initialLoadSize = 30)) { source }.flow }.collectAsLazyPagingItems()
                grid = rememberLazyGridState()
                GamesScreen(
                    root = true, games = items, filter = BrowseFilter(), options = FollowingFixtures.options.copy(image = { null }),
                    actions = RecordingBrowseActions(), grid = grid,
                )
            }
        }
        scrollToEnd(GamesGridTag, { grid }, { items })
        assertEquals((0 until 90).map { "g$it" }, items.itemSnapshotList.items.map { it.id })
        assertEquals(listOf(0, 1, 2), source.loads)
        compose.onNodeWithText("Game 89").assertIsDisplayed()
    }

    @Test
    fun theStreamListLoadsThreePagesWithoutGapsOrRepeats() {
        val source = ThreePages { Stream(id = "s$it", channelLogin = "c$it", channelName = "Channel $it", title = "Stream $it", viewerCount = 1000 - it) }
        lateinit var items: LazyPagingItems<Stream>
        lateinit var grid: LazyGridState
        compose.setContent {
            LagoonTheme(reducedMotion = true) {
                // GameStreamsViewModel's and TopStreamsViewModel's config for full cards
                items = remember { Pager(PagingConfig(pageSize = 30, prefetchDistance = 3, initialLoadSize = 30)) { source }.flow }.collectAsLazyPagingItems()
                TopStreamsScreen(
                    root = true, streams = items, filter = BrowseFilter(), options = FollowingFixtures.options.copy(image = { null }),
                    compactStreams = false, actions = RecordingBrowseActions(), onApplyFilter = { _, _ -> },
                    gridState = rememberLazyGridState().also { grid = it },
                )
            }
        }
        scrollToEnd(TopStreamsListTag, { grid }, { items })
        assertEquals((0 until 90).map { "s$it" }, items.itemSnapshotList.items.map { it.id })
        assertEquals(listOf(0, 1, 2), source.loads)
    }
}
