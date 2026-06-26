// port-lint: source rcvec.rs
package io.github.kotlinmania.procmacro2

internal class RcVec<T> internal constructor(
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

    internal fun build(): RcVec<T> = RcVec(SharedList(inner, refCount = 1))

    internal fun intoIter(): RcVecIntoIter<T> = RcVecIntoIter(inner)

    override fun iterator(): RcVecIntoIter<T> = intoIter()
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
) : Iterator<T> {
    private var index: Int = 0

    override fun hasNext(): Boolean = index < inner.size

    override fun next(): T {
        if (!hasNext()) {
            throw NoSuchElementException()
        }
        val item = inner[index]
        index += 1
        return item
    }

    internal fun sizeHint(): Pair<Int, Int?> {
        val remaining = inner.size - index
        return Pair(remaining, remaining)
    }

    internal fun remaining(): List<T> = inner.subList(index, inner.size).toList()

    internal fun clone(): RcVecIntoIter<T> = RcVecIntoIter(remaining().toMutableList())
}

// Shared mutable storage with a reference count, mirroring Rust's Rc<Vec<T>>.
// Not safe for concurrent access — Rust's Rc is !Send + !Sync; Kotlin has no
// equivalent marker, so callers must enforce single-threaded use.
internal class SharedList<T>(
    var list: MutableList<T>,
    var refCount: Int,
)
