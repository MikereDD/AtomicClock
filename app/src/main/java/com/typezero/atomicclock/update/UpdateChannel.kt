package com.typezero.atomicclock.update

enum class UpdateChannel(val manifestValue: String) {
    STABLE("stable"),
    DEVELOPMENT("development");

    companion object {
        fun fromManifestValue(value: String): UpdateChannel =
            entries.firstOrNull { it.manifestValue == value }
                ?: throw IllegalArgumentException("Unsupported update channel: $value")
    }
}
