// port-lint: source rcvec.rs
package io.github.kotlinmania.procmacro2

internal class RcVec<T> private constructor(
    private var inner: SharedList<T>,
) {
    internal fun isEmpty(): Boolean = inner.list.isEmpty()

    internal fun len(): Int = inner.list.size

    internal fun iter(): Iterator<T> = inner.list.iterator()

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

    internal companion object {
        internal fun <T> fromVec(vec: MutableList<T>): RcVec<T> = RcVec(SharedList(vec, refCount = 1))
    }
}

internal class RcVecBuilder<T> internal constructor(
    internal val inner: MutableList<T>,
) : Iterable<T> {
    internal companion object {
        internal fun <T> new(): RcVecBuilder<T> = RcVecBuilder(mutableListOf())

        internal fun <T> withCapacity(cap: Int): RcVecBuilder<T> = RcVecBuilder(ArrayList(cap))
    }

    internal fun push(element: T) {
        inner.add(element)
    }

    internal fun extend(iter: Iterable<T>) {
        inner.addAll(iter)
    }

    internal fun asMut(): RcVecMut<T> = RcVecMut(inner)

    internal fun build(): RcVec<T> = RcVec.fromVec(inner)

    internal fun intoIter(): RcVecIntoIter<T> = RcVecIntoIter(inner)

    override fun iterator(): Iterator<T> = intoIter().iterator()
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

    internal fun asMut(): RcVecMut<T> = RcVecMut(inner)

    internal fun take(): RcVecBuilder<T> {
        val vec = inner.toMutableList()
        inner.clear()
        return RcVecBuilder(vec)
    }
}

internal class RcVecIntoIter<T>(
    private val inner: MutableList<T>,
) : Iterable<T> {
    private var index: Int = 0

    internal fun next(): T? {
        if (index >= inner.size) {
            return null
        }
        val item = inner[index]
        index += 1
        return item
    }

    internal fun sizeHint(): Pair<Int, Int?> {
        val remaining = inner.size - index
        return Pair(remaining, remaining)
    }

    override fun iterator(): Iterator<T> {
        return object : Iterator<T> {
            override fun hasNext(): Boolean = index < inner.size

            override fun next(): T = this@RcVecIntoIter.next() ?: throw NoSuchElementException()
        }
    }
}

private class SharedList<T>(
    var list: MutableList<T>,
    var refCount: Int,
)
