package com.github.andreyasadchy.xtra.player.ads

import com.github.andreyasadchy.xtra.util.C

/**
 * One-tap settings for the ad blocking page. Every preset keeps the gap filter and hiding on,
 * so an ad is never shown; they differ in which sources may replace the stream during an ad.
 */
enum class AdBlockPreset(val key: String, val values: Map<String, Boolean>) {
    /** Every source, including the public playlist proxies (they see which channel you watch). */
    AGGRESSIVE("aggressive", mapOf(
        C.PLAYER_USE_CUSTOM_PROXY to true,
        C.PLAYER_USE_STREAM_PROXY to false,
        C.PLAYER_USE_VIDEO_SWAP to true,
        C.PLAYER_FILTER_AD_SEGMENTS to true,
        C.PLAYER_HIDE_ADS to true,
    )),
    /** Twitch only: clean player types during ads, no third party proxies. */
    BALANCED("balanced", mapOf(
        C.PLAYER_USE_CUSTOM_PROXY to false,
        C.PLAYER_USE_STREAM_PROXY to false,
        C.PLAYER_USE_VIDEO_SWAP to true,
        C.PLAYER_FILTER_AD_SEGMENTS to true,
        C.PLAYER_HIDE_ADS to true,
    )),
    /** No replacement stream: the ad is filtered out and the player waits. */
    HIDE_ONLY("hide_only", mapOf(
        C.PLAYER_USE_CUSTOM_PROXY to false,
        C.PLAYER_USE_STREAM_PROXY to false,
        C.PLAYER_USE_VIDEO_SWAP to false,
        C.PLAYER_FILTER_AD_SEGMENTS to true,
        C.PLAYER_HIDE_ADS to true,
    ));

    companion object {
        const val CUSTOM = "custom"

        /** The preset the current switches match, or [CUSTOM]. */
        fun match(get: (key: String, default: Boolean) -> Boolean): String {
            val defaults = AGGRESSIVE.values.mapValues { (key, _) -> defaultOf(key) }
            return entries.firstOrNull { preset ->
                preset.values.all { (key, value) -> get(key, defaults.getValue(key)) == value }
            }?.key ?: CUSTOM
        }

        fun defaultOf(key: String) = when (key) {
            C.PLAYER_USE_STREAM_PROXY -> false
            else -> true
        }
    }
}
