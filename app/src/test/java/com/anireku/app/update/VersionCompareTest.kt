package com.anireku.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionCompareTest {

    @Test
    fun `numeric components are compared as numbers, not strings`() {
        // The bug string comparison would introduce: "0.10.0" < "0.9.2".
        assertTrue(UpdateChecker.compareVersions("0.10.0", "0.9.2") > 0)
        assertTrue(UpdateChecker.compareVersions("1.0.0", "0.99.99") > 0)
        assertTrue(UpdateChecker.compareVersions("0.1.2", "0.1.1") > 0)
    }

    @Test
    fun `identical versions compare equal`() {
        assertEquals(0, UpdateChecker.compareVersions("0.1.0", "0.1.0"))
        assertEquals(0, UpdateChecker.compareVersions("0.1", "0.1.0"))
    }

    @Test
    fun `pre-release suffix does not make a build look newer`() {
        assertEquals(0, UpdateChecker.compareVersions("0.1.0-beta", "0.1.0"))
    }

    @Test
    fun `older remote version never triggers an update`() {
        assertTrue(UpdateChecker.compareVersions("0.0.9", "0.1.0") < 0)
    }
}
