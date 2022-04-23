package jitrs.links.util

class ArrayIterator<T>(private val array: Array<T>) : Iterator<T> {
    private constructor(array: Array<T>, i: Int) : this(array) {
        this.i = i
    }

    private var i = 0

    fun reset() {
        i = 0
    }

    fun clone(): ArrayIterator<T> {
        return ArrayIterator(array, i)
    }

    override operator fun next(): T {
        i++
        return array[i - 1]
    }

    override operator fun hasNext(): Boolean = i < array.size
}