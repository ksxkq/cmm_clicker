package com.ksxkq.cmm_clicker.accessibility

internal object OverlayStackMotion {
    const val SHEET_SCRIM_ALPHA = 0.24f
    const val SHEET_HEIGHT_FRACTION = 0.9f
    const val SHEET_MAX_WIDTH_DP = 520

    // Visible stack keeps only two layers: previous + current.
    const val PREVIOUS_LAYER_SCALE = 0.965f
    const val PREVIOUS_LAYER_TRANSLATE_Y = 0f

    // Current page occupies foreground slot; previous page stays in background slot.
    const val FOREGROUND_LAYER_TOP_INSET_DP = 24
    const val BACKGROUND_LAYER_TOP_INSET_DP = 0

    const val ENTER_OFFSET_RATIO = 0.22f
    const val EXIT_OFFSET_RATIO = 0.18f
}
