/*
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

/*
 *
 *
 *
 *
 *
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package com.code.queue;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.AbstractQueue;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;

/**
 * An optionally-bounded {@linkplain BlockingQueue blocking queue} based on
 * linked nodes.
 * This queue orders elements FIFO (first-in-first-out).
 * The <em>head</em> of the queue is that element that has been on the
 * queue the longest time.
 * The <em>tail</em> of the queue is that element that has been on the
 * queue the shortest time. New elements
 * are inserted at the tail of the queue, and the queue retrieval
 * operations obtain elements at the head of the queue.
 * Linked queues typically have higher throughput than array-based queues but
 * less predictable performance in most concurrent applications.
 *
 * <p>The optional capacity bound constructor argument serves as a
 * way to prevent excessive queue expansion. The capacity, if unspecified,
 * is equal to {@link Integer#MAX_VALUE}.  Linked nodes are
 * dynamically created upon each insertion unless this would bring the
 * queue above capacity.
 *
 * <p>This class and its iterator implement all of the
 * <em>optional</em> methods of the {@link Collection} and {@link
 * Iterator} interfaces.
 *
 * <p>This class is a member of the
 * <a href="{@docRoot}/../technotes/guides/collections/index.html">
 * Java Collections Framework</a>.
 *
 * @param <E> the type of elements held in this collection
 * @author Doug Lea
 * @since 1.5
 */
public class LinkedBlockingQueue<E> extends AbstractQueue<E>
        implements BlockingQueue<E>, java.io.Serializable {
    private static final long serialVersionUID = -6903933977591709194L;

    /*
     * A variant of the "two lock queue" algorithm.  The putLock gates
     * entry to put (and offer), and has an associated condition for
     * waiting puts.  Similarly for the takeLock.  The "count" field
     * that they both rely on is maintained as an atomic to avoid
     * needing to get both locks in most cases. Also, to minimize need
     * for puts to get takeLock and vice-versa, cascading notifies are
     * used. When a put notices that it has enabled at least one take,
     * it signals taker. That taker in turn signals others if more
     * items have been entered since the signal. And symmetrically for
     * takes signalling puts. Operations such as remove(Object) and
     * iterators acquire both locks.
     *
     * Visibility between writers and readers is provided as follows:
     *
     * Whenever an element is enqueued, the putLock is acquired and
     * count updated.  A subsequent reader guarantees visibility to the
     * enqueued Node by either acquiring the putLock (via fullyLock)
     * or by acquiring the takeLock, and then reading n = count.get();
     * this gives visibility to the first n items.
     *
     * To implement weakly consistent iterators, it appears we need to
     * keep all Nodes GC-reachable from a predecessor dequeued Node.
     * That would cause two problems:
     * - allow a rogue Iterator to cause unbounded memory retention
     * - cause cross-generational linking of old Nodes to new Nodes if
     *   a Node was tenured while live, which generational GCs have a
     *   hard time dealing with, causing repeated major collections.
     * However, only non-deleted Nodes need to be reachable from
     * dequeued Nodes, and reachability does not necessarily have to
     * be of the kind understood by the GC.  We use the trick of
     * linking a Node that has just been dequeued to itself.  Such a
     * self-link implicitly means to advance to head.next.
     */

    /*
      说明：
      1. LinkedBlockingQueue 是一个基于链表（单链表）实现的先进先出的阻塞队列。
      2. 在入队和出队时（不含删除、清除），分别使用两把独占锁保证安全，也提高了并发度
      3. 在涉及到遍历链表的操作时，需要同时使用到两把锁，因为遍历链表的过程不能有出队和入队的操作，
         具体包括：判断是否包含 contains 、删除元素 remove、清除 clear、toString、toArray 等
     */

    /**
     * Linked list node class
     * <p>
     * 链表节点
     */
    static class Node<E> {
        /**
         * 存放数据
         */
        E item;

        /**
         * 后继指针
         * One of:
         * - the real successor Node
         * - this Node, meaning the successor is head.next
         * - null, meaning there is no successor (this is the last node)
         */
        Node<E> next;

        Node(E x) {
            item = x;
        }
    }

    /**
     * The capacity bound, or Integer.MAX_VALUE if none
     * <p>
     * 队列的容量，默认容量是 Integer.MAX_VALUE
     */
    private final int capacity;

    /**
     * Current number of elements
     * <p>
     * 队列中元素的个数
     */
    private final AtomicInteger count = new AtomicInteger();

    /**
     * Head of linked list.
     * Invariant: head.item == null
     * <p>
     * 队列的头节点，是个虚节点，后继节点才是真实节点
     */
    transient Node<E> head;

    /**
     * Tail of linked list.
     * Invariant: last.next == null
     * <p>
     * 队列的尾节点
     */
    private transient Node<E> last;

    /**
     * Lock held by take, poll, etc
     * <p>
     * 出队 take、poll 使用的锁
     */
    private final ReentrantLock takeLock = new ReentrantLock();

    /**
     * Wait queue for waiting takes
     * <p>
     * 等待出队的条件，即队列非空条件
     */
    private final Condition notEmpty = takeLock.newCondition();

    /**
     * Lock held by put, offer, etc
     * <p>
     * 入队 put、offer 使用的锁
     */
    private final ReentrantLock putLock = new ReentrantLock();

    /**
     * Wait queue for waiting puts
     * <p>
     * 等待入队的条件，即队列非满条件
     */
    private final Condition notFull = putLock.newCondition();


    /**
     * Signals a waiting take. Called only from put/offer (which do not
     * otherwise ordinarily lock takeLock.)
     * <p>
     * 唤醒等待获取元素的线程。仅从 put/offer 调用（通常不会锁定 takeLock。）
     */
    private void signalNotEmpty() {
        // 唤醒前先拿到出队锁
        final ReentrantLock takeLock = this.takeLock;
        takeLock.lock();

        try {
            // 唤醒阻塞在非空条件队列中的某个线程
            notEmpty.signal();

            // 释放出队锁
        } finally {
            takeLock.unlock();
        }
    }

    /**
     * Signals a waiting put. Called only from take/poll.
     * <p>
     * 唤醒等待添加元素的线程。仅从 take/poll 调用
     */
    private void signalNotFull() {
        // 唤醒前先拿到入队锁
        final ReentrantLock putLock = this.putLock;
        putLock.lock();


        try {
            // 唤醒阻塞在非满条件队列中的某个线程
            notFull.signal();

            // 释放入队锁
        } finally {
            putLock.unlock();
        }
    }

    /**
     * 入队底层操作
     * <p>
     * Links node at end of queue.
     *
     * @param node the node
     */
    private void enqueue(Node<E> node) {
        // assert putLock.isHeldByCurrentThread();
        // assert last.next == null;
        // 尾插法
        last = last.next = node;
    }

    /**
     * 出队底层操作
     * <p>
     * Removes a node from head of queue.
     *
     * @return the node
     */
    private E dequeue() {
        // assert takeLock.isHeldByCurrentThread();
        // assert head.item == null;
        Node<E> h = head;            // head 是一个虚节点
        Node<E> first = h.next;      // 获取虚节点后一个节点，也就是真正的节点
        h.next = h;                  // help GC
        head = first;                // 重新设置 head
        E x = first.item;            // 获取出队的值
        first.item = null;           // 置空元素
        return x;
    }

    /**
     * Locks to prevent both puts and takes.
     */
    void fullyLock() {
        putLock.lock();
        takeLock.lock();
    }

    /**
     * Unlocks to allow both puts and takes.
     */
    void fullyUnlock() {
        takeLock.unlock();
        putLock.unlock();
    }

//     /**
//      * Tells whether both locks are held by current thread.
//      */
//     boolean isFullyLocked() {
//         return (putLock.isHeldByCurrentThread() &&
//                 takeLock.isHeldByCurrentThread());
//     }


    /**
     * 默认无界的队列
     * <p>
     * Creates a {@code LinkedBlockingQueue} with a capacity of
     * {@link Integer#MAX_VALUE}.
     */
    public LinkedBlockingQueue() {
        this(Integer.MAX_VALUE);
    }

    /**
     * Creates a {@code LinkedBlockingQueue} with the given (fixed) capacity.
     * <p>
     * 创建给定容量的队列
     *
     * @param capacity the capacity of this queue
     * @throws IllegalArgumentException if {@code capacity} is not greater
     *                                  than zero
     */
    public LinkedBlockingQueue(int capacity) {
        // 容量大小不能 <= 0
        if (capacity <= 0) throw new IllegalArgumentException();

        // 设置队列容量大小
        this.capacity = capacity;

        // 初始化链表
        last = head = new Node<E>(null);
    }

    /**
     * Creates a {@code LinkedBlockingQueue} with a capacity of
     * {@link Integer#MAX_VALUE}, initially containing the elements of the
     * given collection,
     * added in traversal order of the collection's iterator.
     *
     * @param c the collection of elements to initially contain
     * @throws NullPointerException if the specified collection or any
     *                              of its elements are null
     */
    public LinkedBlockingQueue(Collection<? extends E> c) {
        this(Integer.MAX_VALUE);
        final ReentrantLock putLock = this.putLock;
        putLock.lock(); // Never contended, but necessary for visibility
        try {
            int n = 0;
            for (E e : c) {
                if (e == null)
                    throw new NullPointerException();
                if (n == capacity)
                    throw new IllegalStateException("Queue full");
                enqueue(new Node<E>(e));
                ++n;
            }
            count.set(n);
        } finally {
            putLock.unlock();
        }
    }

    // this doc comment is overridden to remove the reference to collections
    // greater in size than Integer.MAX_VALUE

    /**
     * Returns the number of elements in this queue.
     *
     * @return the number of elements in this queue
     */
    public int size() {
        return count.get();
    }

    // this doc comment is a modified copy of the inherited doc comment,
    // without the reference to unlimited queues.

    /**
     * Returns the number of additional elements that this queue can ideally
     * (in the absence of memory or resource constraints) accept without
     * blocking. This is always equal to the initial capacity of this queue
     * less the current {@code size} of this queue.
     *
     * <p>Note that you <em>cannot</em> always tell if an attempt to insert
     * an element will succeed by inspecting {@code remainingCapacity}
     * because it may be the case that another thread is about to
     * insert or remove an element.
     */
    public int remainingCapacity() {
        return capacity - count.get();
    }

    /**
     * Inserts the specified element at the tail of this queue, waiting if
     * necessary for space to become available.
     * <p>
     * 在此队列的尾部插入指定元素，如有必要，等待空间可用。
     *
     * @throws InterruptedException {@inheritDoc}
     * @throws NullPointerException {@inheritDoc}
     */
    public void put(E e) throws InterruptedException {
        // 要添加的元素不能为空
        if (e == null) throw new NullPointerException();

        // Note: convention in all put/take/etc is to preset local var
        // holding count negative to indicate failure unless set.
        // 初始值为 -1
        int c = -1;

        // 创建一个节点存元素 e
        Node<E> node = new Node<E>(e);

        // 获取入队锁
        final ReentrantLock putLock = this.putLock;

        // 获取队列中元素个数
        final AtomicInteger count = this.count;
        putLock.lockInterruptibly();

        try {
            /*
             * Note that count is used in wait guard even though it is
             * not protected by lock. This works because count can
             * only decrease at this point (all other puts are shut
             * out by lock), and we (or some other waiting put) are
             * signalled if it ever changes from capacity. Similarly
             * for all other uses of count in other wait guards.
             */
            // 队列满了，则进入非满条件队列进行等待
            while (count.get() == capacity) {
                notFull.await();
            }

            // 入队，链表操作，尾插法
            enqueue(node);

            // 获取并更新队列中元素个数
            c = count.getAndIncrement();

            // 如果 c+1 < 队列容量，说明队列还可以继续添加元素，则唤醒在非满条件队列中等待的线程
            // 这里 c+1 是因为前面已经加入了一个元素
            if (c + 1 < capacity)
                notFull.signal();

            // 释放入队锁
        } finally {
            putLock.unlock();
        }

        // c == 0 说明原来queue是空的, 现在添加了元素，所以这里 signalNotEmpty 一下, 唤醒正在 poll/take 等待中的线程
        if (c == 0)
            signalNotEmpty();
    }

    /**
     * Inserts the specified element at the tail of this queue, waiting if
     * necessary up to the specified wait time for space to become available.
     * <p>
     * 在此队列的尾部插入指定元素，如有必要，等待指定的等待时间以使空间可用。
     * <p>
     * 和 put 方法基本类似，唯一区别是，该方法不会一直阻塞等待，有个最大等待时长
     *
     * @return {@code true} if successful, or {@code false} if
     * the specified waiting time elapses before space is available
     * @throws InterruptedException {@inheritDoc}
     * @throws NullPointerException {@inheritDoc}
     */
    public boolean offer(E e, long timeout, TimeUnit unit)
            throws InterruptedException {

        // 要添加的元素不能为空
        if (e == null) throw new NullPointerException();

        // 超时等待时间
        long nanos = unit.toNanos(timeout);

        int c = -1;

        // 获取入队锁
        final ReentrantLock putLock = this.putLock;
        final AtomicInteger count = this.count;
        putLock.lockInterruptibly();

        try {
            // 如果队列满了，则等待指定的时间，尽最大努力添加元素
            while (count.get() == capacity) {
                // 超时了还没有空的空间，那么就不添加了
                if (nanos <= 0)
                    return false;
                nanos = notFull.awaitNanos(nanos);
            }

            // 入队，可能条件如下：
            // 1）队列没有满  2）在指定的超时时间内有空的空间了，线程提前被唤醒
            enqueue(new Node<E>(e));

            // 获取并更新队列中元素个数
            c = count.getAndIncrement();

            // 如果 c+1 < 队列容量，说明队列还可以继续添加元素，则唤醒在非满条件队列中等待的线程
            // 这里 c+1 是因为前面已经加入了一个元素
            if (c + 1 < capacity)
                notFull.signal();

            // 释放入队锁
        } finally {
            putLock.unlock();
        }

        // c == 0 说明原来queue是空的, 现在添加了元素，所以这里 signalNotEmpty 一下, 唤醒正在 poll/take 等待中的线程
        if (c == 0)
            signalNotEmpty();

        return true;
    }

    /**
     * Inserts the specified element at the tail of this queue if it is
     * possible to do so immediately without exceeding the queue's capacity,
     * returning {@code true} upon success and {@code false} if this queue
     * is full.
     * <p>
     * 在不超出队列容量的情况下立即在此队列的尾部插入指定元素，成功时返回 true，如果此队列已满则返回 false。
     * <p>
     * <p>
     * When using a capacity-restricted queue, this method is generally
     * preferable to method {@link BlockingQueue#add add}, which can fail to
     * insert an element only by throwing an exception.
     *
     * @throws NullPointerException if the specified element is null
     */
    public boolean offer(E e) {
        // 要添加的元素不能为空
        if (e == null) throw new NullPointerException();

        // 队列中元素的个数
        final AtomicInteger count = this.count;

        // 如果队列已满，直接返回 false
        if (count.get() == capacity)
            return false;

        int c = -1;

        // 创建一个节点存元素 e
        Node<E> node = new Node<E>(e);

        // 获取入队锁
        final ReentrantLock putLock = this.putLock;
        putLock.lock();
        try {

            // 如果队列没有满，则入队元素，即链表尾插法
            if (count.get() < capacity) {
                enqueue(node);

                // 获取并更新队列中元素个数
                c = count.getAndIncrement();

                // 如果 c+1 < 队列容量，说明队列还可以继续添加元素，则唤醒在非满条件队列中等待的线程
                // 这里 c+1 是因为前面已经加入了一个元素
                if (c + 1 < capacity)
                    notFull.signal();
            }

            // 释放入队锁
        } finally {
            putLock.unlock();
        }

        // c == 0 说明原来queue是空的, 现在添加了元素，所以这里 signalNotEmpty 一下, 唤醒正在 poll/take 等待中的线程
        if (c == 0)
            signalNotEmpty();

        // 如果 count.get() < capacity 不成立，直接释放锁，此时 c==-1
        return c >= 0;
    }

    /**
     * 出队，从链表的头节点开始出队节点
     *
     * @return
     * @throws InterruptedException
     */
    public E take() throws InterruptedException {
        E x;
        int c = -1;

        // 队列中元素的个数
        final AtomicInteger count = this.count;

        // 获取可中断出队锁
        final ReentrantLock takeLock = this.takeLock;
        takeLock.lockInterruptibly();

        try {
            // 如果队列为空，则进入 notEmpty 等待队列进行等待队列非空
            while (count.get() == 0) {
                notEmpty.await();
            }

            // 出队，链表操作
            x = dequeue();

            // 获取并更新队列中元素的个数
            c = count.getAndDecrement();

            // 说明队列中还有元素，唤醒在 notEmpty 等待队列进行等待的线程
            if (c > 1)
                notEmpty.signal();

            // 释放出队锁
        } finally {
            takeLock.unlock();
        }

        // c == capacity 说明队列在本次出队之前是满的，现在出队了一个元素，有空的空间了，可以唤醒 put/offer 操作阻塞的线程（如果有的话）
        if (c == capacity)
            signalNotFull();

        return x;
    }

    /**
     * 超时出队
     *
     * @param timeout
     * @param unit
     * @return
     * @throws InterruptedException
     */
    public E poll(long timeout, TimeUnit unit) throws InterruptedException {
        E x = null;
        int c = -1;

        // 超时等待时长
        long nanos = unit.toNanos(timeout);

        // 获取队列元素的个数
        final AtomicInteger count = this.count;

        // 获取可中断出队锁
        final ReentrantLock takeLock = this.takeLock;
        takeLock.lockInterruptibly();

        try {
            // 队列为空，则进入超时等待
            while (count.get() == 0) {
                // 等待超时也没有元素
                if (nanos <= 0)
                    return null;
                // 超时等待
                nanos = notEmpty.awaitNanos(nanos);
            }

            // 出队，可能情况如下：
            // 1）队列没有空  2）在指定的超时时间内队列又有元素了，线程提前被唤醒
            x = dequeue();

            // 获取并更新队列中元素的个数
            c = count.getAndDecrement();

            // 说明队列中还有元素，唤醒在 notEmpty 等待队列进行等待的线程
            if (c > 1)
                notEmpty.signal();

            // 释放出队锁
        } finally {
            takeLock.unlock();
        }

        // c == capacity 说明队列在本次出队之前是满的，现在出队了一个元素，有空的空间了，可以唤醒 put/offer 操作阻塞的线程（如果有的话）
        if (c == capacity)
            signalNotFull();

        return x;
    }

    /**
     * 尝试出队。和超时出队类似，唯一区别是，在队列为空时该方法不会超时等待，直接返回 null
     *
     * @return
     */
    public E poll() {
        // 获取队列中元素的个数
        final AtomicInteger count = this.count;

        // 如果队列为空，则直接返回 null
        if (count.get() == 0)
            return null;

        E x = null;
        int c = -1;

        // 获取出队锁
        final ReentrantLock takeLock = this.takeLock;
        takeLock.lock();

        try {

            // 如果队列不为空，那么出队
            if (count.get() > 0) {
                // 出队
                x = dequeue();

                // 获取并更新队列中元素的个数
                c = count.getAndDecrement();

                // 说明队列中还有元素，唤醒在 notEmpty 等待队列进行等待的线程
                if (c > 1)
                    notEmpty.signal();
            }

            // 释放出队锁
        } finally {
            takeLock.unlock();
        }

        // c == capacity 说明队列在本次出队之前是满的，现在出队了一个元素，有空的空间了，可以唤醒 put/offer 操作阻塞的线程（如果有的话）
        if (c == capacity)
            signalNotFull();

        return x;
    }

    /**
     * 获取但不移除队列头部元素
     *
     * @return
     */
    public E peek() {
        // 队列为空，直接返回 null
        if (count.get() == 0)
            return null;

        // 获取出队锁
        final ReentrantLock takeLock = this.takeLock;
        takeLock.lock();


        try {
            // 获取有效节点
            Node<E> first = head.next;

            // 返回元素
            if (first == null)
                return null;
            else
                return first.item;

            // 释放出队列锁
        } finally {
            takeLock.unlock();
        }
    }

    /**
     * Unlinks interior Node p with predecessor trail.
     */
    void unlink(Node<E> p, Node<E> trail) {
        // assert isFullyLocked();
        // p.next is not changed, to allow iterators that are
        // traversing p to maintain their weak-consistency guarantee.
        p.item = null;
        trail.next = p.next;
        if (last == p)
            last = trail;
        if (count.getAndDecrement() == capacity)
            notFull.signal();
    }

    /**
     * Removes a single instance of the specified element from this queue,
     * if it is present.  More formally, removes an element {@code e} such
     * that {@code o.equals(e)}, if this queue contains one or more such
     * elements.
     * Returns {@code true} if this queue contained the specified element
     * (or equivalently, if this queue changed as a result of the call).
     *
     * @param o element to be removed from this queue, if present
     * @return {@code true} if this queue changed as a result of the call
     */
    public boolean remove(Object o) {
        if (o == null) return false;
        fullyLock();
        try {
            for (Node<E> trail = head, p = trail.next;
                 p != null;
                 trail = p, p = p.next) {
                if (o.equals(p.item)) {
                    unlink(p, trail);
                    return true;
                }
            }
            return false;
        } finally {
            fullyUnlock();
        }
    }

    /**
     * Atomically removes all of the elements from this queue.
     * The queue will be empty after this call returns.
     */
    public void clear() {
        fullyLock();
        try {
            for (Node<E> p, h = head; (p = h.next) != null; h = p) {
                h.next = h;
                p.item = null;
            }
            head = last;
            // assert head.item == null && head.next == null;
            if (count.getAndSet(0) == capacity)
                notFull.signal();
        } finally {
            fullyUnlock();
        }
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
        if (o == null) return false;
        fullyLock();
        try {
            for (Node<E> p = head.next; p != null; p = p.next)
                if (o.equals(p.item))
                    return true;
            return false;
        } finally {
            fullyUnlock();
        }
    }

    /**
     * Returns an array containing all of the elements in this queue, in
     * proper sequence.
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
        fullyLock();
        try {
            int size = count.get();
            Object[] a = new Object[size];
            int k = 0;
            for (Node<E> p = head.next; p != null; p = p.next)
                a[k++] = p.item;
            return a;
        } finally {
            fullyUnlock();
        }
    }

    /**
     * Returns an array containing all of the elements in this queue, in
     * proper sequence; the runtime type of the returned array is that of
     * the specified array.  If the queue fits in the specified array, it
     * is returned therein.  Otherwise, a new array is allocated with the
     * runtime type of the specified array and the size of this queue.
     *
     * <p>If this queue fits in the specified array with room to spare
     * (i.e., the array has more elements than this queue), the element in
     * the array immediately following the end of the queue is set to
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
     *          same runtime type is allocated for this purpose
     * @return an array containing all of the elements in this queue
     * @throws ArrayStoreException  if the runtime type of the specified array
     *                              is not a supertype of the runtime type of every element in
     *                              this queue
     * @throws NullPointerException if the specified array is null
     */
    @SuppressWarnings("unchecked")
    public <T> T[] toArray(T[] a) {
        fullyLock();
        try {
            int size = count.get();
            if (a.length < size)
                a = (T[]) java.lang.reflect.Array.newInstance
                        (a.getClass().getComponentType(), size);

            int k = 0;
            for (Node<E> p = head.next; p != null; p = p.next)
                a[k++] = (T) p.item;
            if (a.length > k)
                a[k] = null;
            return a;
        } finally {
            fullyUnlock();
        }
    }

    public String toString() {
        fullyLock();
        try {
            Node<E> p = head.next;
            if (p == null)
                return "[]";

            StringBuilder sb = new StringBuilder();
            sb.append('[');
            for (; ; ) {
                E e = p.item;
                sb.append(e == this ? "(this Collection)" : e);
                p = p.next;
                if (p == null)
                    return sb.append(']').toString();
                sb.append(',').append(' ');
            }
        } finally {
            fullyUnlock();
        }
    }



    /**
     * @throws UnsupportedOperationException {@inheritDoc}
     * @throws ClassCastException            {@inheritDoc}
     * @throws NullPointerException          {@inheritDoc}
     * @throws IllegalArgumentException      {@inheritDoc}
     */
    public int drainTo(Collection<? super E> c) {
        return drainTo(c, Integer.MAX_VALUE);
    }

    /**
     * @throws UnsupportedOperationException {@inheritDoc}
     * @throws ClassCastException            {@inheritDoc}
     * @throws NullPointerException          {@inheritDoc}
     * @throws IllegalArgumentException      {@inheritDoc}
     */
    public int drainTo(Collection<? super E> c, int maxElements) {
        if (c == null)
            throw new NullPointerException();
        if (c == this)
            throw new IllegalArgumentException();
        if (maxElements <= 0)
            return 0;
        boolean signalNotFull = false;
        final ReentrantLock takeLock = this.takeLock;
        takeLock.lock();
        try {
            int n = Math.min(maxElements, count.get());
            // count.get provides visibility to first n Nodes
            Node<E> h = head;
            int i = 0;
            try {
                while (i < n) {
                    Node<E> p = h.next;
                    c.add(p.item);
                    p.item = null;
                    h.next = h;
                    h = p;
                    ++i;
                }
                return n;
            } finally {
                // Restore invariants even if c.add() threw
                if (i > 0) {
                    // assert h.item == null;
                    head = h;
                    signalNotFull = (count.getAndAdd(-i) == capacity);
                }
            }
        } finally {
            takeLock.unlock();
            if (signalNotFull)
                signalNotFull();
        }
    }

    /**
     * Returns an iterator over the elements in this queue in proper sequence.
     * The elements will be returned in order from first (head) to last (tail).
     *
     * <p>The returned iterator is
     * <a href="package-summary.html#Weakly"><i>weakly consistent</i></a>.
     *
     * @return an iterator over the elements in this queue in proper sequence
     */
    public Iterator<E> iterator() {
        return new Itr();
    }

    private class Itr implements Iterator<E> {
        /*
         * Basic weakly-consistent iterator.  At all times hold the next
         * item to hand out so that if hasNext() reports true, we will
         * still have it to return even if lost race with a take etc.
         */

        private Node<E> current;
        private Node<E> lastRet;
        private E currentElement;

        Itr() {
            fullyLock();
            try {
                current = head.next;
                if (current != null)
                    currentElement = current.item;
            } finally {
                fullyUnlock();
            }
        }

        public boolean hasNext() {
            return current != null;
        }

        /**
         * Returns the next live successor of p, or null if no such.
         * <p>
         * Unlike other traversal methods, iterators need to handle both:
         * - dequeued nodes (p.next == p)
         * - (possibly multiple) interior removed nodes (p.item == null)
         */
        private Node<E> nextNode(Node<E> p) {
            for (; ; ) {
                Node<E> s = p.next;
                if (s == p)
                    return head.next;
                if (s == null || s.item != null)
                    return s;
                p = s;
            }
        }

        public E next() {
            fullyLock();
            try {
                if (current == null)
                    throw new NoSuchElementException();
                E x = currentElement;
                lastRet = current;
                current = nextNode(current);
                currentElement = (current == null) ? null : current.item;
                return x;
            } finally {
                fullyUnlock();
            }
        }

        public void remove() {
            if (lastRet == null)
                throw new IllegalStateException();
            fullyLock();
            try {
                Node<E> node = lastRet;
                lastRet = null;
                for (Node<E> trail = head, p = trail.next;
                     p != null;
                     trail = p, p = p.next) {
                    if (p == node) {
                        unlink(p, trail);
                        break;
                    }
                }
            } finally {
                fullyUnlock();
            }
        }
    }

    /**
     * A customized variant of Spliterators.IteratorSpliterator
     */
    static final class LBQSpliterator<E> implements Spliterator<E> {
        static final int MAX_BATCH = 1 << 25;  // max batch array size;
        final LinkedBlockingQueue<E> queue;
        Node<E> current;    // current node; null until initialized
        int batch;          // batch size for splits
        boolean exhausted;  // true when no more nodes
        long est;           // size estimate

        LBQSpliterator(LinkedBlockingQueue<E> queue) {
            this.queue = queue;
            this.est = queue.size();
        }

        public long estimateSize() {
            return est;
        }

        public Spliterator<E> trySplit() {
            Node<E> h;
            final LinkedBlockingQueue<E> q = this.queue;
            int b = batch;
            int n = (b <= 0) ? 1 : (b >= MAX_BATCH) ? MAX_BATCH : b + 1;
            if (!exhausted &&
                    ((h = current) != null || (h = q.head.next) != null) &&
                    h.next != null) {
                Object[] a = new Object[n];
                int i = 0;
                Node<E> p = current;
                q.fullyLock();
                try {
                    if (p != null || (p = q.head.next) != null) {
                        do {
                            if ((a[i] = p.item) != null)
                                ++i;
                        } while ((p = p.next) != null && i < n);
                    }
                } finally {
                    q.fullyUnlock();
                }
                if ((current = p) == null) {
                    est = 0L;
                    exhausted = true;
                } else if ((est -= i) < 0L)
                    est = 0L;
                if (i > 0) {
                    batch = i;
                    return Spliterators.spliterator
                            (a, 0, i, Spliterator.ORDERED | Spliterator.NONNULL |
                                    Spliterator.CONCURRENT);
                }
            }
            return null;
        }

        public void forEachRemaining(Consumer<? super E> action) {
            if (action == null) throw new NullPointerException();
            final LinkedBlockingQueue<E> q = this.queue;
            if (!exhausted) {
                exhausted = true;
                Node<E> p = current;
                do {
                    E e = null;
                    q.fullyLock();
                    try {
                        if (p == null)
                            p = q.head.next;
                        while (p != null) {
                            e = p.item;
                            p = p.next;
                            if (e != null)
                                break;
                        }
                    } finally {
                        q.fullyUnlock();
                    }
                    if (e != null)
                        action.accept(e);
                } while (p != null);
            }
        }

        public boolean tryAdvance(Consumer<? super E> action) {
            if (action == null) throw new NullPointerException();
            final LinkedBlockingQueue<E> q = this.queue;
            if (!exhausted) {
                E e = null;
                q.fullyLock();
                try {
                    if (current == null)
                        current = q.head.next;
                    while (current != null) {
                        e = current.item;
                        current = current.next;
                        if (e != null)
                            break;
                    }
                } finally {
                    q.fullyUnlock();
                }
                if (current == null)
                    exhausted = true;
                if (e != null) {
                    action.accept(e);
                    return true;
                }
            }
            return false;
        }

        public int characteristics() {
            return Spliterator.ORDERED | Spliterator.NONNULL |
                    Spliterator.CONCURRENT;
        }
    }

    /**
     * Returns a {@link Spliterator} over the elements in this queue.
     *
     * <p>The returned spliterator is
     * <a href="package-summary.html#Weakly"><i>weakly consistent</i></a>.
     *
     * <p>The {@code Spliterator} reports {@link Spliterator#CONCURRENT},
     * {@link Spliterator#ORDERED}, and {@link Spliterator#NONNULL}.
     *
     * @return a {@code Spliterator} over the elements in this queue
     * @implNote The {@code Spliterator} implements {@code trySplit} to permit limited
     * parallelism.
     * @since 1.8
     */
    public Spliterator<E> spliterator() {
        return new LBQSpliterator<E>(this);
    }

    /**
     * Saves this queue to a stream (that is, serializes it).
     *
     * @param s the stream
     * @throws java.io.IOException if an I/O error occurs
     * @serialData The capacity is emitted (int), followed by all of
     * its elements (each an {@code Object}) in the proper order,
     * followed by a null
     */
    private void writeObject(java.io.ObjectOutputStream s)
            throws java.io.IOException {

        fullyLock();
        try {
            // Write out any hidden stuff, plus capacity
            s.defaultWriteObject();

            // Write out all elements in the proper order.
            for (Node<E> p = head.next; p != null; p = p.next)
                s.writeObject(p.item);

            // Use trailing null as sentinel
            s.writeObject(null);
        } finally {
            fullyUnlock();
        }
    }

    /**
     * Reconstitutes this queue from a stream (that is, deserializes it).
     *
     * @param s the stream
     * @throws ClassNotFoundException if the class of a serialized object
     *                                could not be found
     * @throws java.io.IOException    if an I/O error occurs
     */
    private void readObject(java.io.ObjectInputStream s)
            throws java.io.IOException, ClassNotFoundException {
        // Read in capacity, and any hidden stuff
        s.defaultReadObject();

        count.set(0);
        last = head = new Node<E>(null);

        // Read in all elements and place in queue
        for (; ; ) {
            @SuppressWarnings("unchecked")
            E item = (E) s.readObject();
            if (item == null)
                break;
            add(item);
        }
    }
}
