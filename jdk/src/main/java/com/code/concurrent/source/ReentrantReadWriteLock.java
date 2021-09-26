///*
// * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
// *
// *
// *
// *
// *
// *
// *
// *
// *
// *
// *
// *
// *
// *
// *
// *
// *
// *
// *
// *
// */
//
///*
// *
// *
// *
// *
// *
// * Written by Doug Lea with assistance from members of JCP JSR-166
// * Expert Group and released to the public domain, as explained at
// * http://creativecommons.org/publicdomain/zero/1.0/
// */
//
//package java.util.concurrent.locks;
//
//import java.util.concurrent.TimeUnit;
//import java.util.Collection;
//
///**
// * An implementation of {@link ReadWriteLock} supporting similar
// * semantics to {@link ReentrantLock}.
// * <p>This class has the following properties:
// *
// * <ul>
// * <li><b>Acquisition order</b>
// *
// * <p>This class does not impose a reader or writer preference
// * ordering for lock access.  However, it does support an optional
// * <em>fairness</em> policy.
// *
// * <dl>
// * <dt><b><i>Non-fair mode (default)</i></b>
// * <dd>When constructed as non-fair (the default), the order of entry
// * to the read and write lock is unspecified, subject to reentrancy
// * constraints.  A nonfair lock that is continuously contended may
// * indefinitely postpone one or more reader or writer threads, but
// * will normally have higher throughput than a fair lock.
// *
// * <dt><b><i>Fair mode</i></b>
// * <dd>When constructed as fair, threads contend for entry using an
// * approximately arrival-order policy. When the currently held lock
// * is released, either the longest-waiting single writer thread will
// * be assigned the write lock, or if there is a group of reader threads
// * waiting longer than all waiting writer threads, that group will be
// * assigned the read lock.
// *
// * <p>A thread that tries to acquire a fair read lock (non-reentrantly)
// * will block if either the write lock is held, or there is a waiting
// * writer thread. The thread will not acquire the read lock until
// * after the oldest currently waiting writer thread has acquired and
// * released the write lock. Of course, if a waiting writer abandons
// * its wait, leaving one or more reader threads as the longest waiters
// * in the queue with the write lock free, then those readers will be
// * assigned the read lock.
// *
// * <p>A thread that tries to acquire a fair write lock (non-reentrantly)
// * will block unless both the read lock and write lock are free (which
// * implies there are no waiting threads).  (Note that the non-blocking
// * {@link ReadLock#tryLock()} and {@link WriteLock#tryLock()} methods
// * do not honor this fair setting and will immediately acquire the lock
// * if it is possible, regardless of waiting threads.)
// * <p>
// * </dl>
// *
// * <li><b>Reentrancy</b>
// *
// * <p>This lock allows both readers and writers to reacquire read or
// * write locks in the style of a {@link ReentrantLock}. Non-reentrant
// * readers are not allowed until all write locks held by the writing
// * thread have been released.
// *
// * <p>Additionally, a writer can acquire the read lock, but not
// * vice-versa.  Among other applications, reentrancy can be useful
// * when write locks are held during calls or callbacks to methods that
// * perform reads under read locks.  If a reader tries to acquire the
// * write lock it will never succeed.
// *
// * <li><b>Lock downgrading</b>
// * <p>Reentrancy also allows downgrading from the write lock to a read lock,
// * by acquiring the write lock, then the read lock and then releasing the
// * write lock. However, upgrading from a read lock to the write lock is
// * <b>not</b> possible.
// *
// * <li><b>Interruption of lock acquisition</b>
// * <p>The read lock and write lock both support interruption during lock
// * acquisition.
// *
// * <li><b>{@link Condition} support</b>
// * <p>The write lock provides a {@link Condition} implementation that
// * behaves in the same way, with respect to the write lock, as the
// * {@link Condition} implementation provided by
// * {@link ReentrantLock#newCondition} does for {@link ReentrantLock}.
// * This {@link Condition} can, of course, only be used with the write lock.
// *
// * <p>The read lock does not support a {@link Condition} and
// * {@code readLock().newCondition()} throws
// * {@code UnsupportedOperationException}.
// *
// * <li><b>Instrumentation</b>
// * <p>This class supports methods to determine whether locks
// * are held or contended. These methods are designed for monitoring
// * system state, not for synchronization control.
// * </ul>
// *
// * <p>Serialization of this class behaves in the same way as built-in
// * locks: a deserialized lock is in the unlocked state, regardless of
// * its state when serialized.
// *
// * <p><b>Sample usages</b>. Here is a code sketch showing how to perform
// * lock downgrading after updating a cache (exception handling is
// * particularly tricky when handling multiple locks in a non-nested
// * fashion):
// *
// * <pre> {@code
// * class CachedData {
// *   Object data;
// *   volatile boolean cacheValid;
// *   final ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();
// *
// *   void processCachedData() {
// *     rwl.readLock().lock();
// *     if (!cacheValid) {
// *       // Must release read lock before acquiring write lock
// *       rwl.readLock().unlock();
// *       rwl.writeLock().lock();
// *       try {
// *         // Recheck state because another thread might have
// *         // acquired write lock and changed state before we did.
// *         if (!cacheValid) {
// *           data = ...
// *           cacheValid = true;
// *         }
// *         // Downgrade by acquiring read lock before releasing write lock
// *         rwl.readLock().lock();
// *       } finally {
// *         rwl.writeLock().unlock(); // Unlock write, still hold read
// *       }
// *     }
// *
// *     try {
// *       use(data);
// *     } finally {
// *       rwl.readLock().unlock();
// *     }
// *   }
// * }}</pre>
// * <p>
// * ReentrantReadWriteLocks can be used to improve concurrency in some
// * uses of some kinds of Collections. This is typically worthwhile
// * only when the collections are expected to be large, accessed by
// * more reader threads than writer threads, and entail operations with
// * overhead that outweighs synchronization overhead. For example, here
// * is a class using a TreeMap that is expected to be large and
// * concurrently accessed.
// *
// *  <pre> {@code
// * class RWDictionary {
// *   private final Map<String, Data> m = new TreeMap<String, Data>();
// *   private final ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();
// *   private final Lock r = rwl.readLock();
// *   private final Lock w = rwl.writeLock();
// *
// *   public Data get(String key) {
// *     r.lock();
// *     try { return m.get(key); }
// *     finally { r.unlock(); }
// *   }
// *   public String[] allKeys() {
// *     r.lock();
// *     try { return m.keySet().toArray(); }
// *     finally { r.unlock(); }
// *   }
// *   public Data put(String key, Data value) {
// *     w.lock();
// *     try { return m.put(key, value); }
// *     finally { w.unlock(); }
// *   }
// *   public void clear() {
// *     w.lock();
// *     try { m.clear(); }
// *     finally { w.unlock(); }
// *   }
// * }}</pre>
// *
// * <h3>Implementation Notes</h3>
// *
// * <p>This lock supports a maximum of 65535 recursive write locks
// * and 65535 read locks. Attempts to exceed these limits result in
// * {@link Error} throws from locking methods.
// *
// * @author Doug Lea
// * @since 1.5
// */
//public class ReentrantReadWriteLock
//        implements ReadWriteLock, java.io.Serializable {
//    private static final long serialVersionUID = -6992448646407690164L;
//
//    /**
//     * 读锁
//     */
//    private final ReentrantReadWriteLock.ReadLock readerLock;
//
//    /**
//     * 写锁
//     */
//    private final ReentrantReadWriteLock.WriteLock writerLock;
//
//    /**
//     * Sync 继承自 AQS ,执行所有同步机制
//     * 根据 ReentrantReadWriteLock 构造函数传入的布尔值决定要构造哪一种 Sync 实例
//     */
//    final Sync sync;
//
//    /**
//     * 默认创建非公平的 ReentrantReadWriteLock
//     */
//    public ReentrantReadWriteLock() {
//        this(false);
//    }
//
//    /**
//     * 根据传入的公平策略创建 ReentrantReadWriteLock
//     *
//     * @param fair 公平策略
//     */
//    public ReentrantReadWriteLock(boolean fair) {
//        sync = fair ? new FairSync() : new NonfairSync();
//        readerLock = new ReadLock(this);
//        writerLock = new WriteLock(this);
//    }
//
//    /**
//     * 获取写锁
//     *
//     * @return
//     */
//    public ReentrantReadWriteLock.WriteLock writeLock() {
//        return writerLock;
//    }
//
//    /**
//     * 获取读锁
//     *
//     * @return
//     */
//    public ReentrantReadWriteLock.ReadLock readLock() {
//        return readerLock;
//    }
//
//    /**
//     * 静态内部类 Sync ，继承 AQS
//     * 具体子类包括：非公平模式 NonfairSync 和 公平模式 FairSync
//     */
//    abstract static class Sync extends AbstractQueuedSynchronizer {
//        private static final long serialVersionUID = 6317671515068378041L;
//
//        /**
//         * 将同步状态 state 分为两段：高 16 位用于共享模式；低 16 位用于独占模式
//         */
//        static final int SHARED_SHIFT = 16;
//
//        // 读锁的值操作单位
//        // 由于高 16 位用于读锁，因此每次操作基于 1 左移 16 位的值，也就是从高 16 位的末尾进行计算
//        // 1 << 16 => 1 00000000 00000000
//        static final int SHARED_UNIT = (1 << SHARED_SHIFT);
//
//        // 锁持有次数溢出的阈值，即 2^16 -1
//        static final int MAX_COUNT = (1 << SHARED_SHIFT) - 1;
//
//        // 独占模式掩码，即 1 << 16 -1 => 11111111 11111111
//        static final int EXCLUSIVE_MASK = (1 << SHARED_SHIFT) - 1;
//
//        /**
//         * 取 c 的高 16 位的值，代表读锁的获取次数，包括重入
//         * 注意：该值是所有线程获取次数总和，包括每个线程重入情况
//         */
//        static int sharedCount(int c) {
//            return c >>> SHARED_SHIFT;
//        }
//
//        /**
//         * 取 c 的低 16 位的值，代表写锁的重入次数（写锁是独占模式）
//         */
//        static int exclusiveCount(int c) {
//            return c & EXCLUSIVE_MASK;
//        }
//
//
//        // 用于记录每个线程持有的读锁次数(包括读锁重入)
//        static final class HoldCounter {
//            // 线程持有读锁次数
//            int count = 0;
//            // Use id, not reference, to avoid garbage retention
//            final long tid = getThreadId(Thread.currentThread());
//        }
//
//        // ThreadLocal 的子类，保存线程变量副本 HoldCounter
//        static final class ThreadLocalHoldCounter extends ThreadLocal<HoldCounter> {
//            /**
//             * 初始化 HoldCounter
//             *
//             * @return
//             */
//            @Override
//            public HoldCounter initialValue() {
//                return new HoldCounter();
//            }
//        }
//
//        // 当前线程读锁计数器
//        // 说明：使用 ThreadLocal 来记录当前线程持有的读锁次数
//        private transient ThreadLocalHoldCounter readHolds;
//
//        // 最后获取读锁的线程读锁计数器
//        // 说明：缓存最后一个获取读锁的线程持有读锁的次数，这里不是全局的概念，所以不管哪个线程获取到读锁后，就把这个值占为已用
//        private transient HoldCounter cachedHoldCounter;
//
//
//        // 首个获取读锁的线程(并且其未释放读锁)读锁计数器
//        // 说明：
//        // 1 这里不是全局的概念，等这个 firstReader 代表的线程释放掉读锁以后，会有新的线程占用这个属性，也就是这个"第一个"是动态的。
//        // 2 在读锁不产生竞争的情况下，记录读锁重入次数是非常方便的
//        // 3 如果一个线程使用了 firstReader，那么它就不需要占用 cachedHoldCounter 变量了
//        private transient Thread firstReader = null;
//        private transient int firstReaderHoldCount;
//
//        // 构造方法中初始化
//        Sync() {
//            // 初始化 readHolds 这个 ThreadLocal 属性
//            readHolds = new ThreadLocalHoldCounter();
//            // 为了保证 readHolds 的内存可见性
//            setState(getState()); // ensures visibility of readHolds
//        }
//
//
//        /**
//         * 获取读锁是否需要阻塞，交给子类实现
//         */
//        abstract boolean readerShouldBlock();
//
//        /**
//         * 获取写锁是否需要阻塞，交给子类实现
//         */
//        abstract boolean writerShouldBlock();
//
//
//        /**
//         * AQS 模版方法，获取独占同步状态 - 获取写锁
//         * 说明：
//         * 1 该方法除了重入条件（当前线程是获取了写锁的线程）之外，增加了一个读锁是否存在的判断。
//         * 2 如果存在读锁，则写锁不能被获取，原因在于，读写锁要确保写锁的操作对读锁可见，如果允许读锁在已经被获取的情况下对写锁的获取，
//         * 那么正在运行的其它读线程就无法感知到当前写线程的操作。因此，只有等待其它读线程都释放了读锁，写锁才能被当前线程获取。
//         * 3 写锁一旦被获取，则其它读写线程的后续访问都被阻塞。
//         *
//         * @param acquires
//         * @return
//         */
//        protected final boolean tryAcquire(int acquires) {
//
//            // 1 获取当前线程
//            Thread current = Thread.currentThread();
//
//            // 2 获取同步状态 state
//            int c = getState();
//
//            // 3 根据 state 获取写锁的持有次数
//            int w = exclusiveCount(c);
//
//            // 4  c != 0 表示要么有线程持有读锁，要么有线程持有写锁
//            // 由于该方法是获取写锁，因此下面只能是写锁重入分支（存在持有读锁的情况直接失败）
//            if (c != 0) {
//                // c != 0 && w == 0: 写锁可用，但是有线程持有读锁(也可能是自己持有，但由于不支持锁升级，因此不能获取写锁)
//                if (w == 0 ||
//                        // c != 0 && w !=0 && current != getExclusiveOwnerThread(): 非重入其他线程持有写锁
//                        current != getExclusiveOwnerThread())
//                    // 存在读锁或者当前获取线程不是已经获取写锁的线程
//                    return false;
//
//                // 判断写锁持有次数是否超过阈值（65535）
//                if (w + exclusiveCount(acquires) > MAX_COUNT)
//                    throw new Error("Maximum lock count exceeded");
//
//                // 能到这里的，只可能是写锁重入，更新同步状态即可
//                setState(c + acquires);
//                return true;
//            }
//
//            // 执行到这里，此时 state == 0 ，读锁和写锁都没有被获取
//            // 5 获取写锁，这里判断是否需要阻塞（这里考虑到公平还是非公平）
//            if (writerShouldBlock() ||
//                    // 不需要阻塞，则更新 state
//                    !compareAndSetState(c, c + acquires))
//                return false;
//
//            // 6 当前线程独占锁
//            setExclusiveOwnerThread(current);
//
//            return true;
//        }
//
//
//        /**
//         * AQS 模版方法，释放独占同步状态 - 释放写锁
//         * 说明：
//         * 写锁的释放与 ReentrantLock 的释放过程基本类似，每次释放均减少写状态，当写状态为 0 时表示写锁可以被释放。
//         *
//         * @param releases
//         * @return
//         */
//        protected final boolean tryRelease(int releases) {
//            //  当前线程是否占有锁，否则没有资格尝试释放写锁
//            if (!isHeldExclusively())
//                throw new IllegalMonitorStateException();
//
//            // 计算同步状态剩余值
//            int nextc = getState() - releases;
//
//            // 写锁重入次数是否为 0 ，为 0 表示可以释放
//            boolean free = exclusiveCount(nextc) == 0;
//
//            // 完全释放
//            if (free)
//                // 清空独占线程
//                setExclusiveOwnerThread(null);
//
//            // 更新 state
//            setState(nextc);
//            return free;
//        }
//
//
//
//        /**
//         * AQS 模版方法，获取共享同步状态 - 获取读锁
//         * 说明：
//         * 1 读锁是一个支持重入的共享锁，它能被多个线程同时获取，在没有其它写线程访问（或者写状态为 0）时，读锁总会被成功地获取，而所做的也只是增加读状态。
//         * 2 如果当前线程已经获取了读锁，则增加读状态。如果当前线程在获取读锁时，写锁已被其它线程获取，则进入等待状态。
//         * 3 读锁的实现从 Java 5 到 Java 6 变得复杂许多，主要原因是新增了一些功能，例如 getReadHoldCount() 方法，作用是返回当前线程获取
//         * 读锁的次数。而读状态是所有线程获取读锁次数的总和，而每个线程各自获取读锁的次数只能选择保存在 ThreadLocal 中，由线程自身维护，这
//         * 使读锁实现变得复杂。
//         * 过程：
//         * 1 如果其它线程已经获取了写锁，则当前线程获取读锁失败，进入等待状态。
//         * 2 如果当前线程获取了写锁或者写锁未被获取，则当前线程增加读状态，成功获取读锁
//         *
//         * @param unused
//         * @return
//         */
//        protected final int tryAcquireShared(int unused) {
//
//            // 1 获取当前线程
//            Thread current = Thread.currentThread();
//
//            // 2 获取同步状态
//            int c = getState();
//
//            // 3 exclusiveCount(c) != 0 ，说明有线程持有写锁。如果不是当前线程持有的写锁，那么当前线程获取读锁失败。
//            // 由于读写锁的降级，如果当前线程持有写锁，是可以继续获取读锁的
//            if (exclusiveCount(c) != 0 &&
//                    getExclusiveOwnerThread() != current)
//                return -1;
//
//            // 4 读锁的获取次数
//            int r = sharedCount(c);
//
//            // 5 获取读锁是否需要被阻塞（需要考虑公平与非公平的情况）
//            if (!readerShouldBlock() &&
//                    // 判断持有读锁次数是否会溢出 (2^16-1)
//                    r < MAX_COUNT &&
//                    // 使用  CAS 是将 state 属性的高 16 位加 1，低 16 位不变，如果成功就代表获取到了读锁
//                    // c + 1 00000000 00000000
//                    compareAndSetState(c, c + SHARED_UNIT)) {
//
//                /* 进入当前代码区域，表示获取到了读锁 */
//
//                // 5.1 r == 0 说明当前线程是第一个获取读锁的线程，或者是在它之前的读锁都已经释放了
//                // 记录 firstReader 为当前线程，及其持有的读锁数量：1
//                if (r == 0) {
//                    firstReader = current;
//                    firstReaderHoldCount = 1;
//
//                    // 5.2 当前线程重入锁，加 1 即可
//                } else if (firstReader == current) {
//                    firstReaderHoldCount++;
//
//                    // 5.3 当前线程不是第一个获取读锁，并且已经有其它线程获取了读锁
//                    // - 使用 readHolds 保存当前线程持有的读锁次数
//                    // - 将当前线程持有读锁信息更新为 cachedHoldCounter 的值，该变量用于记录最后一个获取读锁的线程持锁信息
//                } else {
//
//                    // 获取最后一个获取读锁的线程信息。
//                    HoldCounter rh = cachedHoldCounter;
//
//                    // 如果 cachedHoldCounter 缓存的不是当前线程，则将当前线程持有读锁信息缓存到 HoldCounter
//                    if (rh == null || rh.tid != getThreadId(current))
//                        cachedHoldCounter = rh = readHolds.get();
//
//                        // cachedHoldCounter 缓存的是当前线程，但 count 为 0
//                    else if (rh.count == 0)
//                        readHolds.set(rh);
//
//                    // 将当前线程持有读锁次数 count 加 1
//                    rh.count++;
//                }
//
//                // return 大于 0 代表获取到了共享锁
//                return 1;
//            }
//
//            // 以下三种情况进入下面方法：
//            // - compareAndSetState(c, c + SHARED_UNIT) 存在竞争，CAS 失败
//            // - 公平模式 FairSync 下同步队列中有其它线程节点在等待锁
//            // - 非公平模式 NonFairSync 下，同步队列中第一个线程节点（head.next）是获取写锁的，为了避免写锁饥饿，获取读锁的线程不应该和它抢占
//            return fullTryAcquireShared(current);
//        }
//
//
//
//        /**
//         * AQS模版方法，释放共享同步状态 - 释放读锁
//         * 说明：
//         * 读锁的每次释放均减少读状态，减少的值是 1<<16
//         *
//         * @param unused
//         * @return
//         */
//        protected final boolean tryReleaseShared(int unused) {
//            // 1 获取当前线程
//            Thread current = Thread.currentThread();
//
//            // 2 如果当前线程是 firstReader ，说明当前线程是第一个读线程
//            if (firstReader == current) {
//
//                // 如果 firstReaderHoldCount 等于 1 ，那么本次解锁后就不再持有锁了，需要把 firstReader 置为 null
//                // 不顺便设置 firstReaderHoldCount = 0 ，是因为没必要，其他线程使用的时候自己会设值
//                if (firstReaderHoldCount == 1)
//                    firstReader = null;
//                else
//                    firstReaderHoldCount--;
//
//               // 3 当前线程不是首个获取读锁的线程
//            } else {
//
//                // 判断当前线程是不是最后获取读锁的线程，不是的话要到 ThreadLocal 中取
//                HoldCounter rh = cachedHoldCounter;
//                if (rh == null || rh.tid != getThreadId(current))
//                    rh = readHolds.get();
//
//                // 获取计数
//                int count = rh.count;
//                if (count <= 1) {
//                    // 将 ThreadLocal remove 掉，防止内存泄漏。因为已经不再持有读锁了
//                    readHolds.remove();
//                    // 防止释放锁和获取锁次数不匹配
//                    if (count <= 0)
//                        throw unmatchedUnlockException();
//                }
//
//                // count 减 1
//                --rh.count;
//            }
//
//
//            // 将同步状态 state 的高 16 位减 1，如果发现读锁和写锁都释放完了，那么唤醒后继的等待写锁的线程节点
//            for (; ; ) {
//
//                // 获取同步状态 state
//                int c = getState();
//
//                // nextc 是 state 高 16 位减 1 后的值
//                int nextc = c - SHARED_UNIT;
//
//                // 如果 nextc == 0，那就是 state 全部 32 位都为 0，也就是读锁和写锁都没有被占有
//                if (compareAndSetState(c, nextc))
//                    // 释放读锁对读操作没有影响，但是如果现在读锁和写锁都是空闲的，那么释放读锁可能允许等待的写操作继续进行。
//                    return nextc == 0;
//            }
//        }
//
//        private IllegalMonitorStateException unmatchedUnlockException() {
//            return new IllegalMonitorStateException(
//                    "attempt to unlock read lock, not locked by current thread");
//        }
//
//
//        /**
//         * 这段代码与 tryAcquireShared 中的代码在一定程度上是冗余的，但由于没有使用重试和惰性读取保持计数之间的交互使 tryAcquireShared 复杂化，所以总体上更简单。
//         *
//         * 该方法的作用：
//         * 1 CAS 失败后增加成功的机会
//         * 2 在非公平模式 NonFairSync 情况下，如果同步队列中 head.next 是获取写锁的，那么此时获取读锁的线程获取失败，但是如果该线程是重入读锁，则不用考虑同步队列中 head.next 是否获取写锁问题
//         *
//         * 注意，以下两种情况在 tryAcquireShared 方法中已经处理过了
//         * 1 非公平模式：判断同步队列中 head 的第一个后继节点是否是来获取写锁的，如果是就先让该节点获取写锁，避免线程饥饿
//         * 2 公平模式：同步队列中有线程在等待
//         *
//         *
//         * @param current 当前线程
//         * @return
//         */
//        final int fullTryAcquireShared(Thread current) {
//
//            // 记录线程获取读锁的次数
//            HoldCounter rh = null;
//
//            // for 循环
//            for (; ; ) {
//                // 1 获取同步状态
//                int c = getState();
//
//                // 2 如果其它线程获取了写锁，那么当前线程是不能获取到读锁的，只能去同步队列中排队
//                if (exclusiveCount(c) != 0) {
//                    if (getExclusiveOwnerThread() != current)
//                        return -1;
//
//                    // else we hold the exclusive lock; blocking here
//                    // would cause deadlock.
//
//                    // 3 获取读锁应该阻塞，说明同步队列中有其它线程在等待
//                    // 注意： 既然是获取读锁应该阻塞，那么进入有什么用呢？ 是用来处理读锁重入的
//                } else if (readerShouldBlock()) {
//
//                    // firstReader 线程重入锁，暂不做操作，直接执行后面的 CAS
//                    if (firstReader == current) {
//                        // assert firstReaderHoldCount > 0;
//
//                        // 非 firstReader 线程重入锁，则继续判断其它情况重入锁
//                    } else {
//                        if (rh == null) {
//
//                            // 判断是否是 cachedHoldCounter 重入锁，如果也不是，那就是既不是 firstReader 可重入也不是 lastReader 可重入，
//                            // 这是只需从 ThreadLocal 取出当前线程持有读锁信息，如果没有占有，则进行兜底操作，让线程去排队
//                            rh = cachedHoldCounter;
//                            if (rh == null || rh.tid != getThreadId(current)) {
//
//                                // 那么到 ThreadLocal 中获取当前线程的 HoldCounter
//                                // 注意，如果当前线程从来没有初始化过 ThreadLocal 中的值，get() 会执行初始化
//                                rh = readHolds.get();
//
//                                // 如果发现 count == 0，也就是说是上一行代码初始化的，之前该线程并没有持有读锁，那么执行 remove 操作清空信息，因为接下来该线程要入队等待了
//                                // 然后往下两三行，乖乖排队去
//                                if (rh.count == 0)
//                                    readHolds.remove();
//                            }
//                        }
//
//                        // 非重入，去同步队列中排队
//                        if (rh.count == 0)
//                            return -1;
//                    }
//                }
//
//
//                if (sharedCount(c) == MAX_COUNT)
//                    throw new Error("Maximum lock count exceeded");
//
//                // 这里 CAS 成功，那么就意味着成功获取读锁了
//                // 下面需要做的是设置 firstReader 或 cachedHoldCounter，以及 readHolds
//                if (compareAndSetState(c, c + SHARED_UNIT)) {
//
//                    // 注意这里 c 是上面的快照，上面修改的不是 c 而是 state
//                    // 如果发现 sharedCount(c) 等于 0，也就是当前没有线程持有读锁，就将当前线程设置为 firstReader
//                    if (sharedCount(c) == 0) {
//                        firstReader = current;
//                        firstReaderHoldCount = 1;
//
//                        // 如果是重 firstReader 重入，直接累加持有读锁的次数即可
//                    } else if (firstReader == current) {
//                        firstReaderHoldCount++;
//
//                        // 将 cachedHoldCounter 设置为当前线程持有读锁信息，并且使用 ThreadLocal 记录当前线程持有读锁信息
//                    } else {
//                        if (rh == null)
//                            rh = cachedHoldCounter;
//                        if (rh == null || rh.tid != getThreadId(current))
//                            rh = readHolds.get();
//                        else if (rh.count == 0)
//                            readHolds.set(rh);
//
//                        // 累加当前线程持读锁次数
//                        rh.count++;
//
//                        // 更新 cachedHoldCounter 为当前线程持有读锁信息
//                        cachedHoldCounter = rh; // cache for release
//                    }
//
//                    // 返回大于 0 的数，代表获取到了读锁
//                    return 1;
//                }
//            }
//        }
//
//
//
//
//
//
//
//
//
//
//
//        /**
//         * Performs tryLock for write, enabling barging in both modes.
//         * This is identical in effect to tryAcquire except for lack
//         * of calls to writerShouldBlock.
//         */
//        final boolean tryWriteLock() {
//            Thread current = Thread.currentThread();
//            int c = getState();
//            if (c != 0) {
//                int w = exclusiveCount(c);
//                if (w == 0 || current != getExclusiveOwnerThread())
//                    return false;
//                if (w == MAX_COUNT)
//                    throw new Error("Maximum lock count exceeded");
//            }
//            if (!compareAndSetState(c, c + 1))
//                return false;
//            setExclusiveOwnerThread(current);
//            return true;
//        }
//
//        /**
//         * Performs tryLock for read, enabling barging in both modes.
//         * This is identical in effect to tryAcquireShared except for
//         * lack of calls to readerShouldBlock.
//         */
//        final boolean tryReadLock() {
//            Thread current = Thread.currentThread();
//            for (; ; ) {
//                int c = getState();
//                if (exclusiveCount(c) != 0 &&
//                        getExclusiveOwnerThread() != current)
//                    return false;
//                int r = sharedCount(c);
//                if (r == MAX_COUNT)
//                    throw new Error("Maximum lock count exceeded");
//                if (compareAndSetState(c, c + SHARED_UNIT)) {
//                    if (r == 0) {
//                        firstReader = current;
//                        firstReaderHoldCount = 1;
//                    } else if (firstReader == current) {
//                        firstReaderHoldCount++;
//                    } else {
//                        HoldCounter rh = cachedHoldCounter;
//                        if (rh == null || rh.tid != getThreadId(current))
//                            cachedHoldCounter = rh = readHolds.get();
//                        else if (rh.count == 0)
//                            readHolds.set(rh);
//                        rh.count++;
//                    }
//                    return true;
//                }
//            }
//        }
//
//        protected final boolean isHeldExclusively() {
//            // While we must in general read state before owner,
//            // we don't need to do so to check if current thread is owner
//            return getExclusiveOwnerThread() == Thread.currentThread();
//        }
//
//        // Methods relayed to outer class
//
//        final ConditionObject newCondition() {
//            return new ConditionObject();
//        }
//
//        final Thread getOwner() {
//            // Must read state before owner to ensure memory consistency
//            return ((exclusiveCount(getState()) == 0) ?
//                    null :
//                    getExclusiveOwnerThread());
//        }
//
//        final int getReadLockCount() {
//            return sharedCount(getState());
//        }
//
//        final boolean isWriteLocked() {
//            return exclusiveCount(getState()) != 0;
//        }
//
//        final int getWriteHoldCount() {
//            return isHeldExclusively() ? exclusiveCount(getState()) : 0;
//        }
//
//        /**
//         * 返回当前线程获取读锁的次数
//         * @return
//         */
//        final int getReadHoldCount() {
//            // 1 如果读锁持有的总次数为 0 ，则当前线程自然没有持有读锁
//            if (getReadLockCount() == 0)
//                return 0;
//
//            // 2 获取当前线程
//            Thread current = Thread.currentThread();
//
//            // 3 当前线程是目前首个获取读锁的线程
//            if (firstReader == current)
//                return firstReaderHoldCount;
//
//            // 4 当前线程是目前最后获取读锁的线程
//            HoldCounter rh = cachedHoldCounter;
//            if (rh != null && rh.tid == getThreadId(current))
//                return rh.count;
//
//            // 5 既不是首个获取，也不是最后获取，就需要从本地线程中获取
//            int count = readHolds.get().count;
//            if (count == 0) readHolds.remove();
//
//            // 6 返回当前线程持有读锁的次数
//            return count;
//        }
//
//        /**
//         * Reconstitutes the instance from a stream (that is, deserializes it).
//         */
//        private void readObject(java.io.ObjectInputStream s)
//                throws java.io.IOException, ClassNotFoundException {
//            s.defaultReadObject();
//            readHolds = new ThreadLocalHoldCounter();
//            setState(0); // reset to unlocked state
//        }
//
//        final int getCount() {
//            return getState();
//        }
//    }
//
//
//
//    /**
//     * 非公平版本的 Sync
//     */
//    static final class NonfairSync extends Sync {
//        private static final long serialVersionUID = -8159625535654395037L;
//
//        /**
//         * 获取写锁是否需要阻塞
//         * @return
//         */
//        final boolean writerShouldBlock() {
//            // 如果是非公平模式，那么 lock 的时候就可以直接用 CAS 去抢锁，抢不到再排队
//            return false; // writers can always barge
//        }
//
//        /**
//         * 获取读锁是否需要阻塞
//         * @return
//         */
//        final boolean readerShouldBlock() {
//            // 判断同步队列中 head 的第一个后继节点是否是来获取写锁的，如果是，就算是非公平模式，也先让该节点获取写锁，避免线程饥饿
//            return apparentlyFirstQueuedIsExclusive();
//            //     final boolean apparentlyFirstQueuedIsExclusive() {
//            //        Node h, s;
//            //        return (h = head) != null &&
//            //            (s = h.next)  != null &&
//            //            !s.isShared()         &&
//            //            s.thread != null;
//            //    }
//        }
//    }
//
//    /**
//     * 公平版本的 Sync
//     */
//    static final class FairSync extends Sync {
//        private static final long serialVersionUID = -2274990926593161451L;
//
//        /**
//         * 获取写锁是否需要阻塞
//         * @return
//         */
//        final boolean writerShouldBlock() {
//            // 那么如果阻塞队列有线程等待的话，就乖乖去排队
//            return hasQueuedPredecessors();
//        }
//
//        /**
//         * 判断读是否要阻塞
//         * @return
//         */
//        final boolean readerShouldBlock() {
//            // 同步队列中有线程节点在等待
//            return hasQueuedPredecessors();
//        }
//    }
//
//
//    /**
//     * 读锁实现
//     */
//    public static class ReadLock implements Lock, java.io.Serializable {
//        private static final long serialVersionUID = -5992448646407690164L;
//
//        /**
//         * 使用 AQS 管理同步状态
//         */
//        private final Sync sync;
//
//        /**
//         * 构造方法
//         *
//         * @param lock
//         */
//        protected ReadLock(ReentrantReadWriteLock lock) {
//            sync = lock.sync;
//        }
//
//        /**
//         * 获取读锁
//         */
//        public void lock() {
//            // AQS 模版方法，获取共享同步状态
//            sync.acquireShared(1);
//        }
//
//        /**
//         * 可中断获取锁
//         *
//         * @throws InterruptedException
//         */
//        public void lockInterruptibly() throws InterruptedException {
//            sync.acquireSharedInterruptibly(1);
//        }
//
//        /**
//         * 尝试获取读锁
//         *
//         * @return
//         */
//        public boolean tryLock() {
//            return sync.tryReadLock();
//        }
//
//        /**
//         * 尝试超时获取锁
//         *
//         * @param timeout
//         * @param unit
//         * @return
//         * @throws InterruptedException
//         */
//        public boolean tryLock(long timeout, TimeUnit unit)
//                throws InterruptedException {
//            return sync.tryAcquireSharedNanos(1, unit.toNanos(timeout));
//        }
//
//        /**
//         * 释放读锁
//         */
//        public void unlock() {
//            sync.releaseShared(1);
//        }
//
//        /**
//         * 不支持条件对象
//         *
//         * @return
//         */
//        public Condition newCondition() {
//            throw new UnsupportedOperationException();
//        }
//
//    }
//
//
//
//
//    /**
//     * 写锁
//     * 1 写锁是独占锁
//     * 2 如果有读锁被占用，写锁获取要进入同步队列中等待
//     */
//    public static class WriteLock implements Lock, java.io.Serializable {
//        private static final long serialVersionUID = -4992448646407690164L;
//        private final Sync sync;
//
//
//        protected WriteLock(ReentrantReadWriteLock lock) {
//            sync = lock.sync;
//        }
//
//        /**
//         * 获取写锁
//         */
//        public void lock() {
//            sync.acquire(1);
//        }
//
//        public void lockInterruptibly() throws InterruptedException {
//            sync.acquireInterruptibly(1);
//        }
//
//
//        public boolean tryLock() {
//            return sync.tryWriteLock();
//        }
//
//
//        public boolean tryLock(long timeout, TimeUnit unit)
//                throws InterruptedException {
//            return sync.tryAcquireNanos(1, unit.toNanos(timeout));
//        }
//
//        /**
//         * 写锁释放
//         */
//        public void unlock() {
//            sync.release(1);
//        }
//
//
//        public Condition newCondition() {
//            return sync.newCondition();
//        }
//
//
//        /**
//         * Queries if this write lock is held by the current thread.
//         * Identical in effect to {@link
//         * ReentrantReadWriteLock#isWriteLockedByCurrentThread}.
//         *
//         * @return {@code true} if the current thread holds this lock and
//         * {@code false} otherwise
//         * @since 1.6
//         */
//        public boolean isHeldByCurrentThread() {
//            return sync.isHeldExclusively();
//        }
//
//        /**
//         * Queries the number of holds on this write lock by the current
//         * thread.  A thread has a hold on a lock for each lock action
//         * that is not matched by an unlock action.  Identical in effect
//         * to {@link ReentrantReadWriteLock#getWriteHoldCount}.
//         *
//         * @return the number of holds on this lock by the current thread,
//         * or zero if this lock is not held by the current thread
//         * @since 1.6
//         */
//        public int getHoldCount() {
//            return sync.getWriteHoldCount();
//        }
//    }
//
//
//
//
//
//
//    // Instrumentation and status
//
//    /**
//     * Returns {@code true} if this lock has fairness set true.
//     *
//     * @return {@code true} if this lock has fairness set true
//     */
//    public final boolean isFair() {
//        return sync instanceof FairSync;
//    }
//
//    /**
//     * Returns the thread that currently owns the write lock, or
//     * {@code null} if not owned. When this method is called by a
//     * thread that is not the owner, the return value reflects a
//     * best-effort approximation of current lock status. For example,
//     * the owner may be momentarily {@code null} even if there are
//     * threads trying to acquire the lock but have not yet done so.
//     * This method is designed to facilitate construction of
//     * subclasses that provide more extensive lock monitoring
//     * facilities.
//     *
//     * @return the owner, or {@code null} if not owned
//     */
//    protected Thread getOwner() {
//        return sync.getOwner();
//    }
//
//    /**
//     * Queries the number of read locks held for this lock. This
//     * method is designed for use in monitoring system state, not for
//     * synchronization control.
//     *
//     * @return the number of read locks held
//     */
//    public int getReadLockCount() {
//        return sync.getReadLockCount();
//    }
//
//    /**
//     * Queries if the write lock is held by any thread. This method is
//     * designed for use in monitoring system state, not for
//     * synchronization control.
//     *
//     * @return {@code true} if any thread holds the write lock and
//     * {@code false} otherwise
//     */
//    public boolean isWriteLocked() {
//        return sync.isWriteLocked();
//    }
//
//    /**
//     * Queries if the write lock is held by the current thread.
//     *
//     * @return {@code true} if the current thread holds the write lock and
//     * {@code false} otherwise
//     */
//    public boolean isWriteLockedByCurrentThread() {
//        return sync.isHeldExclusively();
//    }
//
//    /**
//     * Queries the number of reentrant write holds on this lock by the
//     * current thread.  A writer thread has a hold on a lock for
//     * each lock action that is not matched by an unlock action.
//     *
//     * @return the number of holds on the write lock by the current thread,
//     * or zero if the write lock is not held by the current thread
//     */
//    public int getWriteHoldCount() {
//        return sync.getWriteHoldCount();
//    }
//
//    /**
//     * Queries the number of reentrant read holds on this lock by the
//     * current thread.  A reader thread has a hold on a lock for
//     * each lock action that is not matched by an unlock action.
//     *
//     * @return the number of holds on the read lock by the current thread,
//     * or zero if the read lock is not held by the current thread
//     * @since 1.6
//     */
//    public int getReadHoldCount() {
//        return sync.getReadHoldCount();
//    }
//
//
//    protected Collection<Thread> getQueuedWriterThreads() {
//        return sync.getExclusiveQueuedThreads();
//    }
//
//
//    protected Collection<Thread> getQueuedReaderThreads() {
//        return sync.getSharedQueuedThreads();
//    }
//
//
//    public final boolean hasQueuedThreads() {
//        return sync.hasQueuedThreads();
//    }
//
//
//    public final boolean hasQueuedThread(Thread thread) {
//        return sync.isQueued(thread);
//    }
//
//
//    public final int getQueueLength() {
//        return sync.getQueueLength();
//    }
//
//
//    protected Collection<Thread> getQueuedThreads() {
//        return sync.getQueuedThreads();
//    }
//
//    public boolean hasWaiters(Condition condition) {
//        if (condition == null)
//            throw new NullPointerException();
//        if (!(condition instanceof AbstractQueuedSynchronizer.ConditionObject))
//            throw new IllegalArgumentException("not owner");
//        return sync.hasWaiters((AbstractQueuedSynchronizer.ConditionObject) condition);
//    }
//
//
//    public int getWaitQueueLength(Condition condition) {
//        if (condition == null)
//            throw new NullPointerException();
//        if (!(condition instanceof AbstractQueuedSynchronizer.ConditionObject))
//            throw new IllegalArgumentException("not owner");
//        return sync.getWaitQueueLength((AbstractQueuedSynchronizer.ConditionObject) condition);
//    }
//
//
//    protected Collection<Thread> getWaitingThreads(Condition condition) {
//        if (condition == null)
//            throw new NullPointerException();
//        if (!(condition instanceof AbstractQueuedSynchronizer.ConditionObject))
//            throw new IllegalArgumentException("not owner");
//        return sync.getWaitingThreads((AbstractQueuedSynchronizer.ConditionObject) condition);
//    }
//
//
//    /**
//     * Returns the thread id for the given thread.  We must access
//     * this directly rather than via method Thread.getId() because
//     * getId() is not final, and has been known to be overridden in
//     * ways that do not preserve unique mappings.
//     */
//    static final long getThreadId(Thread thread) {
//        return UNSAFE.getLongVolatile(thread, TID_OFFSET);
//    }
//
//    // Unsafe mechanics
//    private static final sun.misc.Unsafe UNSAFE;
//    private static final long TID_OFFSET;
//
//    static {
//        try {
//            UNSAFE = sun.misc.Unsafe.getUnsafe();
//            Class<?> tk = Thread.class;
//            TID_OFFSET = UNSAFE.objectFieldOffset
//                    (tk.getDeclaredField("tid"));
//        } catch (Exception e) {
//            throw new Error(e);
//        }
//    }
//
//}
