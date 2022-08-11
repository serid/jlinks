package jitrs.util

/**
 * Has reverse effect of [IdentityWrapper]
 */
@Suppress("EqualsOrHashCode")
class EqualityWrapper<T>(val value: T, val comparator: (T, T) -> Boolean) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EqualityWrapper<*>

        if (value!!::class.java != other.value!!::class.java) throw ClassCastException()

        @Suppress("UNCHECKED_CAST")
        return this.comparator(value, other.value as T)
    }

    override fun toString(): String = value.toString()
}