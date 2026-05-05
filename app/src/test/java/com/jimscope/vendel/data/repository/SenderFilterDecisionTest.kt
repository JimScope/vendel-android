package com.jimscope.vendel.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SenderFilterDecisionTest {

    @Test
    fun offAlwaysForwards() {
        assertTrue(SenderFilterDecision.decide(SenderFilterMode.OFF, matched = false, hasSender = true))
        assertTrue(SenderFilterDecision.decide(SenderFilterMode.OFF, matched = true, hasSender = false))
    }

    @Test
    fun allowForwardsOnlyOnMatchWithSender() {
        assertTrue(SenderFilterDecision.decide(SenderFilterMode.ALLOW, matched = true, hasSender = true))
        assertFalse(SenderFilterDecision.decide(SenderFilterMode.ALLOW, matched = false, hasSender = true))
        assertFalse(SenderFilterDecision.decide(SenderFilterMode.ALLOW, matched = true, hasSender = false))
        assertFalse(SenderFilterDecision.decide(SenderFilterMode.ALLOW, matched = false, hasSender = false))
    }

    @Test
    fun blockForwardsUnlessMatchWithSender() {
        assertFalse(SenderFilterDecision.decide(SenderFilterMode.BLOCK, matched = true, hasSender = true))
        assertTrue(SenderFilterDecision.decide(SenderFilterMode.BLOCK, matched = false, hasSender = true))
        assertTrue(SenderFilterDecision.decide(SenderFilterMode.BLOCK, matched = true, hasSender = false))
        assertTrue(SenderFilterDecision.decide(SenderFilterMode.BLOCK, matched = false, hasSender = false))
    }
}
