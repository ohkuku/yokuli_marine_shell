package com.yokuli.marine.core.testing

class FakeClock(var nowMillis: Long = 0L) {
    fun advanceBy(millis: Long) { nowMillis += millis }
}
