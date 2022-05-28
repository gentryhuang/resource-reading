/*
 * Copyright (c) 2003, 2017, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

package com.code.queue;

import java.util.*;
import java.util.function.Consumer;

import sun.misc.SharedSecrets;

/**
 * An unbounded priority {@linkplain Queue queue} based on a priority heap.
 * The elements of the priority queue are ordered according to their
 * {@linkplain Comparable natural ordering}, or by a {@link Comparator}
 * provided at queue construction time, depending on which constructor is
 * used.  A priority queue does not permit {@code null} elements.
 * A priority queue relying on natural ordering also does not permit
 * insertion of non-comparable objects (doing so may result in
 * {@code ClassCastException}).
 *
 * <p>The <em>head</em> of this queue is the <em>least</em> element
 * with respect to the specified ordering.  If multiple elements are
 * tied for least value, the head is one of those elements -- ties are
 * broken arbitrarily.  The queue retrieval operations {@code poll},
 * {@code remove}, {@code peek}, and {@code element} access the
 * element at the head of the queue.
 *
 * <p>A priority queue is unbounded, but has an internal
 * <i>capacity</i> governing the size of an array used to store the
 * elements on the queue.  It is always at least as large as the queue
 * size.  As elements are added to a priority queue, its capacity
 * grows automatically.  The details of the growth policy are not
 * specified.
 *
 * <p>This class and its iterator implement all of the
 * <em>optional</em> methods of the {@link Collection} and {@link
 * Iterator} interfaces.  The Iterator provided in method {@link
 * #iterator()} is <em>not</em> guaranteed to traverse the elements of
 * the priority queue in any particular order. If you need ordered
 * traversal, consider using {@code Arrays.sort(pq.toArray())}.
 *
 * <p><strong>Note that this implementation is not synchronized.</strong>
 * Multiple threads should not access a {@code PriorityQueue}
 * instance concurrently if any of the threads modifies the queue.
 * Instead, use the thread-safe {@link
 * java.util.concurrent.PriorityBlockingQueue} class.
 *
 * <p>Implementation note: this implementation provides
 * O(log(n)) time for the enqueuing and dequeuing methods
 * ({@code offer}, {@code poll}, {@code remove()} and {@code add});
 * linear time for the {@code remove(Object)} and {@code contains(Object)}
 * methods; and constant time for the retrieval methods
 * ({@code peek}, {@code element}, and {@code size}).
 *
 * <p>This class is a member of the
 * <a href="{@docRoot}/../technotes/guides/collections/index.html">
 * Java Collections Framework</a>.
 *
 * @param <E> the type of elements held in this collection
 * @author Josh Bloch, Doug Lea
 * @desc 是小顶堆
 * @since 1.5
 */
public class PriorityQueue<E> extends AbstractQueue<E>
        implements java.io.Serializable {
    private static final long serialVersionUID = -7720805057305804111L;

    /*
      说明：
      1. 大顶堆：头部为堆中最大的值；小顶堆：头部为堆中最小的值；
      2. 优先级队列 PriorityQueue 默认情况下是小顶堆，然而可以通过传入自定义的Comparator函数来实现大顶堆。
      3. PriorityQueue 可以构造小顶堆，也可以构造大顶堆：
         3.1 构造小顶堆：PriorityQueue small=new PriorityQueue<>();
         3.2 构造大顶堆：PriorityQueue small=new PriorityQueue<>(Collections.reverseOrder());
      4. 优先级队列性能差，主要体现在添加元素、弹出元素，都有可能破坏堆的特性，为了保证堆的特性就需要调整堆，元素数量越多，性能就会越差
     */

    /**
     * 默认容量
     */
    private static final int DEFAULT_INITIAL_CAPACITY = 11;

    /**
     * Priority queue represented as a balanced binary heap: the two
     * children of queue[n] are queue[2*n+1] and queue[2*(n+1)].
     *
     * 优先级队列表示为平衡二叉堆：queue[n] 的两个孩子是 queue[2n+1] 和 queue[2(n+1)]
     *
     * The priority queue is ordered by comparator, or by the elements'
     * natural ordering, if comparator is null: For each node n in the
     * heap and each descendant d of n, n <= d.  The element with the
     * lowest value is in queue[0], assuming the queue is nonempty.
     *
     * 优先级队列按比较器排序，如果比较器为空，则按元素的自然顺序排序：
     *
     */

    /**
     * 存储元素的数组
     */
    transient Object[] queue; // non-private to simplify nested class access

    /**
     * The number of elements in the priority queue.
     * <p>
     * 优先级队列中的元素数。
     */
    private int size = 0;

    /**
     * The comparator, or null if priority queue uses elements' natural ordering.
     * <p>
     * 比较器，如果优先级队列使用元素的自然顺序，则为 null。
     */
    private final Comparator<? super E> comparator;

    /**
     * The number of times this priority queue has been
     * <i>structurally modified</i>.  See AbstractList for gory details.
     * <p>
     * 优先级队列的写次数
     */
    transient int modCount = 0; // non-private to simplify nested class access

    /**
     * Creates a {@code PriorityQueue} with the default initial
     * capacity (11) that orders its elements according to their
     * {@linkplain Comparable natural ordering}.
     * <p>
     * 创建一个默认初始容量为 11 的 PriorityQueue，它根据元素的自然顺序对其元素进行排序。
     */
    public PriorityQueue() {
        this(DEFAULT_INITIAL_CAPACITY, null);
    }

    /**
     * Creates a {@code PriorityQueue} with the specified initial
     * capacity that orders its elements according to their
     * {@linkplain Comparable natural ordering}.
     * <p>
     * 创建一个指定容量大小的 PriorityQueue，它根据元素的自然顺序对其元素进行排序。
     *
     * @param initialCapacity the initial capacity for this priority queue
     * @throws IllegalArgumentException if {@code initialCapacity} is less
     *                                  than 1
     */
    public PriorityQueue(int initialCapacity) {
        this(initialCapacity, null);
    }

    /**
     * Creates a {@code PriorityQueue} with the default initial capacity and
     * whose elements are ordered according to the specified comparator.
     * <p>
     * 创建一个默认初始容量为 11 的 PriorityQueue，它根据指定的比较器对其元素进行排序。
     *
     * @param comparator the comparator that will be used to order this
     *                   priority queue.  If {@code null}, the {@linkplain Comparable
     *                   natural ordering} of the elements will be used.
     * @since 1.8
     */
    public PriorityQueue(Comparator<? super E> comparator) {
        this(DEFAULT_INITIAL_CAPACITY, comparator);
    }

    /**
     * Creates a {@code PriorityQueue} with the specified initial capacity
     * that orders its elements according to the specified comparator.
     *
     * @param initialCapacity the initial capacity for this priority queue  优先级队列的初始容量
     * @param comparator      the comparator that will be used to order this
     *                        priority queue.  If {@code null}, the {@linkplain Comparable
     *                        natural ordering} of the elements will be used.
     *                        将用于排序此优先级队列的比较器。如果为 null，将使用元素的 {@linkplain Comparable 自然排序}。
     * @throws IllegalArgumentException if {@code initialCapacity} is
     *                                  less than 1
     */
    public PriorityQueue(int initialCapacity,
                         Comparator<? super E> comparator) {
        // Note: This restriction of at least one is not actually needed,
        // but continues for 1.5 compatibility
        if (initialCapacity < 1)
            throw new IllegalArgumentException();

        // 创建数组容器
        this.queue = new Object[initialCapacity];

        // 设置元素比较器，可以为空
        this.comparator = comparator;
    }

    /**
     * Creates a {@code PriorityQueue} containing the elements in the
     * specified collection.  If the specified collection is an instance of
     * a {@link SortedSet} or is another {@code PriorityQueue}, this
     * priority queue will be ordered according to the same ordering.
     * Otherwise, this priority queue will be ordered according to the
     * {@linkplain Comparable natural ordering} of its elements.
     *
     * @param c the collection whose elements are to be placed
     *          into this priority queue
     * @throws ClassCastException   if elements of the specified collection
     *                              cannot be compared to one another according to the priority
     *                              queue's ordering
     * @throws NullPointerException if the specified collection or any
     *                              of its elements are null
     */
    @SuppressWarnings("unchecked")
    public PriorityQueue(Collection<? extends E> c) {
        if (c instanceof SortedSet<?>) {
            SortedSet<? extends E> ss = (SortedSet<? extends E>) c;
            this.comparator = (Comparator<? super E>) ss.comparator();
            initElementsFromCollection(ss);
        } else if (c instanceof PriorityQueue<?>) {
            PriorityQueue<? extends E> pq = (PriorityQueue<? extends E>) c;
            this.comparator = (Comparator<? super E>) pq.comparator();
            initFromPriorityQueue(pq);
        } else {
            this.comparator = null;
            initFromCollection(c);
        }
    }

    /**
     * Creates a {@code PriorityQueue} containing the elements in the
     * specified priority queue.  This priority queue will be
     * ordered according to the same ordering as the given priority
     * queue.
     *
     * @param c the priority queue whose elements are to be placed
     *          into this priority queue
     * @throws ClassCastException   if elements of {@code c} cannot be
     *                              compared to one another according to {@code c}'s
     *                              ordering
     * @throws NullPointerException if the specified priority queue or any
     *                              of its elements are null
     */
    @SuppressWarnings("unchecked")
    public PriorityQueue(PriorityQueue<? extends E> c) {
        this.comparator = (Comparator<? super E>) c.comparator();
        initFromPriorityQueue(c);
    }

    /**
     * Creates a {@code PriorityQueue} containing the elements in the
     * specified sorted set.   This priority queue will be ordered
     * according to the same ordering as the given sorted set.
     *
     * @param c the sorted set whose elements are to be placed
     *          into this priority queue
     * @throws ClassCastException   if elements of the specified sorted
     *                              set cannot be compared to one another according to the
     *                              sorted set's ordering
     * @throws NullPointerException if the specified sorted set or any
     *                              of its elements are null
     */
    @SuppressWarnings("unchecked")
    public PriorityQueue(SortedSet<? extends E> c) {
        this.comparator = (Comparator<? super E>) c.comparator();
        initElementsFromCollection(c);
    }

    private void initFromPriorityQueue(PriorityQueue<? extends E> c) {
        if (c.getClass() == PriorityQueue.class) {
            this.queue = c.toArray();
            this.size = c.size();
        } else {
            initFromCollection(c);
        }
    }

    private void initElementsFromCollection(Collection<? extends E> c) {
        Object[] a = c.toArray();
        // If c.toArray incorrectly doesn't return Object[], copy it.
        if (a.getClass() != Object[].class)
            a = Arrays.copyOf(a, a.length, Object[].class);
        int len = a.length;
        if (len == 1 || this.comparator != null)
            for (int i = 0; i < len; i++)
                if (a[i] == null)
                    throw new NullPointerException();
        this.queue = a;
        this.size = a.length;
    }

    /**
     * Initializes queue array with elements from the given Collection.
     *
     * @param c the collection
     */
    private void initFromCollection(Collection<? extends E> c) {
        initElementsFromCollection(c);
        heapify();
    }

    /**
     * The maximum size of array to allocate.
     * 要分配的数组的最大大小。
     * <p>
     * Some VMs reserve some header words in an array.
     * 一些 VM 在数组中保留一些标题字。
     * <p>
     * Attempts to allocate larger arrays may result in
     * OutOfMemoryError: Requested array size exceeds VM limit
     * 尝试分配更大的数组可能会导致 OutOfMemoryError：请求的数组大小超过 VM 限制
     */
    private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;

    /**
     * Increases the capacity of the array.
     * <p>
     * 扩容队列
     *
     * @param minCapacity the desired minimum capacity 所需的最小容量
     */
    private void grow(int minCapacity) {
        // 队列原始长度
        int oldCapacity = queue.length;

        // Double size if small; else grow by 50%
        // 旧容量小于 64 时，容量翻倍 + 2
        // 旧容量大于等于 64 ，容量只增加旧容量的一半
        int newCapacity = oldCapacity + ((oldCapacity < 64) ?
                (oldCapacity + 2) :
                (oldCapacity >> 1));

        // overflow-conscious code
        // 保证新容量的大小不溢出
        if (newCapacity - MAX_ARRAY_SIZE > 0)
            newCapacity = hugeCapacity(minCapacity);

        // 创建出一个新容量大小的新数组并把旧数组元素拷贝过去
        queue = Arrays.copyOf(queue, newCapacity);
    }

    /**
     * 最大容量限制
     *
     * @param minCapacity
     * @return
     */
    private static int hugeCapacity(int minCapacity) {
        // 容量不能 < 0
        if (minCapacity < 0) // overflow
            throw new OutOfMemoryError();

        // 最大使用 Integer.MAX_VALU
        return (minCapacity > MAX_ARRAY_SIZE) ?
                Integer.MAX_VALUE :
                MAX_ARRAY_SIZE;
    }

    /**
     * Inserts the specified element into this priority queue.
     * <p>
     * 将指定元素插入此优先级队列。
     *
     * @return {@code true} (as specified by {@link Collection#add})
     * @throws ClassCastException   if the specified element cannot be
     *                              compared with elements currently in this priority queue
     *                              according to the priority queue's ordering
     * @throws NullPointerException if the specified element is null
     */
    public boolean add(E e) {
        return offer(e);
    }

    /**
     * Inserts the specified element into this priority queue.
     * <p>
     * 将指定元素插入此优先级队列。
     *
     * @return {@code true} (as specified by {@link Queue#offer})
     * @throws ClassCastException   if the specified element cannot be
     *                              compared with elements currently in this priority queue
     *                              according to the priority queue's ordering
     * @throws NullPointerException if the specified element is null
     */
    public boolean offer(E e) {
        // 元素不能为空
        if (e == null)
            throw new NullPointerException();

        // 递增写次数
        modCount++;

        // 获取优先级队列元素个数
        int i = size;

        // 元素个数达到最大容量，则扩容
        if (i >= queue.length)
            grow(i + 1);

        // 元素个数加 1
        size = i + 1;

        // 如果还没有元素，直接插入到数组第一个位置
        if (i == 0)
            queue[0] = e;

            // 否则，将元素插入到数组最后一个元素的下一个位置（为了描述，其实没有真正插入）自下而上的堆调整
        else
            siftUp(i, e);

        return true;
    }

    /**
     * 返回堆顶元素
     *
     * @return
     */
    @SuppressWarnings("unchecked")
    public E peek() {
        return (size == 0) ? null : (E) queue[0];
    }

    /**
     * 返回指定元素在优先级队列中的下标
     *
     * @param o
     * @return
     */
    private int indexOf(Object o) {
        if (o != null) {
            // 遍历数组
            for (int i = 0; i < size; i++)
                if (o.equals(queue[i]))
                    return i;
        }
        return -1;
    }

    /**
     * Removes a single instance of the specified element from this queue,
     * if it is present.  More formally, removes an element {@code e} such
     * that {@code o.equals(e)}, if this queue contains one or more such
     * elements.  Returns {@code true} if and only if this queue contained
     * the specified element (or equivalently, if this queue changed as a
     * result of the call).
     * <p>
     * 从队列中移除指定的元素，如果元素不存在，则返回 false
     *
     * @param o element to be removed from this queue, if present
     * @return {@code true} if this queue changed as a result of the call
     */
    public boolean remove(Object o) {
        // 获取指定元素在队列中的下标
        int i = indexOf(o);

        // 如果元素不存在，则返回 false
        if (i == -1)
            return false;

            // 如果元素存在，则从队列中移除该元素
        else {
            removeAt(i);
            return true;
        }
    }

    /**
     * Version of remove using reference equality, not equals.
     * Needed by iterator.remove.
     *
     * @param o element to be removed from this queue, if present
     * @return {@code true} if removed
     */
    boolean removeEq(Object o) {
        for (int i = 0; i < size; i++) {
            if (o == queue[i]) {
                removeAt(i);
                return true;
            }
        }
        return false;
    }

    /**
     * Removes the ith element from queue.
     * <p>
     * 从队列中移除下标为 i 的元素。
     *
     * <p>
     * Normally this method leaves the elements at up to i-1,
     * inclusive, untouched.  Under these circumstances, it returns
     * null.  Occasionally, in order to maintain the heap invariant,
     * it must swap a later element of the list with one earlier than
     * i.  Under these circumstances, this method returns the element
     * that was previously at the end of the list and is now at some
     * position before i. This fact is used by iterator.remove so as to
     * avoid missing traversing elements.
     */
    @SuppressWarnings("unchecked")
    private E removeAt(int i) {
        // assert i >= 0 && i < size;
        // 优先级队列写操作次数累加
        modCount++;

        // 优先级队列数量减 1
        int s = --size;

        // 如果是最后一个元素，直接删除即可
        if (s == i) // removed last element
            queue[i] = null;

            // 如果不是最后一个元素，则需要调整堆，从下标位置开始调整
        else {
            E moved = (E) queue[s];
            queue[s] = null;
            // 将 moved 移到队列的 i 位置（打算插入 i 位置），再做自上而下的堆调整
            siftDown(i, moved);

            // 如果 moved 确实插入到了 i 位置，那么再尝试 自下而上调整堆
            if (queue[i] == moved) {
                siftUp(i, moved);
                if (queue[i] != moved)
                    return moved;
            }
        }
        return null;
    }

    /**
     * Returns {@code true} if this queue contains the specified element.
     * More formally, returns {@code true} if and only if this queue contains
     * at least one element {@code e} such that {@code o.equals(e)}.
     *
     * @param o object to be checked for containment in this queue
     * @return {@code true} if this queue contains the specified element
     */
    public boolean contains(Object o) {
        return indexOf(o) != -1;
    }

    /**
     * Returns an array containing all of the elements in this queue.
     * The elements are in no particular order.
     *
     * <p>The returned array will be "safe" in that no references to it are
     * maintained by this queue.  (In other words, this method must allocate
     * a new array).  The caller is thus free to modify the returned array.
     *
     * <p>This method acts as bridge between array-based and collection-based
     * APIs.
     *
     * @return an array containing all of the elements in this queue
     */
    public Object[] toArray() {
        return Arrays.copyOf(queue, size);
    }

    /**
     * Returns an array containing all of the elements in this queue; the
     * runtime type of the returned array is that of the specified array.
     * The returned array elements are in no particular order.
     * If the queue fits in the specified array, it is returned therein.
     * Otherwise, a new array is allocated with the runtime type of the
     * specified array and the size of this queue.
     *
     * <p>If the queue fits in the specified array with room to spare
     * (i.e., the array has more elements than the queue), the element in
     * the array immediately following the end of the collection is set to
     * {@code null}.
     *
     * <p>Like the {@link #toArray()} method, this method acts as bridge between
     * array-based and collection-based APIs.  Further, this method allows
     * precise control over the runtime type of the output array, and may,
     * under certain circumstances, be used to save allocation costs.
     *
     * <p>Suppose {@code x} is a queue known to contain only strings.
     * The following code can be used to dump the queue into a newly
     * allocated array of {@code String}:
     *
     * <pre> {@code String[] y = x.toArray(new String[0]);}</pre>
     * <p>
     * Note that {@code toArray(new Object[0])} is identical in function to
     * {@code toArray()}.
     *
     * @param a the array into which the elements of the queue are to
     *          be stored, if it is big enough; otherwise, a new array of the
     *          same runtime type is allocated for this purpose.
     * @return an array containing all of the elements in this queue
     * @throws ArrayStoreException  if the runtime type of the specified array
     *                              is not a supertype of the runtime type of every element in
     *                              this queue
     * @throws NullPointerException if the specified array is null
     */
    @SuppressWarnings("unchecked")
    public <T> T[] toArray(T[] a) {
        final int size = this.size;
        if (a.length < size)
            // Make a new array of a's runtime type, but my contents:
            return (T[]) Arrays.copyOf(queue, size, a.getClass());
        System.arraycopy(queue, 0, a, 0, size);
        if (a.length > size)
            a[size] = null;
        return a;
    }

    /**
     * Returns an iterator over the elements in this queue. The iterator
     * does not return the elements in any particular order.
     *
     * @return an iterator over the elements in this queue
     */
    public Iterator<E> iterator() {
        return new Itr();
    }

    private final class Itr implements Iterator<E> {
        /**
         * Index (into queue array) of element to be returned by
         * subsequent call to next.
         */
        private int cursor = 0;

        /**
         * Index of element returned by most recent call to next,
         * unless that element came from the forgetMeNot list.
         * Set to -1 if element is deleted by a call to remove.
         */
        private int lastRet = -1;

        /**
         * A queue of elements that were moved from the unvisited portion of
         * the heap into the visited portion as a result of "unlucky" element
         * removals during the iteration.  (Unlucky element removals are those
         * that require a siftup instead of a siftdown.)  We must visit all of
         * the elements in this list to complete the iteration.  We do this
         * after we've completed the "normal" iteration.
         * <p>
         * We expect that most iterations, even those involving removals,
         * will not need to store elements in this field.
         */
        private ArrayDeque<E> forgetMeNot = null;

        /**
         * Element returned by the most recent call to next iff that
         * element was drawn from the forgetMeNot list.
         */
        private E lastRetElt = null;

        /**
         * The modCount value that the iterator believes that the backing
         * Queue should have.  If this expectation is violated, the iterator
         * has detected concurrent modification.
         */
        private int expectedModCount = modCount;

        public boolean hasNext() {
            return cursor < size ||
                    (forgetMeNot != null && !forgetMeNot.isEmpty());
        }

        @SuppressWarnings("unchecked")
        public E next() {
            if (expectedModCount != modCount)
                throw new ConcurrentModificationException();
            if (cursor < size)
                return (E) queue[lastRet = cursor++];
            if (forgetMeNot != null) {
                lastRet = -1;
                lastRetElt = forgetMeNot.poll();
                if (lastRetElt != null)
                    return lastRetElt;
            }
            throw new NoSuchElementException();
        }

        public void remove() {
            if (expectedModCount != modCount)
                throw new ConcurrentModificationException();
            if (lastRet != -1) {
                E moved = PriorityQueue.this.removeAt(lastRet);
                lastRet = -1;
                if (moved == null)
                    cursor--;
                else {
                    if (forgetMeNot == null)
                        forgetMeNot = new ArrayDeque<>();
                    forgetMeNot.add(moved);
                }
            } else if (lastRetElt != null) {
                PriorityQueue.this.removeEq(lastRetElt);
                lastRetElt = null;
            } else {
                throw new IllegalStateException();
            }
            expectedModCount = modCount;
        }
    }

    public int size() {
        return size;
    }

    /**
     * Removes all of the elements from this priority queue.
     * The queue will be empty after this call returns.
     */
    public void clear() {
        modCount++;
        for (int i = 0; i < size; i++)
            queue[i] = null;
        size = 0;
    }

    /**
     * 出队
     *
     * @return
     */
    @SuppressWarnings("unchecked")
    public E poll() {
        // 队列为空
        if (size == 0)
            return null;

        // 队列元素个数减 1
        int s = --size;

        // 队列写操作次数递增
        modCount++;

        // 获取队列首元素
        E result = (E) queue[0];

        // 获取队列末元素
        E x = (E) queue[s];

        // 将队列末元素删除
        queue[s] = null;

        // 出队后队列中还有元素（因为移除的是堆顶元素，此时堆的特性不能满足，必须需要调整）
        if (s != 0)
            // 将队列末元素移到队列首，再做自上而下的调整
            siftDown(0, x);

        // 返回弹出的元素
        return result;
    }



    /**
     * Inserts item x at position k, maintaining heap invariant by
     * promoting x up the tree until it is greater than or equal to
     * its parent, or is the root.
     * <p>
     * 在位置 k 处插入项 x，通过将 x 提升到树上直到它大于或等于其父项或者是根来保持堆不变。
     * <p>
     * To simplify and speed up coercions and comparisons. the
     * Comparable and Comparator versions are separated into different
     * methods that are otherwise identical. (Similarly for siftDown.)
     * <p>
     * 简化和加速强制和比较。 Comparable 和 Comparator 版本被分成不同的方法，这些方法在其他方面是相同的。
     *
     * @param k the position to fill 计划要插入的位置
     * @param x the item to insert 要插入的元素
     */
    private void siftUp(int k, E x) {
        // 根据是否有比较器，使用不同的方法
        if (comparator != null)
            // 使用比较器排序
            siftUpUsingComparator(k, x);
        else
            // 按照元素自然顺序
            siftUpComparable(k, x);
    }

    /**
     * 按照元素自然顺序排序（实现 Comparable 接口）调整堆
     *
     * @param k 要填补的位置
     * @param x 要插入的元素
     */
    @SuppressWarnings("unchecked")
    private void siftUpComparable(int k, E x) {
        // 转化类型，这也要求元素必须实现 Comparable 接口
        Comparable<? super E> key = (Comparable<? super E>) x;
        // 自下向上调整堆
        while (k > 0) {
            // 找到父节点的位置， 因为元素是从0开始的，所以减1之后再除以2
            int parent = (k - 1) >>> 1;

            // 父节点的值
            Object e = queue[parent];

            // 比较插入的元素与父节点的值，如果比父节点大，则跳出循环，否则交换位置
            // todo 从这里也能看出是小顶堆
            if (key.compareTo((E) e) >= 0)
                break;

            // 与父节点交换位置，父节点位置空出来了
            queue[k] = e;

            // 此时，元素要插入的位置移到了父节点的位置，继续与父节点再比较以调整堆
            k = parent;
        }

        // 最后找到应该插入的位置，放入元素
        queue[k] = key;
    }

    /**
     * 按照比较器 Comparator 调整堆
     *
     * @param k 要填补的位置
     * @param x 要插入的元素
     */
    @SuppressWarnings("unchecked")
    private void siftUpUsingComparator(int k, E x) {
        // 自底向上调整堆
        while (k > 0) {
            // 找到父节点的位置， 因为元素是从0开始的，所以减1之后再除以2
            int parent = (k - 1) >>> 1;

            // 父节点的值
            Object e = queue[parent];

            // 比较插入的元素与父节点的值，如果比父节点大，则跳出循环，否则交换位置
            // todo 从这里也能看出是小顶堆
            if (comparator.compare(x, (E) e) >= 0)
                break;

            // 与父节点交换位置，父节点位置空出来了
            queue[k] = e;

            // 此时，元素要插入的位置移到了父节点的位置，继续与父节点再比较以调整堆
            k = parent;
        }

        // 最后找到应该插入的位置，放入元素
        queue[k] = x;
    }

    /**
     * Inserts item x at position k, maintaining heap invariant by
     * demoting x down the tree repeatedly until it is less than or
     * equal to its children or is a leaf.
     * <p>
     * 在位置 k 处插入项 x
     * <p>
     * 通过重复将 x 降级到树下，直到它小于或等于其子项或者是叶子来保持堆不变。即自上而下的堆调整。
     *
     * @param k the position to fill 要填补的位置
     * @param x the item to insert 要插入的元素
     */
    private void siftDown(int k, E x) {
        // 根据是否有比较器，选择不同的方法
        if (comparator != null)
            siftDownUsingComparator(k, x);
        else
            siftDownComparable(k, x);
    }

    /**
     * 根据元素自然顺序，自上而下调整堆
     * <p>
     * 本质：将要插入的元素放入到它应该在的位置
     *
     * @param k
     * @param x
     */
    @SuppressWarnings("unchecked")
    private void siftDownComparable(int k, E x) {
        Comparable<? super E> key = (Comparable<? super E>) x;
        // 调整的时候，是按照二分的思想往下寻找子节点的，这里只比较一半就可以了
        int half = size >>> 1;        // loop while a non-leaf

        while (k < half) {
            // 寻找子节点位置，这里加 1 是因为元素从 0 号位置开始
            int child = (k << 1) + 1; // assume left child is least

            // 左子节点的值
            Object c = queue[child];

            // 右子节点的位置
            int right = child + 1;

            // 如果右子节点存在，那么取左右节点中较小的值
            if (right < size &&
                    ((Comparable<? super E>) c).compareTo((E) queue[right]) > 0)
                c = queue[child = right];

            // 如果要插入元素比子节点都小，则结束，说明找到对应的位置了
            if (key.compareTo((E) c) <= 0)
                break;

            // 如果比较小的子节点还大，则交换位置
            queue[k] = c;

            // 指针移动到子节点位置，继续往下调整堆
            k = child;
        }

        // 找到正确的位置，放入元素
        queue[k] = key;
    }

    /**
     * 根据比较器，自上而下调整堆
     *
     * 本质：将要插入的元素放入到它应该在的位置
     *
     * @param k 要填补的位置
     * @param x 要插入的元素
     */
    @SuppressWarnings("unchecked")
    private void siftDownUsingComparator(int k, E x) {
        // 调整的时候，是按照二分的思想往下寻找子节点的，这里只比较一半就可以了
        int half = size >>> 1;

        // 不断往下比较
        while (k < half) {
            // 寻找子节点位置，这里加 1 是因为元素从 0 号位置开始
            int child = (k << 1) + 1;

            // 左子节点的值
            Object c = queue[child];

            // 右子节点的位置
            int right = child + 1;

            // 如果右子节点存在，那么取左右节点中较小的值
            if (right < size &&
                    comparator.compare((E) c, (E) queue[right]) > 0)
                c = queue[child = right];

            // 如果要插入元素比子节点都小，则结束，说明找到对应的位置了
            if (comparator.compare(x, (E) c) <= 0)
                break;

            // 如果比较小的子节点还大，则交换位置
            queue[k] = c;

            // 指针移动到子节点位置，继续往下调整堆
            k = child;
        }

        // 找到正确的位置，放入元素
        queue[k] = x;
    }

    /**
     * Establishes the heap invariant (described above) in the entire tree,
     * assuming nothing about the order of the elements prior to the call.
     */
    @SuppressWarnings("unchecked")
    private void heapify() {
        for (int i = (size >>> 1) - 1; i >= 0; i--)
            siftDown(i, (E) queue[i]);
    }

    /**
     * Returns the comparator used to order the elements in this
     * queue, or {@code null} if this queue is sorted according to
     * the {@linkplain Comparable natural ordering} of its elements.
     *
     * @return the comparator used to order this queue, or
     * {@code null} if this queue is sorted according to the
     * natural ordering of its elements
     */
    public Comparator<? super E> comparator() {
        return comparator;
    }

    /**
     * Saves this queue to a stream (that is, serializes it).
     *
     * @param s the stream
     * @serialData The length of the array backing the instance is
     * emitted (int), followed by all of its elements
     * (each an {@code Object}) in the proper order.
     */
    private void writeObject(java.io.ObjectOutputStream s)
            throws java.io.IOException {
        // Write out element count, and any hidden stuff
        s.defaultWriteObject();

        // Write out array length, for compatibility with 1.5 version
        s.writeInt(Math.max(2, size + 1));

        // Write out all elements in the "proper order".
        for (int i = 0; i < size; i++)
            s.writeObject(queue[i]);
    }

    /**
     * Reconstitutes the {@code PriorityQueue} instance from a stream
     * (that is, deserializes it).
     *
     * @param s the stream
     */
    private void readObject(java.io.ObjectInputStream s)
            throws java.io.IOException, ClassNotFoundException {
        // Read in size, and any hidden stuff
        s.defaultReadObject();

        // Read in (and discard) array length
        s.readInt();

        SharedSecrets.getJavaOISAccess().checkArray(s, Object[].class, size);
        queue = new Object[size];

        // Read in all elements.
        for (int i = 0; i < size; i++)
            queue[i] = s.readObject();

        // Elements are guaranteed to be in "proper order", but the
        // spec has never explained what that might be.
        heapify();
    }

    /**
     * Creates a <em><a href="Spliterator.html#binding">late-binding</a></em>
     * and <em>fail-fast</em> {@link Spliterator} over the elements in this
     * queue.
     *
     * <p>The {@code Spliterator} reports {@link Spliterator#SIZED},
     * {@link Spliterator#SUBSIZED}, and {@link Spliterator#NONNULL}.
     * Overriding implementations should document the reporting of additional
     * characteristic values.
     *
     * @return a {@code Spliterator} over the elements in this queue
     * @since 1.8
     */
    public final Spliterator<E> spliterator() {
        return new PriorityQueueSpliterator<E>(this, 0, -1, 0);
    }

    static final class PriorityQueueSpliterator<E> implements Spliterator<E> {
        /*
         * This is very similar to ArrayList Spliterator, except for
         * extra null checks.
         */
        private final PriorityQueue<E> pq;
        private int index;            // current index, modified on advance/split
        private int fence;            // -1 until first use
        private int expectedModCount; // initialized when fence set

        /**
         * Creates new spliterator covering the given range
         */
        PriorityQueueSpliterator(PriorityQueue<E> pq, int origin, int fence,
                                 int expectedModCount) {
            this.pq = pq;
            this.index = origin;
            this.fence = fence;
            this.expectedModCount = expectedModCount;
        }

        private int getFence() { // initialize fence to size on first use
            int hi;
            if ((hi = fence) < 0) {
                expectedModCount = pq.modCount;
                hi = fence = pq.size;
            }
            return hi;
        }

        public PriorityQueueSpliterator<E> trySplit() {
            int hi = getFence(), lo = index, mid = (lo + hi) >>> 1;
            return (lo >= mid) ? null :
                    new PriorityQueueSpliterator<E>(pq, lo, index = mid,
                            expectedModCount);
        }

        @SuppressWarnings("unchecked")
        public void forEachRemaining(Consumer<? super E> action) {
            int i, hi, mc; // hoist accesses and checks from loop
            PriorityQueue<E> q;
            Object[] a;
            if (action == null)
                throw new NullPointerException();
            if ((q = pq) != null && (a = q.queue) != null) {
                if ((hi = fence) < 0) {
                    mc = q.modCount;
                    hi = q.size;
                } else
                    mc = expectedModCount;
                if ((i = index) >= 0 && (index = hi) <= a.length) {
                    for (E e; ; ++i) {
                        if (i < hi) {
                            if ((e = (E) a[i]) == null) // must be CME
                                break;
                            action.accept(e);
                        } else if (q.modCount != mc)
                            break;
                        else
                            return;
                    }
                }
            }
            throw new ConcurrentModificationException();
        }

        public boolean tryAdvance(Consumer<? super E> action) {
            if (action == null)
                throw new NullPointerException();
            int hi = getFence(), lo = index;
            if (lo >= 0 && lo < hi) {
                index = lo + 1;
                @SuppressWarnings("unchecked") E e = (E) pq.queue[lo];
                if (e == null)
                    throw new ConcurrentModificationException();
                action.accept(e);
                if (pq.modCount != expectedModCount)
                    throw new ConcurrentModificationException();
                return true;
            }
            return false;
        }

        public long estimateSize() {
            return (long) (getFence() - index);
        }

        public int characteristics() {
            return Spliterator.SIZED | Spliterator.SUBSIZED | Spliterator.NONNULL;
        }
    }
}
