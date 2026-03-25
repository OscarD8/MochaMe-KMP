package com.mochame.app.domain.exceptions

class SyncInitializationException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)


class HlcParseException(rawString: String) :
    RuntimeException("Failed to parse HLC string: '$rawString'. Data integrity at risk.")