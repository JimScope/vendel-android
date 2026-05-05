package com.jimscope.vendel.data.repository

object SenderFilterDecision {
    fun decide(mode: SenderFilterMode, matched: Boolean, hasSender: Boolean): Boolean = when (mode) {
        SenderFilterMode.OFF -> true
        SenderFilterMode.ALLOW -> hasSender && matched
        SenderFilterMode.BLOCK -> !(hasSender && matched)
    }
}
