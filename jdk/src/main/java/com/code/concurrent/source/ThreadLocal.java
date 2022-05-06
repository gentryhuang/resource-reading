//package java.lang;
//
//import java.lang.ref.*;
//import java.util.Objects;
//import java.util.concurrent.atomic.AtomicInteger;
//import java.util.function.Supplier;
//
///**
// * 说明：
// * 1 ThreadLocal 类提供了线程局部变量。这些变量与普通变量不同，每个线程都可以通过 ThreadLocal 的 get 或 set 方法来访问自己的独立初始化的变量副本。
// * 2 ThreadLocal 实例通常是类中的 private static 字段，他们希望将状态（入用户ID或事物ID）与某个线程相关关联
// *
// * @param <T>
// */
//public class ThreadLocal<T> {
//
//
//    /**
//     * 1 作为 ThreadLocal 实例的变量，私有、不可变。每当创建 ThreadLocal 时这个值都会累加 HASH_INCREMENT
//     * 2 主要为了多个 ThreadLocal 实例的情况下，让哈希码能均匀的分布在2的N次方的数组里, 即 Entry[] table
//     */
//    private final int threadLocalHashCode = nextHashCode();
//
//    /**
//     * 二进制：-10011110001101110111100110111001
//     * 十进制：-2654435769
//     * 说明：
//     * 1 Entry[] table 的大小必须是 2^N，那 len-1 的二进制表示就是低位连续的N个1，
//     * 2 key.threadLocalHashCode & (len-1) 的值就是 threadLocalHashCode 的低 N 位
//     * 3 因此要求 threadLocalHashCode 的值要均匀
//     * 原因：
//     * 取该值与fibonacci hashing(斐波那契散列法)以及黄金分割有关，目的就是为了让哈希码能均匀的分布在2的n次方的数组里, 也就是Entry[] table中。
//     */
//    private static final int HASH_INCREMENT = 0x61c88647;
//
//
//    /**
//     * 作为 ThreadLocal 类变量
//     * <p>
//     * 基于当前 ThreadLocal ，下一个要给出的哈希码，自动更新，从 0 开始计数。
//     * 每次获取当前值并加上固定的 HASH_INCREMENT
//     */
//    private static AtomicInteger nextHashCode = new AtomicInteger();
//
//    /**
//     * 返回下一个 ThreadLocal 的 hash
//     */
//    private static int nextHashCode() {
//        return nextHashCode.getAndAdd(HASH_INCREMENT);
//    }
//
//
//    /**
//     * 初始化值，子类覆盖
//     *
//     * @return
//     */
//    protected T initialValue() {
//        return null;
//    }
//
//
//    public ThreadLocal() {
//    }
//
//    /**
//     * 获取当前线程的线程副本变量
//     *
//     * @return
//     */
//    public T get() {
//        // 1 获取当前线程
//        Thread t = Thread.currentThread();
//
//        // 2 根据当前线程获取其 ThreadLocalMap 实例
//        ThreadLocalMap map = getMap(t);
//
//        // 3 如果当前线程的 ThreadLocalMap 不为空
//        if (map != null) {
//            ThreadLocalMap.Entry e = map.getEntry(this);
//            if (e != null) {
//                @SuppressWarnings("unchecked")
//                T result = (T) e.value;
//                return result;
//            }
//        }
//
//        // 4 如果当前线程的 ThreadLocalMap 为空，则调用 initialValue() 方法初始化值
//        return setInitialValue();
//    }
//
//
//    /**
//     * 设置初始化值
//     *
//     * @return the initial value
//     */
//    private T setInitialValue() {
//
//        // 1 调用 initialValue() 方法获取指定的初始化值，默认为 null
//        T value = initialValue();
//
//        // 2 获取当前线程
//        Thread t = Thread.currentThread();
//
//        // 3 获取当前线程的 ThreadLocalMap
//        ThreadLocalMap map = getMap(t);
//
//        // 4 如果 ThreadLocalMap 不为空，则直接将初始值设置到 ThreadLocalMap 中
//        if (map != null)
//            map.set(this, value);
//
//            // 5 如果 ThreadLocalMap 为空，则创建 ThreadLocalMap 对象，并设置初始值
//        else
//            createMap(t, value);
//        return value;
//    }
//
//    /**
//     * 设置当前线程变量的值 value
//     *
//     * @param value
//     */
//    public void set(T value) {
//        // 1 获取当前线程
//        Thread t = Thread.currentThread();
//
//        // 2 根据当前线程获取其成员变量 threadLocals 所指向的 ThreadLocalMap 对象
//        ThreadLocalMap map = getMap(t);
//
//        // 3 判断当前线程的 ThreadLocalMap 是否为空
//        if (map != null)
//            // 3.1 如果不为空，说明当前线程内部已经有ThreadLocalMap对象了
//            // 那么直接将当前对应的 ThreadLocal 对象的引用作为键，存入的 value 作为值存储到 ThreadLocalMap 中
//            map.set(this, value);
//        else
//            // 3.2 创建一个 ThreadLocalMap 对象并将值存入到该对象中，并赋值给当前线程的threadLocals成员变量
//            createMap(t, value);
//    }
//
//    /**
//     * 为线程 t 初始化 ThreadLocalMap 对象
//     * 说明：线程中的 ThreadLocalMap 使用的是延迟初始化，第一次调用get()或者set()方法的时候才会进行初始化。
//     *
//     * @param t          当前线程
//     * @param firstValue 值
//     */
//    void createMap(Thread t, T firstValue) {
//        t.threadLocals = new ThreadLocalMap(this, firstValue);
//    }
//
//
//    /**
//     * 清理
//     */
//    public void remove() {
//        // 1 获取当前线程持有的 ThreadLocalMap
//        ThreadLocalMap m = getMap(Thread.currentThread());
//
//        // 2 删除 ThreadLocalMap 中当前 ThreadLocal 相关的信息
//        // 包括清理对应的引用和值
//        if (m != null)
//            m.remove(this);
//    }
//
//    /**
//     * Get the map associated with a ThreadLocal. Overridden in
//     * InheritableThreadLocal.
//     *
//     * @param t the current thread
//     * @return the map
//     */
//    ThreadLocalMap getMap(Thread t) {
//        return t.threadLocals;
//    }
//
//
//    /**
//     * Factory method to create map of inherited thread locals.
//     * Designed to be called only from Thread constructor.
//     *
//     * @param parentMap the map associated with parent thread
//     * @return a map containing the parent's inheritable bindings
//     */
//    static ThreadLocalMap createInheritedMap(ThreadLocalMap parentMap) {
//        return new ThreadLocalMap(parentMap);
//    }
//
//
//    /**
//     * ThreadLocal 的静态内部类，本质上是一个 Map，和 HashMap 类似，依然是 key-value 的形式，具体由 Entry 结构封装。
//     * <p>
//     * 1 Entry 结构：
//     * key: 持有 ThreadLocal 实例的弱引用
//     * value: 线程局部变量副本
//     * 2 ThreadLocalMap 是一个定制的哈希表，用于维护线程本地值。
//     */
//    static class ThreadLocalMap {
//
//        /**
//         * 1 Entry 继承 WeakReference，并且使用 ThreadLocal 作为 key，使用弱引用，这样可以将ThreadLocal对象的生命周期和线程生命周期解绑，
//         * 持有对ThreadLocal的弱引用，可以使得ThreadLocal在没有其他强引用的时候被回收掉，这样可以避免因为线程得不到销毁导致ThreadLocal对象无法被回收。
//         * 2 如果 key 为 null ，即 entry.get() == null 表示 key 不再被引用，ThreadLocal 对象被回收，因此正常情况下 entry 可以从数组清除
//         */
//        static class Entry extends WeakReference<ThreadLocal<?>> {
//
//            /**
//             * 与这个ThreadLocal关联的值，也就是线程局部变量缓存值
//             */
//            Object value;
//
//            /**
//             *  Entry的 key 是对 ThreadLocal 的弱引用，当抛弃掉 ThreadLocal 对象时，垃圾收集器会忽略这个 key 的引用而清理掉 ThreadLocal 对象，放置内存泄漏。
//             * @param k
//             * @param v
//             */
//            Entry(ThreadLocal<?> k, Object v) {
//                super(k);
//                value = v;
//            }
//        }
//
//        /**
//         * 数组初始化容量
//         */
//        private static final int INITIAL_CAPACITY = 16;
//
//        /**
//         * 存储数据的 Entry 数组，长度是 2 的幂。
//         */
//        private Entry[] table;
//
//        /**
//         * 数组中元素的个数
//         */
//        private int size = 0;
//
//        /**
//         * 数组扩容阈值，默认为0，创建了 ThreadLocalMap 对象后会被重新设置
//         */
//        private int threshold; // Default to 0
//
//        /**
//         * 设置调整大小阈值，以保持在最坏 2/3 的负载因子。
//         */
//        private void setThreshold(int len) {
//            threshold = len * 2 / 3;
//        }
//
//
//        /* ThreadLocalMap使用线性探测法来解决哈希冲突 */
//        // 向后一个位置找，注意从头开始的情况
//        private static int nextIndex(int i, int len) {
//            return ((i + 1 < len) ? i + 1 : 0);
//        }
//
//        // 向前一个位置找，注意跳到尾部的情况
//        private static int prevIndex(int i, int len) {
//            return ((i - 1 >= 0) ? i - 1 : len - 1);
//        }
//
//
//        /**
//         * 懒加载构造方法
//         *
//         * @param firstKey   ThreadLocal 引用
//         * @param firstValue 设置的值
//         */
//        ThreadLocalMap(ThreadLocal<?> firstKey, Object firstValue) {
//            // 1 初始化 Entry 数组，大小为 16
//            table = new Entry[INITIAL_CAPACITY];
//
//            // 2 获取 ThreadLocal 的 hash 值(该值是累加的)
//            // 计算当前 key 对应的数组下标位置， 和 HashMap 的位运算代替取模原理一样
//            int i = firstKey.threadLocalHashCode & (INITIAL_CAPACITY - 1);
//
//            // 3 将 Entry 对象存入到数组指定的位置
//            table[i] = new Entry(firstKey, firstValue);
//
//            // 4 记录数组元素个数
//            size = 1;
//
//            // 5 初始化扩容阈值
//            setThreshold(INITIAL_CAPACITY);
//        }
//
//        /**
//         * Construct a new map including all Inheritable ThreadLocals
//         * from given parent map. Called only by createInheritedMap.
//         *
//         * @param parentMap the map associated with parent thread.
//         */
//        private ThreadLocalMap(ThreadLocalMap parentMap) {
//            // 1 获取 parentMap 的 Table 信息
//            Entry[] parentTable = parentMap.table;
//            int len = parentTable.length;
//            setThreshold(len);
//
//            // 2 创建新的 Table
//            table = new Entry[len];
//
//            // 3 将 parentMap 中的数据依次映射到新创建的 Table 中
//            for (int j = 0; j < len; j++) {
//                // 3.1 获取当前 Entry
//                Entry e = parentTable[j];
//                if (e != null) {
//
//                    // 3.2 获取 Entry 中的 key
//                    @SuppressWarnings("unchecked")
//                    ThreadLocal<Object> key = (ThreadLocal<Object>) e.get();
//
//                    // 3.3 Entry 有效的话就进行映射
//                    if (key != null) {
//
//                        // 用于获取父线程变量值的转换，默认就是父线程 Entry.value 值
//                        Object value = key.childValue(e.value);
//
//                        // 线性探测存储元素
//                        Entry c = new Entry(key, value);
//                        int h = key.threadLocalHashCode & (len - 1);
//                        while (table[h] != null)
//                            h = nextIndex(h, len);
//                        table[h] = c;
//                        size++;
//                    }
//                }
//            }
//        }
//
//        /**
//         * 获取与 key 关联的 Entry
//         *
//         * @param key the thread local object
//         * @return the entry associated with key, or null if no such
//         */
//        private Entry getEntry(ThreadLocal<?> key) {
//            // 1 根据 key 计算下标
//            int i = key.threadLocalHashCode & (table.length - 1);
//
//            // 2 根据下标获取 Entry
//            Entry e = table[i];
//            if (e != null && e.get() == key)
//                return e;
//
//            // 3 通过下标直接得到的 Entry 不是要找的，那么就线性探测找
//            else
//                return getEntryAfterMiss(key, i, e);
//        }
//
//        /**
//         * getEntry方法的版本，用于在键未在其直接散列槽中找到时使用。
//         * 注意，该方法会清理无效 Entry
//         *
//         * @param key the thread local object
//         * @param i   the table index for key's hash code
//         * @param e   the entry at table[i]
//         * @return the entry associated with key, or null if no such
//         */
//        private Entry getEntryAfterMiss(ThreadLocal<?> key, int i, Entry e) {
//            // 1 获取 Table 信息
//            Entry[] tab = table;
//            int len = tab.length;
//
//            // 2 线性探测
//            while (e != null) {
//                // 获取 Entry 的 key
//                ThreadLocal<?> k = e.get();
//
//                // 2.1 命中，直接返回
//                if (k == key)
//                    return e;
//                // 2.2 无效 Entry，执行连续段删除
//                if (k == null)
//                    expungeStaleEntry(i);
//
//                // 2.3 获取下个位置
//                else
//                    i = nextIndex(i, len);
//                e = tab[i];
//            }
//
//            // 3 遍历完也没查到，返回 null
//            return null;
//        }
//
//        /**
//         * 设置与 key 关联的值
//         * 说明：
//         * ThreadLocal 的 set 方法，就是为了将指定的值存入到指定线程的 ThreadLocalMap 对象中，具体还是通过 ThreadLocalMap 的 set 方法
//         *
//         * @param key   the thread local object
//         * @param value the value to be set
//         */
//        private void set(ThreadLocal<?> key, Object value) {
//
//            // 1 获取数组信息
//            Entry[] tab = table;
//            int len = tab.length;
//
//            // 2 计算当前 ThreadLocal 对象引用作为键在数组中的下标
//            int i = key.threadLocalHashCode & (len - 1);
//
//            // 3 根据获取到的下标进行线性探测，寻找空的位置。
//            for (Entry e = tab[i];
//                 e != null;
//                // 线性探测，搜寻下一个合适的位置
//                 e = tab[i = nextIndex(i, len)]) {
//
//                // 3.1 获取当前 Entry 中的 key ，即 ThreadLocal 的引用，注意是弱引用。
//                ThreadLocal<?> k = e.get();
//
//                // 3.2 判断和当前的 ThreadLocal 对象是否是同一个对象，如果是，那么直接进行值替换，并结束方法，
//                if (k == key) {
//                    e.value = value;
//                    return;
//                }
//
//                // 3.3 判断当前 Entry 的 key 是否失效，即是否被回收（弱引用）。
//                // 如果失效，说明当前位置可以重新使用，就使用新的 key-value 将其替换
//                // 该过程还会删除其它无效的 entry
//                if (k == null) {
//                    replaceStaleEntry(key, value, i);
//                    return;
//                }
//            }
//
//            // 4 找到某个空位置，直接将键值对设置到这个位置上。
//            tab[i] = new Entry(key, value);
//            int sz = ++size;
//
//            // 5 尝试清理无效 entry ，如果没有可清理的 entry 且数组元素大小 >= 扩容阈值，则进行 rehash
//            if (!cleanSomeSlots(i, sz) && sz >= threshold)
//                rehash();
//        }
//
//        /**
//         * 删除
//         */
//        private void remove(ThreadLocal<?> key) {
//            // 1 获取 Table 信息
//            Entry[] tab = table;
//            int len = tab.length;
//
//            // 2 计算 key 对应的下标
//            int i = key.threadLocalHashCode & (len - 1);
//
//            // 3 进行线性探测，查找正确的 Entry
//            for (Entry e = tab[i];
//                 e != null;
//                 e = tab[i = nextIndex(i, len)]) {
//
//                // 找到对应的 Entry
//                if (e.get() == key) {
//                    // 调用 WeakReference.clear() 方法清除对应的引用
//                    e.clear();
//
//                    // 连续段删除 Entry
//                    expungeStaleEntry(i);
//                    return;
//                }
//            }
//        }
//
//        /**
//         * 替换无效 Entry ，并删除其它无效的 Entry。
//         * 说明：
//         * 重用无效 Entry 位置有两种可能：
//         * 1 如果 Table 中有和要插入的 key 相同的 Entry ，那么就使用更新 value 后的该 Entry 替换无效 Entry 以避免新创建 Entry 。同时，该 Entry 成为了无效 Entry ，等待后续删除即可。
//         * 2 如果 Table 中没有和要插入的 key 相同的 Entry ，那么直接创建 Entry 重用无效 Entry
//         *
//         * @param key key
//         * @param value value
//         * @param staleSlot 无效 entry 的位置
//         */
//        private void replaceStaleEntry(ThreadLocal<?> key, Object value, int staleSlot) {
//            // 1 获取数组信息
//            Entry[] tab = table;
//            int len = tab.length;
//            Entry e;
//
//            // 标志 Table 中是否存在无效 Entry。slotToExpunge != staleSlot 说明 table 中存在无效 Entry 需要进行清理，否则说明没有。
//            int slotToExpunge = staleSlot;
//
//            // 2 根据传入的无效 Entry 的位置 staleSlot 向前扫描一段连续非空的 Entry ，并记录最后一个无效的 Entry 的位置。或者扫描完也没有找到。
//            for (int i = prevIndex(staleSlot, len); (e = tab[i]) != null; i = prevIndex(i, len))
//                // 2.1 如果是无效 Entry ，则更新删除标记 slotToExpunge
//                if (e.get() == null)
//                    slotToExpunge = i;
//
//
//            // 3 根据传入的无效 Entry 向后扫描一段连续的 Entry ,以寻找是否有相同 key 的 Entry，以及在需要时更新删除标记位 slotToExpunge
//            // 找到运行的关键字或末尾的空槽，以最先出现的为准
//            for (int i = nextIndex(staleSlot, len);
//                 (e = tab[i]) != null;
//                 i = nextIndex(i, len)) {
//
//                // 3.1 获取当前 Entry 的 key
//                ThreadLocal<?> k = e.get();
//
//                // 3.2 如果找到了具有相同的 key ，则更新其值。也就是更新其值并将其与传入的无效 Entry 替换，即与 table[staleSlot] 进行替换
//                if (k == key) {
//                    // 3.2.1 更新为传入的 value
//                    e.value = value;
//
//                    // 3.2.2 使用相同 key 的 Entry 重用失效位置以避免新创建一个 Entry，以维持散列表的顺序。
//                    tab[i] = tab[staleSlot];
//                    tab[staleSlot] = e;
//
//                    // 3.2.3 如果向前查找没有找到无效的 Entry，则更新删除标记位 slotToExpunge 为当前位置 i，此时 i 位置对应的是无效 Entry
//                    // todo slotToExpunge 是否可以覆盖到 i
//                    if (slotToExpunge == staleSlot)
//                        slotToExpunge = i;
//
//                    // 3.3.4 将新过时的槽或在其上面遇到的任何其他过时槽发送到expungeStaleEntry，以删除或重新散列运行中的所有其他条目。
//                    cleanSomeSlots(expungeStaleEntry(slotToExpunge), len);
//                    return;
//                }
//
//                // 如果向前查找没有找到无效 entry，并且当前向后扫描的entry无效，则更新 slotToExpunge 为当前值 i
//                if (k == null && slotToExpunge == staleSlot)
//                    slotToExpunge = i;
//            }
//
//            // 4 执行到这里，说明 table 中不存在相同 key 的 Entry，此时只需直接重用无效位置即可
//            tab[staleSlot].value = null;
//            tab[staleSlot] = new Entry(key, value);
//
//            // 5 slotToExpunge != staleSlot 说明 table 中存在无效 Entry 需要进行清理
//            if (slotToExpunge != staleSlot)
//                cleanSomeSlots(expungeStaleEntry(slotToExpunge), len);
//        }
//
//        /**
//         * 删除过期的条目，是连续段删除
//         *
//         * 1 根据传入的 stateSlot 清理对应的无效 Entry
//         * 2 根据当前传入的 stateSlot 向后扫描一段连续非空的 Entry ，对可能存在 hash 冲突的 Entry 进行 rehash ，并且清理遇到的无效 Entry
//         *
//         */
//        private int expungeStaleEntry(int staleSlot) {
//            // 1 获取 Table 信息
//            Entry[] tab = table;
//            int len = tab.length;
//
//            // 2 清理无效 Entry ，并递减元素个数
//            tab[staleSlot].value = null;
//            tab[staleSlot] = null;
//            size--;
//
//            // Rehash until we encounter null
//            Entry e;
//            int i;
//
//            // 3 从 stateSlot 开始向后扫描一段连续的非空的 Entry
//            for (i = nextIndex(staleSlot, len);
//                 (e = tab[i]) != null;
//                 i = nextIndex(i, len)) {
//
//                // 3.1 获取当前 Entry 的 k
//                ThreadLocal<?> k = e.get();
//
//                // 3.2 如果遇到key为null,表示无效entry，进行清理.
//                if (k == null) {
//                    e.value = null;
//                    tab[i] = null;
//                    size--;
//
//                    // 3.3 如果 key 不为 null ，则对可能存在 hash 冲突的 Entry 进行 rehash
//                } else {
//
//                    // 计算 key 对应的下标，如果与现在所在位置不一致，认为存在 hash 冲突，置空当前 table[i] ，
//                    // 并从 h 开始向后线性探测到第一个空的 slot，把当前的 entry 移动过去。
//                    int h = k.threadLocalHashCode & (len - 1);
//                    if (h != i) {
//                        tab[i] = null;
//
//                        // Unlike Knuth 6.4 Algorithm R, we must scan until
//                        // null because multiple entries could have been stale.
//                        while (tab[h] != null)
//                            h = nextIndex(h, len);
//                        tab[h] = e;
//                    }
//                }
//            }
//
//            // 下一个为空的下标
//            return i;
//        }
//
//        /**
//         * 启发式的扫描清除，扫描次数由传入的参数n决定
//         *
//         * @param i 从i向后开始扫描（不包括i，因为索引为i的Slot肯定为null）
//         * @param n  控制扫描次数，正常情况下为 log2(n) ，如果找到了无效 Entry ，会将 n 重置为 table 的长度 len 进行阶段删除
//         * @return true if any stale entries have been removed.
//         */
//        private boolean cleanSomeSlots(int i, int n) {
//            // 1 删除标志
//            boolean removed = false;
//
//            // 2 数组信息
//            Entry[] tab = table;
//            int len = tab.length;
//
//            // 从 i 位置向后遍历，删除无效的 Entry
//            do {
//                i = nextIndex(i, len);
//                Entry e = tab[i];
//
//                // 如果当前 Entry 无效，则进行清理并进行连坐
//                if (e != null && e.get() == null) {
//                    n = len;
//                    removed = true;
//                    i = expungeStaleEntry(i);
//                }
//
//                // 无符号的右移动，可以用于控制扫描次数在log2(n)
//            } while ((n >>>= 1) != 0);
//
//            return removed;
//        }
//
//        /**
//         * rehash 操作
//         * 1 扫描整个 Table ，删除无效的 Entry
//         * 2 执行清理后，如果还需要扩容，则将表扩大一倍
//         */
//        private void rehash() {
//            // 1 清理所有无效 Entry
//            expungeStaleEntries();
//
//            //  2 threshold - threshold / 4 = 1en / 2，降低扩容阈值是因为上面做了一次全清理所以 size 可能会减小
//            if (size >= threshold - threshold / 4)
//                resize();
//        }
//
//        /**
//         * Table 容量扩大为原来 2 倍
//         */
//        private void resize() {
//            // 1 获取 Table 信息
//            Entry[] oldTab = table;
//            int oldLen = oldTab.length;
//
//            // 2 容量扩大为原来 2 倍
//            int newLen = oldLen * 2;
//            Entry[] newTab = new Entry[newLen];
//            int count = 0;
//
//            // 3 重新映射旧数组中的元素
//            for (int j = 0; j < oldLen; ++j) {
//                Entry e = oldTab[j];
//                if (e != null) {
//                    ThreadLocal<?> k = e.get();
//
//                    // 清理无效 Entry
//                    if (k == null) {
//                        e.value = null; // Help the GC
//
//                        // 线性探测重新设置值
//                    } else {
//                        int h = k.threadLocalHashCode & (newLen - 1);
//                        while (newTab[h] != null)
//                            h = nextIndex(h, newLen);
//                        newTab[h] = e;
//                        count++;
//                    }
//                }
//            }
//
//            // 4 设置新的阈值
//            setThreshold(newLen);
//            size = count;
//            table = newTab;
//        }
//
//        /**
//         * 扫描整个 Table ，删除无效的 Entry
//         */
//        private void expungeStaleEntries() {
//            Entry[] tab = table;
//            int len = tab.length;
//            for (int j = 0; j < len; j++) {
//                Entry e = tab[j];
//                // 无效 Entry ，则调用 expungeStaleEntry 方法删除对应位置及连坐删除 Entry
//                if (e != null && e.get() == null)
//                    expungeStaleEntry(j);
//            }
//        }
//    }
//}
