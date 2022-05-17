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

import static java.util.concurrent.TimeUnit.NANOSECONDS;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.*;

/**
 * An unbounded {@linkplain BlockingQueue blocking queue} of
 * {@code Delayed} elements, in which an element can only be taken
 * when its delay has expired.  The <em>head</em> of the queue is that
 * {@code Delayed} element whose delay expired furthest in the
 * past.  If no delay has expired there is no head and {@code poll}
 * will return {@code null}. Expiration occurs when an element's
 * {@code getDelay(TimeUnit.NANOSECONDS)} method returns a value less
 * than or equal to zero.  Even though unexpired elements cannot be
 * removed using {@code take} or {@code poll}, they are otherwise
 * treated as normal elements. For example, the {@code size} method
 * returns the count of both expired and unexpired elements.
 * This queue does not permit null elements.
 *
 * <p>This class and its iterator implement all of the
 * <em>optional</em> methods of the {@link Collection} and {@link
 * Iterator} interfaces.  The Iterator provided in method {@link
 * #iterator()} is <em>not</em> guaranteed to traverse the elements of
 * the DelayQueue in any particular order.
 *
 * <p>This class is a member of the
 * <a href="{@docRoot}/../technotes/guides/collections/index.html">
 * Java Collections Framework</a>.
 *
 * @param <E> the type of elements held in this collection
 * @author Doug Lea
 * @since 1.5
 */
public class DelayQueue<E extends Delayed> extends AbstractQueue<E>
        implements BlockingQueue<E> {


    /*
      说明：

      1. DelayQueue 是一个 无界阻塞延时队列，它使用的是优先级队列 + 时间维度(过期时间) 来实现的；
          即 原理就是在队列的基础上增加了时间维度的优先级，然后通过锁和条件变量来控制取/放流程。
      2. DelayQueue 使用了leader这个结构来实现优化，减少不必要的竞争。
      3. ScheduledThreadPoolExecutor 中使用的是它自己定义的内部类 DelayedWorkQueue，其实里面的实现逻辑基本都是一样的，
         只不过DelayedWorkQueue里面没有使用现成的PriorityQueue，而是使用数组又实现了一遍优先级队列，本质上没有什么区别。

     */


    // 保证线程安全的锁
    private final transient ReentrantLock lock = new ReentrantLock();

    // 优先队列
    private final PriorityQueue<E> q = new PriorityQueue<E>();

    /**
     * Thread designated to wait for the element at the head of
     * the queue.
     * <p>
     * 等待队列头部元素的线程
     * <p>
     * This variant of the Leader-Follower pattern
     * (http://www.cs.wustl.edu/~schmidt/POSA/POSA2/) serves to
     * minimize unnecessary timed waiting.
     * <p>
     * Leader-Follower 模式的这种变体用于最大限度地减少不必要的定时等待。
     * <p>
     * When a thread becomes the leader, it waits only for the next delay to elapse, but
     * other threads await indefinitely.
     * <p>
     * 当一个线程成为领导者时，它只等待下一个延迟过去，但其他线程无限期地等待。
     * <p>
     * The leader thread must signal some other thread before returning from take() or
     * poll(...), unless some other thread becomes leader in the
     * interim.
     * <p>
     * 领导者线程必须在从 take() 或 poll(...) 返回之前向其他线程发出信号，除非其他一些线程在此期间成为领导者
     * <p>
     * Whenever the head of the queue is replaced with
     * an element with an earlier expiration time, the leader
     * field is invalidated by being reset to null, and some
     * waiting thread, but not necessarily the current leader, is
     * signalled.
     * <p>
     * 每当队列的头部被一个具有更早过期时间​​的元素替换时，leader 字段通过被重置为 null 而失效，并且一些等待线程（但不一定是当前的leader）被发出信号。
     * <p>
     * So waiting threads must be prepared to acquire
     * and lose leadership while waiting.
     * <p>
     * 所以等待线程必须准备好在等待时获取和失去领导权。
     */

    // 标记取元素时是否有线程在排队，减少不必要的竞争
    // leader 不为空，说明有线程在等待元素，后来的线程乖乖地等着就行了
    private Thread leader = null;

    /**
     * Condition signalled when a newer element becomes available
     * at the head of the queue or a new thread may need to
     * become leader.
     */
    // 是否可取的条件变量
    private final Condition available = lock.newCondition();

    /**
     * Creates a new {@code DelayQueue} that is initially empty.
     */
    public DelayQueue() {
    }

    /**
     * Creates a {@code DelayQueue} initially containing the elements of the
     * given collection of {@link Delayed} instances.
     *
     * @param c the collection of elements to initially contain
     * @throws NullPointerException if the specified collection or any
     *                              of its elements are null
     */
    public DelayQueue(Collection<? extends E> c) {
        this.addAll(c);
    }

    /**
     * Inserts the specified element into this delay queue.
     * <p>
     * 将指定元素插入此延迟队列。
     *
     * @param e the element to add
     * @return {@code true} (as specified by {@link Collection#add})
     * @throws NullPointerException if the specified element is null
     */
    public boolean add(E e) {
        return offer(e);
    }

    /**
     * Inserts the specified element into this delay queue.
     * <p>
     * 将指定元素插入此延迟队列。
     *
     * @param e the element to add
     * @return {@code true}
     * @throws NullPointerException if the specified element is null
     */
    public boolean offer(E e) {
        // 获取锁
        final ReentrantLock lock = this.lock;
        lock.lock();

        try {
            // 将元素加入到优先队列中
            q.offer(e);

            // 如果添加的元素是堆顶元素
            if (q.peek() == e) {
                // leader 置空
                leader = null;

                // 唤醒可取条件队列的线程
                available.signal();
            }

            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Inserts the specified element into this delay queue. As the queue is
     * unbounded this method will never block.
     * <p>
     * 将指定元素插入此延迟队列。由于队列是无限的，这个方法永远不会阻塞。
     *
     * @param e the element to add
     * @throws NullPointerException {@inheritDoc}
     */
    public void put(E e) {
        offer(e);
    }

    /**
     * Inserts the specified element into this delay queue. As the queue is
     * unbounded this method will never block.
     * <p>
     * 将指定元素插入此延迟队列。由于队列是无限的，这个方法永远不会阻塞。
     *
     * @param e       the element to add
     * @param timeout This parameter is ignored as the method never blocks 此参数被忽略，因为该方法从不阻塞
     * @param unit    This parameter is ignored as the method never blocks 此参数被忽略，因为该方法从不阻塞
     * @return {@code true}
     * @throws NullPointerException {@inheritDoc}
     */
    public boolean offer(E e, long timeout, TimeUnit unit) {
        return offer(e);
    }

    /**
     * Retrieves and removes the head of this queue, or returns {@code null}
     * if this queue has no elements with an expired delay.
     * <p>
     * 检索并删除此队列的头部，如果此队列没有具有过期延迟的元素，则返回 null
     *
     * @return the head of this queue, or {@code null} if this
     * queue has no elements with an expired delay
     */
    public E poll() {
        // 获取锁
        final ReentrantLock lock = this.lock;
        lock.lock();

        try {
            // 从优先队列中获取元素
            E first = q.peek();

            // 如果为空，或者没有到期，则返回空
            if (first == null || first.getDelay(NANOSECONDS) > 0)
                return null;

                // 到期则取出堆顶元素
            else
                return q.poll();

            // 释放锁
        } finally {
            lock.unlock();
        }
    }

    /**
     * Retrieves and removes the head of this queue, waiting if necessary
     * until an element with an expired delay is available on this queue.
     * <p>
     * 检索并移除队列的头部元素，如有必要，则阻塞等待直到队列上有一个具有到期的元素。
     *
     * @return the head of this queue 队列头部元素
     * @throws InterruptedException {@inheritDoc} 中断异常
     */
    public E take() throws InterruptedException {
        // 获取锁
        final ReentrantLock lock = this.lock;
        lock.lockInterruptibly();

        try {
            // 自旋
            for (; ; ) {

                // 获取堆顶元素
                E first = q.peek();

                // 为空则进入等待，阻塞式获取元素
                if (first == null)
                    available.await();

                    // 堆顶不为空
                else {

                    // 获取元素的超时时间
                    long delay = first.getDelay(NANOSECONDS);
                    // 判断是否到期，到期直接弹出即可
                    if (delay <= 0)
                        return q.poll();

                    /* 执行到这里，说明堆顶元素还没有到期，不能弹出*/

                    // 要进入等待了，需要设置为 null
                    first = null; // don't retain ref while waiting

                    // 检验前面是否已有等待获取元素的线程，有的话就直接进入等待（快速进入等待）
                    if (leader != null)
                        available.await();

                        // 还没有等待获取元素的线程，就把自己设置为等待线程，然后进入超时等待
                    else {
                        Thread thisThread = Thread.currentThread();
                        leader = thisThread;
                        try {
                            // 等待的时间是，堆顶元素剩余的过期时间
                            available.awaitNanos(delay);

                        } finally {
                            // 唤醒后就把 leader 置空
                            if (leader == thisThread)
                                leader = null;
                        }
                    }
                }
            }
        } finally {
            // 没有等待元素的线程，并且队列还有数据，就唤醒下一个线程来取（如果有的话）
            if (leader == null && q.peek() != null)
                available.signal();

            // 释放锁
            lock.unlock();
        }
    }

    /**
     * Retrieves and removes the head of this queue, waiting if necessary
     * until an element with an expired delay is available on this queue,
     * or the specified wait time expires.
     *
     * 检索并删除队列的头部元素，如有必要，等待直到队列上有一个具有到期的元素，或指定的等待时间过期。
     *
     * @return the head of this queue, or {@code null} if the
     * specified waiting time elapses before an element with
     * an expired delay becomes available
     * @throws InterruptedException {@inheritDoc}
     */
    public E poll(long timeout, TimeUnit unit) throws InterruptedException {
        // 计算超时等待的时长
        long nanos = unit.toNanos(timeout);

        // 获取锁
        final ReentrantLock lock = this.lock;
        lock.lockInterruptibly();

        try {
            // 自旋
            for (; ; ) {
                // 获取堆顶元素
                E first = q.peek();

                // 队列为空，则进入超时等待
                if (first == null) {
                    // 等待时间到了，还是为空，则返回 null
                    if (nanos <= 0)
                        return null;
                    else
                        nanos = available.awaitNanos(nanos);

                    // 队列非空
                } else {
                    // 获取元素的超时时间
                    long delay = first.getDelay(NANOSECONDS);

                    // 判断是否到期，到期直接弹出即可
                    if (delay <= 0)
                        return q.poll();


                    /* 执行到这里，说明堆顶元素还没有到期，还不能弹出*/

                    // 判断超时时间到了没
                    if (nanos <= 0)
                        return null;
                    first = null; // don't retain ref while waiting

                    // 当前超时小于到期剩余时间，或者前面已经有等待的线程了
                    if (nanos < delay || leader != null)
                        nanos = available.awaitNanos(nanos);

                    else {
                        Thread thisThread = Thread.currentThread();
                        leader = thisThread;
                        try {
                            long timeLeft = available.awaitNanos(delay);

                            // 计算剩余等待时长
                            nanos -= delay - timeLeft;
                        } finally {
                            if (leader == thisThread)
                                leader = null;
                        }
                    }
                }
            }
        } finally {
            if (leader == null && q.peek() != null)
                available.signal();
            lock.unlock();
        }
    }

    /**
     * Retrieves, but does not remove, the head of this queue, or
     * returns {@code null} if this queue is empty.  Unlike
     * {@code poll}, if no expired elements are available in the queue,
     * this method returns the element that will expire next,
     * if one exists.
     *
     * @return the head of this queue, or {@code null} if this
     * queue is empty
     */
    public E peek() {
        // 获取锁
        final ReentrantLock lock = this.lock;
        lock.lock();

        try {
            // 获取堆顶元素但不删除
            return q.peek();

            // 释放锁
        } finally {
            lock.unlock();
        }
    }

    public int size() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return q.size();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns first element only if it is expired.
     * Used only by drainTo.  Call only when holding lock.
     */
    private E peekExpired() {
        // assert lock.isHeldByCurrentThread();
        E first = q.peek();
        return (first == null || first.getDelay(NANOSECONDS) > 0) ?
                null : first;
    }

    /**
     * @throws UnsupportedOperationException {@inheritDoc}
     * @throws ClassCastException            {@inheritDoc}
     * @throws NullPointerException          {@inheritDoc}
     * @throws IllegalArgumentException      {@inheritDoc}
     */
    public int drainTo(Collection<? super E> c) {
        if (c == null)
            throw new NullPointerException();
        if (c == this)
            throw new IllegalArgumentException();
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            int n = 0;
            for (E e; (e = peekExpired()) != null; ) {
                c.add(e);       // In this order, in case add() throws.
                q.poll();
                ++n;
            }
            return n;
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
    public int drainTo(Collection<? super E> c, int maxElements) {
        if (c == null)
            throw new NullPointerException();
        if (c == this)
            throw new IllegalArgumentException();
        if (maxElements <= 0)
            return 0;
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            int n = 0;
            for (E e; n < maxElements && (e = peekExpired()) != null; ) {
                c.add(e);       // In this order, in case add() throws.
                q.poll();
                ++n;
            }
            return n;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Atomically removes all of the elements from this delay queue.
     * The queue will be empty after this call returns.
     * Elements with an unexpired delay are not waited for; they are
     * simply discarded from the queue.
     */
    public void clear() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            q.clear();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Always returns {@code Integer.MAX_VALUE} because
     * a {@code DelayQueue} is not capacity constrained.
     *
     * @return {@code Integer.MAX_VALUE}
     */
    public int remainingCapacity() {
        return Integer.MAX_VALUE;
    }

    /**
     * Returns an array containing all of the elements in this queue.
     * The returned array elements are in no particular order.
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
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return q.toArray();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns an array containing all of the elements in this queue; the
     * runtime type of the returned array is that of the specified array.
     * The returned array elements are in no particular order.
     * If the queue fits in the specified array, it is returned therein.
     * Otherwise, a new array is allocated with the runtime type of the
     * specified array and the size of this queue.
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
     * <p>The following code can be used to dump a delay queue into a newly
     * allocated array of {@code Delayed}:
     *
     * <pre> {@code Delayed[] a = q.toArray(new Delayed[0]);}</pre>
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
    public <T> T[] toArray(T[] a) {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return q.toArray(a);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Removes a single instance of the specified element from this
     * queue, if it is present, whether or not it has expired.
     *
     * 从队列中删除指定元素（如果存在），无论它是否已过期。
     */
    public boolean remove(Object o) {
        // 获取锁
        final ReentrantLock lock = this.lock;
        lock.lock();

        // 从优先级队列中删除该元素
        try {
            return q.remove(o);

            // 释放锁
        } finally {
            lock.unlock();
        }
    }

    /**
     * Identity-based version for use in Itr.remove
     */
    void removeEQ(Object o) {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            for (Iterator<E> it = q.iterator(); it.hasNext(); ) {
                if (o == it.next()) {
                    it.remove();
                    break;
                }
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns an iterator over all the elements (both expired and
     * unexpired) in this queue. The iterator does not return the
     * elements in any particular order.
     *
     * <p>The returned iterator is
     * <a href="package-summary.html#Weakly"><i>weakly consistent</i></a>.
     *
     * @return an iterator over the elements in this queue
     */
    public Iterator<E> iterator() {
        return new Itr(toArray());
    }

    /**
     * Snapshot iterator that works off copy of underlying q array.
     */
    private class Itr implements Iterator<E> {
        final Object[] array; // Array of all elements
        int cursor;           // index of next element to return
        int lastRet;          // index of last element, or -1 if no such

        Itr(Object[] array) {
            lastRet = -1;
            this.array = array;
        }

        public boolean hasNext() {
            return cursor < array.length;
        }

        @SuppressWarnings("unchecked")
        public E next() {
            if (cursor >= array.length)
                throw new NoSuchElementException();
            lastRet = cursor;
            return (E) array[cursor++];
        }

        public void remove() {
            if (lastRet < 0)
                throw new IllegalStateException();
            removeEQ(array[lastRet]);
            lastRet = -1;
        }
    }

}
