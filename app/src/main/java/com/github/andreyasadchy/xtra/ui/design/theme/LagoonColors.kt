package com.github.andreyasadchy.xtra.ui.design.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Colour tokens (plan section 4, style B "soft cards"). Layers are told apart by lightness only, no outlines or
 * shadows; teal is reserved for live, selected and primary actions; soft red is the only other colour.
 *
 * The app is dark only for now. Every colour is a parameter so a light set is just another instance of this class.
 */
@Immutable
data class LagoonColors(
    /** Page background. */
    val page: Color,
    /** Cards and grouped lists sitting on the page. */
    val card: Color,
    /** Controls sitting on a card (chips, icon tiles, the floating nav). */
    val control: Color,
    /** Pressed state of a card or row: one step lighter. */
    val pressed: Color,
    /** Selected block in a segmented control. */
    val selectedTrack: Color,
    val accent: Color,
    /** Text and icons on an accent fill. */
    val onAccent: Color,
    /** Accent tint behind selected items (nav pill, icon tiles). */
    val accentSoft: Color,
    /** Live dot, destructive actions, errors. */
    val danger: Color,
    val dangerSoft: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    /** Placeholders, disabled text, captions under thumbnails. */
    val textHint: Color,
    /** Dark glass behind chips that sit on images (viewer count, duration). */
    val overlay: Color,
    /** Dimmed background behind sheets and menus. */
    val scrim: Color,
    /** Skeleton blocks and the shimmer highlight that sweeps across them. */
    val skeleton: Color,
    val skeletonHighlight: Color,
)

val DarkLagoonColors = LagoonColors(
    page = Color(0xFF111113),
    card = Color(0xFF1B1B1F),
    control = Color(0xFF26262B),
    pressed = Color(0xFF303036),
    selectedTrack = Color(0xFF2A2A30),
    accent = Color(0xFF5EE2C4),
    onAccent = Color(0xFF111113),
    accentSoft = Color(0x245EE2C4),
    danger = Color(0xFFFF6B6E),
    dangerSoft = Color(0x29FF6B6E),
    // three text levels (plan: 92 % / 62 % / 40 % white). The hint level is 50 % so it still reaches 4.5:1 on the
    // control layer (plan 10.5); 40 % white measures 3.9:1 on a card.
    textPrimary = Color.White.copy(alpha = 0.92f),
    textSecondary = Color.White.copy(alpha = 0.62f),
    textHint = Color.White.copy(alpha = 0.50f),
    overlay = Color(0xC7111113),
    scrim = Color(0x99000000),
    skeleton = Color(0xFF222227),
    skeletonHighlight = Color(0xFF2E2E34),
)
