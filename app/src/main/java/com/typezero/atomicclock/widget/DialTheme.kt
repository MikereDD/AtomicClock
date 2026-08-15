package com.typezero.atomicclock.widget

/**
 * Visual families for the precision Dial widget.
 *
 * Keep geometry separate from appearance. New themes decorate the same verified
 * dial math instead of reimplementing the clock from scratch.
 */
enum class DialTheme {
    MIDNIGHT,
    ARCTIC,
    EMERALD,
    RETRO_BRASS,
}

enum class DialLabelOrientation {
    /** Traditional fixed watch face: labels remain upright to the viewer. */
    UPRIGHT,

    /** Mechanical rotating ring: labels remain physically attached to the ring. */
    ATTACHED_TO_RING,
}
