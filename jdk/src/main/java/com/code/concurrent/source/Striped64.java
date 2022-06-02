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

package java.util.concurrent.atomic;

import java.util.function.LongBinaryOperator;
import java.util.function.DoubleBinaryOperator;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A package-local class holding common representation and mechanics
 * for classes supporting dynamic striping on 64bit values. The class
 * extends Number so that concrete subclasses must publicly do so.
 */
@SuppressWarnings("serial")
abstract class Striped64 extends Number {
    /*
     * This class maintains a lazily-initialized table of atomically
     * updated variables, plus an extra "base" field. The table size
     * is a power of two. Indexing uses masked per-thread hash codes.
     * Nearly all declarations in this class are package-private,
     * accessed directly by subclasses.
     *
     * 此类维护一个惰性初始化的原子更新变量表，以及一个额外的“基础”字段。表大小是 2 的幂。
     * 索引使用屏蔽的每线程哈希码。这个类中几乎所有的声明都是包私有的，由子类直接访问。
     *
     * Table entries are of class Cell; a variant of AtomicLong padded
     * (via @sun.misc.Contended) to reduce cache contention. Padding
     * is overkill for most Atomics because they are usually
     * irregularly scattered in memory and thus don't interfere much
     * with each other. But Atomic objects residing in arrays will
     * tend to be placed adjacent to each other, and so will most
     * often share cache lines (with a huge negative performance
     * impact) without this precaution.
     *
     * In part because Cells are relatively large, we avoid creating
     * them until they are needed.  When there is no contention, all
     * updates are made to the base field.  Upon first contention (a
     * failed CAS on base update), the table is initialized to size 2.
     * The table size is doubled upon further contention until
     * reaching the nearest power of two greater than or equal to the
     * number of CPUS. Table slots remain empty (null) until they are
     * needed.
     *
     * A single spinlock ("cellsBusy") is used for initializing and
     * resizing the table, as well as populating slots with new Cells.
     * There is no need for a blocking lock; when the lock is not
     * available, threads try other slots (or the base).  During these
     * retries, there is increased contention and reduced locality,
     * which is still better than alternatives.
     *
     * The Thread probe fields maintained via ThreadLocalRandom serve
     * as per-thread hash codes. We let them remain uninitialized as
     * zero (if they come in this way) until they contend at slot
     * 0. They are then initialized to values that typically do not
     * often conflict with others.  Contention and/or table collisions
     * are indicated by failed CASes when performing an update
     * operation. Upon a collision, if the table size is less than
     * the capacity, it is doubled in size unless some other thread
     * holds the lock. If a hashed slot is empty, and lock is
     * available, a new Cell is created. Otherwise, if the slot
     * exists, a CAS is tried.  Retries proceed by "double hashing",
     * using a secondary hash (Marsaglia XorShift) to try to find a
     * free slot.
     *
     * The table size is capped because, when there are more threads
     * than CPUs, supposing that each thread were bound to a CPU,
     * there would exist a perfect hash function mapping threads to
     * slots that eliminates collisions. When we reach capacity, we
     * search for this mapping by randomly varying the hash codes of
     * colliding threads.  Because search is random, and collisions
     * only become known via CAS failures, convergence can be slow,
     * and because threads are typically not bound to CPUS forever,
     * may not occur at all. However, despite these limitations,
     * observed contention rates are typically low in these cases.
     *
     * It is possible for a Cell to become unused when threads that
     * once hashed to it terminate, as well as in the case where
     * doubling the table causes no thread to hash to it under
     * expanded mask.  We do not try to detect or remove such cells,
     * under the assumption that for long-running instances, observed
     * contention levels will recur, so the cells will eventually be
     * needed again; and for short-lived ones, it does not matter.
     */

    /**
     * Padded variant of AtomicLong supporting only raw accesses plus CAS.
     * AtomicLong 的填充变体仅支持原始访问和 CAS，计算单元
     * <p>
     * <p>
     * <p>
     * JVM intrinsics note: It would be possible to use a release-only
     * form of CAS here, if it were provided.
     *
     * @sun.misc.Contended 注解，用于消除伪共享
     */
    @sun.misc.Contended
    static final class Cell {
        /**
         * 计数属性，使用 volatile 修饰保证可见性
         */
        volatile long value;

        Cell(long x) {
            value = x;
        }

        /**
         * CAS 更新计数值
         *
         * @param cmp 原始值
         * @param val 新值
         * @return
         */
        final boolean cas(long cmp, long val) {
            return UNSAFE.compareAndSwapLong(this, valueOffset, cmp, val);
        }

        // Unsafe mechanics
        // Unsafe 实例
        private static final sun.misc.Unsafe UNSAFE;
        // value 字段的偏移量
        private static final long valueOffset;

        static {
            try {
                UNSAFE = sun.misc.Unsafe.getUnsafe();
                Class<?> ak = Cell.class;
                valueOffset = UNSAFE.objectFieldOffset
                        (ak.getDeclaredField("value"));
            } catch (Exception e) {
                throw new Error(e);
            }
        }
    }

    /**
     * Number of CPUS, to place bound on table size
     * CPU 核数，用于限制 cells 数组的大小
     */
    static final int NCPU = Runtime.getRuntime().availableProcessors();

    /**
     * Table of cells. When non-null, size is a power of 2.
     * <p>
     * 计算单位数组，大小是 2^n；
     * 核心思想是，每个线程会映射自己的 Cell 进行累加，从而减少竞争。
     */
    transient volatile Cell[] cells;

    /**
     * Base value, used mainly when there is no contention, but also as
     * a fallback during table initialization races. Updated via CAS.
     * <p>
     * 基础计算器，用于最初无竞争的情况下以及初始化计数单元失败的情况
     */
    transient volatile long base;


    /**
     * Spinlock (locked via CAS) used when resizing and/or creating Cells.
     * 通过 CAS 更新该值，标记当前是否有线程在创建或扩容 cells，或创建 Cell
     */
    transient volatile int cellsBusy;

    /**
     * Package-private default constructor
     */
    Striped64() {
    }

    /**
     * CASes the base field.
     * <p>
     * CAS 更新 base 基础计数器
     */
    final boolean casBase(long cmp, long val) {
        return UNSAFE.compareAndSwapLong(this, BASE, cmp, val);
    }

    /**
     * CASes the cellsBusy field from 0 to 1 to acquire lock.
     * <p>
     * CAS 更新 cellsBusy 为 1，以标记操作 Cell 中
     */
    final boolean casCellsBusy() {
        return UNSAFE.compareAndSwapInt(this, CELLSBUSY, 0, 1);
    }

    /**
     * Returns the probe value for the current thread.
     * 返回当前线程的探测值。返回的是线程中的 threadLocalRandomProbe 字段，它是通过随机数生成的一个值，对于一个确定的线程，这个值是固定的，除非刻意修改它
     * Duplicated from ThreadLocalRandom because of packaging restrictions.
     */
    static final int getProbe() {
        return UNSAFE.getInt(Thread.currentThread(), PROBE);
    }

    /**
     * Pseudo-randomly advances and records the given probe value for the
     * given thread.
     * Duplicated from ThreadLocalRandom because of packaging restrictions.
     */
    static final int advanceProbe(int probe) {
        probe ^= probe << 13;   // xorshift
        probe ^= probe >>> 17;
        probe ^= probe << 5;
        UNSAFE.putInt(Thread.currentThread(), PROBE, probe);
        return probe;
    }

    /**
     * Handles cases of updates involving initialization, resizing,
     * creating new Cells, and/or contention. See above for
     * explanation. This method suffers the usual non-modularity
     * problems of optimistic retry code, relying on rechecked sets of
     * reads.
     * <p>
     * 处理涉及初始化、调整大小、创建新单元和或争用的更新案例。参见上面的解释。这种方法存在乐观重试代码的常见非模块化问题，依赖于重新检查的读取集。
     *
     * @param x              the value 待更新的值
     * @param fn             the update function, or null for add (this convention avoids the need for an extra field or function in LongAdder). 更新函数，或 null 用于添加（此约定避免了 LongAdder 中需要额外的字段或函数）。
     * @param wasUncontended false if CAS failed before call 进入该方法前，是否 CAS 操作失败，也就是是否有竞争
     */
    final void longAccumulate(long x, LongBinaryOperator fn,
                              boolean wasUncontended) {

        // 存储线程的 probe 值
        int h;
        // 如果 getProbe() 方法返回 0 ，说明随机数未初始化
        if ((h = getProbe()) == 0) {
            // 强制初始化
            ThreadLocalRandom.current(); // force initialization
            // 重新获取 probe 值
            h = getProbe();

            // 都未初始化，肯定还不存在竞争的情况
            wasUncontended = true;
        }

        // 是否发生碰撞，即多个线程映射到同一个 Cell 元素
        boolean collide = false;                // True if last slot nonempty
        for (; ; ) {
            Cell[] as;
            Cell a;
            int n;
            long v;

            // cell 已经初始化过的情况
            if ((as = cells) != null && (n = as.length) > 0) {
                // 当前线程映射的 Cell 为空，那么尝试创建一个 Cell
                if ((a = as[(n - 1) & h]) == null) {

                    // 当前无其它线程在创建或扩容 cells，也没有线程在创建 Cell
                    if (cellsBusy == 0) {       // Try to attach new Cell
                        // 创建一个 Cell，值为当前需要增加的值
                        Cell r = new Cell(x);   // Optimistically create

                        // 再次检测 cellsBusy 的状态，如果没有其它线程忙碌，那么当前线程 CAS 设置值为 1，相当于获取了锁
                        // spin lock
                        if (cellsBusy == 0 && casCellsBusy()) {
                            // 标记是否创建成功
                            boolean created = false;
                            try {               // Recheck under lock
                                Cell[] rs;
                                int m, j;
                                // 找到当前线程映射到 cells 数组中的位置
                                if ((rs = cells) != null &&
                                        (m = rs.length) > 0 &&
                                        rs[j = (m - 1) & h] == null) {
                                    // 将创建的 Cell 放在 cells
                                    rs[j] = r;
                                    // 标记成功
                                    created = true;
                                }

                            } finally {
                                // 相当于释放锁
                                cellsBusy = 0;
                            }

                            // 创建成功后直接返回
                            if (created)
                                break;

                            // 创建不成功，下一轮循环重试
                            continue;           // Slot is now non-empty
                        }
                    }

                    // 当前线程映射的位置为空，自然是没有发生碰撞
                    collide = false;

                    // 当前线程映射的 Cell 不为空且更新失败了，表示有竞争。这里简单地设为 true，重置线程的 probe 并自旋重试。
                    // 这里对应调用方法 add() 中的 Cell 冲突更新的情况；设置 wasUncontended 为 true
                } else if (!wasUncontended)       // CAS already known to fail
                    wasUncontended = true;      // Continue after rehash

                    // 至少 1 轮重试
                    // 尝试 CAS 更新当前线程映射的 Cell 的值，如果成功返回即可；失败重置线程的 probe 再进行操作
                else if (a.cas(v = a.value, ((fn == null) ? v + x :
                        fn.applyAsLong(v, x))))
                    break;

                    // 如果 cells 数组的长度达到了 CPU 核心数，或者 cells 扩容了，那么重试即可。
                    // 设置 collide 为 false ，并通过下面的语句修改线程的 probe 再重新尝试
                /*
                  todo 特别说明：
                   1. 如果 cells 数组的长度达到了 CPU 核心数就不会扩容了，即使竞争激烈。而 cells 数组的大小是 2^n ，这就意味着 cells 数组最大只能达到 >= NCPU 的最小2次方；比如服务器是 8 核的，那么 cells 数组的最大只会到 8 ，
                       达到 8 就不会扩容了。
                   2. 同一个 CPU 核心同时只能运行一个线程。
                 */
                else if (n >= NCPU || cells != as)
                    collide = false;            // At max size or stale

                    // 执行到这里说明发生了碰撞，且竞争失败了，则设置 collide 为 true 表示发生碰撞竞争失败，然后进行重试
                else if (!collide)
                    collide = true;

                    // todo 只有重试一定次数后仍然失败才会扩容
                else if (cellsBusy == 0 && casCellsBusy()) {
                    try {
                        // 检查是否有其它线程已经扩容过了
                        if (cells == as) {      // Expand table unless stale

                            // 新数组大小是原来数组的两倍
                            Cell[] rs = new Cell[n << 1];

                            // 把旧数组元素直接拷贝到新数组
                            for (int i = 0; i < n; ++i)
                                rs[i] = as[i];

                            // 设置 cells 为新数组
                            cells = rs;
                        }
                    } finally {
                        // 释放资格
                        cellsBusy = 0;
                    }

                    // 扩容成功后，重置 collide，解除冲突标志
                    collide = false;

                    // 使用扩容后的新数组，重新尝试
                    continue;                   // Retry with expanded table
                }

                // 更新失败或者达到了CPU核心数，重新生成probe，并重试
                h = advanceProbe(h);


                // 未初始化过 cells 数组，尝试获取资格并初始化 cells 数组
            } else if (cellsBusy == 0 && cells == as && casCellsBusy()) {
                // 标记初始化成功
                boolean init = false;
                try {                           // Initialize table
                    // 检测是否有其它线程初始化过
                    if (cells == as) {
                        // 新建一个大小为 2 的 Cell 数组
                        Cell[] rs = new Cell[2];

                        // 找到当前线程映射到的位置，并创建对应的 Cell
                        rs[h & 1] = new Cell(x);

                        // 将创建的数组赋值给 cells
                        cells = rs;

                        // 初始化成功
                        init = true;
                    }

                } finally {
                    // 释放资格
                    cellsBusy = 0;
                }

                // 初始化成功直接返回，因为增加的值已经同时创建到Cell中了
                if (init)
                    break;

                // 如果初始化 cells 数组有竞争，就尝试更新基础计数器 base，成功返回即可；失败重试；
            } else if (casBase(v = base, ((fn == null) ? v + x :
                    fn.applyAsLong(v, x))))
                break;                          // Fall back on using base
        }
    }

    /**
     * Same as longAccumulate, but injecting long/double conversions
     * in too many places to sensibly merge with long version, given
     * the low-overhead requirements of this class. So must instead be
     * maintained by copy/paste/adapt.
     */
    final void doubleAccumulate(double x, DoubleBinaryOperator fn,
                                boolean wasUncontended) {
        int h;
        if ((h = getProbe()) == 0) {
            ThreadLocalRandom.current(); // force initialization
            h = getProbe();
            wasUncontended = true;
        }
        boolean collide = false;                // True if last slot nonempty
        for (; ; ) {
            Cell[] as;
            Cell a;
            int n;
            long v;
            if ((as = cells) != null && (n = as.length) > 0) {
                if ((a = as[(n - 1) & h]) == null) {
                    if (cellsBusy == 0) {       // Try to attach new Cell
                        Cell r = new Cell(Double.doubleToRawLongBits(x));
                        if (cellsBusy == 0 && casCellsBusy()) {
                            boolean created = false;
                            try {               // Recheck under lock
                                Cell[] rs;
                                int m, j;
                                if ((rs = cells) != null &&
                                        (m = rs.length) > 0 &&
                                        rs[j = (m - 1) & h] == null) {
                                    rs[j] = r;
                                    created = true;
                                }
                            } finally {
                                cellsBusy = 0;
                            }
                            if (created)
                                break;
                            continue;           // Slot is now non-empty
                        }
                    }
                    collide = false;
                } else if (!wasUncontended)       // CAS already known to fail
                    wasUncontended = true;      // Continue after rehash
                else if (a.cas(v = a.value,
                        ((fn == null) ?
                                Double.doubleToRawLongBits
                                        (Double.longBitsToDouble(v) + x) :
                                Double.doubleToRawLongBits
                                        (fn.applyAsDouble
                                                (Double.longBitsToDouble(v), x)))))
                    break;
                else if (n >= NCPU || cells != as)
                    collide = false;            // At max size or stale
                else if (!collide)
                    collide = true;
                else if (cellsBusy == 0 && casCellsBusy()) {
                    try {
                        if (cells == as) {      // Expand table unless stale
                            Cell[] rs = new Cell[n << 1];
                            for (int i = 0; i < n; ++i)
                                rs[i] = as[i];
                            cells = rs;
                        }
                    } finally {
                        cellsBusy = 0;
                    }
                    collide = false;
                    continue;                   // Retry with expanded table
                }
                h = advanceProbe(h);
            } else if (cellsBusy == 0 && cells == as && casCellsBusy()) {
                boolean init = false;
                try {                           // Initialize table
                    if (cells == as) {
                        Cell[] rs = new Cell[2];
                        rs[h & 1] = new Cell(Double.doubleToRawLongBits(x));
                        cells = rs;
                        init = true;
                    }
                } finally {
                    cellsBusy = 0;
                }
                if (init)
                    break;
            } else if (casBase(v = base,
                    ((fn == null) ?
                            Double.doubleToRawLongBits
                                    (Double.longBitsToDouble(v) + x) :
                            Double.doubleToRawLongBits
                                    (fn.applyAsDouble
                                            (Double.longBitsToDouble(v), x)))))
                break;                          // Fall back on using base
        }
    }

    // Unsafe mechanics
    private static final sun.misc.Unsafe UNSAFE;
    private static final long BASE;
    private static final long CELLSBUSY;
    private static final long PROBE;

    static {
        try {
            UNSAFE = sun.misc.Unsafe.getUnsafe();
            Class<?> sk = Striped64.class;
            BASE = UNSAFE.objectFieldOffset
                    (sk.getDeclaredField("base"));
            CELLSBUSY = UNSAFE.objectFieldOffset
                    (sk.getDeclaredField("cellsBusy"));
            Class<?> tk = Thread.class;
            PROBE = UNSAFE.objectFieldOffset
                    (tk.getDeclaredField("threadLocalRandomProbe"));
        } catch (Exception e) {
            throw new Error(e);
        }
    }

}
