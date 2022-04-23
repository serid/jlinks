package jitrs.links.util

fun matchPrefix(string: String, from: Int, prefix: String): Boolean {
    if (string.length - from < prefix.length) return false
    for (i in prefix.indices) {
        if (string[from + i] != prefix[i]) return false
    }
    return true
}

fun myAssert(debug: Boolean, condition: Boolean) {
    if (debug && !condition) throw AssertionError("Assertion failed")
}