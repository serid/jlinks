package jitrs.util

/**
 * Wrap an object providing [equals] and [hashCode] based on identity instead of invoking overridden methods
 */
class IdentityWrapper<T>(
    val data: T
) {
    // Uses default implementation of [equals] and [hashCode]
}