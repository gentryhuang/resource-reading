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

package java.util.concurrent.locks;

import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;

import sun.misc.Unsafe;

/**
 * Provides a framework for implementing blocking locks and related
 * synchronizers (semaphores, events, etc) that rely on
 * first-in-first-out (FIFO) wait queues.  This class is designed to
 * be a useful basis for most kinds of synchronizers that rely on a
 * single atomic {@code int} value to represent state. Subclasses
 * must define the protected methods that change this state, and which
 * define what that state means in terms of this object being acquired
 * or released.  Given these, the other methods in this class carry
 * out all queuing and blocking mechanics. Subclasses can maintain
 * other state fields, but only the atomically updated {@code int}
 * value manipulated using methods {@link #getState}, {@link
 * #setState} and {@link #compareAndSetState} is tracked with respect
 * to synchronization.
 *
 * <p>Subclasses should be defined as non-public internal helper
 * classes that are used to implement the synchronization properties
 * of their enclosing class.  Class
 * {@code AbstractQueuedSynchronizer} does not implement any
 * synchronization interface.  Instead it defines methods such as
 * {@link #acquireInterruptibly} that can be invoked as
 * appropriate by concrete locks and related synchronizers to
 * implement their public methods.
 *
 * <p>This class supports either or both a default <em>exclusive</em>
 * mode and a <em>shared</em> mode. When acquired in exclusive mode,
 * attempted acquires by other threads cannot succeed. Shared mode
 * acquires by multiple threads may (but need not) succeed. This class
 * does not &quot;understand&quot; these differences except in the
 * mechanical sense that when a shared mode acquire succeeds, the next
 * waiting thread (if one exists) must also determine whether it can
 * acquire as well. Threads waiting in the different modes share the
 * same FIFO queue. Usually, implementation subclasses support only
 * one of these modes, but both can come into play for example in a
 * {@link ReadWriteLock}. Subclasses that support only exclusive or
 * only shared modes need not define the methods supporting the unused mode.
 *
 * <p>This class defines a nested {@link ConditionObject} class that
 * can be used as a {@link Condition} implementation by subclasses
 * supporting exclusive mode for which method {@link
 * #isHeldExclusively} reports whether synchronization is exclusively
 * held with respect to the current thread, method {@link #release}
 * invoked with the current {@link #getState} value fully releases
 * this object, and {@link #acquire}, given this saved state value,
 * eventually restores this object to its previous acquired state.  No
 * {@code AbstractQueuedSynchronizer} method otherwise creates such a
 * condition, so if this constraint cannot be met, do not use it.  The
 * behavior of {@link ConditionObject} depends of course on the
 * semantics of its synchronizer implementation.
 *
 * <p>This class provides inspection, instrumentation, and monitoring
 * methods for the internal queue, as well as similar methods for
 * condition objects. These can be exported as desired into classes
 * using an {@code AbstractQueuedSynchronizer} for their
 * synchronization mechanics.
 *
 * <p>Serialization of this class stores only the underlying atomic
 * integer maintaining state, so deserialized objects have empty
 * thread queues. Typical subclasses requiring serializability will
 * define a {@code readObject} method that restores this to a known
 * initial state upon deserialization.
 *
 * <h3>Usage</h3>
 *
 * <p>To use this class as the basis of a synchronizer, redefine the
 * following methods, as applicable, by inspecting and/or modifying
 * the synchronization state using {@link #getState}, {@link
 * #setState} and/or {@link #compareAndSetState}:
 *
 * <ul>
 * <li> {@link #tryAcquire}
 * <li> {@link #tryRelease}
 * <li> {@link #tryAcquireShared}
 * <li> {@link #tryReleaseShared}
 * <li> {@link #isHeldExclusively}
 * </ul>
 * <p>
 * Each of these methods by default throws {@link
 * UnsupportedOperationException}.  Implementations of these methods
 * must be internally thread-safe, and should in general be short and
 * not block. Defining these methods is the <em>only</em> supported
 * means of using this class. All other methods are declared
 * {@code final} because they cannot be independently varied.
 *
 * <p>You may also find the inherited methods from {@link
 * AbstractOwnableSynchronizer} useful to keep track of the thread
 * owning an exclusive synchronizer.  You are encouraged to use them
 * -- this enables monitoring and diagnostic tools to assist users in
 * determining which threads hold locks.
 *
 * <p>Even though this class is based on an internal FIFO queue, it
 * does not automatically enforce FIFO acquisition policies.  The core
 * of exclusive synchronization takes the form:
 *
 * <pre>
 * Acquire:
 *     while (!tryAcquire(arg)) {
 *        <em>enqueue thread if it is not already queued</em>;
 *        <em>possibly block current thread</em>;
 *     }
 *
 * Release:
 *     if (tryRelease(arg))
 *        <em>unblock the first queued thread</em>;
 * </pre>
 * <p>
 * (Shared mode is similar but may involve cascading signals.)
 *
 * <p id="barging">Because checks in acquire are invoked before
 * enqueuing, a newly acquiring thread may <em>barge</em> ahead of
 * others that are blocked and queued.  However, you can, if desired,
 * define {@code tryAcquire} and/or {@code tryAcquireShared} to
 * disable barging by internally invoking one or more of the inspection
 * methods, thereby providing a <em>fair</em> FIFO acquisition order.
 * In particular, most fair synchronizers can define {@code tryAcquire}
 * to return {@code false} if {@link #hasQueuedPredecessors} (a method
 * specifically designed to be used by fair synchronizers) returns
 * {@code true}.  Other variations are possible.
 *
 * <p>Throughput and scalability are generally highest for the
 * default barging (also known as <em>greedy</em>,
 * <em>renouncement</em>, and <em>convoy-avoidance</em>) strategy.
 * While this is not guaranteed to be fair or starvation-free, earlier
 * queued threads are allowed to recontend before later queued
 * threads, and each recontention has an unbiased chance to succeed
 * against incoming threads.  Also, while acquires do not
 * &quot;spin&quot; in the usual sense, they may perform multiple
 * invocations of {@code tryAcquire} interspersed with other
 * computations before blocking.  This gives most of the benefits of
 * spins when exclusive synchronization is only briefly held, without
 * most of the liabilities when it isn't. If so desired, you can
 * augment this by preceding calls to acquire methods with
 * "fast-path" checks, possibly prechecking {@link #hasContended}
 * and/or {@link #hasQueuedThreads} to only do so if the synchronizer
 * is likely not to be contended.
 *
 * <p>This class provides an efficient and scalable basis for
 * synchronization in part by specializing its range of use to
 * synchronizers that can rely on {@code int} state, acquire, and
 * release parameters, and an internal FIFO wait queue. When this does
 * not suffice, you can build synchronizers from a lower level using
 * {@link java.util.concurrent.atomic atomic} classes, your own custom
 * {@link java.util.Queue} classes, and {@link LockSupport} blocking
 * support.
 *
 * <h3>Usage Examples</h3>
 *
 * <p>Here is a non-reentrant mutual exclusion lock class that uses
 * the value zero to represent the unlocked state, and one to
 * represent the locked state. While a non-reentrant lock
 * does not strictly require recording of the current owner
 * thread, this class does so anyway to make usage easier to monitor.
 * It also supports conditions and exposes
 * one of the instrumentation methods:
 *
 *  <pre> {@code
 * class Mutex implements Lock, java.io.Serializable {
 *
 *   // Our internal helper class
 *   private static class Sync extends AbstractQueuedSynchronizer {
 *     // Reports whether in locked state
 *     protected boolean isHeldExclusively() {
 *       return getState() == 1;
 *     }
 *
 *     // Acquires the lock if state is zero
 *     public boolean tryAcquire(int acquires) {
 *       assert acquires == 1; // Otherwise unused
 *       if (compareAndSetState(0, 1)) {
 *         setExclusiveOwnerThread(Thread.currentThread());
 *         return true;
 *       }
 *       return false;
 *     }
 *
 *     // Releases the lock by setting state to zero
 *     protected boolean tryRelease(int releases) {
 *       assert releases == 1; // Otherwise unused
 *       if (getState() == 0) throw new IllegalMonitorStateException();
 *       setExclusiveOwnerThread(null);
 *       setState(0);
 *       return true;
 *     }
 *
 *     // Provides a Condition
 *     Condition newCondition() { return new ConditionObject(); }
 *
 *     // Deserializes properly
 *     private void readObject(ObjectInputStream s)
 *         throws IOException, ClassNotFoundException {
 *       s.defaultReadObject();
 *       setState(0); // reset to unlocked state
 *     }
 *   }
 *
 *   // The sync object does all the hard work. We just forward to it.
 *   private final Sync sync = new Sync();
 *
 *   public void lock()                { sync.acquire(1); }
 *   public boolean tryLock()          { return sync.tryAcquire(1); }
 *   public void unlock()              { sync.release(1); }
 *   public Condition newCondition()   { return sync.newCondition(); }
 *   public boolean isLocked()         { return sync.isHeldExclusively(); }
 *   public boolean hasQueuedThreads() { return sync.hasQueuedThreads(); }
 *   public void lockInterruptibly() throws InterruptedException {
 *     sync.acquireInterruptibly(1);
 *   }
 *   public boolean tryLock(long timeout, TimeUnit unit)
 *       throws InterruptedException {
 *     return sync.tryAcquireNanos(1, unit.toNanos(timeout));
 *   }
 * }}</pre>
 *
 * <p>Here is a latch class that is like a
 * {@link java.util.concurrent.CountDownLatch CountDownLatch}
 * except that it only requires a single {@code signal} to
 * fire. Because a latch is non-exclusive, it uses the {@code shared}
 * acquire and release methods.
 *
 *  <pre> {@code
 * class BooleanLatch {
 *
 *   private static class Sync extends AbstractQueuedSynchronizer {
 *     boolean isSignalled() { return getState() != 0; }
 *
 *     protected int tryAcquireShared(int ignore) {
 *       return isSignalled() ? 1 : -1;
 *     }
 *
 *     protected boolean tryReleaseShared(int ignore) {
 *       setState(1);
 *       return true;
 *     }
 *   }
 *
 *   private final Sync sync = new Sync();
 *   public boolean isSignalled() { return sync.isSignalled(); }
 *   public void signal()         { sync.releaseShared(1); }
 *   public void await() throws InterruptedException {
 *     sync.acquireSharedInterruptibly(1);
 *   }
 * }}</pre>
 *
 * @author Doug Lea
 * @since 1.5
 */
public abstract class AbstractQueuedSynchronizer
        extends AbstractOwnableSynchronizer
        implements java.io.Serializable {

    private static final long serialVersionUID = 7373984972572414691L;

    /**
     * Creates a new {@code AbstractQueuedSynchronizer} instance
     * with initial synchronization state of zero.
     */
    protected AbstractQueuedSynchronizer() {
    }

    /**
     * Wait queue node class.
     */
    static final class Node {
        /**
         * 共享类型节点，表明节点在共享模式下等待
         */
        static final Node SHARED = new Node();
        /**
         * 独占类型节点，表明节点在独占模式下等待
         */
        static final Node EXCLUSIVE = null;

        /**
         * 等待状态 - 取消
         * 当前线程因为超时或被中断取消，属于一个终结态
         */
        static final int CANCELLED = 1;
        /**
         * 等待状态 - 通知（后继线程需要被唤醒）
         * 获取同步状态的线程释放同步状态或者取消后需要唤醒后继线程；这个状态一般都是后继线程来设置前驱节点的。
         */
        static final int SIGNAL = -1;
        /**
         * 等待状态 - 条件等待（线程在 Condition 上等待）
         * 0 状态 和 CONDITION 都属于初始状态
         */
        static final int CONDITION = -2;
        /**
         * 等待状态 - 传播（无条件向后传播唤醒动作）
         * 用于将唤醒的后继线程传递下去，该状态的引入是为了完善和增强共享状态的唤醒机制。在一个节点成为头节点之前，是不会成为此状态的。
         * 特别说明：
         * 该状态的引入是为了解决共享同步状态并发释放导致的线程 hang 住问题
         */
        static final int PROPAGATE = -3;
        /**
         * 等待状态，初始值为 0，表示无状态
         */
        volatile int waitStatus;
        /**
         * 同步队列中使用，前驱节点
         */
        volatile Node prev;
        /**
         * 同步队列中使用，后继节点
         */
        volatile Node next;
        /**
         * 节点中封装的线程
         */
        volatile Thread thread;
        /**
         * 条件队列中使用，下一个节点
         */
        Node nextWaiter;

        /**
         * 判断当前节点是否处于共享模式等待
         */
        final boolean isShared() {
            return nextWaiter == SHARED;
        }

        /**
         * 获取前驱节点，如果为空的话抛出空指针异常
         *
         * @return
         */
        final Node predecessor() throws NullPointerException {
            Node p = prev;
            if (p == null)
                throw new NullPointerException();
            else
                return p;
        }

        Node() {    // Used to establish initial head or SHARED marker
        }

        /**
         * addWaiter会调用此构造函数
         */
        Node(Thread thread, Node mode) {     // Used by addWaiter
            this.nextWaiter = mode;
            this.thread = thread;
        }

        /**
         * Condition会用到此构造函数
         */
        Node(Thread thread, int waitStatus) { // Used by Condition
            this.waitStatus = waitStatus;
            this.thread = thread;
        }
    }

    /**
     * 延迟初始化的同步队列头，除了初始化，它只能通过方法setHead进行修改。
     * 注意：
     * 1 head 在逻辑上的含义是当前持有锁的线程，head 节点实际上是一个虚节点，本身并不会存储线程信息
     * 2 如果head存在，它的waitStatus保证不会被CANCELLED。
     */
    private transient volatile Node head;

    /**
     * 同步队列的尾部，延迟初始化。仅通过方法enq修改以添加新的等待节点。
     * 说明：
     * 当一个线程无法获取同步状态而需要被加入到同步队列时，会使用 CAS 来设置尾节点 tail 为当前线程对应的 Node 节点
     */
    private transient volatile Node tail;

    /**
     * 同步状态
     */
    private volatile int state;


    protected final int getState() {
        return state;
    }

    protected final void setState(int newState) {
        state = newState;
    }

    protected final boolean compareAndSetState(int expect, int update) {
        // See below for intrinsics setup to support this
        return unsafe.compareAndSwapInt(this, stateOffset, expect, update);
    }

    // Queuing utilities

    /**
     * 1000 纳秒 = 1 毫秒
     */
    static final long spinForTimeoutThreshold = 1000L;


    /**
     * 在同步队列中新增一个节点 Node
     *
     * @param mode Node.EXCLUSIVE 类型是独占模式, Node.SHARED 类型是共享模式
     * @return 返回新创建的节点
     */
    private Node addWaiter(Node mode) {
        // 将当前线程和对应的类型 封装成一个 Node
        Node node = new Node(Thread.currentThread(), mode);

        // 将当前 node 设置为链表的尾部
        Node pred = tail;

        // 链表不为空
        if (pred != null) {

            // 先设置当前节点的前驱，确保 node 前驱节点不为 null
            node.prev = pred;

            // 通过CAS将当前节点设置为 tail
            if (compareAndSetTail(pred, node)) {

                // 上面的已经先处理 node.prev = pred ，再加上下面的  pred.next = node ，也就是实现了将当前节点 node 完整加入到链表中，也就是同步队列的末尾。
                pred.next = node;

                // 入队后直接返回当前节点
                return node;
            }
        }

        // 执行到这里说明队列为空(pred == null) 或者 CAS 加入尾部失败
        enq(node);

        return node;
    }

    /**
     * 通过自旋+CAS 在队列中成功插入一个节点后返回。
     * 说明：
     * 该方法处理两种可能：等待队列为空，或者有线程竞争入队
     *
     * @param node the node to insert
     * @return node's predecessor
     */
    private Node enq(final Node node) {
        for (; ; ) {
            Node t = tail;

            // 队列为空处理
            if (t == null) { // Must initialize

                // head 和 tail 初始化的时候都是 null ，这里使用 CAS 为了处理多个线程同时进来的情况。
                // 注意：这里只是设置了 tail = head，并没有返回，也就是接着自旋
                if (compareAndSetHead(new Node()))
                    tail = head;


            } else {

                // 确保 node 前驱节点不为 null
                node.prev = t;

                // CAS 设置 tail 为 node，成功后把老的 tail也就是t连接到 node。
                // 注意：这里也是 CAS 操作，就是将当前线程节点排到队尾，有线程竞争的话排不上重复排
                if (compareAndSetTail(t, node)) {
                    t.next = node;
                    return t;
                }
            }
        }
    }


    /**
     * 占领 head 节点，即将当前节点 node 设置为虚节点 head
     * 注意：
     * 1 头节点都是虚节点，它对应当前持有同步状态的节点。
     * 2 先当前节点的线程信息抹除掉，且断开和前置节点的联系，便于 GC
     * 3 不修改 waitStatus，因为它是一直需要用的数据
     *
     * @param node the node
     */
    private void setHead(Node node) {
        head = node;
        node.thread = null;
        node.prev = null;
    }

    /**
     * 唤醒后继节点（线程）
     *
     * @param node the node
     */
    private void unparkSuccessor(Node node) {

        // 尝试将 node 的等待状态设置为 0 ，这样的话后继竞争线程可以有机会再尝试获取一次同步状态
        int ws = node.waitStatus;
        if (ws < 0)
            compareAndSetWaitStatus(node, ws, 0);


        /**
         * 如果 node.next 存在且状态不为取消，则直接唤醒 s 即可。否则需要从 tail 开始向前找到 node 之后最近的非取消节点然后唤醒它，没有则无需唤醒。
         * 注意：s == null ，不代表 node 就是 tail ，如 addWaiter 方法过程：
         *  1 某时刻 node 为 tail
         *  2 有新的线程通过 addWaiter 方法添加自己到同步队列
         *  3 compareAndSetTail 成功，但此时 node.next 指针还没有更新完成，值仍为 null ，而此时 node 已经不是 tail，它有后继了
         */
        Node s = node.next;
        if (s == null || s.waitStatus > 0) {
            s = null;
            // 从后往前找，不必担心中间有节点取消 (waitStatus==1) 的情况
            for (Node t = tail; t != null && t != node; t = t.prev)
                if (t.waitStatus <= 0)
                    s = t;
        }

        // 唤醒节点 s 中的线程
        if (s != null)
            LockSupport.unpark(s.thread);
    }

    /**
     * 共享模式的核心唤醒方法
     * 主要任务：唤醒后继节点或设置传播状态 PROPAGATE
     * 后继节点被唤醒后，会尝试获取共享同步状态，如果成功后又会调用 setHeadAndPropagate 将唤醒传播下去。
     * 注意：
     * 该方法用于保障在 acquire 和 release 存在竞争的情况下，保证队列中处于等待状态的节点能够有办法被唤醒。
     */
    private void doReleaseShared() {

        /**
         * 以下循环做的事情是，在队列存在后继节点时，唤醒后继节点；或者由于并发释放共享同步状态导致读到 head 节点等待状态为 0 ，虽然不能执行 unparkSuccessor ，
         * 但为了保证唤醒能够正确传递下去，设置节点状态为 PROPAGATE。这样的话获取同步状态的线程在执行 setHeadAndPropagate 时可以读到 PROPAGATE，从而由获取
         * 同步状态的线程去释放后继等待节点。
         */
        for (; ; ) {
            Node h = head;
            // 如果队列中存在后继节点
            if (h != null && h != tail) {
                int ws = h.waitStatus;

                // 如果 head 的状态为 SIGNAL ，则尝试将其设置为 0 并唤醒后继节点
                if (ws == Node.SIGNAL) {
                    if (!compareAndSetWaitStatus(h, Node.SIGNAL, 0))
                        continue;            // loop to recheck cases
                    // 唤醒后继节点
                    unparkSuccessor(h);


                    // 如果 head 节点的状态为 0 ,需要设置为 PROPAGATE 用以保证唤醒的传播，即通过 setHeadAndPropagate 方法唤醒此时由于并发导致的未能唤醒的后继节点
                } else if (ws == 0 &&
                        !compareAndSetWaitStatus(h, 0, Node.PROPAGATE))
                    continue;                // loop on failed CAS
            }

            // 检查h是否仍然是head，如果不是的话需要再进行循环。
            if (h == head)                   // loop if head changed
                break;
        }
    }

    /**
     * 该方法主要做以下两件事：
     * 1. 在获取共享同步状态后，占领 head 节点
     * 2. 根据情况唤醒后继线程
     *
     * @param node      the node
     * @param propagate the return value from a tryAcquireShared
     */
    private void setHeadAndPropagate(Node node, int propagate) {
        // 记录 head
        Node h = head; // Record old head for check below

        // 占领 head
        setHead(node);

        /**
         * 1 propagate 是 tryAcquireShared 的返回值，这是决定是否传播唤醒的依据之一。
         * 2 h.waitStatus 为 SIGNAL 或 PROPAGATE 时，根据 node 的下一个节点类型（共享模式）来决定是否传播唤醒
         * 注意：
         *  todo 这里不能只用 propagate > 0 来决定是否可以传播，节点等待状态为 PROPAGATE 也要考虑，这种情况是由于并发释放同步状态导致持有同步状态的线程没有去唤醒本该被唤醒的节点，
         * 这种情况相当于做了一个补偿。
         */
        if (propagate > 0 || h == null || h.waitStatus < 0 ||
                (h = head) == null || h.waitStatus < 0) {

            Node s = node.next;

            // 注意 s == null 不代表 node 就是尾节点，可能它的后继节点取消了排队，这种情况已经继续尝试唤醒有效的后继节点
            if (s == null || s.isShared())
                doReleaseShared();
        }
    }

    // Utilities for various versions of acquire

    /**
     * 取消 node 排队。
     * 注意：
     * 取消的节点会在 shouldParkAfterFailedAcquire 中被踢掉
     *
     * @param node the node
     */
    private void cancelAcquire(Node node) {
        // Ignore if node doesn't exist
        if (node == null)
            return;

        // 设置节点 node 不关联任何线程
        node.thread = null;

        /* 寻找一个有效的前驱的节点作为 node 的前驱，下面在调整链表时会用到 */
        // 获取 node 的前驱节点
        Node pred = node.prev;

        // 跳过取消的节点，向前寻找第一个非取消节点作为 node 的前驱节点
        while (pred.waitStatus > 0)
            node.prev = pred = pred.prev;

        // 记录 node 的第一个有效前驱节点的后继节点，后续 CAS 会用到
        Node predNext = pred.next;

        // 直接把当前节点 node 的等待状态置为取消,后继节点即便也在取消也可以跨越 node节点。
        node.waitStatus = Node.CANCELLED;


        /* 根据当前取消节点 node 的位置，考虑以下三种情况：
         * 1 当前节点是尾节点
         * 2 当前节点是 head 的后继节点
         * 3 当前节点既不是 head 后继节点，也不是尾节点
         */

        // 1 如果 node 是尾节点，则使用 CAS 尝试将它的有效前驱节点 pred 设置为 tail
        if (node == tail && compareAndSetTail(node, pred)) {
            // 这里的CAS更新 pre d的 next 即使失败了也没关系，说明被其它新入队线程或者其它取消线程更新掉了。
            compareAndSetNext(pred, predNext, null);

            // 如果 node 不是尾节点，那么要做的事情就是将 node 有效前驱和后继节点连接起来
        } else {
            // If successor needs signal, try to set pred's next-link
            // so it will get one. Otherwise wake it up to propagate.
            int ws;

            // 2 当前节点不是 head 的后继节点：
            // a 判断当前节点前驱节点是否为 -1
            // b 如果不是，则把前驱节点设置为 SIGNAL 看是否成功
            // c 如果 a 和 b 中有一个为true，再判断当前节点的线程是否不为null
            // 如果上述条件都满足，把当前节点的前驱节点的后继指针指向当前节点的后继节点。
            if (pred != head &&
                    ((ws = pred.waitStatus) == Node.SIGNAL ||
                            (ws <= 0 && compareAndSetWaitStatus(pred, ws, Node.SIGNAL))) &&
                    pred.thread != null) {

                // 如果node的后继节点next非取消状态的话，则用CAS尝试把pred的后继置为node的后继节点
                Node next = node.next;
                if (next != null && next.waitStatus <= 0)
                    compareAndSetNext(pred, predNext, next);


                // 3 pred == head 或者 pred 状态取消或者 pred.thread == null ，这时为了保证队列的活跃性，会尝试唤醒一次后继线程。
            } else {
                unparkSuccessor(node);
            }

            // 将取消节点的 next 设置为自己而非 null，原因如下：
            //  AQS 中 Condition部分的isOnSyncQueue 方法会根据 next 判断一个原先属于条件队列的节点是否转移到了同步队列。同步队列中节点会用到 next 域，取消节点的 next 也有值的话，
            //  可以判断该节点一定在同步队列上
            node.next = node; // help GC
        }
    }

    /**
     * 为 node 节点找大哥，也就是有效前驱节点。
     * 说明：
     * 1 前驱节点的 waitStatus=-1 是依赖于后继节点设置的。也就是说，当还没给前驱设置-1 时返回 false，第二次进来的时候状态就是-1了
     * 2 进入同步队列中挂起的线程唤醒操作是由其有效前驱节点完成的。等着前驱节点获取到同步状态，然后释放同步状态时唤醒自己。
     * 3 shouldParkAfterFailedAcquire 在读到前驱节点状态不为 SIGNAL 会给当前线程再一次获取同步状态的机会
     *
     * @param pred node's predecessor holding status
     * @param node the node
     * @return {@code true} if thread should block
     */
    private static boolean shouldParkAfterFailedAcquire(Node pred, Node node) {
        int ws = pred.waitStatus;

        // 前驱节点已经是 SIGNAL 状态，说明是有效的前驱，则后继节点（也就是当前节点）可以进入挂起模式等待它的前驱节点唤醒自己。
        // 因为节点状态为 SIGNAL 在释放同步状态时会唤醒后继节点。
        if (ws == Node.SIGNAL)
            /*
             * This node has already set status asking a release
             * to signal it, so it can safely park.
             */
            return true;

        // 前驱节点状态为 CANCELLED 状态，说明当前前驱节点取消了排队，是个无效的节点，需要把该节点剔除掉
        // 因此需要向前找第一个非取消节点作为 node 的有效前驱（就靠这个大哥到时候唤醒自己），往前遍历总能找到一个大哥
        if (ws > 0) {
            /*
             * Predecessor was cancelled. Skip over predecessors and
             * indicate retry.
             */
            do {
                node.prev = pred = pred.prev;
            } while (pred.waitStatus > 0);
            pred.next = node;

            // 前驱节点状态为 0 或者 PROPAGATE ，则设置前驱节点状态为 SIGNAL，即将当前 pred 对应的节点作为大哥
        } else {
            /*
             * waitStatus must be 0 or PROPAGATE.  Indicate that we
             * need a signal, but don't park yet.  Caller will need to
             * retry to make sure it cannot acquire before parking.
             */
            compareAndSetWaitStatus(pred, ws, Node.SIGNAL);
        }

        // 返回 false ，那么最后会再走一次外部的 for 循环然后再次进入此方法
        return false;
    }

    /**
     * 中断当前线程
     */
    static void selfInterrupt() {
        Thread.currentThread().interrupt();
    }

    /**
     * 挂起当前线程，返回当前线程的中断状态
     * 备注：
     * 1 interrupt() 中断线程，给线程设置一个中断标志
     * 2 interrupted() 判断当前线程是否被中断，返回一个boolean并清除中断状态，第二次再调用时中断状态已经被清除，将返回一个false。
     * 3 isInterrupted() 判断线程是否被中断，不清除中断状态
     *
     * @return {@code true} if interrupted
     */
    private final boolean parkAndCheckInterrupt() {
        LockSupport.park(this);
        return Thread.interrupted();
    }

    /*
     * Various flavors of acquire, varying in exclusive/shared and
     * control modes.  Each is mostly the same, but annoyingly
     * different.  Only a little bit of factoring is possible due to
     * interactions of exception mechanics (including ensuring that we
     * cancel if tryAcquire throws exception) and other control, at
     * least not without hurting performance too much.
     */

    /**
     * 线程挂起，被唤醒后去获取同步状态
     *
     * @param node the node
     * @param arg  the acquire argument
     * @return {@code true} if interrupted while waiting
     */
    final boolean acquireQueued(final Node node, int arg) {
        boolean failed = true;
        try {
            boolean interrupted = false;

            for (; ; ) {

                //  node 的前驱节点，如果为空会跑出 np 异常
                final Node p = node.predecessor();

                // 检测当前节点前驱是否 head，这是尝试获取同步状态的前提条件。注意，如果 p 是 head，说明当前节点在真实数据队列的首部，就尝试获取同步状态，头节点可是一个虚节点。
                // 执行这一步一般有两种情况：
                // 1 线程因获取不到同步状态而入队，在进入等待之前再次尝试获取同步状态，可能此时它的前驱节点已经完全释放同步状态
                // 2 只能进入同步队列中等待，等醒来时继续尝试执行该方法
                if (p == head && tryAcquire(arg)) {

                    // 当前节点占领 head，并将 p 从同步队列中移除，防止内存泄漏
                    setHead(node);
                    p.next = null; // help GC

                    failed = false;
                    return interrupted;
                }

                // 执行到这里，说明上面的 if 分支没有成功，要么当前 node 的前驱节点不是 head ，要么就是 tryAcquire 没有竞争过其他节点。
                // 进入找大哥阶段，找到大哥后阻塞挂起自己。
                if (shouldParkAfterFailedAcquire(p, node) &&
                        parkAndCheckInterrupt())

                    // 如果阻塞过程中被中断，则设置 interrupted 为 true
                    interrupted = true;
            }

        } finally {
            // node.predecessor() 为空 或 tryAcquire 方法抛出异常的情况
            if (failed)
                cancelAcquire(node);
        }
    }

    /**
     * 在队列中的节点通过此方法获取锁，对中断敏感
     *
     * @param arg the acquire argument
     */
    private void doAcquireInterruptibly(int arg)
            throws InterruptedException {
        final Node node = addWaiter(Node.EXCLUSIVE);
        boolean failed = true;
        try {
            for (; ; ) {
                final Node p = node.predecessor();
                if (p == head && tryAcquire(arg)) {
                    setHead(node);
                    p.next = null; // help GC
                    failed = false;
                    return;
                }
                if (shouldParkAfterFailedAcquire(p, node) &&
                        parkAndCheckInterrupt())
                    // 发生中断，直接抛出异常
                    throw new InterruptedException();
            }
        } finally {
            if (failed)
                cancelAcquire(node);
        }
    }

    /**
     * Acquires in exclusive timed mode.
     *
     * @param arg          the acquire argument
     * @param nanosTimeout max wait time
     * @return {@code true} if acquired
     */
    private boolean doAcquireNanos(int arg, long nanosTimeout)
            throws InterruptedException {
        if (nanosTimeout <= 0L)
            return false;

        // 记录超时时间
        final long deadline = System.nanoTime() + nanosTimeout;
        final Node node = addWaiter(Node.EXCLUSIVE);
        boolean failed = true;
        try {
            for (; ; ) {
                final Node p = node.predecessor();
                if (p == head && tryAcquire(arg)) {
                    setHead(node);
                    p.next = null; // help GC
                    failed = false;
                    return true;
                }

                // 获取同步状态等待时间
                nanosTimeout = deadline - System.nanoTime();
                // 超时则直接返回
                if (nanosTimeout <= 0L)
                    return false;

                // 寻找有效的前驱节点，找到后挂起当前线程 nanosTimeout 时间，在这段时间内没有被唤醒也会自动醒来
                if (shouldParkAfterFailedAcquire(p, node) &&
                        nanosTimeout > spinForTimeoutThreshold)
                    LockSupport.parkNanos(this, nanosTimeout);
                if (Thread.interrupted())
                    throw new InterruptedException();
            }
        } finally {
            if (failed)
                cancelAcquire(node);
        }
    }

    /**
     * 共享模式获取同步状态
     *
     * @param arg the acquire argument
     */
    private void doAcquireShared(int arg) {

        // 将当前线程以共享模式的方式加入到同步队列
        final Node node = addWaiter(Node.SHARED);
        boolean failed = true;
        try {
            boolean interrupted = false;

            for (; ; ) {
                // 获取前驱节点
                final Node p = node.predecessor();

                // 如果前驱节点为 head ，则尝试获取同步状态
                if (p == head) {
                    int r = tryAcquireShared(arg);

                    // 一旦获取共享同步状态成功，通过传播机制唤醒后继节点
                    if (r >= 0) {

                        setHeadAndPropagate(node, r);
                        p.next = null; // help GC
                        if (interrupted)
                            selfInterrupt();
                        failed = false;
                        return;
                    }
                }

                // 进入找大哥流程
                if (shouldParkAfterFailedAcquire(p, node) &&
                        // 挂起线程
                        parkAndCheckInterrupt())
                    interrupted = true;
            }
        } finally {
            // 取消节点
            if (failed)
                cancelAcquire(node);
        }
    }

    /**
     * 共享中断模式获取
     *
     * @param arg the acquire argument
     */
    private void doAcquireSharedInterruptibly(int arg)
            throws InterruptedException {
        final Node node = addWaiter(Node.SHARED);
        boolean failed = true;
        try {
            for (; ; ) {
                final Node p = node.predecessor();
                if (p == head) {
                    int r = tryAcquireShared(arg);
                    if (r >= 0) {
                        setHeadAndPropagate(node, r);
                        p.next = null; // help GC
                        failed = false;
                        return;
                    }
                }
                if (shouldParkAfterFailedAcquire(p, node) &&
                        parkAndCheckInterrupt())
                    // 响应中断
                    throw new InterruptedException();
            }
        } finally {
            if (failed)
                cancelAcquire(node);
        }
    }

    /**
     * Acquires in shared timed mode.
     *
     * @param arg          the acquire argument
     * @param nanosTimeout max wait time
     * @return {@code true} if acquired
     */
    private boolean doAcquireSharedNanos(int arg, long nanosTimeout)
            throws InterruptedException {
        if (nanosTimeout <= 0L)
            return false;

        // 记录超时时间
        final long deadline = System.nanoTime() + nanosTimeout;
        final Node node = addWaiter(Node.SHARED);
        boolean failed = true;
        try {
            for (; ; ) {
                final Node p = node.predecessor();
                if (p == head) {
                    int r = tryAcquireShared(arg);
                    if (r >= 0) {
                        setHeadAndPropagate(node, r);
                        p.next = null; // help GC
                        failed = false;
                        return true;
                    }
                }

                // 获取同步状态等待时间
                nanosTimeout = deadline - System.nanoTime();

                // 超时则直接返回 false
                if (nanosTimeout <= 0L)
                    return false;

                // 寻找有效的前驱节点，找到后挂起当前线程 nanosTimeout 时间，在这段时间内没有被唤醒也会自动醒来
                if (shouldParkAfterFailedAcquire(p, node) &&
                        nanosTimeout > spinForTimeoutThreshold)
                    LockSupport.parkNanos(this, nanosTimeout);
                if (Thread.interrupted())
                    throw new InterruptedException();
            }
        } finally {
            if (failed)
                cancelAcquire(node);
        }
    }

    // Main exported methods


    protected boolean tryAcquire(int arg) {
        throw new UnsupportedOperationException();
    }


    protected boolean tryRelease(int arg) {
        throw new UnsupportedOperationException();
    }

    protected int tryAcquireShared(int arg) {
        throw new UnsupportedOperationException();
    }

    protected boolean tryReleaseShared(int arg) {
        throw new UnsupportedOperationException();
    }

    /**
     * 判断当前线程是否获得了独占锁，由具体同步器实现
     */
    protected boolean isHeldExclusively() {
        throw new UnsupportedOperationException();
    }

    /**
     * 获取独占同步状态，忽略中断
     * <p>
     * 1 先尝试获取一次同步状态，如果成功则返回
     * 2 获取同步状态失败则将当前线程包装成 Node 插入到同步队列中
     * 3 检测创建的 Node 是否为 head 的直接后继节点，如果是会尝试获取同步状态。
     * 如果获取失败则通过 LockSupport 阻塞当前线程，直至被释放同步状态的线程唤醒或则被中断，随后再次尝试获取同步状态，如此反复。
     *
     * @param arg 同步状态值
     */
    public final void acquire(int arg) {
        // 1 调用具体同步器实现的 tryAcquire 方法获取同步状态
        if (!tryAcquire(arg) &&

                // 2 获取同步状态失败，先调用 addWaiter 方法将当前线程封装成独占模式的 Node 插入到同步队列中，然后调用 acquireQueued 方法
                acquireQueued(addWaiter(Node.EXCLUSIVE), arg))

            // 3 执行到这里说明在等待期间线程被中断了，那么线程需要自我中断，用于复位中断标志
            selfInterrupt();
    }

    /**
     * 独占模式中断即终止
     *
     * @param arg the acquire argument.  This value is conveyed to
     *            {@link #tryAcquire} but is otherwise uninterpreted and
     *            can represent anything you like.
     * @throws InterruptedException if the current thread is interrupted
     */
    public final void acquireInterruptibly(int arg)
            throws InterruptedException {
        // 如果线程被中断则直接抛出中断异常
        if (Thread.interrupted())
            throw new InterruptedException();
        if (!tryAcquire(arg))
            // 线程如果被中断过会抛出中断异常
            doAcquireInterruptibly(arg);
    }

    /**
     * 超时获取独占同步状态，忽略中断
     *
     * @param arg          the acquire argument.  This value is conveyed to
     *                     {@link #tryAcquire} but is otherwise uninterpreted and
     *                     can represent anything you like.
     * @param nanosTimeout the maximum number of nanoseconds to wait
     * @return {@code true} if acquired; {@code false} if timed out
     * @throws InterruptedException if the current thread is interrupted
     */
    public final boolean tryAcquireNanos(int arg, long nanosTimeout)
            throws InterruptedException {
        if (Thread.interrupted())
            throw new InterruptedException();
        return tryAcquire(arg) ||
                // 获取同步状态失败，进入超时获取同步状态逻辑
                doAcquireNanos(arg, nanosTimeout);
    }

    /**
     * 独占模式释放同步状态
     * <p>
     * 对于释放独占同步状态，首先会调用 tryRelease 方法，在完全释放掉独占锁后，这时后继线程就可以尝试获取同步状态了，
     * 因此，释放者线程需要做的事情是唤醒同步队列中的后继节点，让它尝试获取独占同步状态
     *
     * @param arg the release argument.  This value is conveyed to
     *            {@link #tryRelease} but is otherwise uninterpreted and
     *            can represent anything you like.
     * @return the value returned from {@link #tryRelease}
     */
    public final boolean release(int arg) {
        // 调用 tryRelease 方法释放 arg 个同步状态
        if (tryRelease(arg)) {

            // 这里的 head 可能性比较多，不一定是当前线程占有的节点。head 有以下几种情况：
            // 1. null ，AQS 的 head 延迟初始化 + 无竞争的情况
            // 2. 当前线程通过 tryRelease 方法已经完全释放掉了同步状态，刚好此时有新的节点在 acquireQueue 中获取到了同步状态，并设置了 head
//                * 第 2 种情况可以再分为两种情况：
//                     （一）时刻1:线程A通过acquireQueued，持锁成功，set了head
//                              时刻2:线程B通过tryAcquire试图获取独占锁失败失败，进入acquiredQueued
//                              时刻3:线程A通过tryRelease释放了独占锁
//                              时刻4:线程B通过acquireQueued中的tryAcquire获取到了独占锁并调用setHead
//                              时刻5:线程A读到了此时的head实际上是线程B对应的node
//                     （二）时刻1:线程A通过tryAcquire直接持锁成功，head为null
//                              时刻2:线程B通过tryAcquire试图获取独占锁失败失败，入队过程中初始化了head，进入acquiredQueued
//                              时刻3:线程A通过tryRelease释放了独占锁，此时线程B还未开始tryAcquire
//                              时刻4:线程A读到了此时的head实际上是线程B初始化出来的傀儡head

            // 当前线程获取 head 节点
            Node h = head;

            // head 节点状态不会是 CANCELLED ，所以这里 h.waitStatus != 0 相当于 h.waitStatus < 0
            // 只有 head 存在且状态小于 0 的情况下唤醒
            if (h != null && h.waitStatus != 0)
                // 唤醒后继节点
                unparkSuccessor(h);
            return true;
        }
        return false;
    }

    /**
     * 获取共享模式的同步状态，对中断不敏感。
     * 如果需要使用共享模式，那么在实现 tryAcquireShared 方法时需要注意，返回负数表示获取失败；返回 0 表示成功，但是后继竞争线程不会成功；返回正数表示获取成功，
     * 并且后继竞争线程也可能成功。
     *
     * @param arg the acquire argument.  This value is conveyed to
     *            {@link #tryAcquireShared} but is otherwise uninterpreted
     *            and can represent anything you like.
     */
    public final void acquireShared(int arg) {

        // 调用 tryAcquireShared 方法尝试获取同步状态
        if (tryAcquireShared(arg) < 0)
            // 获取失败
            doAcquireShared(arg);
    }

    /**
     * 共享模式中断即终止
     *
     * @param arg
     * @throws InterruptedException
     */
    public final void acquireSharedInterruptibly(int arg)
            throws InterruptedException {
        // 中断处理
        if (Thread.interrupted())
            throw new InterruptedException();
        if (tryAcquireShared(arg) < 0)
            // 线程如果被中断过会抛出中断异常
            doAcquireSharedInterruptibly(arg);
    }

    /**
     * 共享模式忽略中断
     *
     * @param arg
     * @param nanosTimeout
     * @return
     * @throws InterruptedException
     */
    public final boolean tryAcquireSharedNanos(int arg, long nanosTimeout)
            throws InterruptedException {
        // 中断处理
        if (Thread.interrupted())
            throw new InterruptedException();
        return tryAcquireShared(arg) >= 0 ||
                // 获取同步状态失败，进入超时获取同步状态逻辑
                doAcquireSharedNanos(arg, nanosTimeout);
    }

    /**
     * 释放共享同步状态
     * 注意：
     * 共享锁的获取过程（执行传播）和释放都会涉及到 doReleaseShared 方法，也就是后继节点的唤醒
     *
     * @param arg the release argument.  This value is conveyed to
     *            {@link #tryReleaseShared} but is otherwise uninterpreted
     *            and can represent anything you like.
     * @return the value returned from {@link #tryReleaseShared}
     */
    public final boolean releaseShared(int arg) {
        // 调用 tryReleaseShared 方法尝试释放同步状态
        if (tryReleaseShared(arg)) {
            // 进入唤醒后继节点逻辑
            doReleaseShared();
            return true;
        }
        return false;
    }

    // Queue inspection methods


    public final boolean hasQueuedThreads() {
        return head != tail;
    }


    /**
     * 判断是否有其他线程在同步队列中等待
     * <p>
     * Queries whether any threads have been waiting to acquire longer
     * than the current thread.
     *
     * <p>An invocation of this method is equivalent to (but may be
     * more efficient than):
     * <pre> {@code
     * getFirstQueuedThread() != Thread.currentThread() &&
     * hasQueuedThreads()}</pre>
     *
     * <p>Note that because cancellations due to interrupts and
     * timeouts may occur at any time, a {@code true} return does not
     * guarantee that some other thread will acquire before the current
     * thread.  Likewise, it is possible for another thread to win a
     * race to enqueue after this method has returned {@code false},
     * due to the queue being empty.
     *
     * <p>This method is designed to be used by a fair synchronizer to
     * avoid <a href="AbstractQueuedSynchronizer#barging">barging</a>.
     * Such a synchronizer's {@link #tryAcquire} method should return
     * {@code false}, and its {@link #tryAcquireShared} method should
     * return a negative value, if this method returns {@code true}
     * (unless this is a reentrant acquire).  For example, the {@code
     * tryAcquire} method for a fair, reentrant, exclusive mode
     * synchronizer might look like this:
     *
     * <pre> {@code
     * protected boolean tryAcquire(int arg) {
     *   if (isHeldExclusively()) {
     *     // A reentrant acquire; increment hold count
     *     return true;
     *   } else if (hasQueuedPredecessors()) {
     *     return false;
     *   } else {
     *     // try to acquire normally
     *   }
     * }}</pre>
     *
     * @return {@code true} if there is a queued thread preceding the
     * current thread, and {@code false} if the current thread
     * is at the head of the queue or the queue is empty
     * @since 1.7
     */

    /**
     * 用于公平模式时判断同步队列中是否存在有效节点
     *
     * @return true - 说明队列中存在有效节点，当前线程必须加入同步队列中等待；false - 说明当前线程可以竞争同步状态
     */
    public final boolean hasQueuedPredecessors() {
        // The correctness of this depends on head being initialized
        // before tail and on head.next being accurate if the current
        // thread is first in queue.
        Node t = tail; // Read fields in reverse initialization order
        Node h = head;
        Node s;
        return h != t &&
                ((s = h.next) == null || s.thread != Thread.currentThread());
    }


    // Internal support methods for Conditions

    // 判断节点是否在同步队列中
    final boolean isOnSyncQueue(Node node) {

        /**
         * 1 同步队列中的节点状态可能为 0、SIGNAL = -1、PROPAGATE = -3、CANCELLED = 1，但不会是 CONDITION = -2
         * 2 node.prev 仅会在节点获取同步状态后，调用 setHead 方法将自己设为头结点时被设置为 null，所以只要节点在同步队列中，node.prev 一定不会为 null
         */
        if (node.waitStatus == Node.CONDITION || node.prev == null)
            return false;

        /**
         * 1 条件队列中节点是使用 nextWaiter 指向后继节点，next 均为 null 。同步队列中节点是使用 next 指向后继节点。
         * 2 node.next != null 代表当前节点 node 一定在同步队列中。
         */
        if (node.next != null) // If has successor, it must be on queue
            return true;

        /**
         * node.next == null 也不能说明节点 node 一定不在同步队列中，因为同步队列入队方法不是同步的而是自旋方式，
         * 是先设置 node.prev，后设置 node.next，CAS 失败时 node 可能已经在同步队列上了，所以这里还需要进一步查找。
         */
        return findNodeFromTail(node);
    }

    /**
     * 从同步队列尾部开始搜索，查找是否存在 node 节点。
     * 为什么不从头开始搜索，因为节点的 prev 可能会为 null
     *
     * @return true if present
     */
    private boolean findNodeFromTail(Node node) {
        Node t = tail;
        for (; ; ) {
            if (t == node)
                return true;
            if (t == null)
                return false;
            t = t.prev;
        }
    }


    /**
     * 用于完全释放同步状态。
     * <p>
     * 完全释放原因：
     * 一般情况下为了避免死锁的产生，锁的实现上一般应该支持重入功能。对应的场景就是一个线程在不释放锁的情况下可以多次调用同一把锁的lock方法进行加锁，且不会加锁失败，如果失败必然导致死锁。
     * 锁的实现类可通过 AQS 中的 state 计数，每次加锁，将 state++。每次 unlock 方法释放锁时，则将 state--，直到 state = 0 ，线程完全释放锁。
     *
     * @param node
     * @return
     */
    final int fullyRelease(Node node) {
        boolean failed = true;
        try {

            // 获取同步状态（还是拿到锁的线程）
            int savedState = getState();

            // 释放指定数量的同步状态（完全释放锁）
            // java.util.concurrent.locks.ReentrantLock.Sync.tryRelease ，没有持有锁会抛出异常
            if (release(savedState)) {
                failed = false;
                return savedState;
            } else {
                throw new IllegalMonitorStateException();
            }
        } finally {
            // 释放同步状态失败，需要将节点状态设置为取消状态，后续会被清理
            if (failed)
                node.waitStatus = Node.CANCELLED;
        }
    }


    /**
     * 条件
     * <p>
     * Condition implementation for a {@link
     * AbstractQueuedSynchronizer} serving as the basis of a {@link
     * Lock} implementation.
     *
     * <p>Method documentation for this class describes mechanics,
     * not behavioral specifications from the point of view of Lock
     * and Condition users. Exported versions of this class will in
     * general need to be accompanied by documentation describing
     * condition semantics that rely on those of the associated
     * {@code AbstractQueuedSynchronizer}.
     *
     * <p>This class is Serializable, but all fields are transient,
     * so deserialized conditions have no waiters.
     */
    public class ConditionObject implements Condition, java.io.Serializable {
        private static final long serialVersionUID = 1173984872572414699L;

        /**
         * 头节点
         */
        private transient Node firstWaiter;
        /**
         * 尾节点
         */
        private transient Node lastWaiter;

        /**
         * Creates a new {@code ConditionObject} instance.
         */
        public ConditionObject() {
        }

        // Internal methods

        /**
         * 将当前线程封装到节点中，并加入到条件队列中
         *
         * @return its new wait node
         */
        private Node addConditionWaiter() {
            // 条件队列尾节点
            Node t = lastWaiter;

            // 选出条件队列中有效尾节点
            if (t != null && t.waitStatus != Node.CONDITION) {
                // 如果需要，清理条件队列中取消的节点（重组链表）
                // fullRelease 内部调用 release 发生异常或释放同步状态失败，都会将节点的等待状态设置为取消状态，所以这里要清理已取消的节点。todo 是否删除
                unlinkCancelledWaiters();
                // 重读尾节点，可能为 null
                t = lastWaiter;
            }

            // 创建节点封装当前线程，节点状态为 CONDITION
            Node node = new Node(Thread.currentThread(), Node.CONDITION);

            // 初始化条件队列，firstWaiter 更新为当前节点
            if (t == null)
                firstWaiter = node;

                // 将当前节点加入到条件队列尾
            else
                t.nextWaiter = node;

            // 更新条件队列尾指针指向
            lastWaiter = node;
            return node;
        }


        /**
         * Unlinks cancelled waiter nodes from condition queue.
         * Called only while holding lock. This is called when
         * cancellation occurred during condition wait, and upon
         * insertion of a new waiter when lastWaiter is seen to have
         * been cancelled. This method is needed to avoid garbage
         * retention in the absence of signals. So even though it may
         * require a full traversal, it comes into play only when
         * timeouts or cancellations occur in the absence of
         * signals. It traverses all nodes rather than stopping at a
         * particular target to unlink all pointers to garbage nodes
         * without requiring many re-traversals during cancellation
         * storms.
         */
        private void unlinkCancelledWaiters() { // 清理条件队列中取消的节点，重组链表。
            // 从首节点开始进行节点检测
            Node t = firstWaiter;

            // 记录上一个非取消状态节点，参照节点是当前遍历节点
            Node trail = null;

            // 遍历链表
            while (t != null) {
                // 保存当前节点的下一个节点，在当前节点处于取消状态时进行替换
                Node next = t.nextWaiter;

                // 当前节点处于取消状态，执行清理逻辑
                if (t.waitStatus != Node.CONDITION) {
                    // 取消状态的节点要断开和链表的关联
                    t.nextWaiter = null;

                    /**
                     * 重组链表，保证链条为空或者所有节点都是非取消状态
                     *
                     * trail == null，表明 next 之前的节点的等待状态均为取消状态，此时更新 firstWaiter 引用指向
                     * trail != null，表明 next 之前有节点的等待状态为 CONDITION ，此时只需 trail.nextWaiter 指向 next 节点
                     * 注意：
                     * 1 firstWaiter 一定指向链表第一个非取消节点，或者为 null
                     * 2 trail 第一次赋值一定和 firstWaiter 一样的值
                     * 3 firstWaiter 一旦被赋予非 null 的值后就不会再变动，后续的节点连接就看 trail 的表演：
                     *   - 如果当前节点是取消节点，就 trail.nextWaiter 指向 next 节点
                     *   - 如果当前节点是非取消节点，trail 跟着节点走
                     */
                    if (trail == null)
                        firstWaiter = next;
                    else
                        trail.nextWaiter = next;

                    // 当前节点没有后继则遍历结束，此时当前节点是无效节点，因此将 lastWaiter 回退即更新为上一个非取消节点
                    if (next == null)
                        lastWaiter = trail;

                    // 当前节点处于等待状态
                } else
                    trail = t;

                // 下一个节点
                t = next;
            }
        }

        // public methods

        /**
         * Moves the longest-waiting thread, if one exists, from the
         * wait queue for this condition to the wait queue for the
         * owning lock.
         *
         * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
         *                                      returns {@code false}
         */

        /**
         * 将条件队列中的头节点转到同步队列中
         */
        public final void signal() {
            // 检查线程是否获取了独占锁，未获取独占锁调用 signal 方法是不合法的
            if (!isHeldExclusively())
                throw new IllegalMonitorStateException();

            // 条件队列的头节点
            Node first = firstWaiter;

            // 将条件队列的头节点转到同步队列中
            if (first != null)
                doSignal(first);
        }


        /**
         * Removes and transfers nodes until hit non-cancelled one or
         * null. Split out from signal in part to encourage compilers
         * to inline the case of no waiters.
         *
         * @param first (non-null) the first node on condition queue
         */
        private void doSignal(Node first) {
            do {
                // 因为条件队列的 firstWaiter 要出队转到同步队列中，因此使用 firstWaiter 后继节点占领 firstWaiter。
                if ((firstWaiter = first.nextWaiter) == null)
                    // 只有一个节点的话，尾节点指向设置为 null
                    lastWaiter = null;

                // 断开 first 与条件队列的连接
                first.nextWaiter = null;

                /**
                 * 调用 transferForSignal 方法将节点移到同步队列中，如果转到同步队列失败，则对后面的节点进行操作，依次类推
                 */
            } while (!transferForSignal(first) && (first = firstWaiter) != null);
        }


        /**
         * Moves all threads from the wait queue for this condition to
         * the wait queue for the owning lock.
         *
         * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
         *                                      returns {@code false}
         */
        public final void signalAll() {
            // 检查线程是否获取了独占锁，未获取独占锁调用 signalAll 方法是不合法的
            if (!isHeldExclusively())
                throw new IllegalMonitorStateException();

            // 条件队列的头节点
            Node first = firstWaiter;

            if (first != null)
                doSignalAll(first);
        }

        /**
         * Removes and transfers all nodes.
         *
         * @param first (non-null) the first node on condition queue
         */
        private void doSignalAll(Node first) {
            // 置空条件队列的头、尾指针，因为当前队列元素要全部出队，避免将新入队的节点误唤醒
            lastWaiter = firstWaiter = null;

            /**
             * 将条件队列中所有的节点都转到同步队列中。
             */
            do {
                Node next = first.nextWaiter;

                // 将节点从条件队列中移除
                first.nextWaiter = null;

                // 将节点转到同步队列中
                transferForSignal(first);

                first = next;

            } while (first != null);
        }


        /**
         * 将节点从条件队列转到同步队列，成功则返回 true
         *
         * @param node the node
         * @return true if successfully transferred (else the node was
         * cancelled before signal)
         */
        final boolean transferForSignal(Node node) {
            /**
             * 如果更新节点的等待状态由 CONDITION 到 0 失败，则说明该节点已经被取消，也就不需要再转到同步队列中了。
             * 由于整个 signal /signalAll 都需要拿到锁才能执行，因此这里不存在线程竞争的问题。
             */
            if (!compareAndSetWaitStatus(node, Node.CONDITION, 0))
                return false;

            // 调用 enq 方法将 node 加入到同步队列中，并返回 node 的前驱节点
            Node p = enq(node);

            // 获取前驱节点的等待状态
            int ws = p.waitStatus;

            /**
             * 1 如果前驱节点的等待状态 ws > 0，说明前驱节点已经被取消了，此时应该唤醒 node 对应的线程去尝试获取同步状态。注意，这里没有执行剔除取消节点的逻辑，因此需要主动唤醒 node 对应的线程。
             * 2 如果前驱节点的等待状态 ws <= 0 ，通过 CAS 操作将 node 的前驱节点 p 的等待状态设置为 SIGNAL，当节点 p 释放同步状态后会唤醒它的后继节点 node。
             *   如果 CAS 设置失败，则应该唤醒 node 节点对应的线程，避免 node 对应的线程无法唤醒导致同步队列挂掉？ todo
             */
            if (ws > 0 || !compareAndSetWaitStatus(p, ws, Node.SIGNAL))
                LockSupport.unpark(node.thread);

            return true;
        }


        /**
         * Mode meaning to reinterrupt on exit from wait
         */
        private static final int REINTERRUPT = 1;
        /**
         * Mode meaning to throw InterruptedException on exit from wait
         */
        private static final int THROW_IE = -1;

        /**
         * Checks for interrupt, returning THROW_IE if interrupted
         * before signalled, REINTERRUPT if after signalled, or
         * 0 if not interrupted.
         */

        /**
         * 检查在线程挂起期间发生了中断：
         * <p>
         * 1. 线程未被中断，则返回 0
         * 2. 线程被中断且自行入同步队列成功，则返回 THROW_IE，这种情况下后续需要抛出中断异常
         * 3. 线程被中断且未能自行入同步队列（其它线程已经执行 signal/signalAll 方法，节点状态已被更改），则返回 REINTERRUPT ，这种情况下后续需要重新中断线程以恢复中断标志
         *
         * @param node
         * @return
         */
        private int checkInterruptWhileWaiting(Node node) {
            return Thread.interrupted() ?
                    (transferAfterCancelledWait(node) ? THROW_IE : REINTERRUPT) :
                    0;
        }


        /**
         * Transfers node, if necessary, to sync queue after a cancelled wait.
         * Returns true if thread was cancelled before being signalled.
         *
         * @param node the node
         * @return true if cancelled before the node was signalled
         */
        /**
         *
         * @param node
         * @return
         */
        /**
         * 判断中断发生的时机，分为两种：
         * 1. 中断在节点被转到同步队列前发生，此时返回 true
         * 2. 中断在节点被转到同步队列过程或之后发生，此时返回 false
         *
         * @param node
         * @return
         */
        final boolean transferAfterCancelledWait(Node node) {
            // 中断如果发生在 节点被转到同步队列前，应该尝试自行将节点转到同步队列中，并返回 true
            if (compareAndSetWaitStatus(node, Node.CONDITION, 0)) {
                // 将节点转到同步队列中
                enq(node);
                return true;
            }
            /*
             * If we lost out to a signal(), then we can't proceed
             * until it finishes its enq().  Cancelling during an
             * incomplete transfer is both rare and transient, so just
             * spin.
             */
            /**
             * 如果上面的CAS失败，则表明已经有线程调用 signal/signalAll 方法更新过节点状态（CONDITION -> 0 ），并调用 enq 方法尝试将节点转到同步队列中。
             * 这里使用 while 进行判断节点是否已经在同步队列上的原因是，signal/signalAll 方法可能仅设置了等待状态，还没有完成将线程节点转到同步队列中，所以这里用自旋的
             * 方式等待线程节点加入到同步队列，否则会影响后续重新获取同步状态（调用 acquireQueued() 方法，该方法需要线程节点入同步队列才能调用，否则会抛出异常）。这种情况表明了中断发生在节点被转移到同步队列期间。
             */
            while (!isOnSyncQueue(node))
                // 让出 CPU
                Thread.yield();

            // 中断在节点被转到同步队列期间或之后发生，返回 false
            return false;
        }

        /**
         * Throws InterruptedException, reinterrupts current thread, or
         * does nothing, depending on mode.
         */
        /**
         * 根据中断模式做出相应的处理：
         * THROW_IE：抛出中断异常
         * REINTERRUPT：重新设置线程中断标志
         *
         * @param interruptMode
         * @throws InterruptedException
         */
        private void reportInterruptAfterWait(int interruptMode)
                throws InterruptedException {
            if (interruptMode == THROW_IE)
                throw new InterruptedException();
            else if (interruptMode == REINTERRUPT)
                // 中断线程
                selfInterrupt();
        }

        /**
         * 响应中断的等待方法。线程进入条件队列直到被通知(signal)或中断
         * <ol>
         * <li> 如果线程被中断了则抛出中断异常
         * <li> 将线程封装到节点对象中，并将节点添加到条件队列尾部
         * <li> 保存并完全释放同步状态，保存下来的同步状态会在重新竞争锁时会用到
         * <li> 线程进入阻塞等待状态，直到被通知或中断才会从阻塞中返回
         * <li> 使用保存的同步状态去再次竞争锁
         * <li> 阻塞时被中断会抛出中断异常
         * </ol>
         */
        public final void await() throws InterruptedException {
            // 响应中断
            if (Thread.interrupted())
                throw new InterruptedException();

            // 将当前线程封装到节点中，并将节点加入到条件队列尾部
            Node node = addConditionWaiter();

            // 保存并完全释放同步状态，注意是完全释放，因为允许可重入锁。todo 注意和 unlock 的联系与区别
            // 1. 如果没有持锁会抛出异常，也就是释放同步状态失败
            int savedState = fullyRelease(node);
            // 中断模式
            int interruptMode = 0;

            /**
             * 判断上述加入到条件队列的线程节点是否被移动到同步队列中，不在则阻塞线程（曾经获取到锁的线程）.
             *
             * 循环结束的条件：
             * 1. 其它线程调用 signal/signalAll，将当前线程节点移动到同步队列中，节点对应的线程将会在获取同步状态的过程被唤醒。
             * 2. 其它线程中断了当前线程，当前线程会自行尝试进入同步队列中。
             */
            while (!isOnSyncQueue(node)) {
                // 挂起线程，直到被唤醒或被中断
                LockSupport.park(this);

                /**
                 * 检测中断模式：
                 * 在线程从 park 中返回时，需要判断是被唤醒返回还是被中断返回。
                 * 1. 如果线程没有被中断，则返回 0，此时需要重试循环继续判断当前线程节点是否在同步队列中。
                 * 2. 如果线程被中断
                 *   - 中断发生在被唤醒之前，当前线程（线程节点）会尝试自行进入同步队列并返回 THROW_IE，后续需要抛出中断异常。
                 *   - 中断发生在被唤醒之后，即当前线程（线程节点）尝试自行进入同步队列失败（说明其它线程调用过了 signal/signalAll 唤醒线程并尝试将线程节点转到同步队列），
                 *     返回 REINTERRUPT ，后续需要重新中断线程，向后传递中断标志，由后续代码去处理中断。
                 *
                 */
                if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
                    break;
            }

            // 醒来后，被移动到同步队列的节点 node 重新尝试获取同步状态成功，且获取同步状态的过程中如果被中断，接着判断中断模式非 THROW_IE 的情况会更新为 REINTERRUPT
            if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
                interruptMode = REINTERRUPT;

            // 正常情况下 signal/signalAll 将节点转到同步队列的同时会将节点的 nextWaiter 置空。
            // 1 中断模式为 THROW_IE 的情况下 nextWaiter 不会被置空，且等待状态为 0 ，这种情况下节点应该从条件队列中移除。
            // 2 fullyRelease 方法出现异常，nextWaiter 不会被置空，且等待状态为 CANCELLED，清理任务会由后继的节点完成。
            if (node.nextWaiter != null) // clean up if cancelled
                // 清理条件队列中取消的节点（重组链表）
                unlinkCancelledWaiters();

            // 如果线程发生过中断则根据 THROW_IE 或 REINTERRUPT 分别抛出异常或者重新中断。 todo 为毛先获取锁再抛出异常，既然最终都要抛出异常还获取个求求的锁
            if (interruptMode != 0)
                reportInterruptAfterWait(interruptMode);
        }

        /**
         * 不响应中断，不会抛出中断异常
         * <p>
         * 当前线程进入条件队列直到被通知或被中断
         */
        public final void awaitUninterruptibly() {
            // 加入条件队列
            Node node = addConditionWaiter();
            // 完全释放同步状态
            int savedState = fullyRelease(node);
            // 中断模式
            boolean interrupted = false;

            // 自旋等待
            while (!isOnSyncQueue(node)) {
                LockSupport.park(this);
                if (Thread.interrupted())
                    interrupted = true;
            }

            // 竞争同步状态
            if (acquireQueued(node, savedState) || interrupted)
                // 发生中断需要复位中断标志
                selfInterrupt();
        }

        /**
         * 当前线程进入条件队列直到被通知、中断或超时。返回值表示剩余的时间，如果在 nanosTimeout 内返回，那么返回值就是 nanosTimeout - 实际耗时，如果返回值是 0 或者负数，表示超时了。
         */
        public final long awaitNanos(long nanosTimeout) throws InterruptedException {

            if (Thread.interrupted())
                throw new InterruptedException();
            // 加入条件队列
            Node node = addConditionWaiter();
            // 完全释放同步状态
            int savedState = fullyRelease(node);
            // 过期时间
            final long deadline = System.nanoTime() + nanosTimeout;

            int interruptMode = 0;

            // 超时的话，自行转入到同步队列
            while (!isOnSyncQueue(node)) {
                // 超时时间到，跳出自旋等待
                if (nanosTimeout <= 0L) {
                    transferAfterCancelledWait(node);
                    break;
                }

                // 自旋还是挂起
                if (nanosTimeout >= spinForTimeoutThreshold)
                    LockSupport.parkNanos(this, nanosTimeout);

                // 检查中断模式
                if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
                    break;

                // 计算超时时间
                nanosTimeout = deadline - System.nanoTime();
            }

            if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
                interruptMode = REINTERRUPT;

            if (node.nextWaiter != null)
                unlinkCancelledWaiters();

            if (interruptMode != 0)
                reportInterruptAfterWait(interruptMode);

            return deadline - System.nanoTime();
        }

        /**
         * 绝对时间
         * 当前线程进入条件队列直到被通知、中断或者到某个时间。如果没有到指定时间就通知，返回 ture，否则表示超时
         */
        public final boolean awaitUntil(Date deadline) throws InterruptedException {
            // 超时时间
            long abstime = deadline.getTime();

            if (Thread.interrupted())
                throw new InterruptedException();

            // 加入到条件队列中
            Node node = addConditionWaiter();

            // 完全释放同步状态
            int savedState = fullyRelease(node);

            boolean timedout = false;
            int interruptMode = 0;

            // 自旋
            while (!isOnSyncQueue(node)) {
                // 超时了，结束自旋等待
                if (System.currentTimeMillis() > abstime) {
                    timedout = transferAfterCancelledWait(node);
                    break;
                }

                // 挂起线程，等待 abstime
                LockSupport.parkUntil(this, abstime);

                // 中断检查
                if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
                    break;
            }

            // 重新获取同步状态
            if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
                interruptMode = REINTERRUPT;

            if (node.nextWaiter != null)
                unlinkCancelledWaiters();
            if (interruptMode != 0)
                reportInterruptAfterWait(interruptMode);

            return !timedout;
        }

        /**
         * 相对时间
         */
        public final boolean await(long time, TimeUnit unit) throws InterruptedException {

            // 计算超时时间
            long nanosTimeout = unit.toNanos(time);

            if (Thread.interrupted())
                throw new InterruptedException();

            // 加入条件队列
            Node node = addConditionWaiter();

            // 释放同步状态
            int savedState = fullyRelease(node);

            // 过期时间
            final long deadline = System.nanoTime() + nanosTimeout;

            // 用于标记是否超时
            boolean timedout = false;
            // 中断模式
            int interruptMode = 0;

            // 上述加入到条件队列的节点是否移动到同步队列中
            while (!isOnSyncQueue(node)) {

                // 超时时间到了，跳出自旋不再等待，这是超时等待的必要条件。会尝试自行入同步队列，即调用 transferAfterCancelledWait 方法
                // 1. 返回 true ，自行加入同步队列成功。
                // 2. 返回 false ，说明其它线程调用了 signal /signalAll 方法将节点转到同步队列中，即在规定的等待超时时间内有线程执行了唤醒操作
                if (nanosTimeout <= 0L) {
                    timedout = transferAfterCancelledWait(node);
                    break;
                }

                // 如果超时时间 >= spinForTimeoutThreshold ，就将线程挂起等待 nanosTimeout
                // spinForTimeoutThreshold 为自旋超时阈值，小于这个数没必要将线程挂起，自旋性能会更好
                if (nanosTimeout >= spinForTimeoutThreshold)
                    LockSupport.parkNanos(this, nanosTimeout);

                // 检查在线程挂起期间是否发生了中断
                if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
                    break;

                // 重新读取超时时间
                nanosTimeout = deadline - System.nanoTime();
            }

            // 重新获取同步状态
            if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
                interruptMode = REINTERRUPT;

            if (node.nextWaiter != null)
                unlinkCancelledWaiters();

            if (interruptMode != 0)
                reportInterruptAfterWait(interruptMode);

            // 是否超时
            return !timedout;
        }
    }

    /**
     * Setup to support compareAndSet. We need to natively implement
     * this here: For the sake of permitting future enhancements, we
     * cannot explicitly subclass AtomicInteger, which would be
     * efficient and useful otherwise. So, as the lesser of evils, we
     * natively implement using hotspot intrinsics API. And while we
     * are at it, we do the same for other CASable fields (which could
     * otherwise be done with atomic field updaters).
     */
    private static final Unsafe unsafe = Unsafe.getUnsafe();
    private static final long stateOffset;
    private static final long headOffset;
    private static final long tailOffset;
    private static final long waitStatusOffset;
    private static final long nextOffset;

    static {
        try {
            stateOffset = unsafe.objectFieldOffset
                    (AbstractQueuedSynchronizer.class.getDeclaredField("state"));
            headOffset = unsafe.objectFieldOffset
                    (AbstractQueuedSynchronizer.class.getDeclaredField("head"));
            tailOffset = unsafe.objectFieldOffset
                    (AbstractQueuedSynchronizer.class.getDeclaredField("tail"));
            waitStatusOffset = unsafe.objectFieldOffset
                    (Node.class.getDeclaredField("waitStatus"));
            nextOffset = unsafe.objectFieldOffset
                    (Node.class.getDeclaredField("next"));

        } catch (Exception ex) {
            throw new Error(ex);
        }
    }

    /**
     * CAS head field. Used only by enq.
     */
    private final boolean compareAndSetHead(Node update) {
        return unsafe.compareAndSwapObject(this, headOffset, null, update);
    }

    /**
     * CAS tail field. Used only by enq.
     */
    private final boolean compareAndSetTail(Node expect, Node update) {
        return unsafe.compareAndSwapObject(this, tailOffset, expect, update);
    }

    /**
     * CAS waitStatus field of a node.
     */
    private static final boolean compareAndSetWaitStatus(Node node,
                                                         int expect,
                                                         int update) {
        return unsafe.compareAndSwapInt(node, waitStatusOffset,
                expect, update);
    }

    /**
     * CAS next field of a node.
     */
    private static final boolean compareAndSetNext(Node node,
                                                   Node expect,
                                                   Node update) {
        return unsafe.compareAndSwapObject(node, nextOffset, expect, update);
    }
}
