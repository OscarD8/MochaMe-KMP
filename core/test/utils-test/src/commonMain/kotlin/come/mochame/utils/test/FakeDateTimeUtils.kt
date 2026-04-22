package come.mochame.utils.test

import com.mochame.utils.DateTimeUtils
import kotlin.time.Instant

class FakeDateTimeUtils(initialMillis: Long = 1740787200000L) : DateTimeUtils() {
    private var currentTime = initialMillis

    fun advanceTime(ms: Long) { currentTime += ms }
    fun reverseTime(ms: Long) { currentTime -= ms }
    fun setTime(ms: Long) { currentTime = ms }

    override fun now(): Instant = Instant.fromEpochMilliseconds(currentTime)
}