package com.mochame.platform.providers

import co.touchlab.kermit.Logger
import com.mochame.sync.spi.infrastructure.DigestState


expect fun createPlatformDigest(algorithm: String = "SHA-256", logger: Logger): DigestState

