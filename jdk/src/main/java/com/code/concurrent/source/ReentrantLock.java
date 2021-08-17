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
import java.util.Collection;

/**
 * A reentrant mutual exclusion {@link Lock} with the same basic
 * behavior and semantics as the implicit monitor lock accessed using
 * {@code synchronized} methods and statements, but with extended
 * capabilities.
 *
 * <p>A {@code ReentrantLock} is <em>owned</em> by the thread last
 * successfully locking, but not yet unlocking it. A thread invoking
 * {@code lock} will return, successfully acquiring the lock, when
 * the lock is not owned by another thread. The method will return
 * immediately if the current thread already owns the lock. This can
 * be checked using methods {@link #isHeldByCurrentThread}, and {@link
 * #getHoldCount}.
 *
 * <p>The constructor for this class accepts an optional
 * <em>fairness</em> parameter.  When set {@code true}, under
 * contention, locks favor granting access to the longest-waiting
 * thread.  Otherwise this lock does not guarantee any particular
 * access order.  Programs using fair locks accessed by many threads
 * may display lower overall throughput (i.e., are slower; often much
 * slower) than those using the default setting, but have smaller
 * variances in times to obtain locks and guarantee lack of
 * starvation. Note however, that fairness of locks does not guarantee
 * fairness of thread scheduling. Thus, one of many threads using a
 * fair lock may obtain it multiple times in succession while other
 * active threads are not progressing and not currently holding the
 * lock.
 * Also note that the untimed {@link #tryLock()} method does not
 * honor the fairness setting. It will succeed if the lock
 * is available even if other threads are waiting.
 *
 * <p>It is recommended practice to <em>always</em> immediately
 * follow a call to {@code lock} with a {@code try} block, most
 * typically in a before/after construction such as:
 *
 * <pre> {@code
 * class X {
 *   private final ReentrantLock lock = new ReentrantLock();
 *   // ...
 *
 *   public void m() {
 *     lock.lock();  // block until condition holds
 *     try {
 *       // ... method body
 *     } finally {
 *       lock.unlock()
 *     }
 *   }
 * }}</pre>
 *
 * <p>In addition to implementing the {@link Lock} interface, this
 * class defines a number of {@code public} and {@code protected}
 * methods for inspecting the state of the lock.  Some of these
 * methods are only useful for instrumentation and monitoring.
 *
 * <p>Serialization of this class behaves in the same way as built-in
 * locks: a deserialized lock is in the unlocked state, regardless of
 * its state when serialized.
 *
 * <p>This lock supports a maximum of 2147483647 recursive locks by
 * the same thread. Attempts to exceed this limit result in
 * {@link Error} throws from locking methods.
 *
 * @author Doug Lea
 * @since 1.5
 */
public class ReentrantLock implements Lock, java.io.Serializable {
    private static final long serialVersionUID = 7373984872572414699L;


    /**
     * 继承 AQS 的内部类
     */
    private final Sync sync;

    /**
     * ReentrantLock 通过在内部继承 AQS 来管理锁。真正获取锁和释放锁是由内部类 Sync 的实现类来控制的。
     * 说明：
     * Sync 是一个抽象的内部类，它有两个实现，分别是 NonfairSync（非公平锁）和 FairSync（公平锁）
     */
    abstract static class Sync extends AbstractQueuedSynchronizer {
        private static final long serialVersionUID = -5179523762034025860L;

        /**
         * 获取锁的方法
         */
        abstract void lock();

        /**
         * 非公平尝试获取同步状态。
         * 用于：非公平锁的 tryAcquire 和 尝试获取锁的 tryLock
         */
        final boolean nonfairTryAcquire(int acquires) {
            // 1 获取当前线程
            final Thread current = Thread.currentThread();

            // 2 获取同步状态，也就是锁资源
            int c = getState();

            // 3 如果 state == 0 ，说明此时没有线程持有锁
            if (c == 0) {
                // 直接使用 CAS 尝试获取锁。
                // 注意，相比较公平锁，这里没有对同步队列进行判断，因为是非公平锁
                if (compareAndSetState(0, acquires)) {
                    setExclusiveOwnerThread(current);
                    return true;
                }

                // 4 此时有线程持有锁，判断是否是重入的情况，根据占用锁的线程是否是当前线程
            } else if (current == getExclusiveOwnerThread()) {
                // 4.1 重入的情况需要操作： state = state + 1
                int nextc = c + acquires;
                if (nextc < 0) // overflow
                    throw new Error("Maximum lock count exceeded");
                // 4.2 修改 state
                setState(nextc);
                return true;
            }

            // 5 获取锁失败
            return false;
        }

        /**
         * 可重入锁的释放锁，不区分是否为公平锁。
         *
         * @param releases
         * @return
         */
        protected final boolean tryRelease(int releases) {
            // 释放同步状态
            int c = getState() - releases;

            // 释放锁的操作只能是获取锁的线程
            if (Thread.currentThread() != getExclusiveOwnerThread())
                throw new IllegalMonitorStateException();

            // 标记是否完全释放，因为 ReentrantLock 支持可重入
            boolean free = false;

            // 如果释放后，同步状态为 0 ，说明是完全释放，那么重置独占锁的线程为 null
            if (c == 0) {
                free = true;
                setExclusiveOwnerThread(null);
            }

            // 更新同步状态
            setState(c);

            // 是否完全释放，完全释放才算释放成功
            return free;
        }

        /**
         * 判断当前线程是否正在独占锁
         *
         * @return
         */
        protected final boolean isHeldExclusively() {
            // While we must in general read state before owner,
            // we don't need to do so to check if current thread is owner
            return getExclusiveOwnerThread() == Thread.currentThread();
        }

        /**
         * 创建当前 ReentrantLock 的 Condition 对象
         *
         * @return
         */
        final ConditionObject newCondition() {
            return new ConditionObject();
        }

        // Methods relayed from outer class

        final Thread getOwner() {
            return getState() == 0 ? null : getExclusiveOwnerThread();
        }

        final int getHoldCount() {
            return isHeldExclusively() ? getState() : 0;
        }

        final boolean isLocked() {
            return getState() != 0;
        }

        /**
         * Reconstitutes the instance from a stream (that is, deserializes it).
         */
        private void readObject(java.io.ObjectInputStream s)
                throws java.io.IOException, ClassNotFoundException {
            s.defaultReadObject();
            setState(0); // reset to unlocked state
        }
    }

    /**
     * 非公平锁实现
     */
    static final class NonfairSync extends Sync {
        private static final long serialVersionUID = 7316153563782823691L;

        /**
         * 实现 Sync 抽象类的 lock 方法，用于非公平获取锁
         */
        final void lock() {
            // 1 调用 AQS 的设置通过状态的方法，尝试将同步状态由 0 设置为 1
            if (compareAndSetState(0, 1))
                // 1.1 将同步状态由 0 设置为 1 成功，表示获取锁成功。这里记录获取锁的线程
                setExclusiveOwnerThread(Thread.currentThread());

                // 2 调用 AQS 独占模式获取同步状态的方法。同步状态参数固定是 1
            else
                acquire(1);
        }

        /**
         * 实现 AQS 的模版方法，尝试获取独占模式的同步状态。这里是获取锁
         *
         * @param acquires
         * @return
         */
        protected final boolean tryAcquire(int acquires) {
            return nonfairTryAcquire(acquires);
        }
    }

    /**
     * 公平锁实现
     */
    static final class FairSync extends Sync {
        private static final long serialVersionUID = -3000897897090466540L;

        /**
         * 实现 Sync 抽象类的 lock 方法，用于公平获取锁
         */
        final void lock() {
            // 调用 AQS 的模版方法，会调用实现的 tryAcquire 方法。
            // 注意，这里的同步状态固定为 1
            acquire(1);
        }

        /**
         * 实现 AQS 的模版方法。公平地尝试获取锁
         *
         * @param acquires
         * @return true - 获取锁成功（1 没有线程在等待锁 2 重入锁的情况，线程本来就持有锁，当然可以再次拿到）
         */
        protected final boolean tryAcquire(int acquires) {
            // 1 获取当前线程
            final Thread current = Thread.currentThread();

            // 2 获取同步状态，也就是锁资源
            int c = getState();

            // 3 如果 state == 0 ，说明此时没有线程持有锁
            if (c == 0) {
                // 3.1 虽然此时没有线程持有锁，但是由于这是公平锁，要讲究先来后到，此时可能在同步队列中有等待的线程节点。
                if (!hasQueuedPredecessors() &&

                        // 3.2 如果同步队列中没有线程在等待，那么就使用 CAS 尝试获取锁。不成功的情况，说明就在刚刚几乎同一时刻有其它线程抢先了
                        compareAndSetState(0, acquires)) {

                    // 3.3 获取到锁就进行标记下，告知其它线程自己持有了锁
                    setExclusiveOwnerThread(current);
                    return true;
                }

                // 4 此时有线程持有锁，判断是否是重入的情况，根据占用锁的线程是否是当前线程
            } else if (current == getExclusiveOwnerThread()) {
                // 4.1 重入的情况需要操作： state = state + 1
                int nextc = c + acquires;
                if (nextc < 0)
                    throw new Error("Maximum lock count exceeded");
                // 4.2 修改 state
                setState(nextc);
                return true;
            }

            // 5 获取锁失败
            // 回到外层调用方法：
            //     public final void acquire(int arg) {
            //        if (!tryAcquire(arg) &&
            //            // 线程入队及后续操作
            //            acquireQueued(addWaiter(Node.EXCLUSIVE), arg))
            //            selfInterrupt();
            //    }
            return false;
        }
    }


    /**
     * 默认创建非公平锁
     */
    public ReentrantLock() {
        sync = new NonfairSync();
    }

    /**
     * 创建公平或非公平锁，根据传入参数决定
     *
     * @param fair true: 公平 false: 非公平
     */
    public ReentrantLock(boolean fair) {
        sync = fair ? new FairSync() : new NonfairSync();
    }

    /**
     * 获取锁
     */
    public void lock() {
        sync.lock();
    }

    /**
     * 可中断获取锁
     *
     * @throws InterruptedException
     */
    public void lockInterruptibly() throws InterruptedException {
        sync.acquireInterruptibly(1);
    }

    /**
     * 尝试获取锁，获取不到也没关系，不会入队
     *
     * @return
     */
    public boolean tryLock() {
        return sync.nonfairTryAcquire(1);
    }

    /**
     * 尝试在指定的时间内获取锁
     *
     * @param timeout
     * @param unit
     * @return
     * @throws InterruptedException
     */
    public boolean tryLock(long timeout, TimeUnit unit)
            throws InterruptedException {
        return sync.tryAcquireNanos(1, unit.toNanos(timeout));
    }

    /**
     * 解锁，不区分公平还是不公平
     */
    public void unlock() {
        sync.release(1);
    }

    /**
     * 创建当前 ReentrantLock 的 Condition 对象
     *
     * @return
     */
    public Condition newCondition() {
        return sync.newCondition();
    }

    /**
     * 查询当前线程对锁持有的次数
     *
     * @return
     */
    public int getHoldCount() {
        return sync.getHoldCount();
    }

    /**
     * 判断当前线程是否持有锁
     *
     * @return
     */
    public boolean isHeldByCurrentThread() {
        return sync.isHeldExclusively();
    }


    /**
     * 判断锁是否有线程持有，标志是 state != 0
     *
     * @return
     */
    public boolean isLocked() {
        return sync.isLocked();
    }

    /**
     * 判断当前 ReentrantLock 是否公平
     *
     * @return
     */
    public final boolean isFair() {
        return sync instanceof FairSync;
    }


    protected Thread getOwner() {
        return sync.getOwner();
    }

    public final boolean hasQueuedThreads() {
        return sync.hasQueuedThreads();
    }

    public final boolean hasQueuedThread(Thread thread) {
        return sync.isQueued(thread);
    }

    public final int getQueueLength() {
        return sync.getQueueLength();
    }

    protected Collection<Thread> getQueuedThreads() {
        return sync.getQueuedThreads();
    }

    public boolean hasWaiters(Condition condition) {
        if (condition == null)
            throw new NullPointerException();
        if (!(condition instanceof AbstractQueuedSynchronizer.ConditionObject))
            throw new IllegalArgumentException("not owner");
        return sync.hasWaiters((AbstractQueuedSynchronizer.ConditionObject) condition);
    }

    public int getWaitQueueLength(Condition condition) {
        if (condition == null)
            throw new NullPointerException();
        if (!(condition instanceof AbstractQueuedSynchronizer.ConditionObject))
            throw new IllegalArgumentException("not owner");
        return sync.getWaitQueueLength((AbstractQueuedSynchronizer.ConditionObject) condition);
    }

    protected Collection<Thread> getWaitingThreads(Condition condition) {
        if (condition == null)
            throw new NullPointerException();
        if (!(condition instanceof AbstractQueuedSynchronizer.ConditionObject))
            throw new IllegalArgumentException("not owner");
        return sync.getWaitingThreads((AbstractQueuedSynchronizer.ConditionObject) condition);
    }

}
