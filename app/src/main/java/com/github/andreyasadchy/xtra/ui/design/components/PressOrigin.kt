package com.github.andreyasadchy.xtra.ui.design.components

import android.os.SystemClock
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.TraversableNode
import androidx.compose.ui.node.requireLayoutCoordinates
import androidx.compose.ui.node.traverseDescendants
import androidx.compose.ui.platform.InspectorInfo

/**
 * Where the last tap on a card happened (motion plan M2a item 3): the player expands from that card's picture.
 *
 * Every [pressable] records its own bounds on click, or better the bounds of the picture inside it when the card
 * marks one with [pressOriginMedia] (the thumbnail of a live, video or clip card), together with the picture's image
 * model so the player can show the same picture while it opens. The slot lives for [MaxAgeMs]; the player takes it
 * once ([take]) and every other entry (link, notification, a page without cards) finds it empty or expired and
 * rises from the bottom instead.
 */
object PressOrigin {

    /** A tap older than this did not open the player (the player opens in the same click handler). */
    const val MaxAgeMs = 500L

    /** One recorded tap: the rectangle in window pixels, the picture's image model (a URL), and when. */
    data class Tap(val bounds: Rect, val model: Any?, val atMs: Long)

    private var last: Tap? = null

    fun record(bounds: Rect, model: Any?, nowMs: Long = SystemClock.uptimeMillis()) {
        last = Tap(bounds, model, nowMs)
    }

    /**
     * The tap that opened the player, or null when there was none within [MaxAgeMs] (a link, a notification, a raid).
     * Always empties the slot. What the tap is used for is decided by [com.github.andreyasadchy.xtra.ui.player.PlayerArrivalRules]:
     * with a player already on the screen it never turns into an expansion (the raid bar and "watch full video" are
     * pressables too).
     */
    fun take(nowMs: Long = SystemClock.uptimeMillis()): Tap? {
        val tap = last
        last = null
        if (tap == null) return null
        if (nowMs - tap.atMs !in 0..MaxAgeMs) return null
        if (tap.bounds.width <= 0f || tap.bounds.height <= 0f) return null
        return tap
    }

    /** Tests: forget the last tap. */
    fun clear() {
        last = null
    }

    internal const val MediaKey = "lagoon.pressOriginMedia"
}

/**
 * Marks the picture of a card: a tap on the surrounding [pressable] records this picture's bounds and [model]
 * instead of the whole card (motion plan M2a item 3).
 */
fun Modifier.pressOriginMedia(model: Any?): Modifier = this then PressOriginMediaElement(model)

private data class PressOriginMediaElement(val model: Any?) : ModifierNodeElement<PressOriginMediaNode>() {
    override fun create() = PressOriginMediaNode(model)
    override fun update(node: PressOriginMediaNode) {
        node.model = model
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "pressOriginMedia"
    }
}

internal class PressOriginMediaNode(var model: Any?) : Modifier.Node(), TraversableNode {
    override val traverseKey: Any get() = PressOrigin.MediaKey
}

/** Holder the click handler of a [pressable] reads its node through (one per pressable, remembered). */
internal class PressOriginHandle {
    var node: PressOriginNode? = null

    /** Records the tap: the marked picture inside this pressable if there is one, else the pressable itself. */
    fun record() {
        val node = node?.takeIf { it.isAttached } ?: return
        var media: PressOriginMediaNode? = null
        node.traverseDescendants(PressOrigin.MediaKey) {
            media = it as? PressOriginMediaNode
            TraversableNode.Companion.TraverseDescendantsAction.CancelTraversal
        }
        val target = media?.takeIf { it.isAttached }
        val bounds = (target ?: node).requireLayoutCoordinates().takeIf { it.isAttached }?.boundsInWindow() ?: return
        PressOrigin.record(bounds, target?.model)
    }
}

internal class PressOriginElement(private val handle: PressOriginHandle) : ModifierNodeElement<PressOriginNode>() {
    override fun create() = PressOriginNode(handle)
    override fun update(node: PressOriginNode) {
        node.setHandle(handle)
    }

    override fun equals(other: Any?) = other is PressOriginElement && other.handle === handle
    override fun hashCode() = System.identityHashCode(handle)
    override fun InspectorInfo.inspectableProperties() {
        name = "pressOrigin"
    }
}

internal class PressOriginNode(private var handle: PressOriginHandle) : Modifier.Node() {
    fun setHandle(new: PressOriginHandle) {
        if (handle.node === this) handle.node = null
        handle = new
        if (isAttached) new.node = this
    }

    override fun onAttach() {
        handle.node = this
    }

    override fun onDetach() {
        if (handle.node === this) handle.node = null
    }
}
