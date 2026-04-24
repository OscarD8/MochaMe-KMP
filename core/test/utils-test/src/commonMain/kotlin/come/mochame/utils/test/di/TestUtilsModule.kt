package come.mochame.utils.test.di


import com.mochame.utils.DateTimeUtils
import come.mochame.utils.test.FakeDateTimeUtils
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

/**
 * Simple fake clock.
 */
@Module
class FakeClockModule {
    @Single
    fun provideDateTimeUtils(): DateTimeUtils = FakeDateTimeUtils()
}

