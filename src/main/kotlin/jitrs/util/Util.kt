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