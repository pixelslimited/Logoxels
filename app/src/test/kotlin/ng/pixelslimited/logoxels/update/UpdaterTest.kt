package ng.pixelslimited.logoxels.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdaterTest {
    @Test fun readsBuildNumberFromTag() {
        assertEquals(42, Updater.buildOf("build-42"))
        assertEquals(7, Updater.buildOf(" build-7 "))
        assertNull(Updater.buildOf("v1.2"))
        assertNull(Updater.buildOf("build-"))
    }

    @Test fun onlyNewerBuildsOfferAnUpdate() {
        assertTrue(Updater.isNewer(43, 42))
        assertFalse(Updater.isNewer(42, 42))
        assertFalse(Updater.isNewer(41, 42))
    }
}
