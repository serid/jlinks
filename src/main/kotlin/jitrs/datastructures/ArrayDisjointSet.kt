package jitrs.datastructures

import jitrs.util.UnreachableError

/**
 * A disjoint set with a [duplicate] operation to keep older versions of the list intact.
 * Useful for implementing proof search with unification and backtracking.
 *
 * https://en.wikipedia.org/wiki/Disjoint-set_data_structure
 */
// Key is a disjoint set index
class ArrayDisjointSet<T: ADSData<T>> private constructor(
    private val arr: ArrayList<ADSValue<T>>
) {
    constructor() : this(arrayListOf())

    fun findData(t0: ADSIndex): T = this.arr[this.findRepresentative(t0)].tryData()

    fun setData(t0: ADSIndex, data: T) {
        val oldData = (this.arr[this.findRepresentative(t0)] as? ADSValue.Data)?.data
        if (oldData == null || oldData.lessThan(data)) {
            this.arr[this.findRepresentative(t0)] = ADSValue.Data(data)
            return
        }
        throw RuntimeException("$data is not a subtype of $oldData")
    }

    /**
     * If [resolver] returns [left], the first variable becomes the representative
     */
    fun union(t0: ADSIndex, t1: ADSIndex, resolver: (T, T) -> LeftOrRight) {
        val representative0 = findRepresentative(t0)
        val representative1 = findRepresentative(t1)
        when (resolver(
            this.arr[representative0].tryData(),
            this.arr[representative1].tryData()
        )) {
            left -> setParent(representative1, representative0)
            right -> setParent(representative0, representative1)
        }
    }

    private fun findRepresentative(t0: ADSIndex): ADSIndex {
        var iterator = t0

        while (true) {
            when (val x = this.arr[iterator]) {
                is ADSValue.Data -> break
                is ADSValue.Parent -> iterator = x.parent
            }

            println("path compression opportunity")
        }

        return iterator
    }

    private fun setParent(t0: ADSIndex, t1: ADSIndex) {
        when (val v = this.arr[t0]) {
            is ADSValue.Parent -> throw RuntimeException("Parent already set")
            is ADSValue.Data -> if (v.data.isSet()) throw RuntimeException("Data already set")
        }
        this.arr[t0] = ADSValue.Parent(t1)
    }

    /**
     * @return index of the new variable
     */
    fun pushNewVariable(data: T): ADSIndex = pushNewVariable0(ADSValue.Data(data))

    private fun pushNewVariable0(value: ADSValue<T>): ADSIndex {
        val index = this.arr.size
        this.arr.add(value)
        return index
    }

    fun duplicate(): ArrayDisjointSet<T> = ArrayDisjointSet(ArrayList(this.arr))

    fun isEmpty(): Boolean = arr.isEmpty()
}

typealias ADSIndex = Int

sealed class ADSValue<T> {
    data class Parent<T>(val parent: ADSIndex) : ADSValue<T>()

    data class Data<T>(val data: T) : ADSValue<T>()

    fun tryData(): T = when (this) {
        is Data -> this.data
        is Parent -> throw UnreachableError()
    }
}

/**
 * Data in ADS can provide some introspection methods so that ADS can enforce stronger correctness guarantees
 */
interface ADSData<Self> {
    /**
     * ADS will only set variable's parent if data is not set
     */
    fun isSet(): Boolean

    /**
     * ADS will only replace variable's data if its new value has more information
     * See: https://en.wikipedia.org/wiki/Hindley%E2%80%93Milner_type_system#Type_order
     */
    fun lessThan(other: Self): Boolean
}