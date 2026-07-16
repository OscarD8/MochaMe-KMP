package com.mochame.platform.providers

import co.touchlab.kermit.Logger
import com.mochame.sync.spi.infrastructure.Digest


expect fun createPlatformDigest(algorithm: String = "SHA-256", logger: Logger): Digest

