package jitrs.util

/**
 * Wrap an object providing [equals] and [hashCode] based on identity instead of invoking overridden methods
 */
class IdentityWrapper<T>(
    val data: T
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as IdentityWrapper<*>

        return data === other.data
    }

    override fun hashCode(): Int = System.identityHashCode(data)
}