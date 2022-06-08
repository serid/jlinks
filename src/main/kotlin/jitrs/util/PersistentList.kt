package jitrs.util

sealed class PersistentList<T> {
    data class Cons<T>(val data: T, val tail: PersistentList<T>) : PersistentList<T>()
    class Nil<T> : PersistentList<T>() {
        companion object {
            private val instance: Nil<Unit> = Nil()

            @Suppress("UNCHECKED_CAST")
            fun <T> getNil(): Nil<T> = instance as Nil<T>
        }
    }

    operator fun iterator(): Iterator<T> = PersistentListIterator(this)

    fun asSequence(): Sequence<T> = iterator().asSequence()

    data class PersistentListIterator<T>(
        var list: PersistentList<T>
    ) : Iterator<T> {
        override fun hasNext(): Boolean = list is Cons

        override fun next(): T = when (list) {
            is Nil -> throw NoSuchElementException()
            is Cons -> {
                val result = (list as Cons).data
                list = (list as Cons).tail
                result
            }
        }
    }
}

typealias PersistentMap<K, V> = PersistentList<Pair<K, V>>