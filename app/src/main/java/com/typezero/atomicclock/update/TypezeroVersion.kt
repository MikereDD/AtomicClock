package com.typezero.atomicclock.update

/** Exact numeric Typezer∅ version comparison. Lexical comparison is intentionally forbidden. */
data class TypezeroVersion(
    val core: List<Int>,
    val development: List<Int>?,
) : Comparable<TypezeroVersion> {
    override fun compareTo(other: TypezeroVersion): Int {
        val coreLength = maxOf(core.size, other.core.size)
        repeat(coreLength) { index ->
            val left = core.getOrElse(index) { 0 }
            val right = other.core.getOrElse(index) { 0 }
            if (left != right) return left.compareTo(right)
        }

        if (development == null && other.development != null) return 1
        if (development != null && other.development == null) return -1
        if (development == null) return 0

        val devLength = maxOf(development.size, other.development!!.size)
        repeat(devLength) { index ->
            val left = development.getOrElse(index) { 0 }
            val right = other.development.getOrElse(index) { 0 }
            if (left != right) return left.compareTo(right)
        }
        return 0
    }

    companion object {
        private val pattern = Regex("^(\\d+(?:\\.\\d+)*)(?:-dev\\.(\\d+(?:\\.\\d+)*))?$")

        fun parse(value: String): TypezeroVersion {
            val match = pattern.matchEntire(value)
                ?: throw IllegalArgumentException("Malformed Typezer∅ version: $value")
            return TypezeroVersion(
                core = match.groupValues[1].split('.').map(::parsePart),
                development = match.groupValues[2]
                    .takeIf(String::isNotEmpty)
                    ?.split('.')
                    ?.map(::parsePart),
            )
        }

        private fun parsePart(value: String): Int =
            value.toIntOrNull() ?: throw IllegalArgumentException("Invalid numeric version component: $value")
    }
}
