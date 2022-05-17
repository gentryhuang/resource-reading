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

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.*;

/**
 * A {@link ThreadPoolExecutor} that can additionally schedule
 * commands to run after a given delay, or to execute
 * periodically. This class is preferable to {@link java.util.Timer}
 * when multiple worker threads are needed, or when the additional
 * flexibility or capabilities of {@link ThreadPoolExecutor} (which
 * this class extends) are required.
 *
 * <p>Delayed tasks execute no sooner than they are enabled, but
 * without any real-time guarantees about when, after they are
 * enabled, they will commence. Tasks scheduled for exactly the same
 * execution time are enabled in first-in-first-out (FIFO) order of
 * submission.
 *
 * <p>When a submitted task is cancelled before it is run, execution
 * is suppressed. By default, such a cancelled task is not
 * automatically removed from the work queue until its delay
 * elapses. While this enables further inspection and monitoring, it
 * may also cause unbounded retention of cancelled tasks. To avoid
 * this, set {@link #setRemoveOnCancelPolicy} to {@code true}, which
 * causes tasks to be immediately removed from the work queue at
 * time of cancellation.
 *
 * <p>Successive executions of a task scheduled via
 * {@code scheduleAtFixedRate} or
 * {@code scheduleWithFixedDelay} do not overlap. While different
 * executions may be performed by different threads, the effects of
 * prior executions <a
 * href="package-summary.html#MemoryVisibility"><i>happen-before</i></a>
 * those of subsequent ones.
 *
 * <p>While this class inherits from {@link ThreadPoolExecutor}, a few
 * of the inherited tuning methods are not useful for it. In
 * particular, because it acts as a fixed-sized pool using
 * {@code corePoolSize} threads and an unbounded queue, adjustments
 * to {@code maximumPoolSize} have no useful effect. Additionally, it
 * is almost never a good idea to set {@code corePoolSize} to zero or
 * use {@code allowCoreThreadTimeOut} because this may leave the pool
 * without threads to handle tasks once they become eligible to run.
 *
 * <p><b>Extension notes:</b> This class overrides the
 * {@link ThreadPoolExecutor#execute(Runnable) execute} and
 * {@link AbstractExecutorService#submit(Runnable) submit}
 * methods to generate internal {@link ScheduledFuture} objects to
 * control per-task delays and scheduling.  To preserve
 * functionality, any further overrides of these methods in
 * subclasses must invoke superclass versions, which effectively
 * disables additional task customization.  However, this class
 * provides alternative protected extension method
 * {@code decorateTask} (one version each for {@code Runnable} and
 * {@code Callable}) that can be used to customize the concrete task
 * types used to execute commands entered via {@code execute},
 * {@code submit}, {@code schedule}, {@code scheduleAtFixedRate},
 * and {@code scheduleWithFixedDelay}.  By default, a
 * {@code ScheduledThreadPoolExecutor} uses a task type extending
 * {@link FutureTask}. However, this may be modified or replaced using
 * subclasses of the form:
 *
 * <pre> {@code
 * public class CustomScheduledExecutor extends ScheduledThreadPoolExecutor {
 *
 *   static class CustomTask<V> implements RunnableScheduledFuture<V> { ... }
 *
 *   protected <V> RunnableScheduledFuture<V> decorateTask(
 *                Runnable r, RunnableScheduledFuture<V> task) {
 *       return new CustomTask<V>(r, task);
 *   }
 *
 *   protected <V> RunnableScheduledFuture<V> decorateTask(
 *                Callable<V> c, RunnableScheduledFuture<V> task) {
 *       return new CustomTask<V>(c, task);
 *   }
 *   // ... add constructors, etc.
 * }}</pre>
 *
 * @author Doug Lea
 * @since 1.5
 */
public class ScheduledThreadPoolExecutor
        extends ThreadPoolExecutor
        implements ScheduledExecutorService {


    /*
      说明：

      1.  ScheduledThreadPoolExecutor 是基于 ThreadPoolExecutor 扩展的定时任务线程池。用于实现定时任务相关功能，将任务包装成定时任务，
          并按照定时策略来执行。
      2.  定时任务线程池使用的是延时队列，并没有直接使用并发集合中的DelayQueue，而是自己又实现了一个DelayedWorkQueue，不过跟DelayQueue的实现原理是一样的。
     */


    /*
     * This class specializes ThreadPoolExecutor implementation by
     *
     * 此类专门通过以下方式实现 ThreadPoolExecutor
     *
     * 1. Using a custom task type, ScheduledFutureTask for
     *    tasks, even those that don't require scheduling (i.e.,
     *    those submitted using ExecutorService execute, not
     *    ScheduledExecutorService methods) which are treated as
     *    delayed tasks with a delay of zero.
     *
     *    对任务使用自定义任务类型 ScheduledFutureTask，即使是那些不需要调度的任务（即使用 ExecutorService 执行，而不是 ScheduledExecutorService 方法提交的任务），
     *    这些任务都被视为延迟为零的延迟任务。
     *
     * 2. Using a custom queue (DelayedWorkQueue), a variant of
     *    unbounded DelayQueue. The lack of capacity constraint and
     *    the fact that corePoolSize and maximumPoolSize are
     *    effectively identical simplifies some execution mechanics
     *    (see delayedExecute) compared to ThreadPoolExecutor.
     *
     *    使用自定义队列（DelayedWorkQueue），无界DelayQueue 的一种变体。与 ThreadPoolExecutor 相比，缺乏容量限制
     *    以及 corePoolSize 和 maximumPoolSize 实际上相同的事实简化了一些执行机制（请参阅delayedExecute）。
     *
     * 3. Supporting optional run-after-shutdown parameters, which
     *    leads to overrides of shutdown methods to remove and cancel
     *    tasks that should NOT be run after shutdown, as well as
     *    different recheck logic when task (re)submission overlaps
     *    with a shutdown.
     *
     *    支持可选的 run-after-shutdown 参数，这会导致覆盖关闭方法以删除和取消不应在关闭后运行的任务，
     *    以及当任务（重新）提交与关闭重叠时不同的重新检查逻辑。
     *
     *
     *
     * 4. Task decoration methods to allow interception and
     *    instrumentation, which are needed because subclasses cannot
     *    otherwise override submit methods to get this effect. These
     *    don't have any impact on pool control logic though.
     *
     *    允许拦截和检测的任务修饰方法，这是必需的，因为子类不能以其他方式覆盖提交方法以获得此效果。但是，这些对池控制逻辑没有任何影响。
     */

    /**
     * False if should cancel/suppress periodic tasks on shutdown.
     * <p>
     * 线程池关闭后（shutdown)是否继续执行存在的周期性任务（包括 fixedRate/fixedDelay），默认为 false
     */
    private volatile boolean continueExistingPeriodicTasksAfterShutdown;

    /**
     * False if should cancel non-periodic tasks on shutdown.
     * <p>
     * 线程关闭后（shutdown）是否继续执行存在的延迟任务，默认为 true
     */
    private volatile boolean executeExistingDelayedTasksAfterShutdown = true;

    /**
     * True if ScheduledFutureTask.cancel should remove from queue
     * <p>
     * 取消任务时，是否立即从队列中删除
     */
    private volatile boolean removeOnCancel = false;

    /**
     * Sequence number to break scheduling ties, and in turn to
     * guarantee FIFO order among tied entries.
     * <p>
     * 用于生成任务添加到 ScheduledThreadPoolExecutor 中的序号
     */
    private static final AtomicLong sequencer = new AtomicLong();

    /**
     * Returns current nanosecond time.
     * <p>
     * 返回当前纳秒时间。
     */
    final long now() {
        return System.nanoTime();
    }


    /*----------------------------------- 任务 ---------------------------------*/

    /**
     * todo ScheduledThreadPoolExecutor 使用的任务体，继承了和实现了多个类，具备多个特征
     * - 任务
     * - 延时
     *
     * @param <V>
     */
    private class ScheduledFutureTask<V>
            extends FutureTask<V> implements RunnableScheduledFuture<V> {

        /**
         * Sequence number to break ties FIFO
         * <p>
         * 当前任务被添加到 ScheduledThreadPoolExecutor 中的序号
         */
        private final long sequenceNumber;

        /**
         * The time the task is enabled to execute in nanoTime units
         * <p>
         * 以 nanoTime 为单位，任务被执行的时间
         */
        private long time;

        /**
         * Period in nanoseconds for repeating tasks.
         * <p>
         * 重复任务的周期（以纳秒为单位）。
         * <p>
         * <p>
         * A positive value indicates fixed-rate execution.
         * <p>
         * 正值表示固定频率执行。
         * <p>
         * A negative value indicates fixed-delay execution.
         * <p>
         * 负值表示固定延迟执行。
         * <p>
         * A value of 0 indicates a non-repeating task.
         * <p>
         * 值 0 表示非重复任务。
         */
        private final long period;

        /**
         * The actual task to be re-enqueued by reExecutePeriodic
         * <p>
         * 由 reExecutePeriodic 重新入队的实际任务，用于周期性任务
         */
        RunnableScheduledFuture<V> outerTask = this;

        /**
         * Index into delay queue, to support faster cancellation.
         * <p>
         * 索引到延迟队列，以支持更快的取消。
         */
        int heapIndex;

        /**
         * Creates a one-shot action with given nanoTime-based trigger time.
         * <p>
         * 使用给定的基于 nanoTime 的触发时间创建一次性动作。
         */
        ScheduledFutureTask(Runnable r, V result, long ns) {
            // FutureTask 的构造方法
            super(r, result);
            this.time = ns;
            this.period = 0;
            this.sequenceNumber = sequencer.getAndIncrement();
        }

        /**
         * Creates a periodic action with given nano time and period.
         * <p>
         * 创建给定的基于 nanoTime 的触发时间 和 周期的周期性动作。
         */
        ScheduledFutureTask(Runnable r, V result, long ns, long period) {
            // FutureTask 的构造方法
            super(r, result);
            this.time = ns;
            this.period = period;
            this.sequenceNumber = sequencer.getAndIncrement();
        }

        /**
         * Creates a one-shot action with given nanoTime-based trigger time.
         * <p>
         * 使用给定的基于 nanoTime 的触发时间创建一次性动作。
         */
        ScheduledFutureTask(Callable<V> callable, long ns) {
            // FutureTask 的构造方法
            super(callable);
            this.time = ns;
            this.period = 0;
            this.sequenceNumber = sequencer.getAndIncrement();
        }

        /**
         * 获取剩余过期时间，<= 0 才能执行
         *
         * @param unit 时间单位
         * @return
         */
        public long getDelay(TimeUnit unit) {
            return unit.convert(time - now(), NANOSECONDS);
        }

        /**
         * 元素优先级比较
         * <p>
         * 1. time 小的排在前面（时间早的任务将被先执行）
         * 2. 如果两个任务的 time 相同，那么比较 sequenceNumber ，sequenceNumber 小的排在前面（如果两个任务的执行时间相同，那么先提交的任务将被先执行）
         *
         * @param other
         * @return
         */
        public int compareTo(Delayed other) {
            // 元素相同
            if (other == this) // compare zero if same object
                return 0;

            // ScheduledFutureTask 类型的，则先比较 time 大小，无法区分大小再比较 sequenceNumber
            if (other instanceof ScheduledFutureTask) {
                ScheduledFutureTask<?> x = (ScheduledFutureTask<?>) other;
                long diff = time - x.time;
                if (diff < 0)
                    return -1;
                else if (diff > 0)
                    return 1;
                else if (sequenceNumber < x.sequenceNumber)
                    return -1;
                else
                    return 1;
            }

            // 比较剩余延时时间
            long diff = getDelay(NANOSECONDS) - other.getDelay(NANOSECONDS);
            return (diff < 0) ? -1 : (diff > 0) ? 1 : 0;
        }

        /**
         * Returns {@code true} if this is a periodic (not a one-shot) action.
         * <p>
         * 如果这是一个周期性的（不是一次性的）动作，则返回 true
         *
         * @return {@code true} if periodic
         */
        public boolean isPeriodic() {
            return period != 0;
        }

        /**
         * Sets the next time to run for a periodic task.
         * <p>
         * 设置下一次运行周期性任务的时间。
         */
        private void setNextRunTime() {
            long p = period;
            // scheduleAtFixedRate 方式，以上一个任务开始时间为基础，计算下次触发时间
            if (p > 0)
                time += p;

                // scheduleWithFixedDelay 方式，以上个任务结束时间即当前时间为基础，计算下次触发时间
            else
                time = triggerTime(-p);
        }

        /**
         * 取消任务
         *
         * @param mayInterruptIfRunning
         * @return
         */
        public boolean cancel(boolean mayInterruptIfRunning) {
            // 调用父类 FutureTask 的 cancel 方法
            boolean cancelled = super.cancel(mayInterruptIfRunning);

            // 取消成功后，判断是否立即移除队列中的任务
            if (cancelled && removeOnCancel && heapIndex >= 0)
                remove(this);

            return cancelled;
        }

        /**
         * Overrides FutureTask version so as to reset/requeue if periodic.
         * <p>
         * 覆盖 FutureTask 版本，以便定期重置 requeue。
         */
        public void run() {
            // 判断是否周期性任务
            boolean periodic = isPeriodic();

            // 判断是否可以运行任务，不可以运行就取消并移除任务
            if (!canRunInCurrentRunState(periodic))
                cancel(false);

                // 如果是一次性任务，直接调用父类的 run 方法，这个方法实际是 FutureTask 的方法
            else if (!periodic)
                ScheduledFutureTask.super.run();

                // 如果是周期性任务，调用父类的 runAndReset() 方法，执行并重复任务，这个父类是 FutureTask 的方法
                // runAndReset 和 run 方法类似，只是其任务运行完毕后不会把状态修改为NORMAL
                // todo 如果任务执行异常，那么就不会继续周期性执行。注意，线程并没有退出。
            else if (ScheduledFutureTask.super.runAndReset()) {
                // 设置下次任务执行的时间
                setNextRunTime();

                // 重新排队周期任务
                reExecutePeriodic(outerTask);
            }
        }
    }

    /*----------------------------------- 任务 ---------------------------------*/

    /**
     * Returns true if can run a task given current run state
     * and run-after-shutdown parameters.
     * <p>
     * 如果可以在给定当前运行状态和 run-after-shutdown 参数的情况下运行任务，则返回 true。
     *
     * @param periodic 如果此任务是周期性的，则为 true；如果延迟，则为 false
     */
    boolean canRunInCurrentRunState(boolean periodic) {
        /*
           final boolean isRunningOrShutdown(boolean shutdownOK) {
              int rs = runStateOf(ctl.get());
              return rs == RUNNING || (rs == SHUTDOWN && shutdownOK);
             }
         */
        return isRunningOrShutdown(periodic ?
                // 线程池关闭后是否继续执行周期性任务
                continueExistingPeriodicTasksAfterShutdown :
                // 线程池关闭后是否继续执行延迟任务
                executeExistingDelayedTasksAfterShutdown);
    }

    /**
     * Main execution method for delayed or periodic tasks.
     * <p>
     * 延迟或周期性任务的主要执行方法。
     * <p>
     * If pool is shut down, rejects the task. Otherwise adds task to queue
     * and starts a thread, if necessary, to run it.  (We cannot
     * prestart the thread to run the task because the task (probably)
     * shouldn't be run yet.)
     * <p>
     * 如果池已关闭，则拒绝该任务。否则，将任务添加到队列并在必要时启动一个线程来运行它。 （我们不能预先启动线程来运行任务，因为任务（可能）还不应该运行。）
     * <p>
     * If the pool is shut down while the task is being added, cancel and remove it if required by state and
     * run-after-shutdown parameters.
     * <p>
     * 如果在添加任务时池已关闭，则根据 state 和 run-after-shutdown 参数的要求取消并删除它。
     *
     * @param task the task
     */
    private void delayedExecute(RunnableScheduledFuture<?> task) {
        // 如果线程池关闭了，执行拒绝策略
        if (isShutdown())
            reject(task);

            // 线程池未关闭
        else {
            // 先把任务放到队列中（这个队列本质上是一个优先级队列）
            // 注意，ThreadPollExecutor 是 >= 核心线程数时才添加任务到阻塞队列中。因为是定时线程池，任务时间到了才会执行，因此是从任务队列中获取。
            super.getQueue().add(task);

            // 再次检查线程池状态，必要时取消并删除任务
            if (isShutdown() &&
                    !canRunInCurrentRunState(task.isPeriodic()) &&
                    remove(task))
                task.cancel(false);

                // 确保有线程执行任务
            else
                ensurePrestart();
        }
    }

    /**
     * Requeues a periodic task unless current run state precludes it.
     * <p>
     * 重新排队周期任务，除非当前运行状态排除它。
     * <p>
     * Same idea as delayedExecute except drops task rather than rejecting.
     * <p>
     * 与 delayedExecute 相同的想法，除了丢弃任务而不是拒绝。
     *
     * @param task the task
     */
    void reExecutePeriodic(RunnableScheduledFuture<?> task) {
        // 判断是否可以该运行任务
        if (canRunInCurrentRunState(true)) {

            // 将任务再次添加到任务队列中
            super.getQueue().add(task);

            // 再次检查是否可以运行任务，如果不能运行则移除并取消任务
            if (!canRunInCurrentRunState(true) && remove(task))
                task.cancel(false);

            else
                // 确保有线程执行任务
                ensurePrestart();
        }
    }

    /**
     * Cancels and clears the queue of all tasks that should not be run
     * due to shutdown policy.  Invoked within super.shutdown.
     * <p>
     * 取消并清除由于关闭策略而不应运行的所有任务的队列。
     * <p>
     * 在 super.shutdown 中调用该方法，也就是如果 continueExistingPeriodicTasksAfterShutdown 或者 continueExistingPeriodicTasksAfterShutdown 为 true，
     * 那么表示线程池关闭也会执行任务；为 false ，那么在关闭的时候就会移除并取消任务。
     */
    @Override
    void onShutdown() {
        BlockingQueue<Runnable> q = super.getQueue();
        // 延迟任务关闭策略
        boolean keepDelayed =
                getExecuteExistingDelayedTasksAfterShutdownPolicy();

        // 周期任务关闭策略
        boolean keepPeriodic =
                getContinueExistingPeriodicTasksAfterShutdownPolicy();

        // 不支持线程池关闭执行，则取消并移除相关任务
        if (!keepDelayed && !keepPeriodic) {
            for (Object e : q.toArray())
                if (e instanceof RunnableScheduledFuture<?>)
                    ((RunnableScheduledFuture<?>) e).cancel(false);
            q.clear();
        } else {
            // Traverse snapshot to avoid iterator exceptions
            for (Object e : q.toArray()) {
                if (e instanceof RunnableScheduledFuture) {
                    RunnableScheduledFuture<?> t =
                            (RunnableScheduledFuture<?>) e;
                    if ((t.isPeriodic() ? !keepPeriodic : !keepDelayed) || t.isCancelled()) { // also remove if already cancelled
                        if (q.remove(t))
                            t.cancel(false);
                    }
                }
            }
        }

        // 尝试终止线程池
        tryTerminate();
    }

    /**
     * Modifies or replaces the task used to execute a runnable.
     * This method can be used to override the concrete
     * class used for managing internal tasks.
     * The default implementation simply returns the given task.
     * <p>
     * 钩子方法，使用方可以对任务尽心处理
     *
     * @param runnable the submitted Runnable
     * @param task     the task created to execute the runnable
     * @param <V>      the type of the task's result
     * @return a task that can execute the runnable
     * @since 1.6
     */
    protected <V> RunnableScheduledFuture<V> decorateTask(
            Runnable runnable, RunnableScheduledFuture<V> task) {
        return task;
    }

    /**
     * Modifies or replaces the task used to execute a callable.
     * This method can be used to override the concrete
     * class used for managing internal tasks.
     * The default implementation simply returns the given task.
     *
     * @param callable the submitted Callable
     * @param task     the task created to execute the callable
     * @param <V>      the type of the task's result
     * @return a task that can execute the callable
     * @since 1.6
     */
    protected <V> RunnableScheduledFuture<V> decorateTask(
            Callable<V> callable, RunnableScheduledFuture<V> task) {
        return task;
    }


    /*---------------------------------- 构造方法 -------------------------------*/

    /**
     * Creates a new {@code ScheduledThreadPoolExecutor} with the
     * given core pool size.
     * <p>
     * 创建给定核心线程数的 定时任务线程池
     *
     * @param corePoolSize the number of threads to keep in the pool, even
     *                     if they are idle, unless {@code allowCoreThreadTimeOut} is set
     * @throws IllegalArgumentException if {@code corePoolSize < 0}
     */
    public ScheduledThreadPoolExecutor(int corePoolSize) {
        // 空闲线程存活时间为 0，阻塞队列为 ScheduledThreadPoolExecutor 自定义的延时队列 DelayedWorkQueue
        super(corePoolSize, Integer.MAX_VALUE, 0, NANOSECONDS,
                new DelayedWorkQueue());
    }

    /**
     * Creates a new {@code ScheduledThreadPoolExecutor} with the
     * given initial parameters.
     *
     * @param corePoolSize  the number of threads to keep in the pool, even
     *                      if they are idle, unless {@code allowCoreThreadTimeOut} is set
     * @param threadFactory the factory to use when the executor
     *                      creates a new thread
     * @throws IllegalArgumentException if {@code corePoolSize < 0}
     * @throws NullPointerException     if {@code threadFactory} is null
     */
    public ScheduledThreadPoolExecutor(int corePoolSize,
                                       ThreadFactory threadFactory) {
        super(corePoolSize, Integer.MAX_VALUE, 0, NANOSECONDS,
                new DelayedWorkQueue(), threadFactory);
    }

    /**
     * Creates a new ScheduledThreadPoolExecutor with the given
     * initial parameters.
     *
     * @param corePoolSize the number of threads to keep in the pool, even
     *                     if they are idle, unless {@code allowCoreThreadTimeOut} is set
     * @param handler      the handler to use when execution is blocked
     *                     because the thread bounds and queue capacities are reached
     * @throws IllegalArgumentException if {@code corePoolSize < 0}
     * @throws NullPointerException     if {@code handler} is null
     */
    public ScheduledThreadPoolExecutor(int corePoolSize,
                                       RejectedExecutionHandler handler) {
        super(corePoolSize, Integer.MAX_VALUE, 0, NANOSECONDS,
                new DelayedWorkQueue(), handler);
    }

    /**
     * Creates a new ScheduledThreadPoolExecutor with the given
     * initial parameters.
     *
     * @param corePoolSize  the number of threads to keep in the pool, even
     *                      if they are idle, unless {@code allowCoreThreadTimeOut} is set
     * @param threadFactory the factory to use when the executor
     *                      creates a new thread
     * @param handler       the handler to use when execution is blocked
     *                      because the thread bounds and queue capacities are reached
     * @throws IllegalArgumentException if {@code corePoolSize < 0}
     * @throws NullPointerException     if {@code threadFactory} or
     *                                  {@code handler} is null
     */
    public ScheduledThreadPoolExecutor(int corePoolSize,
                                       ThreadFactory threadFactory,
                                       RejectedExecutionHandler handler) {
        super(corePoolSize, Integer.MAX_VALUE, 0, NANOSECONDS,
                new DelayedWorkQueue(), threadFactory, handler);
    }

    /*---------------------------------- 构造方法 -------------------------------*/


    /**
     * Returns the trigger time of a delayed action.
     * <p>
     * 返回延迟任务的触发时间。
     */
    private long triggerTime(long delay, TimeUnit unit) {
        return triggerTime(unit.toNanos((delay < 0) ? 0 : delay));
    }

    /**
     * Returns the trigger time of a delayed action.
     * <p>
     * 返回延迟任务的触发时间。
     */
    long triggerTime(long delay) {
        return now() +
                ((delay < (Long.MAX_VALUE >> 1)) ? delay : overflowFree(delay));
    }

    /**
     * Constrains the values of all delays in the queue to be within
     * Long.MAX_VALUE of each other, to avoid overflow in compareTo.
     * This may occur if a task is eligible to be dequeued, but has
     * not yet been, while some other task is added with a delay of
     * Long.MAX_VALUE.
     */
    private long overflowFree(long delay) {
        Delayed head = (Delayed) super.getQueue().peek();
        if (head != null) {
            long headDelay = head.getDelay(NANOSECONDS);
            if (headDelay < 0 && (delay - headDelay < 0))
                delay = Long.MAX_VALUE + headDelay;
        }
        return delay;
    }

    /*------------------------------- 提交任务 --------------------------------*/

    /**
     * 执行延迟任务
     *
     * @param command 没有返回值的任务
     * @param delay   延时时间粒度
     * @param unit    延时时间粒度的单位
     * @throws RejectedExecutionException {@inheritDoc}
     * @throws NullPointerException       {@inheritDoc}
     */
    public ScheduledFuture<?> schedule(Runnable command,
                                       long delay,
                                       TimeUnit unit) {
        // 参数校验
        if (command == null || unit == null)
            throw new NullPointerException();

        // 执行钩子方法
        RunnableScheduledFuture<?> t = decorateTask(command,
                // 将普通的任务装饰成 ScheduledFutureTask
                new ScheduledFutureTask<Void>(command, null,
                        triggerTime(delay, unit)));

        // 延时执行
        delayedExecute(t);

        // 返回延时计算任务
        return t;
    }

    /**
     * 执行延迟任务
     *
     * @param callable 有返回值的任务
     * @param delay    延时时间粒度
     * @param unit     延时时间粒度的单位
     * @throws RejectedExecutionException {@inheritDoc}
     * @throws NullPointerException       {@inheritDoc}
     */
    public <V> ScheduledFuture<V> schedule(Callable<V> callable,
                                           long delay,
                                           TimeUnit unit) {
        // 参数校验
        if (callable == null || unit == null)
            throw new NullPointerException();

        // 执行钩子方法
        RunnableScheduledFuture<V> t = decorateTask(callable,
                // 将普通的任务装饰成 ScheduledFutureTask
                new ScheduledFutureTask<V>(callable,
                        triggerTime(delay, unit)));

        // 延时执行
        delayedExecute(t);

        // 返回延时计算任务
        return t;
    }

    /**
     * 提交一个按固定频率执行的任务
     *
     * @param command      没有返回值的任务
     * @param initialDelay 初始延时时间
     * @param period       频率
     * @param unit         延时时间粒度的单位
     * @throws RejectedExecutionException {@inheritDoc}
     * @throws NullPointerException       {@inheritDoc}
     * @throws IllegalArgumentException   {@inheritDoc}
     */
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command,
                                                  long initialDelay,
                                                  long period,
                                                  TimeUnit unit) {

        // 参数判断
        if (command == null || unit == null)
            throw new NullPointerException();
        // 必须是固定频率（该方法就是按固定频率执行任务）
        if (period <= 0)
            throw new IllegalArgumentException();

        // 将普通的任务装饰成 ScheduledFutureTask
        ScheduledFutureTask<Void> sft =
                new ScheduledFutureTask<Void>(
                        command,
                        null,
                        triggerTime(initialDelay, unit),
                        unit.toNanos(period));

        // 钩子方法，可在任务执行之前进行干预
        RunnableScheduledFuture<Void> t = decorateTask(command, sft);

        // 保存任务 t，用于周期性执行
        sft.outerTask = t;

        // 延时执行
        delayedExecute(t);

        // 返回延时计算任务
        return t;
    }

    /**
     * 提交一个固定延迟的任务
     *
     * @param command      没有返回值的任务
     * @param initialDelay 初始延时时间
     * @param delay        固定延时时间
     * @param unit         延时时间粒度的单位
     * @throws RejectedExecutionException {@inheritDoc}
     * @throws NullPointerException       {@inheritDoc}
     * @throws IllegalArgumentException   {@inheritDoc}
     */
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command,
                                                     long initialDelay,
                                                     long delay,
                                                     TimeUnit unit) {
        // 参数判断
        if (command == null || unit == null)
            throw new NullPointerException();

        // 固定延迟时间校验
        if (delay <= 0)
            throw new IllegalArgumentException();

        // 将普通的任务装饰成 ScheduledFutureTask
        ScheduledFutureTask<Void> sft =
                new ScheduledFutureTask<Void>(command,
                        null,
                        triggerTime(initialDelay, unit),
                        unit.toNanos(-delay));

        // 钩子方法，可在任务执行之前进行干预
        RunnableScheduledFuture<Void> t = decorateTask(command, sft);

        // 保存任务 t，用于周期性执行
        sft.outerTask = t;

        // 延时执行
        delayedExecute(t);

        // 返回延时计算任务
        return t;
    }

    /*--------------------------- 保留 execute 和 submit 方法，通过这两个方法提交的任务会被当成 0 延时的任务执行一次 -------------------- */

    /**
     * Executes {@code command} with zero required delay.
     * This has effect equivalent to
     * {@link #schedule(Runnable, long, TimeUnit) schedule(command, 0, anyUnit)}.
     * Note that inspections of the queue and of the list returned by
     * {@code shutdownNow} will access the zero-delayed
     * {@link ScheduledFuture}, not the {@code command} itself.
     *
     * <p>A consequence of the use of {@code ScheduledFuture} objects is
     * that {@link ThreadPoolExecutor#afterExecute afterExecute} is always
     * called with a null second {@code Throwable} argument, even if the
     * {@code command} terminated abruptly.  Instead, the {@code Throwable}
     * thrown by such a task can be obtained via {@link Future#get}.
     *
     * @throws RejectedExecutionException at discretion of
     *                                    {@code RejectedExecutionHandler}, if the task
     *                                    cannot be accepted for execution because the
     *                                    executor has been shut down
     * @throws NullPointerException       {@inheritDoc}
     */
    public void execute(Runnable command) {
        schedule(command, 0, NANOSECONDS);
    }

    // Override AbstractExecutorService methods

    /**
     * @throws RejectedExecutionException {@inheritDoc}
     * @throws NullPointerException       {@inheritDoc}
     */
    public Future<?> submit(Runnable task) {
        return schedule(task, 0, NANOSECONDS);
    }

    /**
     * @throws RejectedExecutionException {@inheritDoc}
     * @throws NullPointerException       {@inheritDoc}
     */
    public <T> Future<T> submit(Runnable task, T result) {
        return schedule(Executors.callable(task, result), 0, NANOSECONDS);
    }

    /**
     * @throws RejectedExecutionException {@inheritDoc}
     * @throws NullPointerException       {@inheritDoc}
     */
    public <T> Future<T> submit(Callable<T> task) {
        return schedule(task, 0, NANOSECONDS);
    }

    /*--------------------------- 保留 execute 和 submit 方法，通过这两个方法提交的任务会被当成 0 延时的任务执行一次 --------------------*/


    /**
     * Sets the policy on whether to continue executing existing
     * periodic tasks even when this executor has been {@code shutdown}.
     * <p>
     * 设置是否继续执行现有周期性任务的策略，即使此执行程序已被 {@code shutdown}。
     * <p>
     * <p>
     * In this case, these tasks will only terminate upon
     * {@code shutdownNow} or after setting the policy to
     * {@code false} when already shutdown.
     * <p>
     * 在这种情况下，这些任务只会在 {@code shutdownNow} 或在已关闭时将策略设置为 {@code false} 后终止。
     * <p>
     * <p>
     * This value is by default {@code false}.
     * 该值默认是 false
     *
     * @param value if {@code true}, continue after shutdown, else don't
     * @see #getContinueExistingPeriodicTasksAfterShutdownPolicy
     */
    public void setContinueExistingPeriodicTasksAfterShutdownPolicy(boolean value) {
        continueExistingPeriodicTasksAfterShutdown = value;
        if (!value && isShutdown())
            onShutdown();
    }

    /**
     * Gets the policy on whether to continue executing existing
     * periodic tasks even when this executor has been {@code shutdown}.
     * In this case, these tasks will only terminate upon
     * {@code shutdownNow} or after setting the policy to
     * {@code false} when already shutdown.
     * This value is by default {@code false}.
     *
     * @return {@code true} if will continue after shutdown
     * @see #setContinueExistingPeriodicTasksAfterShutdownPolicy
     */
    public boolean getContinueExistingPeriodicTasksAfterShutdownPolicy() {
        return continueExistingPeriodicTasksAfterShutdown;
    }

    /**
     * Sets the policy on whether to execute existing delayed
     * tasks even when this executor has been {@code shutdown}.
     * <p>
     * 设置是否执行现有延迟任务的策略，即使此执行程序已{@code shutdown}。
     * <p>
     * In this case, these tasks will only terminate upon
     * {@code shutdownNow}, or after setting the policy to
     * {@code false} when already shutdown.
     * <p>
     * 在这种情况下，这些任务只会在 {@code shutdownNow} 时终止，或者在已关闭时将策略设置为 {@code false} 后终止。
     * <p>
     * <p>
     * This value is by default {@code true}.
     * 该值默认是 true
     *
     * @param value if {@code true}, execute after shutdown, else don't
     * @see #getExecuteExistingDelayedTasksAfterShutdownPolicy
     */
    public void setExecuteExistingDelayedTasksAfterShutdownPolicy(boolean value) {
        executeExistingDelayedTasksAfterShutdown = value;
        if (!value && isShutdown())
            onShutdown();
    }

    /**
     * Gets the policy on whether to execute existing delayed
     * tasks even when this executor has been {@code shutdown}.
     * In this case, these tasks will only terminate upon
     * {@code shutdownNow}, or after setting the policy to
     * {@code false} when already shutdown.
     * This value is by default {@code true}.
     *
     * @return {@code true} if will execute after shutdown
     * @see #setExecuteExistingDelayedTasksAfterShutdownPolicy
     */
    public boolean getExecuteExistingDelayedTasksAfterShutdownPolicy() {
        return executeExistingDelayedTasksAfterShutdown;
    }

    /**
     * Sets the policy on whether cancelled tasks should be immediately
     * removed from the work queue at time of cancellation.  This value is
     * by default {@code false}.
     * <p>
     * 设置取消任务时是否应立即从工作队列中删除的策略。此值默认为 {@code false}。
     *
     * @param value if {@code true}, remove on cancellation, else don't
     * @see #getRemoveOnCancelPolicy
     * @since 1.7
     */
    public void setRemoveOnCancelPolicy(boolean value) {
        removeOnCancel = value;
    }

    /**
     * Gets the policy on whether cancelled tasks should be immediately
     * removed from the work queue at time of cancellation.  This value is
     * by default {@code false}.
     *
     * @return {@code true} if cancelled tasks are immediately removed
     * from the queue
     * @see #setRemoveOnCancelPolicy
     * @since 1.7
     */
    public boolean getRemoveOnCancelPolicy() {
        return removeOnCancel;
    }

    /**
     * Initiates an orderly shutdown in which previously submitted
     * tasks are executed, but no new tasks will be accepted.
     * Invocation has no additional effect if already shut down.
     *
     * <p>This method does not wait for previously submitted tasks to
     * complete execution.  Use {@link #awaitTermination awaitTermination}
     * to do that.
     *
     * <p>If the {@code ExecuteExistingDelayedTasksAfterShutdownPolicy}
     * has been set {@code false}, existing delayed tasks whose delays
     * have not yet elapsed are cancelled.  And unless the {@code
     * ContinueExistingPeriodicTasksAfterShutdownPolicy} has been set
     * {@code true}, future executions of existing periodic tasks will
     * be cancelled.
     *
     * @throws SecurityException {@inheritDoc}
     */
    public void shutdown() {
        super.shutdown();
    }

    /**
     * Attempts to stop all actively executing tasks, halts the
     * processing of waiting tasks, and returns a list of the tasks
     * that were awaiting execution.
     *
     * <p>This method does not wait for actively executing tasks to
     * terminate.  Use {@link #awaitTermination awaitTermination} to
     * do that.
     *
     * <p>There are no guarantees beyond best-effort attempts to stop
     * processing actively executing tasks.  This implementation
     * cancels tasks via {@link Thread#interrupt}, so any task that
     * fails to respond to interrupts may never terminate.
     *
     * @return list of tasks that never commenced execution.
     * Each element of this list is a {@link ScheduledFuture},
     * including those tasks submitted using {@code execute},
     * which are for scheduling purposes used as the basis of a
     * zero-delay {@code ScheduledFuture}.
     * @throws SecurityException {@inheritDoc}
     */
    public List<Runnable> shutdownNow() {
        return super.shutdownNow();
    }

    /**
     * Returns the task queue used by this executor.  Each element of
     * this queue is a {@link ScheduledFuture}, including those
     * tasks submitted using {@code execute} which are for scheduling
     * purposes used as the basis of a zero-delay
     * {@code ScheduledFuture}.  Iteration over this queue is
     * <em>not</em> guaranteed to traverse tasks in the order in
     * which they will execute.
     *
     * @return the task queue
     */
    public BlockingQueue<Runnable> getQueue() {
        return super.getQueue();
    }


    /*-------------------------------- 内部使用的优先级队列 --------------------------------------*/

    /**
     * Specialized delay queue. To mesh with TPE declarations, this
     * class must be declared as a BlockingQueue<Runnable> even though
     * it can only hold RunnableScheduledFutures.
     * <p>
     * ScheduledThreadPoolExecutor 专门实现的的延迟队列
     * <p>
     * 和 DelayQueue 基本一致
     */
    static class DelayedWorkQueue extends AbstractQueue<Runnable>
            implements BlockingQueue<Runnable> {

        /*
         * A DelayedWorkQueue is based on a heap-based data structure
         * like those in DelayQueue and PriorityQueue, except that
         * every ScheduledFutureTask also records its index into the
         * heap array. This eliminates the need to find a task upon
         * cancellation, greatly speeding up removal (down from O(n)
         * to O(log n)), and reducing garbage retention that would
         * otherwise occur by waiting for the element to rise to top
         * before clearing. But because the queue may also hold
         * RunnableScheduledFutures that are not ScheduledFutureTasks,
         * we are not guaranteed to have such indices available, in
         * which case we fall back to linear search. (We expect that
         * most tasks will not be decorated, and that the faster cases
         * will be much more common.)
         *
         * All heap operations must record index changes -- mainly
         * within siftUp and siftDown. Upon removal, a task's
         * heapIndex is set to -1. Note that ScheduledFutureTasks can
         * appear at most once in the queue (this need not be true for
         * other kinds of tasks or work queues), so are uniquely
         * identified by heapIndex.
         */

        /**
         * 初始容量
         */
        private static final int INITIAL_CAPACITY = 16;

        /**
         * 任务数组
         */
        private RunnableScheduledFuture<?>[] queue =
                new RunnableScheduledFuture<?>[INITIAL_CAPACITY];

        /**
         * 锁
         */
        private final ReentrantLock lock = new ReentrantLock();

        /**
         * 数组中元素个数
         */
        private int size = 0;

        /**
         * Thread designated to wait for the task at the head of the
         * queue.  This variant of the Leader-Follower pattern
         * (http://www.cs.wustl.edu/~schmidt/POSA/POSA2/) serves to
         * minimize unnecessary timed waiting.  When a thread becomes
         * the leader, it waits only for the next delay to elapse, but
         * other threads await indefinitely.  The leader thread must
         * signal some other thread before returning from take() or
         * poll(...), unless some other thread becomes leader in the
         * interim.  Whenever the head of the queue is replaced with a
         * task with an earlier expiration time, the leader field is
         * invalidated by being reset to null, and some waiting
         * thread, but not necessarily the current leader, is
         * signalled.  So waiting threads must be prepared to acquire
         * and lose leadership while waiting.
         */

        /**
         * 等待获取元素的标志
         */
        private Thread leader = null;

        /**
         * Condition signalled when a newer task becomes available at the
         * head of the queue or a new thread may need to become leader.
         * <p>
         * 非空等待条件
         */
        private final Condition available = lock.newCondition();

        /**
         * Sets f's heapIndex if it is a ScheduledFutureTask.
         */
        private void setIndex(RunnableScheduledFuture<?> f, int idx) {
            if (f instanceof ScheduledFutureTask)
                ((ScheduledFutureTask) f).heapIndex = idx;
        }

        /**
         * Sifts element added at bottom up to its heap-ordered spot.
         * Call only when holding lock.
         * <p>
         * 自下而上调整堆
         */
        private void siftUp(int k, RunnableScheduledFuture<?> key) {
            while (k > 0) {
                int parent = (k - 1) >>> 1;
                RunnableScheduledFuture<?> e = queue[parent];
                if (key.compareTo(e) >= 0)
                    break;
                queue[k] = e;
                setIndex(e, k);
                k = parent;
            }
            queue[k] = key;
            setIndex(key, k);
        }

        /**
         * Sifts element added at top down to its heap-ordered spot.
         * Call only when holding lock.
         * <p>
         * 自上而下调整堆
         */
        private void siftDown(int k, RunnableScheduledFuture<?> key) {
            int half = size >>> 1;
            while (k < half) {
                int child = (k << 1) + 1;
                RunnableScheduledFuture<?> c = queue[child];
                int right = child + 1;
                if (right < size && c.compareTo(queue[right]) > 0)
                    c = queue[child = right];
                if (key.compareTo(c) <= 0)
                    break;
                queue[k] = c;
                setIndex(c, k);
                k = child;
            }
            queue[k] = key;
            setIndex(key, k);
        }

        /**
         * Resizes the heap array.  Call only when holding lock.
         * <p>
         * 扩容，每次扩容原来的一半
         * <p>
         * 注意，这个
         */
        private void grow() {
            int oldCapacity = queue.length;
            int newCapacity = oldCapacity + (oldCapacity >> 1); // grow 50%
            if (newCapacity < 0) // overflow
                newCapacity = Integer.MAX_VALUE;
            queue = Arrays.copyOf(queue, newCapacity);
        }

        /**
         * Finds index of given object, or -1 if absent.
         */
        private int indexOf(Object x) {
            if (x != null) {
                if (x instanceof ScheduledFutureTask) {
                    int i = ((ScheduledFutureTask) x).heapIndex;
                    // Sanity check; x could conceivably be a
                    // ScheduledFutureTask from some other pool.
                    if (i >= 0 && i < size && queue[i] == x)
                        return i;
                } else {
                    for (int i = 0; i < size; i++)
                        if (x.equals(queue[i]))
                            return i;
                }
            }
            return -1;
        }

        public boolean contains(Object x) {
            final ReentrantLock lock = this.lock;
            lock.lock();
            try {
                return indexOf(x) != -1;
            } finally {
                lock.unlock();
            }
        }

        /**
         * 删除指定元素
         *
         * @param x
         * @return
         */
        public boolean remove(Object x) {
            final ReentrantLock lock = this.lock;
            lock.lock();
            try {
                int i = indexOf(x);
                if (i < 0)
                    return false;

                setIndex(queue[i], -1);
                int s = --size;
                RunnableScheduledFuture<?> replacement = queue[s];
                queue[s] = null;
                if (s != i) {
                    siftDown(i, replacement);
                    if (queue[i] == replacement)
                        siftUp(i, replacement);
                }
                return true;
            } finally {
                lock.unlock();
            }
        }

        public int size() {
            final ReentrantLock lock = this.lock;
            lock.lock();
            try {
                return size;
            } finally {
                lock.unlock();
            }
        }

        public boolean isEmpty() {
            return size() == 0;
        }

        public int remainingCapacity() {
            return Integer.MAX_VALUE;
        }

        /**
         * 获取堆顶元素
         *
         * @return
         */
        public RunnableScheduledFuture<?> peek() {
            final ReentrantLock lock = this.lock;
            lock.lock();
            try {
                return queue[0];
            } finally {
                lock.unlock();
            }
        }

        /*-------------------------------  添加元素 -----------------------------*/

        /**
         * 添加元素
         *
         * @param x
         * @return
         */
        public boolean offer(Runnable x) {
            if (x == null)
                throw new NullPointerException();
            RunnableScheduledFuture<?> e = (RunnableScheduledFuture<?>) x;
            final ReentrantLock lock = this.lock;
            lock.lock();
            try {
                int i = size;
                if (i >= queue.length)
                    grow();
                size = i + 1;
                if (i == 0) {
                    queue[0] = e;
                    setIndex(e, 0);
                } else {
                    siftUp(i, e);
                }
                if (queue[0] == e) {
                    leader = null;
                    available.signal();
                }
            } finally {
                lock.unlock();
            }
            return true;
        }

        public void put(Runnable e) {
            offer(e);
        }

        public boolean add(Runnable e) {
            return offer(e);
        }

        public boolean offer(Runnable e, long timeout, TimeUnit unit) {
            return offer(e);
        }

        /*-------------------------------  添加元素 -----------------------------*/

        /**
         * Performs common bookkeeping for poll and take: Replaces
         * first element with last and sifts it down.  Call only when
         * holding lock.
         *
         * @param f the task to remove and return
         */
        private RunnableScheduledFuture<?> finishPoll(RunnableScheduledFuture<?> f) {
            int s = --size;
            RunnableScheduledFuture<?> x = queue[s];
            queue[s] = null;
            if (s != 0)
                siftDown(0, x);
            setIndex(f, -1);
            return f;
        }

        /*------------------- 获取元素 ------------------------*/
        public RunnableScheduledFuture<?> poll() {
            final ReentrantLock lock = this.lock;
            lock.lock();
            try {
                RunnableScheduledFuture<?> first = queue[0];
                if (first == null || first.getDelay(NANOSECONDS) > 0)
                    return null;
                else
                    return finishPoll(first);
            } finally {
                lock.unlock();
            }
        }

        public RunnableScheduledFuture<?> take() throws InterruptedException {
            // 获取锁
            final ReentrantLock lock = this.lock;
            lock.lockInterruptibly();

            try {
                // 自旋
                for (; ; ) {
                    // 获取但不移除队列中首个元素，也就是堆顶元素
                    RunnableScheduledFuture<?> first = queue[0];

                    // 队列为空，等待直到有任务加入
                    if (first == null)
                        available.await();

                        // 队列不为空
                    else {
                        // 获取元素的剩余延迟时间
                        long delay = first.getDelay(NANOSECONDS);
                        if (delay <= 0)
                            return finishPoll(first);


                        first = null; // don't retain ref while waiting
                        if (leader != null)
                            available.await();
                        else {
                            Thread thisThread = Thread.currentThread();
                            leader = thisThread;
                            try {
                                available.awaitNanos(delay);
                            } finally {
                                if (leader == thisThread)
                                    leader = null;
                            }
                        }
                    }
                }

            } finally {
                if (leader == null && queue[0] != null)
                    available.signal();
                lock.unlock();
            }
        }

        public RunnableScheduledFuture<?> poll(long timeout, TimeUnit unit)
                throws InterruptedException {
            long nanos = unit.toNanos(timeout);
            final ReentrantLock lock = this.lock;
            lock.lockInterruptibly();
            try {
                for (; ; ) {
                    RunnableScheduledFuture<?> first = queue[0];
                    if (first == null) {
                        if (nanos <= 0)
                            return null;
                        else
                            nanos = available.awaitNanos(nanos);
                    } else {
                        long delay = first.getDelay(NANOSECONDS);
                        if (delay <= 0)
                            return finishPoll(first);
                        if (nanos <= 0)
                            return null;
                        first = null; // don't retain ref while waiting
                        if (nanos < delay || leader != null)
                            nanos = available.awaitNanos(nanos);
                        else {
                            Thread thisThread = Thread.currentThread();
                            leader = thisThread;
                            try {
                                long timeLeft = available.awaitNanos(delay);
                                nanos -= delay - timeLeft;
                            } finally {
                                if (leader == thisThread)
                                    leader = null;
                            }
                        }
                    }
                }
            } finally {
                if (leader == null && queue[0] != null)
                    available.signal();
                lock.unlock();
            }
        }

        /*------------------- 获取元素 ------------------------*/


        public void clear() {
            final ReentrantLock lock = this.lock;
            lock.lock();
            try {
                for (int i = 0; i < size; i++) {
                    RunnableScheduledFuture<?> t = queue[i];
                    if (t != null) {
                        queue[i] = null;
                        setIndex(t, -1);
                    }
                }
                size = 0;
            } finally {
                lock.unlock();
            }
        }

        /**
         * Returns first element only if it is expired.
         * <p>
         * 仅当第一个元素过期时才返回它。
         * <p>
         * Used only by drainTo.  Call only when holding lock.
         */
        private RunnableScheduledFuture<?> peekExpired() {
            // assert lock.isHeldByCurrentThread();
            RunnableScheduledFuture<?> first = queue[0];
            return (first == null || first.getDelay(NANOSECONDS) > 0) ?
                    null : first;
        }

        public int drainTo(Collection<? super Runnable> c) {
            if (c == null)
                throw new NullPointerException();
            if (c == this)
                throw new IllegalArgumentException();
            final ReentrantLock lock = this.lock;
            lock.lock();
            try {
                RunnableScheduledFuture<?> first;
                int n = 0;
                while ((first = peekExpired()) != null) {
                    c.add(first);   // In this order, in case add() throws.
                    finishPoll(first);
                    ++n;
                }
                return n;
            } finally {
                lock.unlock();
            }
        }

        public int drainTo(Collection<? super Runnable> c, int maxElements) {
            if (c == null)
                throw new NullPointerException();
            if (c == this)
                throw new IllegalArgumentException();
            if (maxElements <= 0)
                return 0;
            final ReentrantLock lock = this.lock;
            lock.lock();
            try {
                RunnableScheduledFuture<?> first;
                int n = 0;
                while (n < maxElements && (first = peekExpired()) != null) {
                    c.add(first);   // In this order, in case add() throws.
                    finishPoll(first);
                    ++n;
                }
                return n;
            } finally {
                lock.unlock();
            }
        }

        public Object[] toArray() {
            final ReentrantLock lock = this.lock;
            lock.lock();
            try {
                return Arrays.copyOf(queue, size, Object[].class);
            } finally {
                lock.unlock();
            }
        }

        @SuppressWarnings("unchecked")
        public <T> T[] toArray(T[] a) {
            final ReentrantLock lock = this.lock;
            lock.lock();
            try {
                if (a.length < size)
                    return (T[]) Arrays.copyOf(queue, size, a.getClass());
                System.arraycopy(queue, 0, a, 0, size);
                if (a.length > size)
                    a[size] = null;
                return a;
            } finally {
                lock.unlock();
            }
        }

        public Iterator<Runnable> iterator() {
            return new Itr(Arrays.copyOf(queue, size));
        }

        /**
         * Snapshot iterator that works off copy of underlying q array.
         */
        private class Itr implements Iterator<Runnable> {
            final RunnableScheduledFuture<?>[] array;
            int cursor = 0;     // index of next element to return
            int lastRet = -1;   // index of last element, or -1 if no such

            Itr(RunnableScheduledFuture<?>[] array) {
                this.array = array;
            }

            public boolean hasNext() {
                return cursor < array.length;
            }

            public Runnable next() {
                if (cursor >= array.length)
                    throw new NoSuchElementException();
                lastRet = cursor;
                return array[cursor++];
            }

            public void remove() {
                if (lastRet < 0)
                    throw new IllegalStateException();
                DelayedWorkQueue.this.remove(array[lastRet]);
                lastRet = -1;
            }
        }
    }

    /*-------------------------------- 内部使用的优先级队列 --------------------------------------*/

}
