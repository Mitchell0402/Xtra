package com.github.andreyasadchy.xtra.player.ads

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Layer 1: picks the stream to start with. Every candidate (custom proxies, the direct token,
 * the token through a stream proxy) is tried at the same time instead of one after another:
 * multivariant playlist, then the media playlist of its best variant, checked for ads near the
 * live edge. The winner is the first candidate, in [Candidate.priority] order, that answered
 * without ads at 480p or more; a candidate that still runs only loses to a better one that is
 * already clean and good. When nothing is clean the best working candidate is used and the
 * other layers deal with the ad.
 */
class StartupRace(
    private val fetch: suspend (url: String) -> String?,
    private val timeoutMs: Long = 4000,
) {
    data class Candidate(
        val label: String,
        /** Lower is preferred. */
        val priority: Int,
        /** The multivariant playlist url, or null when it could not be made (token request failed). */
        val resolve: suspend () -> String?,
        /** Opaque to the race, passed back with the winner. */
        val tag: Any? = null,
    )

    data class Result(
        val candidate: Candidate,
        val url: String?,
        val ads: Boolean,
        val maxHeight: Int,
        val error: String? = null,
        val elapsedMs: Long = 0,
    ) {
        val ok get() = url != null && error == null
        val good get() = ok && !ads && maxHeight >= GOOD_HEIGHT
    }

    suspend fun run(candidates: List<Candidate>): Pair<Result?, List<Result>> = coroutineScope {
        val results = mutableListOf<Result>()
        if (candidates.isEmpty()) {
            return@coroutineScope null to results
        }
        val channel = Channel<Result>(Channel.UNLIMITED)
        val start = System.currentTimeMillis()
        val jobs = candidates.map { candidate ->
            launch {
                channel.send(probe(candidate).copy(elapsedMs = System.currentTimeMillis() - start))
            }
        }
        val pending = candidates.toMutableSet()
        withTimeoutOrNull(timeoutMs) {
            while (pending.isNotEmpty()) {
                val result = channel.receive()
                results.add(result)
                pending.remove(result.candidate)
                val best = results.filter { it.good }.minByOrNull { it.candidate.priority }
                if (best != null && pending.none { it.priority < best.candidate.priority }) {
                    break
                }
            }
        }
        jobs.forEach { it.cancel() }
        pick(results) to results.toList()
    }

    private suspend fun probe(candidate: Candidate): Result {
        return try {
            val url = candidate.resolve() ?: return Result(candidate, null, false, 0, "no url")
            val master = fetch(url) ?: return Result(candidate, null, false, 0, "multivariant playlist failed")
            val variants = MultivariantPlaylist.variants(master)
            val maxHeight = variants.maxOfOrNull { it.height ?: 0 } ?: 0
            val variantUrl = MultivariantPlaylist.bestVariantUrl(master)?.let { java.net.URI(url).resolve(it).toString() }
                ?: return Result(candidate, null, false, maxHeight, "no variant")
            val media = fetch(variantUrl) ?: return Result(candidate, null, false, maxHeight, "media playlist failed")
            Result(candidate, url, AdDetector.hasAdsNearLiveEdge(AdPlaylist.parse(media)), maxHeight)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result(candidate, null, false, 0, e.toString())
        }
    }

    companion object {
        const val GOOD_HEIGHT = 480

        /** Clean and 480p or more, then clean, then working; each by priority. */
        fun pick(results: List<Result>): Result? {
            val ok = results.filter { it.ok }.sortedBy { it.candidate.priority }
            return ok.firstOrNull { it.good } ?: ok.firstOrNull { !it.ads } ?: ok.firstOrNull()
        }
    }
}
