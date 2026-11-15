package neth.iecal.curbox.data.models

/** The ways Strict Mode can end. */
enum class StrictModeUnlockMethod {
    /** Stays locked until [StrictModeConfig.lockedUntilMs]. It cannot be ended early. */
    TIMER,

    /** Stays locked until the user turns it off while the phone is charging. */
    CHARGER
}

/**
 * A hard lock on the blockers. While it is locked, a change that weakens a restriction is
 * refused outright instead of being delayed. Stricter changes still apply.
 */
data class StrictModeConfig(
    val isEnabled: Boolean = false,
    val unlockMethod: StrictModeUnlockMethod = StrictModeUnlockMethod.TIMER,
    val lockedUntilMs: Long = 0L,
    /** While locked, every block screen behaves as if unlocking were disabled. */
    val blockUnlocks: Boolean = false
) {
    fun isLocked(nowMs: Long): Boolean = isEnabled &&
        (unlockMethod == StrictModeUnlockMethod.CHARGER || nowMs < lockedUntilMs)

    fun canTurnOff(nowMs: Long, isCharging: Boolean): Boolean =
        !isLocked(nowMs) || (unlockMethod == StrictModeUnlockMethod.CHARGER && isCharging)

    companion object {
        /** Thirty days. Keeps a mistyped duration from locking the blockers for years. */
        const val MAX_LOCK_MINUTES = 43_200L
    }
}
