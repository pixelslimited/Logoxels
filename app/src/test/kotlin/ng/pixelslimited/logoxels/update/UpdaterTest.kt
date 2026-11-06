package ng.pixelslimited.logoxels.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdaterTest {
    @Test fun onlyNewerBuildsOfferAnUpdate() {
        assertTrue(Updater.isNewer(43, 42))
        assertFalse(Updater.isNewer(42, 42))
        assertFalse(Updater.isNewer(41, 42))
    }
}
