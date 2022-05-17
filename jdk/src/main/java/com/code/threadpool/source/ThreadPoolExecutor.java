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

import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.*;

public class ThreadPoolExecutor extends AbstractExecutorService {

    //======= 约定使用32位表示线程池状态和数量，高三位表示状态 ，低29位表示数量 =============/

    /**
     * 线程池初始化状态码，状态为 RUNNING，线程数为 0
     */
    private final AtomicInteger ctl = new AtomicInteger(ctlOf(RUNNING, 0));
    /**
     * 这里 COUNT_BITS 设置为 29 (0001 1101)，即约定高三位用于存放线程状态，低29位用于存放线程数
     */
    private static final int COUNT_BITS = Integer.SIZE - 3;

    /**
     * 1 数字1向左移29位，即 1 * 2^29 = 536870912，这是线程池中最大线程数
     * 2 过程：（1）001 （2）左移29位得到001后跟29个0 -> 0010 0000 0000 0000 0000 0000 0000 0000
     * 3 减去1后的结果：                              0001 1111 1111 1111 1111 1111 1111 1111
     */

    /**
     * 线程池允许最大线程池临界值，1 * 2^29 = 536870912
     * 过程：（1）001 （2）左移29位得到001后跟29个0 -> 0010 0000 0000 0000 0000 0000 0000 0000 （3）减去1得0001 1111 1111 1111 1111 1111 1111 1111
     */
    private static final int CAPACITY = (1 << COUNT_BITS) - 1;

    // 线程池的状态存放在高 3 位中

    // 运行状态：接受新的任务，处理等待队列中的任务 。 （线程池一旦创建就处于运行状态，默认值还是？ todo）
    // （ 负数的补码是在原码的基础上除符号位外其余位取反后+1。习惯性按每四位一块，最高位填充1）
    // -1 的补码： 1001（最高位是符号位，11 也是可以的，不过Integer4个字节，32位，习惯性补齐） -> 1110 -> 1111 ，左移29位得到111后跟29个0：
    // 111 00000000000000000000000000000 （共32位）

    /**
     * 运行状态：111 00000000000000000000000000000
     */
    private static final int RUNNING = -1 << COUNT_BITS;


    // 关闭状态：不接受新的任务提交，但是会继续处理等待队列中的任务
    // 000 00000000000000000000000000000

    /**
     * 关闭状态：000 00000000000000000000000000000
     */
    private static final int SHUTDOWN = 0 << COUNT_BITS;


    // 停止状态：不接受新的任务提交，不再处理等待队列中的任务，中断正在执行任务的线程
    // 001 00000000000000000000000000000

    /**
     * 停止状态：001 00000000000000000000000000000
     */
    private static final int STOP = 1 << COUNT_BITS;


    // 所有的任务都销毁了，workCount 为 0。线程池的状态在转换为 TIDYING 状态时，会执行钩子方法 terminated()
    // 010 00000000000000000000000000000

    /**
     * 整理状态：010 00000000000000000000000000000
     */
    private static final int TIDYING = 2 << COUNT_BITS;

    // terminated() 方法结束后，线程池的状态就会变成这个
    // 011 00000000000000000000000000000

    /**
     * 终止状态：011 00000000000000000000000000000
     */
    private static final int TERMINATED = 3 << COUNT_BITS;


    // 获取线程池的状态
    // 将整数 c 的低 29 位修改为 0，就得到了线程池的状态
    private static int runStateOf(int c) {
        return c & ~CAPACITY;
    }

    // 用于计算线程池中线程数量
    // 将整数 c 的高 3 位修改为 0，就得到了线程池中的线程数
    //  000 1 1111 1111 1111 1111 1111 1111 1111
    private static int workerCountOf(int c) {
        return c & CAPACITY;
    }

    // 返回线程池的状态码，包括两部分：线程池状态 + 线程数
    private static int ctlOf(int rs, int wc) {
        return rs | wc;
    }

    /**
     * 比较状态
     *
     * @param c
     * @param s
     * @return
     */
    private static boolean runStateLessThan(int c, int s) {
        return c < s;
    }

    private static boolean runStateAtLeast(int c, int s) {
        return c >= s;
    }

    /**
     * 当前线程池是否处于运行状态
     *
     * @param c
     * @return
     */
    private static boolean isRunning(int c) {
        return c < SHUTDOWN;
    }

    /**
     * 增加线程池中的线程数量
     */
    private boolean compareAndIncrementWorkerCount(int expect) {
        return ctl.compareAndSet(expect, expect + 1);
    }

    /**
     * 减少线程池中的线程数量
     * Attempts to CAS-decrement the workerCount field of ctl.
     */
    private boolean compareAndDecrementWorkerCount(int expect) {
        return ctl.compareAndSet(expect, expect - 1);
    }


    private void decrementWorkerCount() {
        do {
        } while (!compareAndDecrementWorkerCount(ctl.get()));
    }


    /**
     * 线程池阻塞队列
     */
    private final BlockingQueue<Runnable> workQueue;

    /**
     * 线程池全局锁
     */
    private final ReentrantLock mainLock = new ReentrantLock();

    /**
     * 用于保存线程池创建的Worker，动态变化
     */
    private final HashSet<Worker> workers = new HashSet<>();

    /**
     * 全局锁条件 - 等待队列
     */
    private final Condition termination = mainLock.newCondition();

    /**
     * 追踪线程池最大值，仅在获取到全局锁条件下执行
     */
    private int largestPoolSize;

    /**
     * 线程池完成任务数量
     */
    private long completedTaskCount;

    /**
     * 线程工厂
     */
    private volatile ThreadFactory threadFactory;

    /**
     * 饱和策略
     */
    private volatile RejectedExecutionHandler handler;

    /**
     * 保活时间，即最大允许空闲时间
     */
    private volatile long keepAliveTime;

    /**
     * 是否允许线程池被回收
     */
    private volatile boolean allowCoreThreadTimeOut;

    /**
     * 核心线程池数，不会被回收，即 workers的最小值。除非设置 allowCoreThreadTimeOut 。
     */
    private volatile int corePoolSize;

    /**
     * 最大线程数
     */
    private volatile int maximumPoolSize;


    /**
     * 默认的饱和策略
     */
    private static final RejectedExecutionHandler defaultHandler =
            new AbortPolicy();


    private static final RuntimePermission shutdownPerm =
            new RuntimePermission("modifyThread");


    private final AccessControlContext acc;


    // Public constructors and methods 构造方法们
    public ThreadPoolExecutor(int corePoolSize,
                              int maximumPoolSize,
                              long keepAliveTime,
                              TimeUnit unit,
                              BlockingQueue<Runnable> workQueue) {
        this(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue,
                Executors.defaultThreadFactory(), defaultHandler);
    }

    public ThreadPoolExecutor(int corePoolSize,
                              int maximumPoolSize,
                              long keepAliveTime,
                              TimeUnit unit,
                              BlockingQueue<Runnable> workQueue,
                              ThreadFactory threadFactory) {
        this(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue,
                threadFactory, defaultHandler);
    }

    public ThreadPoolExecutor(int corePoolSize,
                              int maximumPoolSize,
                              long keepAliveTime,
                              TimeUnit unit,
                              BlockingQueue<Runnable> workQueue,
                              RejectedExecutionHandler handler) {
        this(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue,
                Executors.defaultThreadFactory(), handler);
    }

    public ThreadPoolExecutor(int corePoolSize,
                              int maximumPoolSize,
                              long keepAliveTime,
                              TimeUnit unit,
                              BlockingQueue<Runnable> workQueue,
                              ThreadFactory threadFactory,
                              RejectedExecutionHandler handler) {
        if (corePoolSize < 0 ||
                maximumPoolSize <= 0 ||
                maximumPoolSize < corePoolSize ||
                keepAliveTime < 0)
            throw new IllegalArgumentException();
        if (workQueue == null || threadFactory == null || handler == null)
            throw new NullPointerException();
        this.acc = System.getSecurityManager() == null ?
                null :
                AccessController.getContext();
        this.corePoolSize = corePoolSize;
        this.maximumPoolSize = maximumPoolSize;
        this.workQueue = workQueue;
        this.keepAliveTime = unit.toNanos(keepAliveTime);
        this.threadFactory = threadFactory;
        this.handler = handler;
    }


    /**
     * ThreadPoolExecutor 的内部类
     * 作用：
     * 把线程池中的线程包装成了一个个 Worker，即线程池中做任务的线程
     */
    private final class Worker extends AbstractQueuedSynchronizer implements Runnable {
        /**
         * This class will never be serialized, but we provide a
         * serialVersionUID to suppress a javac warning.
         */
        private static final long serialVersionUID = 6138294804551838833L;

        /**
         * 任务执行的真正线程
         */
        final Thread thread;


        /** Initial task to run.  Possibly null. */
        /**
         * 创建线程的时候，如果同时指定了该线程启动后需要执行的第一个任务，就会存在这个变量中，线程也可以补执行这个任务。
         * 此外，也可以为 null，这样线程启动后会到任务队列中取任务（getTask()方法）执行。
         */


        /**
         * 主线程提交任务到线程池，任务就会存放到这里。
         */
        Runnable firstTask;
        /**
         * 用于存放当前线程完成的任务数。注意和 completedTaskCount 的区别
         */
        volatile long completedTasks;

        /**
         * Worker 唯一的构造方法
         *
         * @param firstTask 任务，可能为 null
         */
        Worker(Runnable firstTask) {
            // 设置状态值为 -1，防止在启动线程之前，其它操作拿到全局锁
            setState(-1);
            this.firstTask = firstTask;
            // 使用工厂创建线程，注意创建出来的线程的任务体就是 Worker 本身。这意味着当线程启动时，Worker#run方法就会执行
            this.thread = getThreadFactory().newThread(this);
        }

        /**
         * Worker 实现了 Runnable 接口，重写了run() 方法。
         * Worker中的thread 方法启动后后，该方法就会执行。
         */
        public void run() {
            // 这里调用了外部类的 runWorker 方法
            runWorker(this);
        }


        // --------------- Worker 继承了 AQS类，下面的核心方法是重写了 AQS的方法，使用独占锁获得执行权 -----------------/

        protected boolean isHeldExclusively() {
            return getState() != 0;
        }

        /**
         * 独占式获取资源
         *
         * @param unused
         * @return
         */
        protected boolean tryAcquire(int unused) {
            if (compareAndSetState(0, 1)) {
                setExclusiveOwnerThread(Thread.currentThread());
                return true;
            }
            return false;
        }

        /**
         * 释放资源
         *
         * @param unused
         * @return
         */
        protected boolean tryRelease(int unused) {
            setExclusiveOwnerThread(null);
            setState(0);
            return true;
        }

        /**
         * lock
         */
        public void lock() {
            acquire(1);
        }

        public boolean tryLock() {
            return tryAcquire(1);
        }

        /**
         * unlock
         */
        public void unlock() {
            release(1);
        }

        public boolean isLocked() {
            return isHeldExclusively();
        }

        /**
         * 中断线程
         */
        void interruptIfStarted() {
            Thread t;
            if (getState() >= 0 && (t = thread) != null && !t.isInterrupted()) {
                try {
                    t.interrupt();
                } catch (SecurityException ignore) {
                }
            }
        }
    }


    /**
     * Transitions runState to given target, or leaves it alone if
     * already at least the given target.
     *
     * @param targetState the desired state, either SHUTDOWN or STOP
     *                    (but not TIDYING or TERMINATED -- use tryTerminate for that)
     */
    private void advanceRunState(int targetState) {
        for (; ; ) {
            int c = ctl.get();
            if (runStateAtLeast(c, targetState) ||
                    ctl.compareAndSet(c, ctlOf(targetState, workerCountOf(c))))
                break;
        }
    }

    /**
     * 尝试终止线程池，被用在：shutdown()、shutdownNow()、线程退出
     *
     * 几乎每个线程最后消亡的时候都会调用tryTerminate()方法，但最后只会有一个线程真正执行到终止线程池的地方。
     */
    final void tryTerminate() {
        for (; ; ) {

            // 线程池状态码
            int c = ctl.get();

            // 线程池是运行状态，或大于等于 TIDYING 状态，或是 SHUTDOWN 状态且阻塞队列非空。这些条件是不允许关闭线程池的
            // todo STOP 状态一定会执行后续的终止流程
            if (isRunning(c) || runStateAtLeast(c, TIDYING) || (runStateOf(c) == SHUTDOWN && !workQueue.isEmpty()))
                return;

            // 线程池中线程数量不为 0，向任意空闲线程发出中断信号。所有被阻塞的线程，最终都会被一个个唤醒，回收。todo
            if (workerCountOf(c) != 0) { // Eligible to terminate
                interruptIdleWorkers(ONLY_ONE);
                return;
            }

            /* 只有工作线程数为 0 才能进入终止线程池流程。工作线程数为 0 ，那么所有任务其实也终止了*/

            // 全局锁
            final ReentrantLock mainLock = this.mainLock;

            // 终止线程池时加全局锁，保证CAS执行成功
            mainLock.lock();
            try {
                // 设置线程池状态码为 TIDYING
                if (ctl.compareAndSet(c, ctlOf(TIDYING, 0))) {
                    try {
                        // 状态设置成功后执行 terminated() 钩子方法
                        terminated();
                    } finally {

                        // 设置线程池状态码为 TERMINATED 终止状态
                        ctl.set(ctlOf(TERMINATED, 0));

                        // 主要为 awaitTermination 方法服务，唤醒等待线程池终止的线程
                        termination.signalAll();
                    }
                    return;
                }
            } finally {
                mainLock.unlock();
            }
            // else retry on failed CAS
        }
    }


    private void checkShutdownAccess() {
        SecurityManager security = System.getSecurityManager();
        if (security != null) {
            security.checkPermission(shutdownPerm);
            final ReentrantLock mainLock = this.mainLock;
            mainLock.lock();
            try {
                for (Worker w : workers)
                    security.checkAccess(w.thread);
            } finally {
                mainLock.unlock();
            }
        }
    }

    /**
     * Interrupts all threads, even if active. Ignores SecurityExceptions
     * (in which case some threads may remain uninterrupted).
     */
    private void interruptWorkers() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            for (Worker w : workers)
                w.interruptIfStarted();
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * Common form of interruptIdleWorkers, to avoid having to
     * remember what the boolean argument means.
     */
    private void interruptIdleWorkers() {
        interruptIdleWorkers(false);
    }

    /**
     * 中断所有闲置的Worker
     *
     * @param onlyOne 是否仅中断一个
     */
    private void interruptIdleWorkers(boolean onlyOne) {
        final ReentrantLock mainLock = this.mainLock;
        // 全局锁，涉及到 workers 操作线程池都会加该锁
        mainLock.lock();
        try {
            // 遍历 workers ，对每个非中断线程进行中断操作。
            for (Worker w : workers) {
                Thread t = w.thread;
                // 如果线程非中断状态，且能 tryLock() 成功「执行的过程会持有锁，这就不能获取成功」，说明该线程闲置(阻塞等待），需要进行中断
                if (!t.isInterrupted() && w.tryLock()) {
                    try {
                        // 让从阻塞状态的线程醒来后，判断线程池关闭了，进而回收「getTask 方法中」
                        t.interrupt();
                    } catch (SecurityException ignore) {
                    } finally {
                        w.unlock();
                    }
                }
                if (onlyOne)
                    break;
            }
        } finally {
            mainLock.unlock();
        }
    }


    private static final boolean ONLY_ONE = true;


    final void reject(Runnable command) {
        handler.rejectedExecution(command, this);
    }

    void onShutdown() {
    }

    /**
     * State check needed by ScheduledThreadPoolExecutor to
     * enable running tasks during shutdown.
     *
     * @param shutdownOK true if should return true if SHUTDOWN
     */
    final boolean isRunningOrShutdown(boolean shutdownOK) {
        int rs = runStateOf(ctl.get());
        return rs == RUNNING || (rs == SHUTDOWN && shutdownOK);
    }


    private List<Runnable> drainQueue() {
        BlockingQueue<Runnable> q = workQueue;
        ArrayList<Runnable> taskList = new ArrayList<Runnable>();
        q.drainTo(taskList);
        if (!q.isEmpty()) {
            for (Runnable r : q.toArray(new Runnable[0])) {
                if (q.remove(r))
                    taskList.add(r);
            }
        }
        return taskList;
    }


    /**
     * 创建线程
     *
     * @param firstTask 任务，即提交给即将创建的线程执行
     * @param core      是否创建核心线程。 true: 根据核心线程数来创建线程，如果线程池中的线程数>=核心线程数，那么就无法创建。 false: 根据最大线程数来创建线程，达到最大线程数同样不允许创建。
     * @return 新增Worker是否成功
     */
    private boolean addWorker(Runnable firstTask, boolean core) {

        //-------------------------------  1 创建线程前的检测工作 -------------------------------------/
        // for 跳出标志
        retry:
        for (; ; ) {

            //------------------------- 1.1 创建线程前，对线程池状态和队列进行检查，判断是否还可以创建线程 ----------------------/

            // 获取线程池状态码
            int c = ctl.get();

            // 获取线程池状态
            int rs = runStateOf(c);

            /**
             *
             * 如果线程池状态范围是：[SHUTDOWN，TERMINATED]，出现下列任一种情况都不允许创建Worker:
             * 1 firstTask != null
             * 2 workQueue 为空
             *
             *小结：
             * 1 线程池处于 SHUTDOWN 状态时，不允许提交任务，但是已经存在的任务需要继续执行。
             *  1.1 当 firstTask == null 时且阻塞队列不为空，说明非提交任务创建线程，执行阻塞队列中的任务，允许创建 Worker
             *  1.2 当 firstTask == null 但阻塞队为空，不能创建 Worker
             *  1.3 当 firstTask ！= null 时，不能创建
             * 2 线程池状态大于 SHUTDOWN 状态时，不允许提交任务，且中断正在执行的任务。
             */
            if (rs >= SHUTDOWN &&
                    !(rs == SHUTDOWN && firstTask == null && !workQueue.isEmpty()))
                return false;


            //---------------------------- 2 创建线程前，对线程池中线程数检查，判断是否还可以创建线程 ---------------------/

            for (; ; ) {
                // 获取线程池线程数
                int wc = workerCountOf(c);

                // 判断线程池线程数是否达到边界值：1 临界值 2 核心线程数或最大线程数
                if (wc >= CAPACITY || wc >= (core ? corePoolSize : maximumPoolSize))
                    return false;

                // 增加线程池中线程数如果成功，则表示创建 Worker 前的校验工作完成，可以进行创建 Worker 流程了。
                if (compareAndIncrementWorkerCount(c))
                    break retry;

                // 增加线程数失败，说明可能其它线程也在尝试创建Worker，就需要回到起点，重新校验。

                //并发影响，需要重新获取线程池状态码
                c = ctl.get();

                // 确定是否其它线程操作导致的 CAS 失败，如果是则需要重新校验。
                if (runStateOf(c) != rs)
                    continue retry;
            }

        }


        //----------------------------------   创建 Worker 流程     ------------------------------------/

        // Worker 中的线程是否启动的标志
        boolean workerStarted = false;

        // Worker 是否添加到 workers 集合中的标志
        boolean workerAdded = false;

        Worker w = null;
        try {

            // 创建 Worker，将任务传入。注意，如果是非提交任务创建Worker的话，firstTask 为null
            w = new Worker(firstTask);

            // 将创建的Worker中的线程临时保存到 t，这个是真正的线程，Worker 只是对线程进行了包装。
            final Thread t = w.thread;

            // Worker 中的线程创建成功
            if (t != null) {

                // 加锁，注意这个锁的粒度是全局的。也就是说，当这里获取到锁，线程池不能关闭，因为线程池关闭也需要锁。
                final ReentrantLock mainLock = this.mainLock;
                mainLock.lock();

                try {

                    // 再次获取线程池状态
                    int rs = runStateOf(ctl.get());

                    // 如果线程池是运行状态，或者是关闭状态且传入的任务为null(不接收新任务，但是会继续执行任务队列中的任务)，符合条件
                    if (rs < SHUTDOWN || (rs == SHUTDOWN && firstTask == null)) {

                        // 提前检查新创建的Worker中的线程是否是启动状态
                        if (t.isAlive())
                            throw new IllegalThreadStateException();

                        // 将新创建的 Worker 加入到 workers 集合。意味着线程池持有线程的引用。
                        workers.add(w);

                        // 更新 largestPoolSize 的值，该值用于追踪线程池中的线程大小
                        int s = workers.size();
                        if (s > largestPoolSize)
                            largestPoolSize = s;

                        // 更新标记值
                        workerAdded = true;
                    }
                } finally {
                    // 全局锁释放，注意全局锁释放的时机
                    mainLock.unlock();
                }

                // 添加到Worker集合后
                if (workerAdded) {
                    // 启动Worker中的线程，这一步的意义重大
                    t.start();
                    // 标记线程启动成功
                    workerStarted = true;
                }
            }
        } finally {
            // 线程启动失败，需要清理工作
            if (!workerStarted)
                addWorkerFailed(w);
        }
        return workerStarted;
    }

    /**
     * Rolls back the worker thread creation.
     * - removes worker from workers, if present
     * - decrements worker count
     * - rechecks for termination, in case the existence of this
     * worker was holding up termination
     */
    private void addWorkerFailed(Worker w) {
        // 获得全局锁
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {

            if (w != null)
                // 从 workers 缓存中移除启动失败的 Worker。意味着线程池不再持有当前Worker 的引用，GC 可以回收。
                workers.remove(w);

            // 减少线程池中线程数，因为在此之前递增了
            decrementWorkerCount();

            // 尝试终止线程池
            tryTerminate();
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * Worker 线程退出
     *
     * @param w
     * @param completedAbruptly
     */
    private void processWorkerExit(Worker w, boolean completedAbruptly) {
        // 线程执行任务抛出了异常
        // todo 注意，如果正常退出，那么在 getTask 方法中会递减线程池中线程数量
        if (completedAbruptly)
            // 减少线程池中线程数量
            decrementWorkerCount();

        // 获取全局锁
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            // 累计线程池完成的任务数量
            completedTaskCount += w.completedTasks;
            // 将线程从缓存集合中删除
            workers.remove(w);
        } finally {
            // 释放全局锁
            mainLock.unlock();
        }

        // 尝试终止线程池
        tryTerminate();

        int c = ctl.get();
        // 如果线程池状态小于 STOP 状态，说明还可以处理任务
        if (runStateLessThan(c, STOP)) {
            // 当前线程处理任务没有出现异常
            if (!completedAbruptly) {
                // 获取核心线程数，如果设置了允许回收核心线程数，则返回 0，否则取核心线程数
                int min = allowCoreThreadTimeOut ? 0 : corePoolSize;
                if (min == 0 && !workQueue.isEmpty())
                    min = 1;
                // 线程池中线程数大于 min ，说明无需创建线程
                if (workerCountOf(c) >= min)
                    return; // replacement not needed
            }
            // 执行到这里说明：线程池中没有线程执行任务队列中的任务，需要创建线程取执行 // todo
            addWorker(null, false);
        }
    }


    /**
     * 从任务队列中获取任务
     * <p>
     * 1. 如果没有设置回收核心线程（corePoolSize 数内的线程），对于核心线程会阻塞直到获取到任务
     * 2. 超时关闭，如果线程符合回收条件，那么在 keepAliveTime 对应的具体时间内都没有任务，应该回收该线程
     * 3. 以下情况，需要返回 null，用以关闭当前线程
     * 1）线程池处于 SHUTDOWN 状态且队列为空
     * 2）线程池处于 STOP 状态，即不接收新的任务也不执行任务队列中的任务
     * 3）线程池中的线程数大于最大线程数
     *
     * @return 返回null 表示可以对当前线程进行回收
     */
    private Runnable getTask() {
        boolean timedOut = false; // Did the last poll() time out?

        for (; ; ) {

            // 获取线程池状态码
            int c = ctl.get();
            // 获取线程池状态
            int rs = runStateOf(c);

            // 线程池关闭且队列为空，应该回收线程。这个条件不仅可以回收非核心线程，也可以回收核心线程。todo 核心线程唯一回收条件
            if (rs >= SHUTDOWN && (rs >= STOP || workQueue.isEmpty())) {
                // 减少线程池中的线程数
                decrementWorkerCount();
                return null;
            }

            // 线程池中的线程数
            int wc = workerCountOf(c);

            // 允许核心线程数内的线程回收，或线程池中的线程数超过了核心线程数，那么有可能发生超时关闭
            boolean timed = allowCoreThreadTimeOut || wc > corePoolSize;

            // 如果线程池中的线程数大于最大线程数或获取任务超时（不设置 allowCoreThreadTimeOut，核心线程没有超时概念），并且任务队列为空，则应该回收当前线程。
            // wc > maximumPoolSize ，可能是执行 setMaximumPoolSize 方法修改了最大值。
            if ((wc > maximumPoolSize || (timed && timedOut)) && (wc > 1 || workQueue.isEmpty())) {
                // 减少工作线程数
                if (compareAndDecrementWorkerCount(c))
                    return null;
                continue;
            }

            try {
                // 从队列中取出任务
                // 注意，真正响应中断是在 poll() 方法或者 take() 方法中
                Runnable r = timed ?
                        // 超时获取任务，因为线程超时要被回收。如果线程在等待的过程发生了中断，会抛出中断异常
                        workQueue.poll(keepAliveTime, TimeUnit.NANOSECONDS) :
                        // 不需要超时
                        workQueue.take();
                if (r != null)
                    return r;

                // 获取任务超时，进行重试
                timedOut = true;

                /**
                 * 1 发生中断异常 {@link ThreadPoolExecutor#setMaximumPoolSize(int)}
                 * maximumPoolSize 设置小了的话，需要将超出的部分线程关掉，即返回 null 然后执行关闭
                 * 2 线程关闭，可能导致当前线程发生中断异常
                 * 3 中断：
                 * 1）interrupt()是用于中断线程的，调用该方法的线程的状态将被置为"中断"状态。注意：线程中断仅仅是设置线程的中断状态位，不会停止线程。需要用户自己去监视线程的状态为并做处理
                 * 2）isInterrupted() 底层调用 isInterrupted(false) 方法，作用于调用该方法的线程对象所对应的线程。
                 * （线程对象对应的线程不一定是当前运行的线程。例如我们可以在A线程中去调用B线程对象的isInterrupted方法。），返回当前调用该方法线程的中断状态，但是不会清除中断标志位
                 * 3）static interrupted() 底层调用 currentThread().isInterrupted(true) 方法，作用于当前线程，返回当前线程的中断状态，但是会清除中断标志位
                 * 4）如果线程在 wait，sleep，join 等时，对该线程进行了中断（调用线程的 interrupt()方法），不仅会清除中断标志位，还会抛出中断异常。
                 * 5）isInterrupted() 和  interrupted() 这两个方法很好区分，只有当前线程才能清除自己的中断位（对应interrupted（）方法）。
                 *
                 *
                 */
            } catch (InterruptedException retry) {
                // 发生中断重置超时标记
                timedOut = false;
            }
        }
    }

    /**
     * 该方法由Worker中的线程启动后调用
     *
     * @param w Worker 对象
     */
    final void runWorker(Worker w) {
        // 当前线程，即 w 中的线程
        Thread wt = Thread.currentThread();
        // 获取该线程的第一个任务，可能没有。如果有的话，优先执行该任务。
        Runnable task = w.firstTask;
        w.firstTask = null;

        // 允许中断，在关闭线程池的时候会中断（空闲的）线程
        w.unlock(); // allow interrupts

        boolean completedAbruptly = true;
        try {
            // 循环调用getTask() 方法从任务队列中获取任务并执行
            while (task != null || (task = getTask()) != null) { // todo execute 和 submit 执行不一样，submit 会包装任务类

                /*
                 * 特别说明：
                 * 已经通过 getTask() 取出来的任务会正常运行，不管什么中断、shutdown、stop，除非断电了。
                 */

                // 上锁
                w.lock();

                /**
                 * 出现以下任何一种情况都需要中断线程：
                 * 1 如果线程池状态大于等于 STOP，并且当前线程没有被中断
                 * 2 如果当前线程被中断了并且线程池状态大于等于 STOP 状态（恢复中断标识）
                 */
                if ((runStateAtLeast(ctl.get(), STOP) ||
                        (Thread.interrupted() && runStateAtLeast(ctl.get(), STOP))) && !wt.isInterrupted())
                    // 中断当前线程
                    wt.interrupt();

                try {
                    // ThreadPoolExecutor 的扩展方法
                    beforeExecute(wt, task);

                    Throwable thrown = null;
                    try {

                        // 执行目标任务,方法级别调用。
                        task.run();

                    } catch (RuntimeException x) {
                        thrown = x;
                        throw x;
                    } catch (Error x) {
                        thrown = x;
                        throw x;
                    } catch (Throwable x) {
                        thrown = x;
                        throw new Error(x);
                    } finally {
                        // ThreadPoolExecutor 的扩展方法
                        afterExecute(task, thrown);
                    }
                } finally {
                    // 置空 task,为下一个任务做准备
                    task = null;
                    // 更新完成任务数
                    w.completedTasks++;
                    // 释放Worker 独占锁
                    w.unlock();
                }
            }
            // while 循环没有出现异常，completedAbruptly 才会被设置为 false
            completedAbruptly = false;
        } finally {
            /**
             * 线程退出 while 循环后需要进行回收，可能情况如下：
             * 1 线程池关闭且任务队列中已经没有要执行的任务了
             * 2 线程池 Stop 停止了
             * 3 任务执行过程出现异常
             */
            processWorkerExit(w, completedAbruptly);
        }
    }


    /**
     * 线程池执行方法最终都依赖该方法
     *
     * @param command
     */
    public void execute(Runnable command) {
        // 任务体不允许为 null
        if (command == null)
            throw new NullPointerException();

        // 获取线程池的状态码。该值包含了线程池的状态和线程数
        int c = ctl.get();

        // 1 如果当前线程数少于核心线程数，则创建一个 Worker 来执行任务，即创建一个线程并将 command 作为该线程的第一个任务
        if (workerCountOf(c) < corePoolSize) {
            // 创建线程并启动成功即结束。否则，执行下一步。
            // 返回 false 说明线程池不允许创建线程，可能原因：（1）线程池关闭（2）当前线程数已经达到临界值
            if (addWorker(command, true))
                return;
            // 创建失败，再次更新线程池状态码
            c = ctl.get();
        }

        //--- 由 1知：当线程数小于核心线程数时，是无条件开新的线程。当线程池数大于等于核心线程数时，将任务添加到任务队列中  ---/


        // 2 如果线程池处于运行状态，则尝试将任务添加到阻塞队列 workQueue 中
        if (isRunning(c) && workQueue.offer(command)) {
            // 再次获取线程池状态码
            int recheck = ctl.get();
            // 双重检查，再次判断线程池状态。如果线程状态变了（非运行状态）就需要从阻塞队列移除任务，同时执行拒绝策略。防止线程池关闭。
            if (!isRunning(recheck) && remove(command))
                reject(command);

                // 如果线程池状态仍然是运行状态，并且线程池为空则创建一个非核心线程来执行任务，防止线程提交到阻塞队列后线程都关闭了。
            else if (workerCountOf(recheck) == 0)
                addWorker(null, false);

            //---- 第 2 步中的双重判断的目的是防止任务添加到阻塞队列中线程池关闭了或者没有线程执行就立即创建非核心线程


            // 3 如果任务队列满了，则根据 maximumPoolSize 创建非核心线程。如果创建失败，说明当前线程数已经达到 maximumPoolSize 或线程池关闭，需要执行拒绝策略
        } else if (!addWorker(command, false))
            reject(command);
    }


    /**
     * 关闭线程池
     */
    public void shutdown() {
        // 全局锁
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            checkShutdownAccess();
            // 设置线程池状态为 SHUTDOWN
            advanceRunState(SHUTDOWN);
            // 尝试中断线程池中闲置的线程
            interruptIdleWorkers();
            // hook
            onShutdown(); // hook for ScheduledThreadPoolExecutor
        } finally {
            mainLock.unlock();
        }

        // 尝试终止线程池
        tryTerminate();
    }


    /**
     * 立即关闭线程池
     *
     * @return
     */
    public List<Runnable> shutdownNow() {
        List<Runnable> tasks;
        // 全局锁
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            checkShutdownAccess();
            // 设置线程池状态为 STOP
            advanceRunState(STOP);
            // 尝试中断线程池中所有启动状态的线程
            // 没有启动状态的线程，不会去中断，通过 Worker 中的 同步状态 state >= 0 判断
            interruptWorkers();
            // 将阻塞队列中正在等待的所有任务进行备份，然后清空阻塞队列并返回备份。有了这个备份，可以根据需要做补救措施。
            tasks = drainQueue();
        } finally {
            mainLock.unlock();
        }

        // 尝试终止线程池
        tryTerminate();

        return tasks;
    }

    public boolean isShutdown() {
        return !isRunning(ctl.get());
    }


    public boolean isTerminating() {
        int c = ctl.get();
        return !isRunning(c) && runStateLessThan(c, TERMINATED);
    }

    public boolean isTerminated() {
        return runStateAtLeast(ctl.get(), TERMINATED);
    }

    public boolean awaitTermination(long timeout, TimeUnit unit)
            throws InterruptedException {
        long nanos = unit.toNanos(timeout);
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            for (; ; ) {
                if (runStateAtLeast(ctl.get(), TERMINATED))
                    return true;
                if (nanos <= 0)
                    return false;
                nanos = termination.awaitNanos(nanos);
            }
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * Invokes {@code shutdown} when this executor is no longer
     * referenced and it has no threads.
     */
    protected void finalize() {
        SecurityManager sm = System.getSecurityManager();
        if (sm == null || acc == null) {
            shutdown();
        } else {
            PrivilegedAction<Void> pa = () -> {
                shutdown();
                return null;
            };
            AccessController.doPrivileged(pa, acc);
        }
    }

    /**
     * Sets the thread factory used to create new threads.
     *
     * @param threadFactory the new thread factory
     * @throws NullPointerException if threadFactory is null
     * @see #getThreadFactory
     */
    public void setThreadFactory(ThreadFactory threadFactory) {
        if (threadFactory == null)
            throw new NullPointerException();
        this.threadFactory = threadFactory;
    }

    /**
     * Returns the thread factory used to create new threads.
     *
     * @return the current thread factory
     * @see #setThreadFactory(ThreadFactory)
     */
    public ThreadFactory getThreadFactory() {
        return threadFactory;
    }

    /**
     * Sets a new handler for unexecutable tasks.
     *
     * @param handler the new handler
     * @throws NullPointerException if handler is null
     * @see #getRejectedExecutionHandler
     */
    public void setRejectedExecutionHandler(RejectedExecutionHandler handler) {
        if (handler == null)
            throw new NullPointerException();
        this.handler = handler;
    }

    /**
     * Returns the current handler for unexecutable tasks.
     *
     * @return the current handler
     * @see #setRejectedExecutionHandler(RejectedExecutionHandler)
     */
    public RejectedExecutionHandler getRejectedExecutionHandler() {
        return handler;
    }

    /**
     * 设置核心参数
     *
     * @param corePoolSize
     */
    public void setCorePoolSize(int corePoolSize) {
        if (corePoolSize < 0)
            throw new IllegalArgumentException();
        // 计算核心线程数变化值
        int delta = corePoolSize - this.corePoolSize;

        // 覆盖原来的corePoolSize
        this.corePoolSize = corePoolSize;

        //线程池的线程数大于变更的核心线程数，说明有多余的worker线程，此时会向空闲的worker线程发起中断请求以实现回收
        if (workerCountOf(ctl.get()) > corePoolSize)
            interruptIdleWorkers();

            // 核心线程数大于原来值，尝试增加核心线程
        else if (delta > 0) {

            // 取 任务数和 delta 两者的最小值
            int k = Math.min(delta, workQueue.size());

            // 预先创建足够多的新Worker以达到核心线程数，并处理队列中的任务。队列空了则停止
            while (k-- > 0 && addWorker(null, true)) {
                if (workQueue.isEmpty())
                    break;
            }
        }
    }


    public int getCorePoolSize() {
        return corePoolSize;
    }

    public boolean prestartCoreThread() {
        return workerCountOf(ctl.get()) < corePoolSize &&
                addWorker(null, true);
    }

    /**
     * Same as prestartCoreThread except arranges that at least one
     * thread is started even if corePoolSize is 0.
     */
    void ensurePrestart() {
        // 计算线程池中线程数量
        int wc = workerCountOf(ctl.get());

        // 没有达到核心线程，就继续创建核心线程
        if (wc < corePoolSize)
            addWorker(null, true);

        // 线程池为 0 ，创建非核心线程
        else if (wc == 0)
            addWorker(null, false);
    }


    public int prestartAllCoreThreads() {
        int n = 0;
        while (addWorker(null, true))
            ++n;
        return n;
    }

    public boolean allowsCoreThreadTimeOut() {
        return allowCoreThreadTimeOut;
    }


    /**
     * 设置允许回收核心线程
     *
     * @param value
     */
    public void allowCoreThreadTimeOut(boolean value) {
        // 核心线程必须要有保活时间
        if (value && keepAliveTime <= 0)
            throw new IllegalArgumentException("Core threads must have nonzero keep alive times");

        if (value != allowCoreThreadTimeOut) {
            allowCoreThreadTimeOut = value;
            // 允许回收则立即中断空闲线程
            if (value)
                interruptIdleWorkers();
        }
    }

    /**
     * 设置最大线程数
     *
     * @param maximumPoolSize
     */
    public void setMaximumPoolSize(int maximumPoolSize) {
        if (maximumPoolSize <= 0 || maximumPoolSize < corePoolSize)
            throw new IllegalArgumentException();

        // 覆盖原来的 maximumPoolSize
        this.maximumPoolSize = maximumPoolSize;

        // 如果是设置小了的话，此时会向空闲的worker线程发起中断请求以实现回收
        if (workerCountOf(ctl.get()) > maximumPoolSize)
            interruptIdleWorkers();
    }

    /**
     * Returns the maximum allowed number of threads.
     *
     * @return the maximum allowed number of threads
     * @see #setMaximumPoolSize
     */
    public int getMaximumPoolSize() {
        return maximumPoolSize;
    }

    /**
     * 设置超时回收时间
     *
     * @param time
     * @param unit
     */
    public void setKeepAliveTime(long time, TimeUnit unit) {
        if (time < 0)
            throw new IllegalArgumentException();

        if (time == 0 && allowsCoreThreadTimeOut())
            throw new IllegalArgumentException("Core threads must have nonzero keep alive times");
        // 计算超时时间
        long keepAliveTime = unit.toNanos(time);
        // 计算差值
        long delta = keepAliveTime - this.keepAliveTime;
        // 覆盖原来的 keepAliveTime
        this.keepAliveTime = keepAliveTime;
        // 如果时间设置比原来小，则向空闲的worker线程发起中断请求以实现回收
        if (delta < 0)
            interruptIdleWorkers();
    }


    public long getKeepAliveTime(TimeUnit unit) {
        return unit.convert(keepAliveTime, TimeUnit.NANOSECONDS);
    }

    /* User-level queue utilities */

    /**
     * Returns the task queue used by this executor. Access to the
     * task queue is intended primarily for debugging and monitoring.
     * This queue may be in active use.  Retrieving the task queue
     * does not prevent queued tasks from executing.
     *
     * @return the task queue
     */
    public BlockingQueue<Runnable> getQueue() {
        return workQueue;
    }


    public boolean remove(Runnable task) {
        boolean removed = workQueue.remove(task);
        tryTerminate(); // In case SHUTDOWN and now empty
        return removed;
    }


    public void purge() {
        final BlockingQueue<Runnable> q = workQueue;
        try {
            Iterator<Runnable> it = q.iterator();
            while (it.hasNext()) {
                Runnable r = it.next();
                if (r instanceof Future<?> && ((Future<?>) r).isCancelled())
                    it.remove();
            }
        } catch (ConcurrentModificationException fallThrough) {
            // Take slow path if we encounter interference during traversal.
            // Make copy for traversal and call remove for cancelled entries.
            // The slow path is more likely to be O(N*N).
            for (Object r : q.toArray())
                if (r instanceof Future<?> && ((Future<?>) r).isCancelled())
                    q.remove(r);
        }

        tryTerminate(); // In case SHUTDOWN and now empty
    }

    /* Statistics */

    /**
     * Returns the current number of threads in the pool.
     *
     * @return the number of threads
     */
    public int getPoolSize() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            // Remove rare and surprising possibility of
            // isTerminated() && getPoolSize() > 0
            return runStateAtLeast(ctl.get(), TIDYING) ? 0
                    : workers.size();
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * Returns the approximate number of threads that are actively
     * executing tasks.
     *
     * @return the number of threads
     */
    public int getActiveCount() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            int n = 0;
            for (Worker w : workers)
                if (w.isLocked())
                    ++n;
            return n;
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * Returns the largest number of threads that have ever
     * simultaneously been in the pool.
     *
     * @return the number of threads
     */
    public int getLargestPoolSize() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            return largestPoolSize;
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * Returns the approximate total number of tasks that have ever been
     * scheduled for execution. Because the states of tasks and
     * threads may change dynamically during computation, the returned
     * value is only an approximation.
     *
     * @return the number of tasks
     */
    public long getTaskCount() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            long n = completedTaskCount;
            for (Worker w : workers) {
                n += w.completedTasks;
                if (w.isLocked())
                    ++n;
            }
            return n + workQueue.size();
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * Returns the approximate total number of tasks that have
     * completed execution. Because the states of tasks and threads
     * may change dynamically during computation, the returned value
     * is only an approximation, but one that does not ever decrease
     * across successive calls.
     *
     * @return the number of tasks
     */
    public long getCompletedTaskCount() {
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            long n = completedTaskCount;
            for (Worker w : workers)
                n += w.completedTasks;
            return n;
        } finally {
            mainLock.unlock();
        }
    }

    /**
     * Returns a string identifying this pool, as well as its state,
     * including indications of run state and estimated worker and
     * task counts.
     *
     * @return a string identifying this pool, as well as its state
     */
    public String toString() {
        long ncompleted;
        int nworkers, nactive;
        final ReentrantLock mainLock = this.mainLock;
        mainLock.lock();
        try {
            ncompleted = completedTaskCount;
            nactive = 0;
            nworkers = workers.size();
            for (Worker w : workers) {
                ncompleted += w.completedTasks;
                if (w.isLocked())
                    ++nactive;
            }
        } finally {
            mainLock.unlock();
        }
        int c = ctl.get();
        String rs = (runStateLessThan(c, SHUTDOWN) ? "Running" :
                (runStateAtLeast(c, TERMINATED) ? "Terminated" :
                        "Shutting down"));
        return super.toString() +
                "[" + rs +
                ", pool size = " + nworkers +
                ", active threads = " + nactive +
                ", queued tasks = " + workQueue.size() +
                ", completed tasks = " + ncompleted +
                "]";
    }


    protected void beforeExecute(Thread t, Runnable r) {
    }


    protected void afterExecute(Runnable r, Throwable t) {
    }

    protected void terminated() {
    }

    //----------------------------------------- 拒绝策略（饱和策略） -----------------------------------------/


    /**
     * 直接抛出异常（默认策略）
     */
    public static class AbortPolicy implements RejectedExecutionHandler {
        public AbortPolicy() {
        }

        /**
         * 直接抛出异常
         *
         * @param r 任务
         * @param e te
         */
        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
            throw new RejectedExecutionException("Task " + r.toString() +
                    " rejected from " +
                    e.toString());
        }
    }

    /**
     * 由提交任务的线程自己来执行任务
     */
    public static class CallerRunsPolicy implements RejectedExecutionHandler {
        public CallerRunsPolicy() {
        }

        /**
         * 只要线程池没有被关闭，就由提交任务的线程自己来执行这个任务。
         *
         * @param r
         * @param e
         */
        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
            // 线程池没有关闭
            if (!e.isShutdown()) {
                // 方法级别调用
                r.run();
            }
        }
    }

    /**
     * 直接忽略任务
     */
    public static class DiscardPolicy implements RejectedExecutionHandler {
        public DiscardPolicy() {
        }

        /**
         * 直接忽略
         *
         * @param r
         * @param e
         */
        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
        }
    }

    /**
     * 将阻塞队列头的任务扔掉，然后将当前任务提交到线程池尝试执行。
     */
    public static class DiscardOldestPolicy implements RejectedExecutionHandler {
        public DiscardOldestPolicy() {
        }

        /**
         * 将队列都任务移除，并将当前任务提交到线程池
         *
         * @param r
         * @param e
         */
        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
            if (!e.isShutdown()) {
                e.getQueue().poll();
                e.execute(r);
            }
        }
    }
}
