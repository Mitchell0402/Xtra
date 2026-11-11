package com.github.andreyasadchy.xtra.player.ads

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun master(id: String, height: Int) = """
    #EXTM3U
    #EXT-X-STREAM-INF:BANDWIDTH=1000000,RESOLUTION=${height * 16 / 9}x$height,VIDEO="$id",STABLE-VARIANT-ID="${height}p"
    https://example.com/$id/media.m3u8
""".trimIndent()

private fun media(ads: Boolean) = buildString {
    appendLine("#EXTM3U")
    appendLine("#EXT-X-PROGRAM-DATE-TIME:2026-11-11T16:00:00.000Z")
    repeat(10) { appendLine("#EXTINF:2.000,live"); appendLine("https://example.com/s$it.ts") }
    if (ads) {
        repeat(3) { appendLine("#EXTINF:2.000,Amazon|123"); appendLine("https://example.com/ad$it.ts") }
    }
}

class StartupRaceTest {

    /** id -> (delay ms, height, ads); a missing id fails. */
    private class Server(val streams: Map<String, Triple<Long, Int, Boolean>>) {
        val requested = mutableListOf<String>()

        suspend fun fetch(url: String): String? {
            val id = url.removePrefix("https://example.com/").substringBefore('/')
            val stream = streams[id] ?: return null
            synchronized(requested) { requested.add(url) }
            delay(stream.first)
            return if (url.endsWith("master.m3u8")) master(id, stream.second) else media(stream.third)
        }
    }

    private fun candidate(id: String, priority: Int) =
        StartupRace.Candidate(id, priority, { "https://example.com/$id/master.m3u8" })

    @Test
    fun preferredCleanCandidateWinsWithoutWaitingForSlowerOnes() = runBlocking {
        val server = Server(mapOf("proxy" to Triple(10L, 1080, false), "direct" to Triple(3000L, 1080, false)))
        val start = System.currentTimeMillis()
        val (winner, _) = StartupRace(server::fetch).run(listOf(candidate("proxy", 0), candidate("direct", 1)))
        assertEquals("proxy", winner?.candidate?.label)
        assertTrue(System.currentTimeMillis() - start < 1000)
    }

    @Test
    fun cleanBeatsPreferredWithAds() = runBlocking {
        val server = Server(mapOf("proxy" to Triple(10L, 1080, true), "direct" to Triple(50L, 1080, false)))
        val (winner, results) = StartupRace(server::fetch).run(listOf(candidate("proxy", 0), candidate("direct", 1)))
        assertEquals("direct", winner?.candidate?.label)
        assertEquals(2, results.size)
    }

    @Test
    fun waitsForPreferredCandidateWhenAWorseOneIsCleanFirst() = runBlocking {
        val server = Server(mapOf("proxy" to Triple(200L, 1080, false), "direct" to Triple(10L, 1080, false)))
        val (winner, _) = StartupRace(server::fetch).run(listOf(candidate("proxy", 0), candidate("direct", 1)))
        assertEquals("proxy", winner?.candidate?.label)
    }

    @Test
    fun lowQualityCleanLosesToGoodQuality() = runBlocking {
        val server = Server(mapOf("autoplay" to Triple(10L, 360, false), "direct" to Triple(50L, 1080, false)))
        val (winner, _) = StartupRace(server::fetch).run(listOf(candidate("autoplay", 0), candidate("direct", 1)))
        assertEquals("direct", winner?.candidate?.label)
    }

    @Test
    fun deadProxiesAreSkippedAndAdsAreBetterThanNothing() = runBlocking {
        val server = Server(mapOf("direct" to Triple(10L, 1080, true)))
        val (winner, results) = StartupRace(server::fetch).run(
            listOf(candidate("dead1", 0), candidate("dead2", 1), candidate("direct", 2))
        )
        assertEquals("direct", winner?.candidate?.label)
        assertEquals(true, winner?.ads)
        assertEquals(2, results.count { !it.ok })
    }

    @Test
    fun timeoutUsesWhatAnswered() = runBlocking {
        val server = Server(mapOf("slow" to Triple(5000L, 1080, false), "direct" to Triple(10L, 1080, true)))
        val (winner, _) = StartupRace(server::fetch, timeoutMs = 500).run(listOf(candidate("slow", 0), candidate("direct", 1)))
        assertEquals("direct", winner?.candidate?.label)
    }

    @Test
    fun nothingWorks() = runBlocking {
        val (winner, _) = StartupRace({ null }).run(listOf(candidate("a", 0)))
        assertNull(winner)
        assertNull(StartupRace({ null }).run(emptyList()).first)
    }
}
