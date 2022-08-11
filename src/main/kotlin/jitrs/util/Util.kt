package jitrs.util

import java.lang.management.ManagementFactory

fun matchPrefix(string: String, from: Int, prefix: String): Boolean {
    if (string.length - from < prefix.length) return false
    for (i in prefix.indices) {
        if (string[from + i] != prefix[i]) return false
    }
    return true
}

fun myAssert(condition: Boolean) {
    if (!condition)
        throw AssertionError("Assertion failed")
}

@Suppress("NOTHING_TO_INLINE")
inline fun <T> ensureEqual(x: T, y: T): T {
    myAssert(x == y)
    return x
}

fun Int.toAlphabetChar(): Char {
    val code = this + 'a'.code
    if (code > 'z'.code || code < 'a'.code)
        throw IllegalArgumentException()
    return code.toChar()
}

fun printGCStats() {
    var totalGarbageCollections: Long = 0
    var garbageCollectionTime: Long = 0
    for (gc in ManagementFactory.getGarbageCollectorMXBeans()) {
        totalGarbageCollections += gc.collectionCount
        garbageCollectionTime += gc.collectionTime
    }
    println("Total Garbage Collections: $totalGarbageCollections")
    println("Total Garbage Collection Time (ms): $garbageCollectionTime")
}

fun exceptionPrintMessageAndTrace(e: Exception) {
    System.err.println(e.message)
    e.printStackTrace()
}

inline fun <T, U> nullableMap(a: T?, f: (T) -> U): U? =
    if (a == null)
        a
    else
        f(a)

inline fun <reified T> Any.cast() = this as T