// port-lint: source rcvec.rs
package io.github.kotlinmania.procmacro2

<<<<<<< main
internal class RcVec<T> internal constructor(
    private var inner: SharedList<T>,
) {
    internal fun isEmpty(): Boolean {
        return inner.list.isEmpty()
    }

    internal fun len(): Int {
        return inner.list.size
    }

    internal fun iter(): Iterator<T> {
        return inner.list.iterator()
    }
=======
internal class RcVec<T> private constructor(
    private var inner: SharedList<T>,
) {
    internal fun isEmpty(): Boolean = inner.list.isEmpty()

    internal fun len(): Int = inner.list.size

    internal fun iter(): Iterator<T> = inner.list.iterator()
>>>>>>> flatten/2026-05-09

    internal fun makeMut(): RcVecMut<T> {
        if (inner.refCount > 1) {
            inner.refCount -= 1
            inner = SharedList(inner.list.toMutableList(), refCount = 1)
        }
        return RcVecMut(inner.list)
    }

    internal fun getMut(): RcVecMut<T>? {
        if (inner.refCount != 1) {
            return null
        }
        return RcVecMut(inner.list)
    }

    internal fun makeOwned(): RcVecBuilder<T> {
        val vec =
            if (inner.refCount == 1) {
                val taken = inner.list
                inner.list = mutableListOf()
                taken
            } else {
                inner.refCount -= 1
                inner.list.toMutableList()
            }
        return RcVecBuilder(vec)
    }

    internal fun clone(): RcVec<T> {
        inner.refCount += 1
        return RcVec(inner)
    }
<<<<<<< main
=======

    internal companion object {
        internal fun <T> fromVec(vec: MutableList<T>): RcVec<T> = RcVec(SharedList(vec, refCount = 1))
    }
>>>>>>> flatten/2026-05-09
}

internal class RcVecBuilder<T> internal constructor(
    internal val inner: MutableList<T>,
) : Iterable<T> {
    internal companion object {
<<<<<<< main
        internal fun <T> new(): RcVecBuilder<T> {
            return RcVecBuilder(mutableListOf())
        }

        internal fun <T> withCapacity(cap: Int): RcVecBuilder<T> {
            return RcVecBuilder(ArrayList(cap))
        }
=======
        internal fun <T> new(): RcVecBuilder<T> = RcVecBuilder(mutableListOf())

        internal fun <T> withCapacity(cap: Int): RcVecBuilder<T> = RcVecBuilder(ArrayList(cap))
>>>>>>> flatten/2026-05-09
    }

    internal fun push(element: T) {
        inner.add(element)
    }

    internal fun extend(iter: Iterable<T>) {
        inner.addAll(iter)
    }

<<<<<<< main
    internal fun asMut(): RcVecMut<T> {
        return RcVecMut(inner)
    }

    internal fun build(): RcVec<T> {
        return RcVec(SharedList(inner, refCount = 1))
    }

    internal fun intoIter(): RcVecIntoIter<T> {
        return RcVecIntoIter(inner)
    }

    override fun iterator(): RcVecIntoIter<T> {
        return intoIter()
    }
=======
    internal fun asMut(): RcVecMut<T> = RcVecMut(inner)

    internal fun build(): RcVec<T> = RcVec.fromVec(inner)

    internal fun intoIter(): RcVecIntoIter<T> = RcVecIntoIter(inner)

    override fun iterator(): Iterator<T> = intoIter().iterator()
>>>>>>> flatten/2026-05-09
}

internal class RcVecMut<T> internal constructor(
    private val inner: MutableList<T>,
) {
    internal fun push(element: T) {
        inner.add(element)
    }

    internal fun extend(iter: Iterable<T>) {
        inner.addAll(iter)
    }

<<<<<<< main
    internal fun asMut(): RcVecMut<T> {
        return RcVecMut(inner)
    }
=======
    internal fun asMut(): RcVecMut<T> = RcVecMut(inner)
>>>>>>> flatten/2026-05-09

    internal fun take(): RcVecBuilder<T> {
        val vec = inner.toMutableList()
        inner.clear()
        return RcVecBuilder(vec)
    }
}

internal class RcVecIntoIter<T>(
    private val inner: MutableList<T>,
<<<<<<< main
) : Iterator<T> {
    private var index: Int = 0

    override fun hasNext(): Boolean {
        return index < inner.size
    }

    override fun next(): T {
        if (!hasNext()) {
            throw NoSuchElementException()
=======
) : Iterable<T> {
    private var index: Int = 0

    internal fun next(): T? {
        if (index >= inner.size) {
            return null
>>>>>>> flatten/2026-05-09
        }
        val item = inner[index]
        index += 1
        return item
    }

    internal fun sizeHint(): Pair<Int, Int?> {
        val remaining = inner.size - index
        return Pair(remaining, remaining)
    }

<<<<<<< main
    internal fun remaining(): List<T> {
        return inner.subList(index, inner.size).toList()
    }

    internal fun clone(): RcVecIntoIter<T> {
        return RcVecIntoIter(remaining().toMutableList())
    }
}

internal class SharedList<T>(
=======
    override fun iterator(): Iterator<T> {
        return object : Iterator<T> {
            override fun hasNext(): Boolean = index < inner.size

            override fun next(): T = this@RcVecIntoIter.next() ?: throw NoSuchElementException()
        }
    }
}

private class SharedList<T>(
>>>>>>> flatten/2026-05-09
    var list: MutableList<T>,
    var refCount: Int,
)
