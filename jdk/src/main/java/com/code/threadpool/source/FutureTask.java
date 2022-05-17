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

import java.util.concurrent.locks.LockSupport;

/**
 * A cancellable asynchronous computation.
 * <p>
 * 可取消的异步计算
 * <p>
 * This class provides a base implementation of {@link Future}, with methods to start and cancel
 * a computation, query to see if the computation is complete, and
 * retrieve the result of the computation.
 * <p>
 * 此类提供 {@link Future} 的基本实现，具有启动和取消任务、查询任务是否完成以及检索任务执行结果的方法。
 * <p>
 * <p>
 * The result can only be
 * retrieved when the computation has completed; the {@code get}
 * methods will block if the computation has not yet completed.  Once
 * the computation has completed, the computation cannot be restarted
 * or cancelled (unless the computation is invoked using
 * {@link #runAndReset}).
 * <p>
 * 只有任务终结后才能获取结果，如果任务尚未终结，那么获取结果的方法将阻塞。
 * 一旦任务终结，就不能重新开始或取消任务，除非使用 runAndRest 方法。
 *
 *
 * <p>A {@code FutureTask} can be used to wrap a {@link Callable} or
 * {@link Runnable} object.  Because {@code FutureTask} implements
 * {@code Runnable}, a {@code FutureTask} can be submitted to an
 * {@link Executor} for execution.
 * <p>
 * FutureTask 用于包装 Callable 或 Runnable 对象，所以可以将 FutureTask 提交给 Executor 执行。
 *
 * <p>In addition to serving as a standalone class, this class provides
 * {@code protected} functionality that may be useful when creating
 * customized task classes.
 *
 * @param <V> The result type returned by this FutureTask's {@code get} methods
 * @author Doug Lea
 * @since 1.5
 */

/*
 * 1. Future 表示异步计算的结果，它提供了检查任务是否完成、等待任务完成、获取任务结果、以及取消任务等方法；
 * 2. 任务结果只能在任务终结后获取，必要时阻塞，直到任务准备好。任务一旦终结就取消不了了。
 */

public class FutureTask<V> implements RunnableFuture<V> {


    /**
     * The run state of this task, initially NEW.
     * <p>
     * 任务状态，初始化是 NEW 状态
     * <p>
     * The run state transitions to a terminal state only in methods set,
     * setException, and cancel.  During completion, state may take on
     * transient values of COMPLETING (while outcome is being set) or
     * INTERRUPTING (only while interrupting the runner to satisfy a
     * cancel(true)).
     * <p>
     * 运行状态仅在 set、setException 和 cancel 方法中转换为终端状态。在完成期间，
     * 状态可能会采用 COMPLETING（设置结果时）或 INTERRUPTING（仅在中断运行器以满足取消（true）时）的瞬态值。
     * <p>
     * Transitions from these intermediate to final
     * states use cheaper ordered/lazy writes because values are unique
     * and cannot be further modified.
     * <p>
     * 从这些中间状态到最终状态的转换使用更便宜的有序惰性写入，因为值是唯一的，不能进一步修改。
     *
     *
     * <p>
     * Possible state transitions:
     * 可能的状态转换
     * <p>
     * 任务正常运行结束：NEW -> COMPLETING -> NORMAL
     * 任务异常运行结束：NEW -> COMPLETING -> EXCEPTIONAL
     * 任务取消：NEW -> CANCELLED
     * 任务被中断：NEW -> INTERRUPTING -> INTERRUPTED
     * <p>
     * state 状态控制任务的运行过程
     */
    private volatile int state;

    private static final int NEW = 0; // 新建状态
    private static final int COMPLETING = 1; // 执行中间态
    private static final int NORMAL = 2;     // 完成
    private static final int EXCEPTIONAL = 3; // 异常
    private static final int CANCELLED = 4; // 取消
    private static final int INTERRUPTING = 5; // 中断中
    private static final int INTERRUPTED = 6; // 中断

    /**
     * 任务体。如果是 Runnable 类型，则会对Runnable进行适配转为 Callable 类型
     */
    private Callable<V> callable;

    /**
     * The result to return or exception to throw from get()
     * <p>
     * 执行结果
     * <p>
     * 从 get() 返回的结果或抛出的异常
     */
    private Object outcome;

    /**
     * The thread running the callable; CASed during run()
     * <p>
     * 执行任务的线程，一般是线程池中的某个线程
     */
    private volatile Thread runner;

    /**
     * Treiber stack of waiting threads
     * <p>
     * 等待结果的线程节点，即调用者线程组成的链表
     */
    private volatile WaitNode waiters;


    /**
     * Returns result or throws exception for completed task.
     * <p>
     * 为已完成的任务返回结果或抛出异常。
     *
     * @param s completed state value
     */
    @SuppressWarnings("unchecked")
    private V report(int s) throws ExecutionException {
        // 任务执行的结果或异常
        Object x = outcome;

        // 如果任务正常运行结束，返回结果
        if (s == NORMAL)
            return (V) x;

        // 如果任务被取消或被中断，则抛出任务取消异常
        if (s >= CANCELLED)
            throw new CancellationException();

        // 任务异常运行
        throw new ExecutionException((Throwable) x);
    }

    /**
     * 创建任务
     * <p>
     * Creates a {@code FutureTask} that will, upon running, execute the
     * given {@code Callable}.
     *
     * @param callable the callable task 可调用任务
     * @throws NullPointerException if the callable is null
     */
    public FutureTask(Callable<V> callable) {
        // 任务不允许为空
        if (callable == null)
            throw new NullPointerException();

        // 保存到属性中
        this.callable = callable;

        // 异步计算任务状态初始化为新建状态
        this.state = NEW;       // ensure visibility of callable
    }

    /**
     * 创建任务，需要将 Runnable 类型包装成 Callable 类型
     * <p>
     * Creates a {@code FutureTask} that will, upon running, execute the
     * given {@code Runnable}, and arrange that {@code get} will return the
     * given result on successful completion.
     *
     * @param runnable the runnable task 可运行任务
     * @param result   the result to return on successful completion. If
     *                 you don't need a particular result, consider using
     *                 constructions of the form:
     *                 {@code Future<?> f = new FutureTask<Void>(runnable, null)}
     *                 成功完成后返回的结果。如果不需要特定结果，传入 null
     * @throws NullPointerException if the runnable is null
     */
    public FutureTask(Runnable runnable, V result) {
        // 1 将 Runnable 类型任务适配成 Callable 类型，并保存到属性中
        this.callable = Executors.callable(runnable, result);

        // 2 异步计算任务状态初始化为新建状态
        this.state = NEW;       // ensure visibility of callable
    }

    /**
     * 判断任务是否取消
     *
     * @return
     */
    public boolean isCancelled() {
        return state >= CANCELLED;
    }

    /**
     * 判断任务是否执行
     *
     * @return
     */
    public boolean isDone() {
        return state != NEW;
    }

    /**
     * 取消任务，可能是中断，也可能是取消
     *
     * @param mayInterruptIfRunning 是否中断
     * @return
     */
    public boolean cancel(boolean mayInterruptIfRunning) {
        // 取消任务的条件：任务状态为 NEW && 更新任务状态 NEW->INTERRUPTING 或者 NEW->CANCELLED 成功
        if (!(state == NEW &&
                UNSAFE.compareAndSwapInt(this, stateOffset, NEW,
                        mayInterruptIfRunning ? INTERRUPTING : CANCELLED)))
            return false;


        try {    // in case call to interrupt throws exception
            // 如果中断线程
            if (mayInterruptIfRunning) {
                try {
                    Thread t = runner;
                    if (t != null)
                        // 中断线程
                        t.interrupt();
                } finally { // final state
                    UNSAFE.putOrderedInt(this, stateOffset, INTERRUPTED);
                }
            }

        } finally {
            // 完结任务
            finishCompletion();
        }
        return true;
    }

    /**
     * 获得任务的执行结果，如果任务未执行完毕，会阻塞直到任务结束；
     *
     * @throws CancellationException {@inheritDoc}
     */
    public V get() throws InterruptedException, ExecutionException {
        // 任务执行状态
        int s = state;

        // 如果任务还没可能完成，则进入等待链表中等待
        if (s <= COMPLETING)
            s = awaitDone(false, 0L);


        // 返回任务的结果或异常
        return report(s);
    }

    /**
     * 超时获取执行结果
     *
     * @throws CancellationException {@inheritDoc}
     */
    public V get(long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        if (unit == null)
            throw new NullPointerException();

        // 获取任务的状态
        int s = state;

        // 如果任务还没有完成，则超时等待（进入等待链表中），超时或有结果了会醒来
        if (s <= COMPLETING &&
                (s = awaitDone(true, unit.toNanos(timeout))) <= COMPLETING)
            throw new TimeoutException();

        // 结果
        return report(s);
    }

    /**
     * Protected method invoked when this task transitions to state
     * {@code isDone} (whether normally or via cancellation). The
     * default implementation does nothing.  Subclasses may override
     * this method to invoke completion callbacks or perform
     * bookkeeping. Note that you can query status inside the
     * implementation of this method to determine whether this task
     * has been cancelled.
     */
    protected void done() {
    }

    /**
     * 设置结果。该方法是在任务执行 run 方法时，将执行结果设置进来。
     * Sets the result of this future to the given value unless
     * this future has already been set or has been cancelled.
     *
     * <p>This method is invoked internally by the {@link #run} method
     * upon successful completion of the computation.
     *
     * @param v the value
     */
    protected void set(V v) {
        // 将任务状态 NEW 更新为 COMPLETING
        if (UNSAFE.compareAndSwapInt(this, stateOffset, NEW, COMPLETING)) {

            // 结果设置为传入的值
            // 该属性是 get 方法返回的
            outcome = v;

            // 设置任务最终的状态为  NORMAL
            UNSAFE.putOrderedInt(this, stateOffset, NORMAL); // final state

            // 处理任务完成的后续工作
            finishCompletion();
        }
    }

    /**
     * Causes this future to report an {@link ExecutionException}
     * with the given throwable as its cause, unless this future has
     * already been set or has been cancelled.
     *
     * <p>This method is invoked internally by the {@link #run} method
     * upon failure of the computation.
     *
     * @param t the cause of failure
     */
    protected void setException(Throwable t) {
        // 将任务状态 NEW 更新为 COMPLETING
        if (UNSAFE.compareAndSwapInt(this, stateOffset, NEW, COMPLETING)) {

            // 结果设置为异常信息
            // 该属性是 get 方法返回的
            outcome = t;

            // 设置任务最终的状态为 EXCEPTIONAL
            UNSAFE.putOrderedInt(this, stateOffset, EXCEPTIONAL); // final state

            // 处理任务完成的后续工作
            finishCompletion();
        }
    }

    /**
     * 任务体执行，方法级别的调用
     */
    public void run() {
        // 判断任务是否可以执行
        // 任务状态不为 NEW 或 设置执行该任务的线程（也就是 runner 属性）失败 ，那么不能执行该任务
        if (state != NEW ||
                !UNSAFE.compareAndSwapObject(this, runnerOffset,
                        null, Thread.currentThread()))
            return;

        try {
            // 真正的任务，这里统一是 Callable 类型，Runnable 被适配成了 Callable 类型
            Callable<V> c = callable;

            // 任务状态必须是 NEW 才能运行
            if (c != null && state == NEW) {
                // 任务执行结果
                V result;
                // 任务是否执行成功
                boolean ran;
                try {
                    // 执行任务
                    result = c.call();
                    ran = true;

                    // 出现异常
                } catch (Throwable ex) {
                    result = null;
                    ran = false;
                    // 处理异常结果并更新任务状态
                    setException(ex);
                }

                // 任务执行成功，设置结果并更新任务状态
                if (ran)
                    set(result);
            }
        } finally {
            // 任务处理完成后，将 runner 置 null ，防止并发调用 run()
            // runner must be non-null until state is settled to prevent concurrent calls to run()
            runner = null;

            // 重新读取状态以防止漏掉中断的处理「想一下这种场景，线程在执行 c.call() 方法时被中断了」
            // state must be re-read after nulling runner to prevent leaked interrupts
            int s = state;
            if (s >= INTERRUPTING)
                // 处理可能因取消时进行的中断，如果处于中断中，等待中断者完成即可
                handlePossibleCancellationInterrupt(s);
        }
    }

    /**
     * Executes the computation without setting its result, and then
     * resets this future to initial state, failing to do so if the
     * computation encounters an exception or is cancelled.  This is
     * designed for use with tasks that intrinsically execute more
     * than once.
     * <p>
     * 执行计算而不设置其结果，然后将此未来任务重置为初始状态，如果计算遇到异常或被取消则失败。这设计用于本质上执行不止一次的任务。
     *
     * @return {@code true} if successfully run and reset
     */
    protected boolean runAndReset() {
        // 判断任务是否已经开始执行，没有开始执行，那么设置执行该任务的线程，也就是 runner 属性
        if (state != NEW ||
                !UNSAFE.compareAndSwapObject(this, runnerOffset,
                        null, Thread.currentThread()))
            return false;

        // 任务执行成功
        boolean ran = false;
        int s = state;
        try {
            Callable<V> c = callable;
            // 执行任务
            if (c != null && s == NEW) {
                try {
                    // 执行任务，但不设置结果，也不更新完成状态「任务可以重复执行」
                    c.call(); // don't set result
                    ran = true;

                    // 异常情况，设置异常并更新完成状态「任务不能再执行」
                } catch (Throwable ex) {
                    setException(ex);
                }
            }

        } finally {
            // 任务处理完成后，将 runner 置 null ，防止并发调用 run()
            // runner must be non-null until state is settled to prevent concurrent calls to run()
            runner = null;

            // 重新读取状态以防止漏掉中断的处理「想一下这种场景，线程在执行 c.call() 方法时被中断了」
            // state must be re-read after nulling runner to prevent leaked interrupts
            s = state;

            // 处理可能因取消时进行的中断，如果处于中断中，等待中断者完成即可
            if (s >= INTERRUPTING)
                handlePossibleCancellationInterrupt(s);
        }

        // 成功运行并重置
        return ran && s == NEW;
    }

    /**
     * Ensures that any interrupt from a possible cancel(true) is only
     * delivered to a task while in run or runAndReset.
     * <p>
     * 确保来自可能的 cancel(true) 的任何中断仅在运行或 runAndReset 时传递给任务
     */
    private void handlePossibleCancellationInterrupt(int s) {
        // It is possible for our interrupter to stall before getting a
        // chance to interrupt us.  Let's spin-wait patiently.
        // 等待中断者完成中断，这里让出 CPU 等待
        if (s == INTERRUPTING)
            while (state == INTERRUPTING)
                Thread.yield(); // wait out pending interrupt  等待挂起的中断

        // assert state == INTERRUPTED;

        // We want to clear any interrupt we may have received from
        // cancel(true).  However, it is permissible to use interrupts
        // as an independent mechanism for a task to communicate with
        // its caller, and there is no way to clear only the
        // cancellation interrupt.
        //
        // Thread.interrupted();
    }

    /**
     * Simple linked list nodes to record waiting threads in a Treiber stack.
     * <p>
     * 等待任务结果的线程的简单链表节点。
     * <p>
     * See other classes such as Phaser and SynchronousQueue
     * for more detailed explanation.
     */
    static final class WaitNode {
        /**
         * 等待任务结果的线程
         */
        volatile Thread thread;
        /**
         * 后置指针
         */
        volatile WaitNode next;

        /**
         * 创建线程节点
         */
        WaitNode() {
            thread = Thread.currentThread();
        }
    }

    /**
     * Removes and signals all waiting threads, invokes done(), and
     * nulls out callable.
     * <p>
     * 移除所有等待结果的线程并发出信号，调用 done()，并清空可调用的线程。
     */
    private void finishCompletion() {
        // assert state > COMPLETING;
        // 如果线程等待队列不为空（这个线程队列实际上是调用者线程）
        for (WaitNode q; (q = waiters) != null; ) {
            // 置空队列
            if (UNSAFE.compareAndSwapObject(this, waitersOffset, q, null)) {
                // 唤醒并从等待列表中移除等待结果的节点
                for (; ; ) {
                    Thread t = q.thread;
                    if (t != null) {
                        q.thread = null;
                        // 唤醒线程 t
                        LockSupport.unpark(t);
                    }
                    WaitNode next = q.next;
                    if (next == null)
                        break;
                    q.next = null; // unlink to help gc
                    q = next;
                }
                break;
            }
        }

        // 执行钩子方法
        done();

        // 任务体完成后置空
        callable = null;        // to reduce footprint
    }

    /**
     * Awaits completion or aborts on interrupt or timeout.
     * <p>
     * 在中断或超时时等待完成或中止
     *
     * @param timed true if use timed waits 是否超时等待
     * @param nanos time to wait, if timed 等待时间长，纳秒
     * @return state upon completion
     */
    private int awaitDone(boolean timed, long nanos)
            throws InterruptedException {
        // 等待的截止时间，非超时就是 0
        final long deadline = timed ? System.nanoTime() + nanos : 0L;

        WaitNode q = null;
        boolean queued = false;
        for (; ; ) {
            // 如果获取结果的线程被中断，则尝试移除节点 q，然后抛出中断异常
            if (Thread.interrupted()) {
                removeWaiter(q);
                throw new InterruptedException();
            }

            // 任务状态
            int s = state;

            // 如果任务状态 > COMPLETING，那么可以返回了，此时能拿到最终的结果或异常「参考 report 中的逻辑」。
            // todo 这里是自旋出口
            if (s > COMPLETING) {
                if (q != null)
                    q.thread = null;
                return s;

                // 如果任务状态处于 COMPLETING 状态，说明任务快完成了，就差设置状态到NORMAL或EXCEPTIONAL和设置结果了
            } else if (s == COMPLETING) // cannot time out yet 还不能超时
                // 让出 CPU，优先完成任务「调用线程不占用CPU了，让任务尽快完成」
                Thread.yield();

                // 创建线程节点，节点中记录调用者线程
            else if (q == null)
                q = new WaitNode();

                // 将线程节点 q 加入到等待链表中
            else if (!queued)
                // CAS 的链表头插法
                queued = UNSAFE.compareAndSwapObject(this, waitersOffset,
                        q.next = waiters, q);

                // 超时获取结果
            else if (timed) {
                // 计算是否超时
                nanos = deadline - System.nanoTime();
                if (nanos <= 0L) {
                    // 超时了，则从等待链表中移除节点 q，并返回 state
                    removeWaiter(q);
                    return state;
                }

                // 超时阻塞线程
                LockSupport.parkNanos(this, nanos);

                // 阻塞式获取结果，阻塞当前线程（调用者线程）
            } else
                // 阻塞线程
                LockSupport.park(this);
        }
    }

    /**
     * Tries to unlink a timed-out or interrupted wait node to avoid
     * accumulating garbage.  Internal nodes are simply unspliced
     * without CAS since it is harmless if they are traversed anyway
     * by releasers.  To avoid effects of unsplicing from already
     * removed nodes, the list is retraversed in case of an apparent
     * race.  This is slow when there are a lot of nodes, but we don't
     * expect lists to be long enough to outweigh higher-overhead
     * schemes.
     */
    private void removeWaiter(WaitNode node) {
        if (node != null) {
            node.thread = null;
            retry:
            for (; ; ) {          // restart on removeWaiter race
                for (WaitNode pred = null, q = waiters, s; q != null; q = s) {
                    s = q.next;
                    if (q.thread != null)
                        pred = q;
                    else if (pred != null) {
                        pred.next = s;
                        if (pred.thread == null) // check for race
                            continue retry;
                    } else if (!UNSAFE.compareAndSwapObject(this, waitersOffset,
                            q, s))
                        continue retry;
                }
                break;
            }
        }
    }

    // Unsafe mechanics
    private static final sun.misc.Unsafe UNSAFE;
    private static final long stateOffset;
    private static final long runnerOffset;
    private static final long waitersOffset;

    static {
        try {
            UNSAFE = sun.misc.Unsafe.getUnsafe();
            Class<?> k = FutureTask.class;
            stateOffset = UNSAFE.objectFieldOffset
                    (k.getDeclaredField("state"));
            runnerOffset = UNSAFE.objectFieldOffset
                    (k.getDeclaredField("runner"));
            waitersOffset = UNSAFE.objectFieldOffset
                    (k.getDeclaredField("waiters"));
        } catch (Exception e) {
            throw new Error(e);
        }
    }

}
