package com.example.movieboxexplorer.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The three catalogue flags are independent, and the order in which they are
 * reported decides what the UI does.
 */
class RiskPolicyTest {

    @Test
    fun `a plain title has no flags`() {
        val flags = RiskFlags.of(webHighRisk = false, isPornographic = false)
        assertEquals(RiskLevel.Normal, flags.level)
        assertFalse(flags.blocked)
        assertFalse(flags.needsAgeGate)
    }

    @Test
    fun `an unlisted title is not an adult title`() {
        // Black Torch: webHighRisk = true, isPornographic = false.
        val flags = RiskFlags.of(webHighRisk = true, isPornographic = false)
        assertEquals(RiskLevel.Unlisted, flags.level)
        assertTrue(flags.unlisted)
        assertFalse(flags.adult)
        assertFalse(flags.needsAgeGate)
    }

    @Test
    fun `an adult title that is not unlisted still needs the age gate`() {
        // Aikagi The Animation: webHighRisk = false, isPornographic = true.
        val flags = RiskFlags.of(webHighRisk = false, isPornographic = true)
        assertEquals(RiskLevel.Adult, flags.level)
        assertFalse(flags.unlisted)
        assertTrue(flags.needsAgeGate)
    }

    @Test
    fun `a title can be unlisted and adult at the same time`() {
        val flags = RiskFlags.of(webHighRisk = true, isPornographic = true)
        assertEquals(RiskLevel.Adult, flags.level)
        assertTrue(flags.unlisted)
        assertTrue(flags.adult)
    }

    @Test
    fun `forbidden outranks everything`() {
        val flags = RiskFlags.of(webHighRisk = true, isPornographic = true, isForbid = true)
        assertEquals(RiskLevel.Forbidden, flags.level)
        assertTrue(flags.blocked)
    }

    @Test
    fun `only forbidden titles are blocked`() {
        assertTrue(RiskFlags.of(false, false, isForbid = true).blocked)
        assertFalse(RiskFlags.of(true, true, isForbid = false).blocked)
    }
}
