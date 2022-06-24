package jitrs.datastructures

/**
 * Set of sets with a union-find operation
 * https://en.wikipedia.org/wiki/Disjoint-set_data_structure
 */
//class DisjointSet {
//    companion object {
//        fun <T> newObject(data: T): DisjoinSetObject<T> = DisjoinSetObject.new(data)
//    }
//}

class DisjointSetObject<T> private constructor(
    private var parent: DisjointSetObject<T>?,
    private val data: T
) {
    /**
     * Find the representative of this equivalence class.
     * Compresses paths.
     */
    private fun findRepresentative(): DisjointSetObject<T> {
        var root = this
        while (true) {
            root = root.parent ?: break
            println("test")
        }

        // TODO
        // Compress path
//        var x = this
//        while (true) {
//            val oldParent = x.parent!!
//            if (oldParent == root)
//                break
//            x.parent = root
//            x = oldParent
//        }

        return root
    }

    fun findData(): T = this.findRepresentative().data

    private fun setRepresentative(parent: DisjointSetObject<T>) {
        if (this.parent != null)
            throw RuntimeException("Parent already set")
        this.parent = parent
    }

    // Not generated
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DisjointSetObject<*>

        return findData() == other.findData()
    }

    override fun hashCode(): Int = findData().hashCode()

    override fun toString(): String = "$data"

    companion object {
        fun <T> new(data: T): DisjointSetObject<T> = DisjointSetObject(null, data)

        /**
         * If [resolver] returns [left], the first variable becomes the representative
         */
        fun <T> union(t0: DisjointSetObject<T>, t1: DisjointSetObject<T>, resolver: (T, T) -> LeftOrRight) {
            val representative0 = t0.findRepresentative()
            val representative1 = t1.findRepresentative()
            when (resolver(
                representative0.data,
                representative1.data
            )
            ) {
                left -> representative1.setRepresentative(representative0)
                right -> representative0.setRepresentative(representative1)
            }
        }
    }
}

typealias LeftOrRight = Boolean

const val left = false
const val right = true