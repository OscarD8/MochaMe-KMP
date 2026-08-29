package com.mochame.utils.cli

object InputSanitizer {
    private val MULTI_SPACE_REGEX = Regex("\\s+")

    fun sanitize(raw: String?): String? {
        if (raw == null) return null
        val cleaned = raw
            .replace('\u00A0', ' ')
            .replace('\u200B', ' ')
            .trim()
            .replace(MULTI_SPACE_REGEX, " ")

        return cleaned.ifBlank { null }
    }
}

object PrimitiveParsers {

    private val DECIMAL_REGEX = Regex("^[+-]?[0-9]+([.,][0-9]+)?(\\s*[a-zA-Z]*)?$")
    private val INTEGER_REGEX = Regex("^[+-]?[0-9]+(\\s*[a-zA-Z]*)?$")

    fun parseBoundedDouble(
        raw: String?,
        range: ClosedFloatingPointRange<Double>,
        fieldName: String = "Value"
    ): Result<Double?> {
        val sanitized = InputSanitizer.sanitize(raw) ?: return Result.success(null)

        return runCatching {
            require(PrimitiveParsers.DECIMAL_REGEX.matches(sanitized)) {
                "Invalid numeric format for $fieldName: '$raw'."
            }
            val cleanNumber = sanitized.replace(',', '.')
                .replace(Regex("[^0-9.-]"), "")

            val parsed = cleanNumber.toDoubleOrNull()
                ?: throw IllegalArgumentException("Invalid decimal format for $fieldName.")

            require(parsed in range) {
                "$fieldName must be between ${range.start} and ${range.endInclusive} (got $parsed)."
            }
            parsed
        }
    }

    fun parseBoundedInt(
        raw: String?,
        range: IntRange,
        fieldName: String = "Value"
    ): Result<Int?> {
        val sanitized = InputSanitizer.sanitize(raw) ?: return Result.success(null)

        return runCatching {
            require(PrimitiveParsers.INTEGER_REGEX.matches(sanitized)) {
                "Invalid integer format for $fieldName: '$raw'."
            }
            val digitsOnly = sanitized.replace(Regex("[^0-9-]"), "")

            val parsed = digitsOnly.toIntOrNull()
                ?: throw IllegalArgumentException("Invalid integer format for $fieldName.")

            require(parsed in range) {
                "$fieldName must be between ${range.first} and ${range.last} (got $parsed)."
            }
            parsed
        }
    }

    fun parseBoolean(raw: String?, fieldName: String = "Flag"): Result<Boolean?> {
        val sanitized = InputSanitizer.sanitize(raw)?.lowercase() ?: return Result.success(null)

        return when (sanitized) {
            "true", "t", "yes", "y", "1", "on" -> Result.success(true)
            "false", "f", "no", "n", "0", "off" -> Result.success(false)
            else -> Result.failure(
                IllegalArgumentException("Invalid boolean for $fieldName: '$raw'. Use yes/no, true/false, or 1/0.")
            )
        }
    }
}