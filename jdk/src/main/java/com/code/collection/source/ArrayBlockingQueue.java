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

package java.util.concurrent;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.AbstractQueue;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.lang.ref.WeakReference;
import java.util.Spliterators;
import java.util.Spliterator;

/**
 * A bounded {@linkplain BlockingQueue blocking queue} backed by an
 * array.  This queue orders elements FIFO (first-in-first-out).  The
 * <em>head</em> of the queue is that element that has been on the
 * queue the longest time.  The <em>tail</em> of the queue is that
 * element that has been on the queue the shortest time. New elements
 * are inserted at the tail of the queue, and the queue retrieval
 * operations obtain elements at the head of the queue.
 *
 * <p>This is a classic &quot;bounded buffer&quot;, in which a
 * fixed-sized array holds elements inserted by producers and
 * extracted by consumers.  Once created, the capacity cannot be
 * changed.  Attempts to {@code put} an element into a full queue
 * will result in the operation blocking; attempts to {@code take} an
 * element from an empty queue will similarly block.
 *
 * <p>This class supports an optional fairness policy for ordering
 * waiting producer and consumer threads.  By default, this ordering
 * is not guaranteed. However, a queue constructed with fairness set
 * to {@code true} grants threads access in FIFO order. Fairness
 * generally decreases throughput but reduces variability and avoids
 * starvation.
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
 * @desc 以数组实现的阻塞队列
 * @since 1.5
 */
public class ArrayBlockingQueue<E> extends AbstractQueue<E>
        implements BlockingQueue<E>, java.io.Serializable {

    /*
      说明：
      1. 以固定大小数组作为元素的容器，并定义系列描述数组的属性。这个固定大小的数组是可以循环使用的。存取数据遵循：队头取、队尾插；
      2. 基本的增、删、查都要先加锁才能操作
     */

    /**
     * Serialization ID. This class relies on default serialization
     * even for the items array, which is default-serialized, even if
     * it is empty. Otherwise it could not be declared final, which is
     * necessary here.
     */
    private static final long serialVersionUID = -817911632652898426L;

    /**
     * The queued items
     * <p>
     * 存放元素的数组
     */
    final Object[] items;

    /**
     * items index for next take, poll, peek or remove
     * <p>
     * 取元素时的下标；
     * 每从队列中取出一个元素，takeIndex 递增 1，到数组尾部会重置为 0
     */
    int takeIndex;

    /**
     * items index for next put, offer, or add
     * <p>
     * 添加元素的下标
     * 每向队列添加一个元素，putIndex 递增 1，满了会重置为 0
     */
    int putIndex;

    /**
     * Number of elements in the queue
     * <p>
     * 数组中元素个数，即队列的元素个数
     */
    int count;

    /* Concurrency control uses the classic two-condition algorithm found in any textbook.  并发控制使用经典双条件算法。 */

    /**
     * Main lock guarding all access
     * <p>
     * 独占锁
     */
    final ReentrantLock lock;

    /**
     * Condition for waiting takes
     * <p>
     * 当队列为空时，会调用 notEmpty 的 wait 方法，让当前线程等待
     */
    private final Condition notEmpty;

    /**
     * Condition for waiting puts
     * <p>
     * 当队列满了，会调用 notFull 的 wait 方法，让当前线程等待
     */
    private final Condition notFull;

    /* Concurrency control uses the classic two-condition algorithm found in any textbook.  并发控制使用经典双条件算法。 */


    /**
     * Shared state for currently active iterators, or null if there
     * are known not to be any.  Allows queue operations to update
     * iterator state.
     * <p>
     * 当前活动迭代器的共享状态，如果已知不存在则为 null。允许队列操作更新迭代器状态。
     */
    transient Itrs itrs = null;

    // Internal helper methods

    /**
     * Circularly decrement i.
     */
    final int dec(int i) {
        return ((i == 0) ? items.length : i) - 1;
    }

    /**
     * Returns item at index i.
     */
    @SuppressWarnings("unchecked")
    final E itemAt(int i) {
        return (E) items[i];
    }

    /**
     * Throws NullPointerException if argument is null.
     *
     * @param v the element
     */
    private static void checkNotNull(Object v) {
        if (v == null)
            throw new NullPointerException();
    }

    /**
     * Inserts element at current put position, advances, and signals.
     * Call only when holding lock.
     * <p>
     * 在 putIndex 下标位置添加元素；该方法仅在获取到锁时调用
     */
    private void enqueue(E x) {
        // assert lock.getHoldCount() == 1;
        // assert items[putIndex] == null;

        // 获取全局数组
        final Object[] items = this.items;

        // 在 putIndex 下标位置添加元素
        items[putIndex] = x;

        // 向后移动 putIndex ，如果数组满了，则重置 putIndex 为 0
        if (++putIndex == items.length)
            putIndex = 0;

        // 数组元素个数累加
        count++;

        // 尝试唤醒阻塞在 notEmpty 上等待的线程（如果有的话）
        notEmpty.signal();
    }

    /**
     * Extracts element at current take position, advances, and signals.
     * Call only when holding lock.
     * <p>
     * 取出 takeIndex 下标位置的元素；该方法仅在获取到锁时调用；
     */
    private E dequeue() {
        // assert lock.getHoldCount() == 1;
        // assert items[takeIndex] != null;

        // 获取全局数组
        final Object[] items = this.items;

        // 获取 takeIndex 下标位置的元素
        @SuppressWarnings("unchecked")
        E x = (E) items[takeIndex];

        // 设置 null，便于 GC
        items[takeIndex] = null;

        // 更新 takeIndex ，如果到达了数组的末尾，则重置 takeIndex 为 0 ，即下次从数组头开始取元素
        if (++takeIndex == items.length)
            takeIndex = 0;

        // 数组元素个数递减
        count--;

        // 更新迭代器状态
        if (itrs != null)
            itrs.elementDequeued();

        // 尝试唤醒阻塞在 notFull 上等待的线程（如果有的话）
        notFull.signal();

        // 返回元素
        return x;
    }

    /**
     * Deletes item at array index removeIndex.
     * Utility for remove(Object) and iterator.remove.
     * Call only when holding lock.
     * <p>
     * 删除数组下标 removeIndex 处的元素并移动元素使元素连续。仅在获取到锁时调用；
     */
    void removeAt(final int removeIndex) {
        // assert lock.getHoldCount() == 1;
        // assert items[removeIndex] != null;
        // assert removeIndex >= 0 && removeIndex < items.length;

        // 获取全局数组
        final Object[] items = this.items;

        // 如果要删除下标正好是下一个要取出的元素对应的下标，那么同取出元素操作逻辑
        if (removeIndex == takeIndex) {
            // removing front item; just advance
            // 置空
            items[takeIndex] = null;

            // 更新 takeIndex 下标
            if (++takeIndex == items.length)
                takeIndex = 0;

            // 更新队列元素个数
            count--;

            // 更新迭代器状态
            if (itrs != null)
                itrs.elementDequeued();

            // 如果要删除的下标非 takeIndex ，那么说明就是"中间位置"，需要移动元素以覆盖删除的位置
        } else {
            // an "interior" remove

            // slide over all others up through putIndex.
            // 以要删除的下标 removeIndex 为基准，"向前"移动元素，直到追上 putIndex
            // todo 注意数组循环使用的情况
            final int putIndex = this.putIndex;
            for (int i = removeIndex; ; ) {
                int next = i + 1;
                // 从头开始
                if (next == items.length)
                    next = 0;
                // 没有追上 putIndex，就向前移动
                if (next != putIndex) {
                    items[i] = items[next];
                    i = next;

                    // 后退 putIndex
                } else {
                    items[i] = null;
                    this.putIndex = i;
                    break;
                }
            }

            // 更新队列元素个数
            count--;

            // 更新迭代器
            if (itrs != null)
                itrs.removedAt(removeIndex);
        }

        // 尝试唤醒阻塞在 notFull 上等待的线程
        notFull.signal();
    }

    /**
     * Creates an {@code ArrayBlockingQueue} with the given (fixed)
     * capacity and default access policy.
     * <p>
     * 根据指定的大小创建 fair 类型的 ArrayBlockingQueue；这个公平指的是锁的公平性
     *
     * @param capacity the capacity of this queue  队列大小
     * @throws IllegalArgumentException if {@code capacity < 1} 大小 < 1 抛出异常
     */
    public ArrayBlockingQueue(int capacity) {
        this(capacity, false);
    }

    /**
     * Creates an {@code ArrayBlockingQueue} with the given (fixed)
     * capacity and the specified access policy.
     *
     * @param capacity the capacity of this queue
     * @param fair     if {@code true} then queue accesses for threads blocked
     *                 on insertion or removal, are processed in FIFO order;
     *                 if {@code false} the access order is unspecified.
     * @throws IllegalArgumentException if {@code capacity < 1}
     */
    public ArrayBlockingQueue(int capacity, boolean fair) {
        // 阻塞队列的大小必须 > 0
        if (capacity <= 0)
            throw new IllegalArgumentException();

        // 初始化指定大小的 Object 数组
        this.items = new Object[capacity];

        // 创建可重入锁，是否公平取决于 fair
        lock = new ReentrantLock(fair);

        // 创建锁的条件对象，用来等待队列非空
        notEmpty = lock.newCondition();

        // 创建锁的条件对象，用来等待队列非满
        notFull = lock.newCondition();
    }

    /**
     * Creates an {@code ArrayBlockingQueue} with the given (fixed)
     * capacity, the specified access policy and initially containing the
     * elements of the given collection,
     * added in traversal order of the collection's iterator.
     * <p>
     * 创建指定大小的阻塞队列，并将指定的 Collection 中元素依次添加到队列中。
     * 注意，指定的 Collection 中元素个数不能大于指定的队列大小，否则抛出空指针异常 ArrayIndexOutOfBoundsException
     *
     * @param capacity the capacity of this queue
     * @param fair     if {@code true} then queue accesses for threads blocked
     *                 on insertion or removal, are processed in FIFO order;
     *                 if {@code false} the access order is unspecified.
     * @param c        the collection of elements to initially contain
     * @throws IllegalArgumentException if {@code capacity} is less than
     *                                  {@code c.size()}, or less than 1.
     * @throws NullPointerException     if the specified collection or any
     *                                  of its elements are null
     */
    public ArrayBlockingQueue(int capacity, boolean fair,
                              Collection<? extends E> c) {
        this(capacity, fair);

        final ReentrantLock lock = this.lock;
        lock.lock(); // Lock only for visibility, not mutual exclusion
        try {
            int i = 0;
            try {
                for (E e : c) {
                    checkNotNull(e);
                    items[i++] = e;
                }
            } catch (ArrayIndexOutOfBoundsException ex) {
                throw new IllegalArgumentException();
            }
            count = i;
            putIndex = (i == capacity) ? 0 : i;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Inserts the specified element at the tail of this queue if it is
     * possible to do so immediately without exceeding the queue's capacity,
     * returning {@code true} upon success and throwing an
     * {@code IllegalStateException} if this queue is full.
     * <p>
     * 如果可以在不超出队列容量的情况下立即插入指定元素，则在此队列的尾部插入指定元素，
     * 成功时返回 {@code true}，如果此队列已满则抛出 {@code IllegalStateException}。
     *
     * @param e the element to add 要添加的元素
     * @return {@code true} (as specified by {@link Collection#add}) 添加成功返回 true
     * @throws IllegalStateException if this queue is full 队列满了
     * @throws NullPointerException  if the specified element is null 要添加的元素 e 不能为空
     */
    public boolean add(E e) {
        // 调用父类的 add 方法
        return super.add(e);
        /**
         *     public boolean add(E e) {
         *         // 调用 offer 方法
         *         if (offer(e))
         *             return true;
         *         else
         *             throw new IllegalStateException("Queue full");
         *     }
         */
    }

    /**
     * Inserts the specified element at the tail of this queue if it is
     * possible to do so immediately without exceeding the queue's capacity,
     * returning {@code true} upon success and {@code false} if this queue
     * is full.  This method is generally preferable to method {@link #add},
     * which can fail to insert an element only by throwing an exception.
     * <p>
     * 如果可以在不超出队列容量的情况下立即插入指定元素，则在此队列的尾部插入指定元素，成功时返回 {@code true}，
     * 如果此队列已满则返回 {@code false}。此方法通常比方法 {@link add} 更可取，后者仅通过抛出异常可能无法插入元素。
     *
     * @throws NullPointerException if the specified element is null
     */
    public boolean offer(E e) {
        // 检查要插入的元素 e 不能为空
        checkNotNull(e);

        // 加锁
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {

            // 如果队列满了，直接返回 false ，不插入元素
            if (count == items.length)
                return false;

                // 如果队列没有满，则向队列的尾部插入元素 e ，插入成功后返回 true
            else {
                enqueue(e);
                return true;
            }

            // 释放锁
        } finally {
            lock.unlock();
        }
    }

    /**
     * Inserts the specified element at the tail of this queue, waiting
     * for space to become available if the queue is full.
     * <p>
     * 在此队列的尾部插入指定元素，如果队列已满，则等待空间可用。
     *
     * @throws InterruptedException {@inheritDoc}
     * @throws NullPointerException {@inheritDoc}
     */
    public void put(E e) throws InterruptedException {
        // 检查要添加的元素 e 不能为空
        checkNotNull(e);

        // 加获取可中断锁
        final ReentrantLock lock = this.lock;
        lock.lockInterruptibly();

        try {
            // 判断队列是否满了，满了则阻塞在 notFull 等待被唤醒
            while (count == items.length)
                notFull.await();

            // 队列不满，则在队列尾部插入元素
            enqueue(e);

            // 释放锁
        } finally {
            lock.unlock();
        }
    }

    /**
     * Inserts the specified element at the tail of this queue, waiting
     * up to the specified wait time for space to become available if
     * the queue is full.
     * <p>
     * 在此队列的尾部插入指定元素，如果队列已满，则等待指定的等待时间以使空间可用。
     *
     * @throws InterruptedException {@inheritDoc}
     * @throws NullPointerException {@inheritDoc}
     */
    public boolean offer(E e, long timeout, TimeUnit unit)
            throws InterruptedException {

        // 检查要添加的元素 e 不能为空
        checkNotNull(e);

        // 计算最大等待时长
        long nanos = unit.toNanos(timeout);

        // 获取可中断锁
        final ReentrantLock lock = this.lock;
        lock.lockInterruptibly();

        try {
            // 如果队列满了，则最多等待 nanos 纳秒时长
            while (count == items.length) {
                // 等待 nanos 纳秒后，仍然还是满的，则返回 false，表示添加元素失败
                if (nanos <= 0)
                    return false;
                nanos = notFull.awaitNanos(nanos);
            }

            // 执行到这里，有两种情况：1）队列没有满，添加元素即可  2）队列满了，但在超时时间内被唤醒了，可以执行添加元素操作
            enqueue(e);
            return true;

            // 释放锁
        } finally {
            lock.unlock();
        }
    }

    /**
     * 尝试获取队列头部的元素，队列为空返回 null 即可
     *
     * @return
     */
    public E poll() {
        // 获取锁
        final ReentrantLock lock = this.lock;
        lock.lock();

        try {
            // 队列为空，返回 null ;队列非空返回 takeIndex 下标位置的元素
            return (count == 0) ? null : dequeue();

            // 释放锁
        } finally {
            lock.unlock();
        }
    }

    /**
     * 尝试超时获取元素
     *
     * @param timeout
     * @param unit
     * @return
     * @throws InterruptedException
     */
    public E poll(long timeout, TimeUnit unit) throws InterruptedException {
        // 获取元素的超时时间
        long nanos = unit.toNanos(timeout);

        // 获取可中断锁
        final ReentrantLock lock = this.lock;
        lock.lockInterruptibly();

        try {
            // 如果队列为空，等待 nanos 纳秒时长
            while (count == 0) {
                // 等待 nanos 纳秒后，队列还是为空
                if (nanos <= 0)
                    return null;
                nanos = notEmpty.awaitNanos(nanos);
            }

            // 执行到这里有两种可能： 1）队列非空，直接获取  2）队列为空，但在等待时长内队列又有元素了
            return dequeue();

            // 释放锁
        } finally {
            lock.unlock();
        }
    }

    /**
     * 阻塞式获取元素
     *
     * @return
     * @throws InterruptedException
     */
    public E take() throws InterruptedException {
        // 获取可中断锁
        final ReentrantLock lock = this.lock;
        lock.lockInterruptibly();

        try {
            // 队列为空，则阻塞等待，直到队列有元素
            while (count == 0)
                notEmpty.await();

            // 获取 takeIndex 下标位置的元素
            return dequeue();

            // 释放锁
        } finally {
            lock.unlock();
        }
    }

    /**
     * 读取队列头部元素，但不移除该元素；队列为空，则返回 null
     *
     * @return
     */
    public E peek() {
        // 获取锁
        final ReentrantLock lock = this.lock;
        lock.lock();

        // 直接从数组中获取下标为 takeIndex 的元素
        try {
            return itemAt(takeIndex); // null when queue is empty

            // 释放锁
        } finally {
            lock.unlock();
        }
    }

    // this doc comment is overridden to remove the reference to collections
    // greater in size than Integer.MAX_VALUE

    /**
     * Returns the number of elements in this queue.
     * <p>
     * 返回队列中元素的个数
     *
     * @return the number of elements in this queue
     */
    public int size() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return count;
        } finally {
            lock.unlock();
        }
    }

    // this doc comment is a modified copy of the inherited doc comment,
    // without the reference to unlimited queues.

    /**
     * Returns the number of additional elements that this queue can ideally
     * (in the absence of memory or resource constraints) accept without
     * blocking. This is always equal to the initial capacity of this queue
     * less the current {@code size} of this queue.
     * <p>
     * 返回队列剩余空间大小
     *
     * <p>Note that you <em>cannot</em> always tell if an attempt to insert
     * an element will succeed by inspecting {@code remainingCapacity}
     * because it may be the case that another thread is about to
     * insert or remove an element.
     */
    public int remainingCapacity() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return items.length - count;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Removes a single instance of the specified element from this queue,
     * if it is present.  More formally, removes an element {@code e} such
     * that {@code o.equals(e)}, if this queue contains one or more such
     * elements.
     * <p>
     * 从此队列中移除指定元素的单个实例（如果存在）。更正式地说，如果此队列包含一个或多个这样的元素，则删除一个元素 {@code e} 使得 {@code o.equals(e)}。
     * <p>
     * <p>
     * Returns {@code true} if this queue contained the specified element
     * (or equivalently, if this queue changed as a result of the call).
     *
     * <p>Removal of interior elements in circular array based queues
     * is an intrinsically slow and disruptive operation, so should
     * be undertaken only in exceptional circumstances, ideally
     * only when the queue is known not to be accessible by other
     * threads.
     *
     * @param o element to be removed from this queue, if present
     * @return {@code true} if this queue changed as a result of the call
     */
    public boolean remove(Object o) {
        // 要删除的元素为空，则返回 false，不进行处理
        if (o == null) return false;

        // 获取全局数组
        final Object[] items = this.items;

        // 获取锁
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            // 如果队列非空
            if (count > 0) {
                final int putIndex = this.putIndex;
                int i = takeIndex;

                // 遍历数组，找到要删除的元素
                do {
                    if (o.equals(items[i])) {
                        // 删除该元素并移动元素
                        removeAt(i);
                        return true;
                    }

                    // 数组是循环使用的
                    if (++i == items.length)
                        i = 0;

                    // 追上 putIndex ，说明已经遍历完了
                } while (i != putIndex);
            }

            // 队列为空，或者没有找到要删除的元素
            return false;

            // 释放锁
        } finally {
            lock.unlock();
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
        final Object[] items = this.items;
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            if (count > 0) {
                final int putIndex = this.putIndex;
                int i = takeIndex;
                do {
                    if (o.equals(items[i]))
                        return true;
                    if (++i == items.length)
                        i = 0;
                } while (i != putIndex);
            }
            return false;
        } finally {
            lock.unlock();
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
        Object[] a;
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            final int count = this.count;
            a = new Object[count];
            int n = items.length - takeIndex;
            if (count <= n)
                System.arraycopy(items, takeIndex, a, 0, count);
            else {
                System.arraycopy(items, takeIndex, a, 0, n);
                System.arraycopy(items, 0, a, n, count - n);
            }
        } finally {
            lock.unlock();
        }
        return a;
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
        final Object[] items = this.items;
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            final int count = this.count;
            final int len = a.length;
            if (len < count)
                a = (T[]) java.lang.reflect.Array.newInstance(
                        a.getClass().getComponentType(), count);
            int n = items.length - takeIndex;
            if (count <= n)
                System.arraycopy(items, takeIndex, a, 0, count);
            else {
                System.arraycopy(items, takeIndex, a, 0, n);
                System.arraycopy(items, 0, a, n, count - n);
            }
            if (len > count)
                a[count] = null;
        } finally {
            lock.unlock();
        }
        return a;
    }

    public String toString() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            int k = count;
            if (k == 0)
                return "[]";

            final Object[] items = this.items;
            StringBuilder sb = new StringBuilder();
            sb.append('[');
            for (int i = takeIndex; ; ) {
                Object e = items[i];
                sb.append(e == this ? "(this Collection)" : e);
                if (--k == 0)
                    return sb.append(']').toString();
                sb.append(',').append(' ');
                if (++i == items.length)
                    i = 0;
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Atomically removes all of the elements from this queue.
     * The queue will be empty after this call returns.
     */
    public void clear() {
        final Object[] items = this.items;
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            int k = count;
            if (k > 0) {
                final int putIndex = this.putIndex;
                int i = takeIndex;
                do {
                    items[i] = null;
                    if (++i == items.length)
                        i = 0;
                } while (i != putIndex);
                takeIndex = putIndex;
                count = 0;
                if (itrs != null)
                    itrs.queueIsEmpty();
                for (; k > 0 && lock.hasWaiters(notFull); k--)
                    notFull.signal();
            }
        } finally {
            lock.unlock();
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
        checkNotNull(c);
        if (c == this)
            throw new IllegalArgumentException();
        if (maxElements <= 0)
            return 0;
        final Object[] items = this.items;
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            int n = Math.min(maxElements, count);
            int take = takeIndex;
            int i = 0;
            try {
                while (i < n) {
                    @SuppressWarnings("unchecked")
                    E x = (E) items[take];
                    c.add(x);
                    items[take] = null;
                    if (++take == items.length)
                        take = 0;
                    i++;
                }
                return n;
            } finally {
                // Restore invariants even if c.add() threw
                if (i > 0) {
                    count -= i;
                    takeIndex = take;
                    if (itrs != null) {
                        if (count == 0)
                            itrs.queueIsEmpty();
                        else if (i > take)
                            itrs.takeIndexWrapped();
                    }
                    for (; i > 0 && lock.hasWaiters(notFull); i--)
                        notFull.signal();
                }
            }
        } finally {
            lock.unlock();
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

    /**
     * Shared data between iterators and their queue, allowing queue
     * modifications to update iterators when elements are removed.
     * <p>
     * This adds a lot of complexity for the sake of correctly
     * handling some uncommon operations, but the combination of
     * circular-arrays and supporting interior removes (i.e., those
     * not at head) would cause iterators to sometimes lose their
     * places and/or (re)report elements they shouldn't.  To avoid
     * this, when a queue has one or more iterators, it keeps iterator
     * state consistent by:
     * <p>
     * (1) keeping track of the number of "cycles", that is, the
     * number of times takeIndex has wrapped around to 0.
     * (2) notifying all iterators via the callback removedAt whenever
     * an interior element is removed (and thus other elements may
     * be shifted).
     * <p>
     * These suffice to eliminate iterator inconsistencies, but
     * unfortunately add the secondary responsibility of maintaining
     * the list of iterators.  We track all active iterators in a
     * simple linked list (accessed only when the queue's lock is
     * held) of weak references to Itr.  The list is cleaned up using
     * 3 different mechanisms:
     * <p>
     * (1) Whenever a new iterator is created, do some O(1) checking for
     * stale list elements.
     * <p>
     * (2) Whenever takeIndex wraps around to 0, check for iterators
     * that have been unused for more than one wrap-around cycle.
     * <p>
     * (3) Whenever the queue becomes empty, all iterators are notified
     * and this entire data structure is discarded.
     * <p>
     * So in addition to the removedAt callback that is necessary for
     * correctness, iterators have the shutdown and takeIndexWrapped
     * callbacks that help remove stale iterators from the list.
     * <p>
     * Whenever a list element is examined, it is expunged if either
     * the GC has determined that the iterator is discarded, or if the
     * iterator reports that it is "detached" (does not need any
     * further state updates).  Overhead is maximal when takeIndex
     * never advances, iterators are discarded before they are
     * exhausted, and all removals are interior removes, in which case
     * all stale iterators are discovered by the GC.  But even in this
     * case we don't increase the amortized complexity.
     * <p>
     * Care must be taken to keep list sweeping methods from
     * reentrantly invoking another such method, causing subtle
     * corruption bugs.
     */
    class Itrs {

        /**
         * Node in a linked list of weak iterator references.
         */
        private class Node extends WeakReference<Itr> {
            Node next;

            Node(Itr iterator, Node next) {
                super(iterator);
                this.next = next;
            }
        }

        /**
         * Incremented whenever takeIndex wraps around to 0
         */
        int cycles = 0;

        /**
         * Linked list of weak iterator references
         */
        private Node head;

        /**
         * Used to expunge stale iterators
         */
        private Node sweeper = null;

        private static final int SHORT_SWEEP_PROBES = 4;
        private static final int LONG_SWEEP_PROBES = 16;

        Itrs(Itr initial) {
            register(initial);
        }

        /**
         * Sweeps itrs, looking for and expunging stale iterators.
         * If at least one was found, tries harder to find more.
         * Called only from iterating thread.
         *
         * @param tryHarder whether to start in try-harder mode, because
         *                  there is known to be at least one iterator to collect
         */
        void doSomeSweeping(boolean tryHarder) {
            // assert lock.getHoldCount() == 1;
            // assert head != null;
            int probes = tryHarder ? LONG_SWEEP_PROBES : SHORT_SWEEP_PROBES;
            Node o, p;
            final Node sweeper = this.sweeper;
            boolean passedGo;   // to limit search to one full sweep

            if (sweeper == null) {
                o = null;
                p = head;
                passedGo = true;
            } else {
                o = sweeper;
                p = o.next;
                passedGo = false;
            }

            for (; probes > 0; probes--) {
                if (p == null) {
                    if (passedGo)
                        break;
                    o = null;
                    p = head;
                    passedGo = true;
                }
                final Itr it = p.get();
                final Node next = p.next;
                if (it == null || it.isDetached()) {
                    // found a discarded/exhausted iterator
                    probes = LONG_SWEEP_PROBES; // "try harder"
                    // unlink p
                    p.clear();
                    p.next = null;
                    if (o == null) {
                        head = next;
                        if (next == null) {
                            // We've run out of iterators to track; retire
                            itrs = null;
                            return;
                        }
                    } else
                        o.next = next;
                } else {
                    o = p;
                }
                p = next;
            }

            this.sweeper = (p == null) ? null : o;
        }

        /**
         * Adds a new iterator to the linked list of tracked iterators.
         */
        void register(Itr itr) {
            // assert lock.getHoldCount() == 1;
            head = new Node(itr, head);
        }

        /**
         * Called whenever takeIndex wraps around to 0.
         * <p>
         * Notifies all iterators, and expunges any that are now stale.
         */
        void takeIndexWrapped() {
            // assert lock.getHoldCount() == 1;
            cycles++;
            for (Node o = null, p = head; p != null; ) {
                final Itr it = p.get();
                final Node next = p.next;
                if (it == null || it.takeIndexWrapped()) {
                    // unlink p
                    // assert it == null || it.isDetached();
                    p.clear();
                    p.next = null;
                    if (o == null)
                        head = next;
                    else
                        o.next = next;
                } else {
                    o = p;
                }
                p = next;
            }
            if (head == null)   // no more iterators to track
                itrs = null;
        }

        /**
         * Called whenever an interior remove (not at takeIndex) occurred.
         * <p>
         * Notifies all iterators, and expunges any that are now stale.
         */
        void removedAt(int removedIndex) {
            for (Node o = null, p = head; p != null; ) {
                final Itr it = p.get();
                final Node next = p.next;
                if (it == null || it.removedAt(removedIndex)) {
                    // unlink p
                    // assert it == null || it.isDetached();
                    p.clear();
                    p.next = null;
                    if (o == null)
                        head = next;
                    else
                        o.next = next;
                } else {
                    o = p;
                }
                p = next;
            }
            if (head == null)   // no more iterators to track
                itrs = null;
        }

        /**
         * Called whenever the queue becomes empty.
         * <p>
         * Notifies all active iterators that the queue is empty,
         * clears all weak refs, and unlinks the itrs datastructure.
         */
        void queueIsEmpty() {
            // assert lock.getHoldCount() == 1;
            for (Node p = head; p != null; p = p.next) {
                Itr it = p.get();
                if (it != null) {
                    p.clear();
                    it.shutdown();
                }
            }
            head = null;
            itrs = null;
        }

        /**
         * Called whenever an element has been dequeued (at takeIndex).
         */
        void elementDequeued() {
            // assert lock.getHoldCount() == 1;
            if (count == 0)
                queueIsEmpty();
            else if (takeIndex == 0)
                takeIndexWrapped();
        }
    }

    /**
     * Iterator for ArrayBlockingQueue.
     * <p>
     * To maintain weak consistency with respect to puts and takes, we
     * read ahead one slot, so as to not report hasNext true but then
     * not have an element to return.
     * <p>
     * We switch into "detached" mode (allowing prompt unlinking from
     * itrs without help from the GC) when all indices are negative, or
     * when hasNext returns false for the first time.  This allows the
     * iterator to track concurrent updates completely accurately,
     * except for the corner case of the user calling Iterator.remove()
     * after hasNext() returned false.  Even in this case, we ensure
     * that we don't remove the wrong element by keeping track of the
     * expected element to remove, in lastItem.  Yes, we may fail to
     * remove lastItem from the queue if it moved due to an interleaved
     * interior remove while in detached mode.
     */
    private class Itr implements Iterator<E> {
        /**
         * Index to look for new nextItem; NONE at end
         */
        private int cursor;

        /**
         * Element to be returned by next call to next(); null if none
         */
        private E nextItem;

        /**
         * Index of nextItem; NONE if none, REMOVED if removed elsewhere
         */
        private int nextIndex;

        /**
         * Last element returned; null if none or not detached.
         */
        private E lastItem;

        /**
         * Index of lastItem, NONE if none, REMOVED if removed elsewhere
         */
        private int lastRet;

        /**
         * Previous value of takeIndex, or DETACHED when detached
         */
        private int prevTakeIndex;

        /**
         * Previous value of iters.cycles
         */
        private int prevCycles;

        /**
         * Special index value indicating "not available" or "undefined"
         */
        private static final int NONE = -1;

        /**
         * Special index value indicating "removed elsewhere", that is,
         * removed by some operation other than a call to this.remove().
         */
        private static final int REMOVED = -2;

        /**
         * Special value for prevTakeIndex indicating "detached mode"
         */
        private static final int DETACHED = -3;

        Itr() {
            // assert lock.getHoldCount() == 0;
            lastRet = NONE;
            final ReentrantLock lock = ArrayBlockingQueue.this.lock;
            lock.lock();
            try {
                if (count == 0) {
                    // assert itrs == null;
                    cursor = NONE;
                    nextIndex = NONE;
                    prevTakeIndex = DETACHED;
                } else {
                    final int takeIndex = ArrayBlockingQueue.this.takeIndex;
                    prevTakeIndex = takeIndex;
                    nextItem = itemAt(nextIndex = takeIndex);
                    cursor = incCursor(takeIndex);
                    if (itrs == null) {
                        itrs = new Itrs(this);
                    } else {
                        itrs.register(this); // in this order
                        itrs.doSomeSweeping(false);
                    }
                    prevCycles = itrs.cycles;
                    // assert takeIndex >= 0;
                    // assert prevTakeIndex == takeIndex;
                    // assert nextIndex >= 0;
                    // assert nextItem != null;
                }
            } finally {
                lock.unlock();
            }
        }

        boolean isDetached() {
            // assert lock.getHoldCount() == 1;
            return prevTakeIndex < 0;
        }

        private int incCursor(int index) {
            // assert lock.getHoldCount() == 1;
            if (++index == items.length)
                index = 0;
            if (index == putIndex)
                index = NONE;
            return index;
        }

        /**
         * Returns true if index is invalidated by the given number of
         * dequeues, starting from prevTakeIndex.
         */
        private boolean invalidated(int index, int prevTakeIndex,
                                    long dequeues, int length) {
            if (index < 0)
                return false;
            int distance = index - prevTakeIndex;
            if (distance < 0)
                distance += length;
            return dequeues > distance;
        }

        /**
         * Adjusts indices to incorporate all dequeues since the last
         * operation on this iterator.  Call only from iterating thread.
         */
        private void incorporateDequeues() {
            // assert lock.getHoldCount() == 1;
            // assert itrs != null;
            // assert !isDetached();
            // assert count > 0;

            final int cycles = itrs.cycles;
            final int takeIndex = ArrayBlockingQueue.this.takeIndex;
            final int prevCycles = this.prevCycles;
            final int prevTakeIndex = this.prevTakeIndex;

            if (cycles != prevCycles || takeIndex != prevTakeIndex) {
                final int len = items.length;
                // how far takeIndex has advanced since the previous
                // operation of this iterator
                long dequeues = (cycles - prevCycles) * len
                        + (takeIndex - prevTakeIndex);

                // Check indices for invalidation
                if (invalidated(lastRet, prevTakeIndex, dequeues, len))
                    lastRet = REMOVED;
                if (invalidated(nextIndex, prevTakeIndex, dequeues, len))
                    nextIndex = REMOVED;
                if (invalidated(cursor, prevTakeIndex, dequeues, len))
                    cursor = takeIndex;

                if (cursor < 0 && nextIndex < 0 && lastRet < 0)
                    detach();
                else {
                    this.prevCycles = cycles;
                    this.prevTakeIndex = takeIndex;
                }
            }
        }

        /**
         * Called when itrs should stop tracking this iterator, either
         * because there are no more indices to update (cursor < 0 &&
         * nextIndex < 0 && lastRet < 0) or as a special exception, when
         * lastRet >= 0, because hasNext() is about to return false for the
         * first time.  Call only from iterating thread.
         */
        private void detach() {
            // Switch to detached mode
            // assert lock.getHoldCount() == 1;
            // assert cursor == NONE;
            // assert nextIndex < 0;
            // assert lastRet < 0 || nextItem == null;
            // assert lastRet < 0 ^ lastItem != null;
            if (prevTakeIndex >= 0) {
                // assert itrs != null;
                prevTakeIndex = DETACHED;
                // try to unlink from itrs (but not too hard)
                itrs.doSomeSweeping(true);
            }
        }

        /**
         * For performance reasons, we would like not to acquire a lock in
         * hasNext in the common case.  To allow for this, we only access
         * fields (i.e. nextItem) that are not modified by update operations
         * triggered by queue modifications.
         */
        public boolean hasNext() {
            // assert lock.getHoldCount() == 0;
            if (nextItem != null)
                return true;
            noNext();
            return false;
        }

        private void noNext() {
            final ReentrantLock lock = ArrayBlockingQueue.this.lock;
            lock.lock();
            try {
                // assert cursor == NONE;
                // assert nextIndex == NONE;
                if (!isDetached()) {
                    // assert lastRet >= 0;
                    incorporateDequeues(); // might update lastRet
                    if (lastRet >= 0) {
                        lastItem = itemAt(lastRet);
                        // assert lastItem != null;
                        detach();
                    }
                }
                // assert isDetached();
                // assert lastRet < 0 ^ lastItem != null;
            } finally {
                lock.unlock();
            }
        }

        public E next() {
            // assert lock.getHoldCount() == 0;
            final E x = nextItem;
            if (x == null)
                throw new NoSuchElementException();
            final ReentrantLock lock = ArrayBlockingQueue.this.lock;
            lock.lock();
            try {
                if (!isDetached())
                    incorporateDequeues();
                // assert nextIndex != NONE;
                // assert lastItem == null;
                lastRet = nextIndex;
                final int cursor = this.cursor;
                if (cursor >= 0) {
                    nextItem = itemAt(nextIndex = cursor);
                    // assert nextItem != null;
                    this.cursor = incCursor(cursor);
                } else {
                    nextIndex = NONE;
                    nextItem = null;
                }
            } finally {
                lock.unlock();
            }
            return x;
        }

        public void remove() {
            // assert lock.getHoldCount() == 0;
            final ReentrantLock lock = ArrayBlockingQueue.this.lock;
            lock.lock();
            try {
                if (!isDetached())
                    incorporateDequeues(); // might update lastRet or detach
                final int lastRet = this.lastRet;
                this.lastRet = NONE;
                if (lastRet >= 0) {
                    if (!isDetached())
                        removeAt(lastRet);
                    else {
                        final E lastItem = this.lastItem;
                        // assert lastItem != null;
                        this.lastItem = null;
                        if (itemAt(lastRet) == lastItem)
                            removeAt(lastRet);
                    }
                } else if (lastRet == NONE)
                    throw new IllegalStateException();
                // else lastRet == REMOVED and the last returned element was
                // previously asynchronously removed via an operation other
                // than this.remove(), so nothing to do.

                if (cursor < 0 && nextIndex < 0)
                    detach();
            } finally {
                lock.unlock();
                // assert lastRet == NONE;
                // assert lastItem == null;
            }
        }

        /**
         * Called to notify the iterator that the queue is empty, or that it
         * has fallen hopelessly behind, so that it should abandon any
         * further iteration, except possibly to return one more element
         * from next(), as promised by returning true from hasNext().
         */
        void shutdown() {
            // assert lock.getHoldCount() == 1;
            cursor = NONE;
            if (nextIndex >= 0)
                nextIndex = REMOVED;
            if (lastRet >= 0) {
                lastRet = REMOVED;
                lastItem = null;
            }
            prevTakeIndex = DETACHED;
            // Don't set nextItem to null because we must continue to be
            // able to return it on next().
            //
            // Caller will unlink from itrs when convenient.
        }

        private int distance(int index, int prevTakeIndex, int length) {
            int distance = index - prevTakeIndex;
            if (distance < 0)
                distance += length;
            return distance;
        }

        /**
         * Called whenever an interior remove (not at takeIndex) occurred.
         *
         * @return true if this iterator should be unlinked from itrs
         */
        boolean removedAt(int removedIndex) {
            // assert lock.getHoldCount() == 1;
            if (isDetached())
                return true;

            final int cycles = itrs.cycles;
            final int takeIndex = ArrayBlockingQueue.this.takeIndex;
            final int prevCycles = this.prevCycles;
            final int prevTakeIndex = this.prevTakeIndex;
            final int len = items.length;
            int cycleDiff = cycles - prevCycles;
            if (removedIndex < takeIndex)
                cycleDiff++;
            final int removedDistance =
                    (cycleDiff * len) + (removedIndex - prevTakeIndex);
            // assert removedDistance >= 0;
            int cursor = this.cursor;
            if (cursor >= 0) {
                int x = distance(cursor, prevTakeIndex, len);
                if (x == removedDistance) {
                    if (cursor == putIndex)
                        this.cursor = cursor = NONE;
                } else if (x > removedDistance) {
                    // assert cursor != prevTakeIndex;
                    this.cursor = cursor = dec(cursor);
                }
            }
            int lastRet = this.lastRet;
            if (lastRet >= 0) {
                int x = distance(lastRet, prevTakeIndex, len);
                if (x == removedDistance)
                    this.lastRet = lastRet = REMOVED;
                else if (x > removedDistance)
                    this.lastRet = lastRet = dec(lastRet);
            }
            int nextIndex = this.nextIndex;
            if (nextIndex >= 0) {
                int x = distance(nextIndex, prevTakeIndex, len);
                if (x == removedDistance)
                    this.nextIndex = nextIndex = REMOVED;
                else if (x > removedDistance)
                    this.nextIndex = nextIndex = dec(nextIndex);
            } else if (cursor < 0 && nextIndex < 0 && lastRet < 0) {
                this.prevTakeIndex = DETACHED;
                return true;
            }
            return false;
        }

        /**
         * Called whenever takeIndex wraps around to zero.
         *
         * @return true if this iterator should be unlinked from itrs
         */
        boolean takeIndexWrapped() {
            // assert lock.getHoldCount() == 1;
            if (isDetached())
                return true;
            if (itrs.cycles - prevCycles > 1) {
                // All the elements that existed at the time of the last
                // operation are gone, so abandon further iteration.
                shutdown();
                return true;
            }
            return false;
        }

//         /** Uncomment for debugging. */
//         public String toString() {
//             return ("cursor=" + cursor + " " +
//                     "nextIndex=" + nextIndex + " " +
//                     "lastRet=" + lastRet + " " +
//                     "nextItem=" + nextItem + " " +
//                     "lastItem=" + lastItem + " " +
//                     "prevCycles=" + prevCycles + " " +
//                     "prevTakeIndex=" + prevTakeIndex + " " +
//                     "size()=" + size() + " " +
//                     "remainingCapacity()=" + remainingCapacity());
//         }
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
        return Spliterators.spliterator
                (this, Spliterator.ORDERED | Spliterator.NONNULL |
                        Spliterator.CONCURRENT);
    }

    /**
     * Deserializes this queue and then checks some invariants.
     *
     * @param s the input stream
     * @throws ClassNotFoundException         if the class of a serialized object
     *                                        could not be found
     * @throws java.io.InvalidObjectException if invariants are violated
     * @throws java.io.IOException            if an I/O error occurs
     */
    private void readObject(java.io.ObjectInputStream s)
            throws java.io.IOException, ClassNotFoundException {

        // Read in items array and various fields
        s.defaultReadObject();

        // Check invariants over count and index fields. Note that
        // if putIndex==takeIndex, count can be either 0 or items.length.
        if (items.length == 0 ||
                takeIndex < 0 || takeIndex >= items.length ||
                putIndex < 0 || putIndex >= items.length ||
                count < 0 || count > items.length ||
                Math.floorMod(putIndex - takeIndex, items.length) !=
                        Math.floorMod(count, items.length)) {
            throw new java.io.InvalidObjectException("invariants violated");
        }
    }
}
