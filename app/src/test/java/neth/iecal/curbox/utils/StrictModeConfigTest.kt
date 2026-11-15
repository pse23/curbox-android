package neth.iecal.curbox.utils

import neth.iecal.curbox.data.models.StrictModeConfig
import neth.iecal.curbox.data.models.StrictModeUnlockMethod
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StrictModeConfigTest {
    private val timer = StrictModeConfig(
        isEnabled = true,
        unlockMethod = StrictModeUnlockMethod.TIMER,
        lockedUntilMs = 1_000L
    )
    private val charger = StrictModeConfig(
        isEnabled = true,
        unlockMethod = StrictModeUnlockMethod.CHARGER
    )

    @Test
    fun defaultConfigIsNotLocked() {
        assertFalse(StrictModeConfig().isLocked(0L))
        assertTrue(StrictModeConfig().canTurnOff(0L, isCharging = false))
    }

    @Test
    fun timerLocksUntilDeadlineAndChargingDoesNotEndItEarly() {
        assertTrue(timer.isLocked(999L))
        assertFalse(timer.canTurnOff(999L, isCharging = true))
        assertFalse(timer.isLocked(1_000L))
        assertTrue(timer.canTurnOff(1_000L, isCharging = false))
    }

    @Test
    fun chargerStaysLockedUntilTurnedOffWhileCharging() {
        assertTrue(charger.isLocked(Long.MAX_VALUE))
        assertFalse(charger.canTurnOff(0L, isCharging = false))
        assertTrue(charger.canTurnOff(0L, isCharging = true))
    }
}
