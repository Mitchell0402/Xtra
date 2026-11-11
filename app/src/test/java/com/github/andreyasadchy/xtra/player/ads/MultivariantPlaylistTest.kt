package com.github.andreyasadchy.xtra.player.ads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MultivariantPlaylistTest {

    // shape of a Twitch usher response (urls shortened)
    private val playlist = """
        #EXTM3U
        #EXT-X-TWITCH-INFO:NODE="video-edge-1",MANIFEST-NODE-TYPE="weaver_cluster"
        #EXT-X-MEDIA:TYPE=VIDEO,GROUP-ID="chunked",NAME="1080p60 (source)",AUTOSELECT=YES,DEFAULT=YES
        #EXT-X-STREAM-INF:BANDWIDTH=8000000,RESOLUTION=1920x1080,CODECS="avc1.64002A,mp4a.40.2",VIDEO="chunked",FRAME-RATE=60.000,STABLE-VARIANT-ID="source"
        https://video-weaver.example.hls.ttvnw.net/v1/playlist/source.m3u8
        #EXT-X-MEDIA:TYPE=VIDEO,GROUP-ID="720p60",NAME="720p60",AUTOSELECT=YES,DEFAULT=YES
        #EXT-X-STREAM-INF:BANDWIDTH=3400000,RESOLUTION=1280x720,CODECS="avc1.4D401F,mp4a.40.2",VIDEO="720p60",FRAME-RATE=60.000,STABLE-VARIANT-ID="720p60"
        https://video-weaver.example.hls.ttvnw.net/v1/playlist/720p60.m3u8
        #EXT-X-MEDIA:TYPE=VIDEO,GROUP-ID="audio_only",NAME="audio_only",AUTOSELECT=NO,DEFAULT=NO
        #EXT-X-STREAM-INF:BANDWIDTH=160000,CODECS="mp4a.40.2",VIDEO="audio_only",STABLE-VARIANT-ID="audio_only"
        https://video-weaver.example.hls.ttvnw.net/v1/playlist/audio.m3u8
    """.trimIndent()

    @Test
    fun readsVariants() {
        val variants = MultivariantPlaylist.variants(playlist)
        assertEquals(listOf("source", "720p60", "audio_only"), variants.map { it.name })
        assertEquals(1080, variants[0].height)
        assertEquals(60f, variants[0].frameRate)
        assertEquals("avc1.64002A,mp4a.40.2", variants[0].codecs)
    }

    @Test
    fun picksSource() {
        assertEquals("https://video-weaver.example.hls.ttvnw.net/v1/playlist/source.m3u8", MultivariantPlaylist.bestVariantUrl(playlist))
    }

    @Test
    fun picksSelectedQuality() {
        assertEquals("https://video-weaver.example.hls.ttvnw.net/v1/playlist/720p60.m3u8", MultivariantPlaylist.bestVariantUrl(playlist, maxHeight = 720))
        assertEquals("https://video-weaver.example.hls.ttvnw.net/v1/playlist/720p60.m3u8", MultivariantPlaylist.bestVariantUrl(playlist, maxHeight = 900))
        // lower than anything offered: the lowest
        assertEquals("https://video-weaver.example.hls.ttvnw.net/v1/playlist/720p60.m3u8", MultivariantPlaylist.bestVariantUrl(playlist, maxHeight = 160))
    }

    @Test
    fun picksHighestWithoutSource() {
        // android/autoplay tokens only list low qualities
        val autoplay = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=630000,RESOLUTION=640x360,VIDEO="360p30",FRAME-RATE=30.000,STABLE-VARIANT-ID="360p30"
            https://example.com/360.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=230000,RESOLUTION=284x160,VIDEO="160p30",FRAME-RATE=30.000,STABLE-VARIANT-ID="160p30"
            https://example.com/160.m3u8
        """.trimIndent()
        assertEquals("https://example.com/360.m3u8", MultivariantPlaylist.bestVariantUrl(autoplay))
        assertNull(MultivariantPlaylist.bestVariantUrl("#EXTM3U"))
    }
}
