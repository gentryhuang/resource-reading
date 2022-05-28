//
//package com.code.collection;
//
//import java.io.ObjectStreamField;
//import java.io.Serializable;
//import java.util.*;
//import java.util.concurrent.ConcurrentMap;
//import java.util.concurrent.CountedCompleter;
//import java.util.concurrent.ForkJoinPool;
//import java.util.concurrent.ThreadLocalRandom;
//import java.util.concurrent.atomic.AtomicReference;
//import java.util.concurrent.locks.LockSupport;
//import java.util.concurrent.locks.ReentrantLock;
//import java.util.function.BiConsumer;
//import java.util.function.BiFunction;
//import java.util.function.Consumer;
//import java.util.function.DoubleBinaryOperator;
//import java.util.function.Function;
//import java.util.function.IntBinaryOperator;
//import java.util.function.LongBinaryOperator;
//import java.util.function.ToDoubleBiFunction;
//import java.util.function.ToDoubleFunction;
//import java.util.function.ToIntBiFunction;
//import java.util.function.ToIntFunction;
//import java.util.function.ToLongBiFunction;
//import java.util.function.ToLongFunction;
//
//import static com.code.collection.ConcurrentHashMap.comparableClassFor;
//import static com.code.collection.ConcurrentHashMap.compareComparables;
//
//
///**
// * 1. ConcurrentHashMap是HashMap的线程安全版本，内部也是使用（数组 + 链表 + 红黑树）的结构来存储元素。
// * 2.
// *
// * @param <K>
// * @param <V>
// */
//public class ConcurrentHashMap<K, V> extends AbstractMap<K, V>
//        implements ConcurrentMap<K, V>, Serializable {
//    private static final long serialVersionUID = 7249069246763182397L;
//
//
//    /* ---------------- Constants -------------- */
//
//    /**
//     * 最大容量，当两个构造函数中任何一个带参数的函数隐式指定较大的值时使用
//     */
//    private static final int MAXIMUM_CAPACITY = 1 << 30;
//
//
//    /**
//     * 默认容量大小 16，大小必须是 2^N
//     */
//    private static final int DEFAULT_CAPACITY = 16;
//
//    /**
//     * The largest possible (non-power of two) array size.
//     * 最大可能（非二的幂）数组大小。
//     * <p>
//     * Needed by toArray and related methods.
//     */
//    static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;
//
//    /**
//     * The default concurrency level for this table.
//     * 此表的默认并发级别。
//     * <p>
//     * Unused but defined for compatibility with previous versions of this class.
//     * 未使用但为与此类的先前版本兼容而定义。
//     */
//    private static final int DEFAULT_CONCURRENCY_LEVEL = 16;
//
//    /**
//     * 默认负载因子 在构造函数中未指定时使用的负载因子
//     *
//     * 当前版本的 ConcurrentHashMap 没什么用，在计算时固定为 0.75
//     */
//    private static final float LOAD_FACTOR = 0.75f;
//
//    /**
//     * 桶的树化阈值： 使用红黑树时元素个数的阈值。在存储数据时，当链表长度 >= 8时，则将链表转换成红黑树。
//     */
//    static final int TREEIFY_THRESHOLD = 8;
//
//    /**
//     * 桶的链表还原阈值：红黑树转链表阈值。
//     * 当在扩容时，在重新计算存储位置后，当原有的红黑树内数量 <= 6时，则将 红黑树转换成链表
//     */
//    static final int UNTREEIFY_THRESHOLD = 6;
//
//    /**
//     * 最小树形化容量阈值：使用红黑树时最小的表容量。当 HashMap 中的容量 >= 该值时，才允许树形化链表即将链表转成红黑树
//     */
//    static final int MIN_TREEIFY_CAPACITY = 64;
//
//    /**
//     * Minimum number of rebinnings per transfer step. Ranges are
//     * subdivided to allow multiple resizer threads.  This value
//     * serves as a lower bound to avoid resizers encountering
//     * excessive memory contention.  The value should be at least
//     * DEFAULT_CAPACITY.
//     */
//    private static final int MIN_TRANSFER_STRIDE = 16;
//
//    /**
//     * The number of bits used for generation stamp in sizeCtl.
//     * sizeCtl 中用于生成标记的位数。
//     * <p>
//     * Must be at least 6 for 32bit arrays.
//     */
//    private static int RESIZE_STAMP_BITS = 16;
//
//    /**
//     * The maximum number of threads that can help resize.
//     * <p>
//     * 可以辅助调整大小的最大线程数。
//     * <p>
//     * <p>
//     * Must fit in 32 - RESIZE_STAMP_BITS bits.
//     */
//    private static final int MAX_RESIZERS = (1 << (32 - RESIZE_STAMP_BITS)) - 1;
//
//    /**
//     * The bit shift for recording size stamp in sizeCtl.
//     * 在 sizeCtl 中记录大小标记的位移，为 16
//     */
//    private static final int RESIZE_STAMP_SHIFT = 32 - RESIZE_STAMP_BITS;
//
//    /*
//     * Encodings for Node hash fields. See above for explanation.
//     * 节点哈希值
//     */
//    static final int MOVED = -1; // hash for forwarding nodes ForwardingNode 类型节点的 hash 值
//    static final int TREEBIN = -2; // hash for roots of trees  红黑树的根节点的 hash 值
//    static final int RESERVED = -3; // hash for transient reservations
//    static final int HASH_BITS = 0x7fffffff; // usable bits of normal node hash 普通节点哈希的可用位
//
//    /**
//     * Number of CPUS, to place bounds on some sizings
//     * CPU 的数量，以限制某些大小
//     */
//    static final int NCPU = Runtime.getRuntime().availableProcessors();
//
//    /**
//     * For serialization compatibility.
//     * 为了序列化兼容性。
//     */
//    private static final ObjectStreamField[] serialPersistentFields = {
//            new ObjectStreamField("segments", Segment[].class),
//            new ObjectStreamField("segmentMask", Integer.TYPE),
//            new ObjectStreamField("segmentShift", Integer.TYPE)
//    };
//
//    /* ---------------- Nodes -------------- */
//
//    /**
//     * ConcurrentHashMap 中元素项，用于链表的情况
//     */
//    static class Node<K, V> implements Map.Entry<K, V> {
//
//        // key 对应的 hash 值
//        final int hash;
//        // key
//        final K key;
//        // value   保证线程间的可见性
//        volatile V val;
//        // 下一个元素的指针   保证线程间的可见性
//        volatile Node<K, V> next;
//
//        Node(int hash, K key, V val, Node<K, V> next) {
//            this.hash = hash;
//            this.key = key;
//            this.val = val;
//            this.next = next;
//        }
//
//        public final K getKey() {
//            return key;
//        }
//
//        public final V getValue() {
//            return val;
//        }
//
//        public final int hashCode() {
//            return key.hashCode() ^ val.hashCode();
//        }
//
//        public final String toString() {
//            return key + "=" + val;
//        }
//
//        public final V setValue(V value) {
//            throw new UnsupportedOperationException();
//        }
//
//        public final boolean equals(Object o) {
//            Object k, v, u;
//            Map.Entry<?, ?> e;
//            return ((o instanceof Map.Entry) &&
//                    (k = (e = (Map.Entry<?, ?>) o).getKey()) != null &&
//                    (v = e.getValue()) != null &&
//                    (k == key || k.equals(key)) &&
//                    (v == (u = val) || v.equals(u)));
//        }
//
//        /**
//         * Virtualized support for map.get(); overridden in subclasses.
//         */
//        Node<K, V> find(int h, Object k) {
//            Node<K, V> e = this;
//            if (k != null) {
//                do {
//                    K ek;
//                    if (e.hash == h &&
//                            ((ek = e.key) == k || (ek != null && k.equals(ek))))
//                        return e;
//                } while ((e = e.next) != null);
//            }
//            return null;
//        }
//    }
//
//    /* ---------------- Static utilities -------------- */
//
//    /**
//     * Spreads (XORs) higher bits of hash to lower and also forces top
//     * bit to 0. Because the table uses power-of-two masking, sets of
//     * hashes that vary only in bits above the current mask will
//     * always collide. (Among known examples are sets of Float keys
//     * holding consecutive whole numbers in small tables.)  So we
//     * apply a transform that spreads the impact of higher bits
//     * downward. There is a tradeoff between speed, utility, and
//     * quality of bit-spreading. Because many common sets of hashes
//     * are already reasonably distributed (so don't benefit from
//     * spreading), and because we use trees to handle large sets of
//     * collisions in bins, we just XOR some shifted bits in the
//     * cheapest possible way to reduce systematic lossage, as well as
//     * to incorporate impact of the highest bits that would otherwise
//     * never be used in index calculations because of table bounds.
//     */
//    static final int spread(int h) {
//        return (h ^ (h >>> 16)) & HASH_BITS;
//    }
//
//    /**
//     * 返回大于 c 且最近的2的整数次幂的数
//     */
//    private static final int tableSizeFor(int c) {
//        int n = c - 1;
//        n |= n >>> 1;
//        n |= n >>> 2;
//        n |= n >>> 4;
//        n |= n >>> 8;
//        n |= n >>> 16;
//        return (n < 0) ? 1 : (n >= MAXIMUM_CAPACITY) ? MAXIMUM_CAPACITY : n + 1;
//    }
//
//
//
//    /* ---------------- Table element access -------------- */
//
//    /*
//     * Volatile access methods are used for table elements as well as
//     * elements of in-progress next table while resizing.  All uses of
//     * the tab arguments must be null checked by callers.  All callers
//     * also paranoically precheck that tab's length is not zero (or an
//     * equivalent check), thus ensuring that any index argument taking
//     * the form of a hash value anded with (length - 1) is a valid
//     * index.  Note that, to be correct wrt arbitrary concurrency
//     * errors by users, these checks must operate on local variables,
//     * which accounts for some odd-looking inline assignments below.
//     * Note that calls to setTabAt always occur within locked regions,
//     * and so in principle require only release ordering, not
//     * full volatile semantics, but are currently coded as volatile
//     * writes to be conservative.
//     */
//
//    /**
//     * 使用 Unsafe 类 volatile 式的操作查看值，保证每次获取到的值都是最新的。
//     * <p>
//     * todo 虽然 table 变量加了volatile，但也只能保证其引用的可见性，并不能确保其数组中的对象是否是最新的，所以需要Unsafe类volatile式地拿到最新的Node。
//     *
//     * @param tab
//     * @param i
//     * @param <K>
//     * @param <V>
//     * @return
//     */
//    @SuppressWarnings("unchecked")
//    static final <K, V> Node<K, V> tabAt(Node<K, V>[] tab, int i) {
//        return (Node<K, V>) U.getObjectVolatile(tab, ((long) i << ASHIFT) + ABASE);
//    }
//
//    static final <K, V> boolean casTabAt(Node<K, V>[] tab, int i,
//                                         Node<K, V> c, Node<K, V> v) {
//        return U.compareAndSwapObject(tab, ((long) i << ASHIFT) + ABASE, c, v);
//    }
//
//    static final <K, V> void setTabAt(Node<K, V>[] tab, int i, Node<K, V> v) {
//        U.putObjectVolatile(tab, ((long) i << ASHIFT) + ABASE, v);
//    }
//
//    /* ---------------- Fields -------------- */
//
//    /**
//     * 存储数据的 Node 数组，长度是 2 的幂
//     * <p>
//     * 使用 volatile 来保证每次获取到的都是最新的值
//     */
//    transient volatile Node<K, V>[] table;
//
//    /**
//     * The next table to use; non-null only while resizing.
//     * <p>
//     * 下一个要使用的表；仅在调整大小时为非空。
//     */
//    private transient volatile Node<K, V>[] nextTable;
//
//    /**
//     * Base counter value, used mainly when there is no contention,
//     * but also as a fallback during table initialization
//     * races. Updated via CAS.
//     * <p>
//     * 基本计数器值，主要在没有争用时使用，但也可作为表初始化竞赛期间的后备。通过 CAS 更新。
//     */
//    private transient volatile long baseCount;
//
//    /**
//     * todo 多重角色，要重点理解
//     * <p>
//     * Table initialization and resizing control.
//     * 表初始化和调整大小控制。
//     * <p>
//     * <p>
//     * When negative, the table is being initialized or resized:
//     * 当为负数时，表正在初始化或调整大小：
//     * <p>
//     * -1 for initialization,
//     * -1 表示有线程正在进行初始化操作
//     * <p>
//     * else -(1 + the number of active resizing threads).
//     * -(1 + nThreads) 表示有 n 个线程正在一起扩容
//     * <p>
//     * Otherwise,when table is null, holds the initial table size to use upon
//     * creation, or 0 for default.
//     * 0 ，默认值，表示后续在真正初始化的时候使用默认容量；
//     * <p>
//     * After initialization, holds the
//     * next element count value upon which to resize the table.
//     * 初始化后，保存下一个元素计数值，根据该值调整表的大小。
//     * > 0 ，在初始化之前存储的是传入的容量，在初始化或扩容后存储的是下一次的扩容阈值
//     */
//    private transient volatile int sizeCtl;
//
//    /**
//     * The next table index (plus one) to split while resizing.
//     * 调整大小时要拆分的下一个表索引（加一个）。
//     */
//    private transient volatile int transferIndex;
//
//    /**
//     * Spinlock (locked via CAS) used when resizing and/or creating CounterCells.
//     * 调整大小和/或创建 CounterCell 时使用自旋锁（通过 CAS 锁定）。
//     * <p>
//     * 0 - 不 busy
//     * 1 - busy
//     */
//    private transient volatile int cellsBusy;
//
//    /**
//     * Table of counter cells. When non-null, size is a power of 2.
//     * 计数单元桶。当非空时，大小是 2 的幂。
//     */
//    private transient volatile CounterCell[] counterCells;
//
//    // views
//    private transient KeySetView<K, V> keySet;
//    private transient ValuesView<K, V> values;
//    private transient EntrySetView<K, V> entrySet;
//
//
//    /* ---------------- Public operations -------------- */
//
//    /**
//     * Creates a new, empty map with the default initial table size (16).
//     * 使用默认初始表大小 (16) 创建一个新的空映射。
//     */
//    public ConcurrentHashMap() {
//    }
//
//    /**
//     * Creates a new, empty map with an initial table size
//     * accommodating the specified number of elements without the need
//     * to dynamically resize.
//     * 创建一个新的空映射，其初始表大小可容纳指定数量的元素，无需动态调整大小。
//     *
//     * @param initialCapacity The implementation performs internal
//     *                        sizing to accommodate this many elements.
//     * @throws IllegalArgumentException if the initial capacity of
//     *                                  elements is negative
//     */
//    public ConcurrentHashMap(int initialCapacity) {
//        if (initialCapacity < 0)
//            throw new IllegalArgumentException();
//
//        // 根据传入容量大小计算容量，返回大于 initialCapacity 且最近的2的整数次幂的数
//        int cap = ((initialCapacity >= (MAXIMUM_CAPACITY >>> 1)) ?
//                MAXIMUM_CAPACITY :
//                tableSizeFor(initialCapacity + (initialCapacity >>> 1) + 1));
//
//        // 将容量大小赋值给 sizeCtl，初始化后 sizeCtl 作为扩容阈值
//        this.sizeCtl = cap;
//    }
//
//    /**
//     * Creates a new map with the same mappings as the given map.
//     *
//     * @param m the map
//     */
//    public ConcurrentHashMap(Map<? extends K, ? extends V> m) {
//        this.sizeCtl = DEFAULT_CAPACITY;
//        putAll(m);
//    }
//
//
//    public ConcurrentHashMap(int initialCapacity, float loadFactor) {
//        this(initialCapacity, loadFactor, 1);
//    }
//
//    public ConcurrentHashMap(int initialCapacity,
//                             float loadFactor, int concurrencyLevel) {
//        if (!(loadFactor > 0.0f) || initialCapacity < 0 || concurrencyLevel <= 0)
//            throw new IllegalArgumentException();
//        if (initialCapacity < concurrencyLevel)   // Use at least as many bins
//            initialCapacity = concurrencyLevel;   // as estimated threads
//
//        // 计算 size
//        long size = (long) (1.0 + (long) initialCapacity / loadFactor);
//
//        // 根据 size 计算容量
//        int cap = (size >= (long) MAXIMUM_CAPACITY) ?
//                MAXIMUM_CAPACITY : tableSizeFor((int) size);
//
//        this.sizeCtl = cap;
//    }
//
//    // Original (since JDK1.2) Map methods
//
//    /**
//     * 获取元素个数 - 没有加锁
//     * <p>
//     * 元素个数的存储也是采用分段的思想，获取元素个数时需要把所有段加起来
//     * <p>
//     * {@inheritDoc}
//     */
//    public int size() {
//        // 调用 sumCount() 计算元素个数
//        long n = sumCount();
//        return ((n < 0L) ? 0 :
//                (n > (long) Integer.MAX_VALUE) ? Integer.MAX_VALUE :
//                        (int) n);
//    }
//
//    /**
//     * {@inheritDoc}
//     */
//    public boolean isEmpty() {
//        return sumCount() <= 0L; // ignore transient negative values
//    }
//
//    /**
//     * 获取元素 - 没有加锁
//     * <p>
//     * 没有线程安全的问题，只有可见性的问题，只需要确保get的数据是线程之间可见的即可，使用 Unsafe 的 volatile 式获取节点，保证最新值
//     *
//     * <p>
//     * Returns the value to which the specified key is mapped,
//     * or {@code null} if this map contains no mapping for the key.
//     *
//     * <p>More formally, if this map contains a mapping from a key
//     * {@code k} to a value {@code v} such that {@code key.equals(k)},
//     * then this method returns {@code v}; otherwise it returns
//     * {@code null}.  (There can be at most one such mapping.)
//     *
//     * @throws NullPointerException if the specified key is null
//     */
//    public V get(Object key) {
//        Node<K, V>[] tab;
//        Node<K, V> e, p;
//        int n, eh;
//        K ek;
//
//        // 计算 hash
//        int h = spread(key.hashCode());
//
//        // 如果元素所在的桶存在且里面有元素
//        if ((tab = table) != null && (n = tab.length) > 0 &&
//                // 使用 Unsafe 的 volatile 式获取节点，保证最新值
//                (e = tabAt(tab, (n - 1) & h)) != null) {
//
//            // 如果第一个元素就是要找的元素，直接返回
//            if ((eh = e.hash) == h) {
//                if ((ek = e.key) == key || (ek != null && key.equals(ek)))
//                    return e.val;
//
//                // hash < 0 说明第一个元素是树类型，或者数组处于扩容过程（元素类型是 ForwardingNode）
//            } else if (eh < 0)
//                // todo 使用find寻找元素，find的寻找方式依据Node的不同子类有不同的实现方式：
//                // 处于扩容过程：java.util.concurrent.ConcurrentHashMap.ForwardingNode.find
//                // 非扩容过程，也就是树类型：java.util.concurrent.ConcurrentHashMap.TreeNode.find
//                return (p = e.find(h, key)) != null ? p.val : null;
//
//            // 执行到这里，说明元素所在的桶中是链表，遍历链表即可
//            while ((e = e.next) != null) {
//                if (e.hash == h &&
//                        ((ek = e.key) == key || (ek != null && key.equals(ek))))
//                    return e.val;
//            }
//        }
//
//        // 没有找到
//        return null;
//    }
//
//    /**
//     * Tests if the specified object is a key in this table.
//     *
//     * @param key possible key
//     * @return {@code true} if and only if the specified object
//     * is a key in this table, as determined by the
//     * {@code equals} method; {@code false} otherwise
//     * @throws NullPointerException if the specified key is null
//     */
//    public boolean containsKey(Object key) {
//        return get(key) != null;
//    }
//
//    /**
//     * Returns {@code true} if this map maps one or more keys to the
//     * specified value. Note: This method may require a full traversal
//     * of the map, and is much slower than method {@code containsKey}.
//     *
//     * @param value value whose presence in this map is to be tested
//     * @return {@code true} if this map maps one or more keys to the
//     * specified value
//     * @throws NullPointerException if the specified value is null
//     */
//    public boolean containsValue(Object value) {
//        if (value == null)
//            throw new NullPointerException();
//        Node<K, V>[] t;
//        if ((t = table) != null) {
//            Traverser<K, V> it = new Traverser<K, V>(t, t.length, 0, t.length);
//            for (Node<K, V> p; (p = it.advance()) != null; ) {
//                V v;
//                if ((v = p.val) == value || (v != null && value.equals(v)))
//                    return true;
//            }
//        }
//        return false;
//    }
//
//    /**
//     * key 和 value，都不允许为 null
//     */
//    public V put(K key, V value) {
//        // 默认覆盖式
//        return putVal(key, value, false);
//    }
//
//    /** Implementation for put and putIfAbsent */
//    /**
//     * 整体流程和 HashMap 类似
//     * <p>
//     * 添加元素操作中使用的锁主要有：CAS 自旋  + synchronized 锁（分段锁））。
//     * <p>
//     * todo 在插入元素的过程中，定位到的桶处于不同状态，处理的也不同：
//     * 1 如果桶为空，那么 CAS 插入元素；使用 CAS 可以避免即使此时数组处于扩容过程也不会有问题，因为 CAS 会快速失败；
//     * 2 如果桶的元素为 MOVED，表示处于迁移的过程中，那么当前线程协助一起迁移元素，之后再尝试插入元素；
//     * 3 如果桶的元素既不是空，也不是 MOVED，那么就尝试对该桶加锁，加锁成功才会执行插入操作，否则自旋重试；
//     * - 注意，这里加锁保证了针对某个桶的扩容或新增操作只能有一个；
//     *
//     * @param key          key
//     * @param value        value
//     * @param onlyIfAbsent 是否覆盖
//     * @return
//     */
//    final V putVal(K key, V value, boolean onlyIfAbsent) {
//
//        // key 、value 都不允许为空
//        if (key == null || value == null) throw new NullPointerException();
//
//        // 得到 hash 值
//        int hash = spread(key.hashCode());
//
//        // 用于记录要插入的元素所在桶的元素个数
//        int binCount = 0;
//
//        // 自旋，结合 CAS 使用（如果 CAS 失败，则会重新取整个桶进行下面的流程），直到 put 操作完成后退出循环
//        for (Node<K, V>[] tab = table; ; ) {
//            Node<K, V> f;
//            int n, i, fh;
//
//            //  1 如果数组为空，则进行数组初始化
//            if (tab == null || (n = tab.length) == 0)
//                // 初始化数组
//                tab = initTable();
//
//                // 2 定位到 hash 值对应的数组下标，得到第一个节点 f
//            else if ((f = tabAt(tab, i = (n - 1) & hash)) == null) {
//
//                // 如果数组该位置没有节点，则使用一次 CAS 操作将这个新值放入其中即可。
//                // 如果 CAS 失败，说明是并发操作，进入到下一个循环重试；如果 CAS 成功，则 break ，流程结束
//                if (casTabAt(tab, i, null,
//                        new Node<K, V>(hash, key, value, null)))
//                    // 插入成功，退出循环
//                    break;                   // no lock when adding to empty bin
//            }
//
//            // 3 如果 hash 值对应的位置有节点，且取的第一个节点的 hash 值为 MOVED ，说明当前数组处于扩容过程，则当前线程帮忙一起迁移元素，
//            // 然后再执行插入元素操作
//            else if ((fh = f.hash) == MOVED)
//                // 辅助数据迁移
//                tab = helpTransfer(tab, f);
//
//                // 4 非以上三种情况，也就是当前定位的桶不为空，且不在迁移元素，那么锁住这个桶（以桶的第一个元素作为锁对象-分段锁）
//                // 要插入的元素在该桶，则替换值（onlyIfAbsent=false）；不在该桶，则插入到链表结尾或插入树中；
//            else {
//
//                V oldVal = null;
//
//                // 4.1 获取 synchronized 的锁
//                // todo 减小锁粒度，将Node链表的头节点作为锁，若在默认大小16情况下，将有16把锁，大大减小了锁竞争
//                synchronized (f) {
//                    // 再次检测第一个元素是否有变化，如果有变化则进入下一次循环，从头来过
//                    // todo Unsafe 类的 volatile 式查看值，保证获取到的值都是最新的
//                    if (tabAt(tab, i) == f) {
//
//                        // 如果第一个元素的hash值大于等于0（说明不是在迁移，也不是树），那就是桶中的元素使用的是链表方式存储
//                        if (fh >= 0) {
//                            // 用于累加，记录链表的元素个数
//                            binCount = 1;
//
//                            // 遍历链表，尾节点插入数据
//                            for (Node<K, V> e = f; ; ++binCount) {
//                                K ek;
//                                // 如果发现了相同的 key ，判断是否要进行值覆盖，然后也就可以 break 了
//                                if (e.hash == hash &&
//                                        ((ek = e.key) == key ||
//                                                (ek != null && key.equals(ek)))) {
//                                    oldVal = e.val;
//
//                                    // 找到了相同 key 的元素，根据情况进行覆盖并退出循环
//                                    if (!onlyIfAbsent)
//                                        e.val = value;
//                                    break;
//                                }
//
//                                // 到了链表的末尾还没发现相同 key 的元素，那么就将这个新值放到链表的最后，尾插法
//                                Node<K, V> pred = e;
//                                if ((e = e.next) == null) {
//                                    pred.next = new Node<K, V>(hash, key,
//                                            value, null);
//                                    break;
//                                }
//                            }
//                        }
//                        // 树结构
//                        else if (f instanceof TreeBin) {
//                            Node<K, V> p;
//                            // 记录树中元素个数为 2「注意，树的情况没有进行累加」
//                            binCount = 2;
//                            // 调用红黑树的插入方法插入元素，如果成功则返回 null，否则返回找到的节点
//                            if ((p = ((TreeBin<K, V>) f).putTreeVal(hash, key,
//                                    value)) != null) {
//                                oldVal = p.val;
//
//                                // 找到了相同 key 的元素，根据情况进行覆盖并退出循环
//                                if (!onlyIfAbsent)
//                                    p.val = value;
//                            }
//                        }
//                    }
//                }
//
//                // 如果 binCount不为0，说明成功插入了元素或者寻找到了元素
//                if (binCount != 0) {
//                    // 如果链表元素个数 >= 8 ，那么尝试进行链表树化「只有数组容量 >= 64 时才会真正进行树化，否则优先扩容」
//                    // todo 因为上面把元素插入到树中时，binCount只赋值了2，并没有计算整个树中元素的个数，所以不会重复树化
//                    if (binCount >= TREEIFY_THRESHOLD)
//                        treeifyBin(tab, i);
//
//                    // 如果要插入的元素已经存在，那么把旧值返回
//                    if (oldVal != null)
//                        return oldVal;
//
//                    // 退出外层大循环，流程结束
//                    break;
//                }
//            }
//        }
//
//        // 成功插入元素，元素个数加1（是否要扩容在这个里面，binCount >= 0 才会去判断是否要扩容）
//        // todo 判断是否需要扩容
//        addCount(1L, binCount);
//
//        // 成功插入元素返回null
//        return null;
//    }
//
//    /**
//     * Copies all of the mappings from the specified map to this one.
//     * These mappings replace any mappings that this map had for any of the
//     * keys currently in the specified map.
//     *
//     * @param m mappings to be stored in this map
//     */
//    public void putAll(Map<? extends K, ? extends V> m) {
//        tryPresize(m.size());
//        for (Map.Entry<? extends K, ? extends V> e : m.entrySet())
//            putVal(e.getKey(), e.getValue(), false);
//    }
//
//    /**
//     * 删除元素和添加元素一样，都是先找到对应的桶，然后采用分段锁的思想锁住整个桶，再进行操作。
//     * <p>
//     * Removes the key (and its corresponding value) from this map.
//     * This method does nothing if the key is not in the map.
//     *
//     * @param key the key that needs to be removed
//     * @return the previous value associated with {@code key}, or
//     * {@code null} if there was no mapping for {@code key}
//     * @throws NullPointerException if the specified key is null
//     */
//    public V remove(Object key) {
//        // 调用替换节点方法
//        return replaceNode(key, null, null);
//    }
//
//
//    /**
//     * Implementation for the four public remove/replace methods:
//     * Replaces node value with v, conditional upon match of cv if
//     * non-null.  If resulting value is null, delete.
//     */
//    final V replaceNode(Object key, V value, Object cv) {
//        // 计算 hash
//        int hash = spread(key.hashCode());
//
//        // 自旋
//        for (Node<K, V>[] tab = table; ; ) {
//            Node<K, V> f;
//            int n, i, fh;
//
//            // 如果数组为空，或目标 key 所在的桶不存在，跳出循环返回 null
//            if (tab == null || (n = tab.length) == 0 ||
//                    (f = tabAt(tab, i = (n - 1) & hash)) == null)
//                break;
//
//                // 如果桶中第一个元素的 hash 值为 MOVED ，说明这个桶已经迁移完了，但是其它的桶不知道，因此当前线程协助迁移，协助扩容完成后再进行删除操作
//            else if ((fh = f.hash) == MOVED)
//                tab = helpTransfer(tab, f);
//
//                // 查找元素并执行删除
//            else {
//                V oldVal = null;
//                // 标记是否处理过
//                boolean validated = false;
//
//                // 加锁（锁对象使用桶的第一个元素，达到分段的效果）
//                synchronized (f) {
//                    // 再次验证当前桶的第一个元素是否被修改
//                    if (tabAt(tab, i) == f) {
//
//                        // 桶的第一个元素的 hash >= 0 表示是链表节点
//                        if (fh >= 0) {
//                            validated = true;
//
//                            // 遍历链表寻找目标节点
//                            for (Node<K, V> e = f, pred = null; ; ) {
//                                K ek;
//                                if (e.hash == hash &&
//                                        ((ek = e.key) == key ||
//                                                (ek != null && key.equals(ek)))) {
//                                    // 找到目标节点
//                                    V ev = e.val;
//
//                                    if (cv == null || cv == ev ||
//                                            (ev != null && cv.equals(ev))) {
//                                        oldVal = ev;
//
//                                        // 如果 value 不为空则替换旧值
//                                        if (value != null)
//                                            e.val = value;
//                                        else if (pred != null)
//                                            // 如果前置节点不为空，删除当前节点
//                                            pred.next = e.next;
//                                        else
//                                            // 如果前置节点为空，说明是桶中第一个元素，删除即可
//                                            setTabAt(tab, i, e.next);
//                                    }
//                                    break;
//                                }
//                                pred = e;
//
//
//                                // 遍历到链表尾部还没找到元素，跳出循环
//                                if ((e = e.next) == null)
//                                    break;
//                            }
//
//                            // 桶的第一个元素类型为树，则遍历树找目标节点
//                        } else if (f instanceof TreeBin) {
//                            validated = true;
//                            TreeBin<K, V> t = (TreeBin<K, V>) f;
//                            TreeNode<K, V> r, p;
//                            if ((r = t.root) != null &&
//                                    (p = r.findTreeNode(hash, key, null)) != null) {
//
//                                // 找到目标节点
//                                V pv = p.val;
//                                if (cv == null || cv == pv ||
//                                        (pv != null && cv.equals(pv))) {
//                                    oldVal = pv;
//                                    // 如果value不为空则替换旧值
//                                    if (value != null)
//                                        p.val = value;
//                                        // 如果 value 为空，从树中删除
//                                    else if (t.removeTreeNode(p))
//
//                                        // 判断是否退化成链表
//                                        //  t.removeTreeNode(p)这个方法返回true表示删除节点后树的元素个数较少，此时退化成链表
//                                        setTabAt(tab, i, untreeify(t.first));
//                                }
//                            }
//                        }
//                    }
//                }
//
//                // 如果处理过，就不需要循环了，可以退出了
//                if (validated) {
//                    // 如果找到了元素，返回其旧值
//                    if (oldVal != null) {
//                        // 如果要替换的值为空，元素个数减 1，不考虑调整数组大小
//                        if (value == null)
//                            addCount(-1L, -1);
//                        return oldVal;
//                    }
//                    break;
//                }
//            }
//        }
//
//        // 没找到元素返回空
//        return null;
//    }
//
//    /**
//     * Removes all of the mappings from this map.
//     */
//    public void clear() {
//        long delta = 0L; // negative number of deletions
//        int i = 0;
//        Node<K, V>[] tab = table;
//        while (tab != null && i < tab.length) {
//            int fh;
//            Node<K, V> f = tabAt(tab, i);
//            if (f == null)
//                ++i;
//            else if ((fh = f.hash) == MOVED) {
//                tab = helpTransfer(tab, f);
//                i = 0; // restart
//            } else {
//                synchronized (f) {
//                    if (tabAt(tab, i) == f) {
//                        Node<K, V> p = (fh >= 0 ? f :
//                                (f instanceof TreeBin) ?
//                                        ((TreeBin<K, V>) f).first : null);
//                        while (p != null) {
//                            --delta;
//                            p = p.next;
//                        }
//                        setTabAt(tab, i++, null);
//                    }
//                }
//            }
//        }
//        if (delta != 0L)
//            addCount(delta, -1);
//    }
//
//    /**
//     * Returns a {@link Set} view of the keys contained in this map.
//     * The set is backed by the map, so changes to the map are
//     * reflected in the set, and vice-versa. The set supports element
//     * removal, which removes the corresponding mapping from this map,
//     * via the {@code Iterator.remove}, {@code Set.remove},
//     * {@code removeAll}, {@code retainAll}, and {@code clear}
//     * operations.  It does not support the {@code add} or
//     * {@code addAll} operations.
//     *
//     * <p>The view's iterators and spliterators are
//     * <a href="package-summary.html#Weakly"><i>weakly consistent</i></a>.
//     *
//     * <p>The view's {@code spliterator} reports {@link Spliterator#CONCURRENT},
//     * {@link Spliterator#DISTINCT}, and {@link Spliterator#NONNULL}.
//     *
//     * @return the set view
//     */
//    public KeySetView<K, V> keySet() {
//        KeySetView<K, V> ks;
//        return (ks = keySet) != null ? ks : (keySet = new KeySetView<K, V>(this, null));
//    }
//
//    /**
//     * Returns a {@link Collection} view of the values contained in this map.
//     * The collection is backed by the map, so changes to the map are
//     * reflected in the collection, and vice-versa.  The collection
//     * supports element removal, which removes the corresponding
//     * mapping from this map, via the {@code Iterator.remove},
//     * {@code Collection.remove}, {@code removeAll},
//     * {@code retainAll}, and {@code clear} operations.  It does not
//     * support the {@code add} or {@code addAll} operations.
//     *
//     * <p>The view's iterators and spliterators are
//     * <a href="package-summary.html#Weakly"><i>weakly consistent</i></a>.
//     *
//     * <p>The view's {@code spliterator} reports {@link Spliterator#CONCURRENT}
//     * and {@link Spliterator#NONNULL}.
//     *
//     * @return the collection view
//     */
//    public Collection<V> values() {
//        ValuesView<K, V> vs;
//        return (vs = values) != null ? vs : (values = new ValuesView<K, V>(this));
//    }
//
//    /**
//     * Returns a {@link Set} view of the mappings contained in this map.
//     * The set is backed by the map, so changes to the map are
//     * reflected in the set, and vice-versa.  The set supports element
//     * removal, which removes the corresponding mapping from the map,
//     * via the {@code Iterator.remove}, {@code Set.remove},
//     * {@code removeAll}, {@code retainAll}, and {@code clear}
//     * operations.
//     *
//     * <p>The view's iterators and spliterators are
//     * <a href="package-summary.html#Weakly"><i>weakly consistent</i></a>.
//     *
//     * <p>The view's {@code spliterator} reports {@link Spliterator#CONCURRENT},
//     * {@link Spliterator#DISTINCT}, and {@link Spliterator#NONNULL}.
//     *
//     * @return the set view
//     */
//    public Set<Map.Entry<K, V>> entrySet() {
//        EntrySetView<K, V> es;
//        return (es = entrySet) != null ? es : (entrySet = new EntrySetView<K, V>(this));
//    }
//
//    /**
//     * Returns the hash code value for this {@link Map}, i.e.,
//     * the sum of, for each key-value pair in the map,
//     * {@code key.hashCode() ^ value.hashCode()}.
//     *
//     * @return the hash code value for this map
//     */
//    public int hashCode() {
//        int h = 0;
//        Node<K, V>[] t;
//        if ((t = table) != null) {
//            Traverser<K, V> it = new Traverser<K, V>(t, t.length, 0, t.length);
//            for (Node<K, V> p; (p = it.advance()) != null; )
//                h += p.key.hashCode() ^ p.val.hashCode();
//        }
//        return h;
//    }
//
//
//    /**
//     * Stripped-down version of helper class used in previous version,
//     * declared for the sake of serialization compatibility
//     */
//    static class Segment<K, V> extends ReentrantLock implements Serializable {
//        private static final long serialVersionUID = 2249069246763182397L;
//        final float loadFactor;
//
//        Segment(float lf) {
//            this.loadFactor = lf;
//        }
//    }
//
//    /**
//     * Saves the state of the {@code ConcurrentHashMap} instance to a
//     * stream (i.e., serializes it).
//     *
//     * @param s the stream
//     * @throws java.io.IOException if an I/O error occurs
//     * @serialData the key (Object) and value (Object)
//     * for each key-value mapping, followed by a null pair.
//     * The key-value mappings are emitted in no particular order.
//     */
//    private void writeObject(java.io.ObjectOutputStream s)
//            throws java.io.IOException {
//        // For serialization compatibility
//        // Emulate segment calculation from previous version of this class
//        int sshift = 0;
//        int ssize = 1;
//        while (ssize < DEFAULT_CONCURRENCY_LEVEL) {
//            ++sshift;
//            ssize <<= 1;
//        }
//        int segmentShift = 32 - sshift;
//        int segmentMask = ssize - 1;
//        @SuppressWarnings("unchecked")
//        Segment<K, V>[] segments = (Segment<K, V>[])
//                new Segment<?, ?>[DEFAULT_CONCURRENCY_LEVEL];
//        for (int i = 0; i < segments.length; ++i)
//            segments[i] = new Segment<K, V>(LOAD_FACTOR);
//        s.putFields().put("segments", segments);
//        s.putFields().put("segmentShift", segmentShift);
//        s.putFields().put("segmentMask", segmentMask);
//        s.writeFields();
//
//        Node<K, V>[] t;
//        if ((t = table) != null) {
//            Traverser<K, V> it = new Traverser<K, V>(t, t.length, 0, t.length);
//            for (Node<K, V> p; (p = it.advance()) != null; ) {
//                s.writeObject(p.key);
//                s.writeObject(p.val);
//            }
//        }
//        s.writeObject(null);
//        s.writeObject(null);
//        segments = null; // throw away
//    }
//
//    /**
//     * Reconstitutes the instance from a stream (that is, deserializes it).
//     *
//     * @param s the stream
//     * @throws ClassNotFoundException if the class of a serialized object
//     *                                could not be found
//     * @throws java.io.IOException    if an I/O error occurs
//     */
//    private void readObject(java.io.ObjectInputStream s)
//            throws java.io.IOException, ClassNotFoundException {
//        /*
//         * To improve performance in typical cases, we create nodes
//         * while reading, then place in table once size is known.
//         * However, we must also validate uniqueness and deal with
//         * overpopulated bins while doing so, which requires
//         * specialized versions of putVal mechanics.
//         */
//        sizeCtl = -1; // force exclusion for table construction
//        s.defaultReadObject();
//        long size = 0L;
//        Node<K, V> p = null;
//        for (; ; ) {
//            @SuppressWarnings("unchecked")
//            K k = (K) s.readObject();
//            @SuppressWarnings("unchecked")
//            V v = (V) s.readObject();
//            if (k != null && v != null) {
//                p = new Node<K, V>(spread(k.hashCode()), k, v, p);
//                ++size;
//            } else
//                break;
//        }
//        if (size == 0L)
//            sizeCtl = 0;
//        else {
//            int n;
//            if (size >= (long) (MAXIMUM_CAPACITY >>> 1))
//                n = MAXIMUM_CAPACITY;
//            else {
//                int sz = (int) size;
//                n = tableSizeFor(sz + (sz >>> 1) + 1);
//            }
//            @SuppressWarnings("unchecked")
//            Node<K, V>[] tab = (Node<K, V>[]) new Node<?, ?>[n];
//            int mask = n - 1;
//            long added = 0L;
//            while (p != null) {
//                boolean insertAtFront;
//                Node<K, V> next = p.next, first;
//                int h = p.hash, j = h & mask;
//                if ((first = tabAt(tab, j)) == null)
//                    insertAtFront = true;
//                else {
//                    K k = p.key;
//                    if (first.hash < 0) {
//                        TreeBin<K, V> t = (TreeBin<K, V>) first;
//                        if (t.putTreeVal(h, k, p.val) == null)
//                            ++added;
//                        insertAtFront = false;
//                    } else {
//                        int binCount = 0;
//                        insertAtFront = true;
//                        Node<K, V> q;
//                        K qk;
//                        for (q = first; q != null; q = q.next) {
//                            if (q.hash == h &&
//                                    ((qk = q.key) == k ||
//                                            (qk != null && k.equals(qk)))) {
//                                insertAtFront = false;
//                                break;
//                            }
//                            ++binCount;
//                        }
//                        if (insertAtFront && binCount >= TREEIFY_THRESHOLD) {
//                            insertAtFront = false;
//                            ++added;
//                            p.next = first;
//                            TreeNode<K, V> hd = null, tl = null;
//                            for (q = p; q != null; q = q.next) {
//                                TreeNode<K, V> t = new TreeNode<K, V>
//                                        (q.hash, q.key, q.val, null, null);
//                                if ((t.prev = tl) == null)
//                                    hd = t;
//                                else
//                                    tl.next = t;
//                                tl = t;
//                            }
//                            setTabAt(tab, j, new TreeBin<K, V>(hd));
//                        }
//                    }
//                }
//                if (insertAtFront) {
//                    ++added;
//                    p.next = first;
//                    setTabAt(tab, j, p);
//                }
//                p = next;
//            }
//            table = tab;
//            sizeCtl = n - (n >>> 2);
//            baseCount = added;
//        }
//    }
//
//    // ConcurrentMap methods
//
//    /**
//     * {@inheritDoc}
//     *
//     * @return the previous value associated with the specified key,
//     * or {@code null} if there was no mapping for the key
//     * @throws NullPointerException if the specified key or value is null
//     */
//    public V putIfAbsent(K key, V value) {
//        return putVal(key, value, true);
//    }
//
//    /**
//     * {@inheritDoc}
//     *
//     * @throws NullPointerException if the specified key is null
//     */
//    public boolean remove(Object key, Object value) {
//        if (key == null)
//            throw new NullPointerException();
//        return value != null && replaceNode(key, null, value) != null;
//    }
//
//    /**
//     * {@inheritDoc}
//     *
//     * @throws NullPointerException if any of the arguments are null
//     */
//    public boolean replace(K key, V oldValue, V newValue) {
//        if (key == null || oldValue == null || newValue == null)
//            throw new NullPointerException();
//        return replaceNode(key, newValue, oldValue) != null;
//    }
//
//    /**
//     * {@inheritDoc}
//     *
//     * @return the previous value associated with the specified key,
//     * or {@code null} if there was no mapping for the key
//     * @throws NullPointerException if the specified key or value is null
//     */
//    public V replace(K key, V value) {
//        if (key == null || value == null)
//            throw new NullPointerException();
//        return replaceNode(key, value, null);
//    }
//
//    // Overrides of JDK8+ Map extension method defaults
//
//    /**
//     * Returns the value to which the specified key is mapped, or the
//     * given default value if this map contains no mapping for the
//     * key.
//     *
//     * @param key          the key whose associated value is to be returned
//     * @param defaultValue the value to return if this map contains
//     *                     no mapping for the given key
//     * @return the mapping for the key, if present; else the default value
//     * @throws NullPointerException if the specified key is null
//     */
//    public V getOrDefault(Object key, V defaultValue) {
//        V v;
//        return (v = get(key)) == null ? defaultValue : v;
//    }
//
//    public void forEach(BiConsumer<? super K, ? super V> action) {
//        if (action == null) throw new NullPointerException();
//        Node<K, V>[] t;
//        if ((t = table) != null) {
//            Traverser<K, V> it = new Traverser<K, V>(t, t.length, 0, t.length);
//            for (Node<K, V> p; (p = it.advance()) != null; ) {
//                action.accept(p.key, p.val);
//            }
//        }
//    }
//
//    public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
//        if (function == null) throw new NullPointerException();
//        Node<K, V>[] t;
//        if ((t = table) != null) {
//            Traverser<K, V> it = new Traverser<K, V>(t, t.length, 0, t.length);
//            for (Node<K, V> p; (p = it.advance()) != null; ) {
//                V oldValue = p.val;
//                for (K key = p.key; ; ) {
//                    V newValue = function.apply(key, oldValue);
//                    if (newValue == null)
//                        throw new NullPointerException();
//                    if (replaceNode(key, newValue, oldValue) != null ||
//                            (oldValue = get(key)) == null)
//                        break;
//                }
//            }
//        }
//    }
//
//    /**
//     * If the specified key is not already associated with a value,
//     * attempts to compute its value using the given mapping function
//     * and enters it into this map unless {@code null}.  The entire
//     * method invocation is performed atomically, so the function is
//     * applied at most once per key.  Some attempted update operations
//     * on this map by other threads may be blocked while computation
//     * is in progress, so the computation should be short and simple,
//     * and must not attempt to update any other mappings of this map.
//     *
//     * @param key             key with which the specified value is to be associated
//     * @param mappingFunction the function to compute a value
//     * @return the current (existing or computed) value associated with
//     * the specified key, or null if the computed value is null
//     * @throws NullPointerException  if the specified key or mappingFunction
//     *                               is null
//     * @throws IllegalStateException if the computation detectably
//     *                               attempts a recursive update to this map that would
//     *                               otherwise never complete
//     * @throws RuntimeException      or Error if the mappingFunction does so,
//     *                               in which case the mapping is left unestablished
//     */
//    public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
//        if (key == null || mappingFunction == null)
//            throw new NullPointerException();
//        int h = spread(key.hashCode());
//        V val = null;
//        int binCount = 0;
//        for (Node<K, V>[] tab = table; ; ) {
//            Node<K, V> f;
//            int n, i, fh;
//            if (tab == null || (n = tab.length) == 0)
//                tab = initTable();
//            else if ((f = tabAt(tab, i = (n - 1) & h)) == null) {
//                Node<K, V> r = new ReservationNode<K, V>();
//                synchronized (r) {
//                    if (casTabAt(tab, i, null, r)) {
//                        binCount = 1;
//                        Node<K, V> node = null;
//                        try {
//                            if ((val = mappingFunction.apply(key)) != null)
//                                node = new Node<K, V>(h, key, val, null);
//                        } finally {
//                            setTabAt(tab, i, node);
//                        }
//                    }
//                }
//                if (binCount != 0)
//                    break;
//            } else if ((fh = f.hash) == MOVED)
//                tab = helpTransfer(tab, f);
//            else {
//                boolean added = false;
//                synchronized (f) {
//                    if (tabAt(tab, i) == f) {
//                        if (fh >= 0) {
//                            binCount = 1;
//                            for (Node<K, V> e = f; ; ++binCount) {
//                                K ek;
//                                V ev;
//                                if (e.hash == h &&
//                                        ((ek = e.key) == key ||
//                                                (ek != null && key.equals(ek)))) {
//                                    val = e.val;
//                                    break;
//                                }
//                                Node<K, V> pred = e;
//                                if ((e = e.next) == null) {
//                                    if ((val = mappingFunction.apply(key)) != null) {
//                                        added = true;
//                                        pred.next = new Node<K, V>(h, key, val, null);
//                                    }
//                                    break;
//                                }
//                            }
//                        } else if (f instanceof TreeBin) {
//                            binCount = 2;
//                            TreeBin<K, V> t = (TreeBin<K, V>) f;
//                            TreeNode<K, V> r, p;
//                            if ((r = t.root) != null &&
//                                    (p = r.findTreeNode(h, key, null)) != null)
//                                val = p.val;
//                            else if ((val = mappingFunction.apply(key)) != null) {
//                                added = true;
//                                t.putTreeVal(h, key, val);
//                            }
//                        }
//                    }
//                }
//                if (binCount != 0) {
//                    if (binCount >= TREEIFY_THRESHOLD)
//                        treeifyBin(tab, i);
//                    if (!added)
//                        return val;
//                    break;
//                }
//            }
//        }
//        if (val != null)
//            addCount(1L, binCount);
//        return val;
//    }
//
//    /**
//     * If the value for the specified key is present, attempts to
//     * compute a new mapping given the key and its current mapped
//     * value.  The entire method invocation is performed atomically.
//     * Some attempted update operations on this map by other threads
//     * may be blocked while computation is in progress, so the
//     * computation should be short and simple, and must not attempt to
//     * update any other mappings of this map.
//     *
//     * @param key               key with which a value may be associated
//     * @param remappingFunction the function to compute a value
//     * @return the new value associated with the specified key, or null if none
//     * @throws NullPointerException  if the specified key or remappingFunction
//     *                               is null
//     * @throws IllegalStateException if the computation detectably
//     *                               attempts a recursive update to this map that would
//     *                               otherwise never complete
//     * @throws RuntimeException      or Error if the remappingFunction does so,
//     *                               in which case the mapping is unchanged
//     */
//    public V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
//        if (key == null || remappingFunction == null)
//            throw new NullPointerException();
//        int h = spread(key.hashCode());
//        V val = null;
//        int delta = 0;
//        int binCount = 0;
//        for (Node<K, V>[] tab = table; ; ) {
//            Node<K, V> f;
//            int n, i, fh;
//            if (tab == null || (n = tab.length) == 0)
//                tab = initTable();
//            else if ((f = tabAt(tab, i = (n - 1) & h)) == null)
//                break;
//            else if ((fh = f.hash) == MOVED)
//                tab = helpTransfer(tab, f);
//            else {
//                synchronized (f) {
//                    if (tabAt(tab, i) == f) {
//                        if (fh >= 0) {
//                            binCount = 1;
//                            for (Node<K, V> e = f, pred = null; ; ++binCount) {
//                                K ek;
//                                if (e.hash == h &&
//                                        ((ek = e.key) == key ||
//                                                (ek != null && key.equals(ek)))) {
//                                    val = remappingFunction.apply(key, e.val);
//                                    if (val != null)
//                                        e.val = val;
//                                    else {
//                                        delta = -1;
//                                        Node<K, V> en = e.next;
//                                        if (pred != null)
//                                            pred.next = en;
//                                        else
//                                            setTabAt(tab, i, en);
//                                    }
//                                    break;
//                                }
//                                pred = e;
//                                if ((e = e.next) == null)
//                                    break;
//                            }
//                        } else if (f instanceof TreeBin) {
//                            binCount = 2;
//                            TreeBin<K, V> t = (TreeBin<K, V>) f;
//                            TreeNode<K, V> r, p;
//                            if ((r = t.root) != null &&
//                                    (p = r.findTreeNode(h, key, null)) != null) {
//                                val = remappingFunction.apply(key, p.val);
//                                if (val != null)
//                                    p.val = val;
//                                else {
//                                    delta = -1;
//                                    if (t.removeTreeNode(p))
//                                        setTabAt(tab, i, untreeify(t.first));
//                                }
//                            }
//                        }
//                    }
//                }
//                if (binCount != 0)
//                    break;
//            }
//        }
//        if (delta != 0)
//            addCount((long) delta, binCount);
//        return val;
//    }
//
//    /**
//     * Attempts to compute a mapping for the specified key and its
//     * current mapped value (or {@code null} if there is no current
//     * mapping). The entire method invocation is performed atomically.
//     * Some attempted update operations on this map by other threads
//     * may be blocked while computation is in progress, so the
//     * computation should be short and simple, and must not attempt to
//     * update any other mappings of this Map.
//     *
//     * @param key               key with which the specified value is to be associated
//     * @param remappingFunction the function to compute a value
//     * @return the new value associated with the specified key, or null if none
//     * @throws NullPointerException  if the specified key or remappingFunction
//     *                               is null
//     * @throws IllegalStateException if the computation detectably
//     *                               attempts a recursive update to this map that would
//     *                               otherwise never complete
//     * @throws RuntimeException      or Error if the remappingFunction does so,
//     *                               in which case the mapping is unchanged
//     */
//    public V compute(K key,
//                     BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
//        if (key == null || remappingFunction == null)
//            throw new NullPointerException();
//        int h = spread(key.hashCode());
//        V val = null;
//        int delta = 0;
//        int binCount = 0;
//        for (Node<K, V>[] tab = table; ; ) {
//            Node<K, V> f;
//            int n, i, fh;
//            if (tab == null || (n = tab.length) == 0)
//                tab = initTable();
//            else if ((f = tabAt(tab, i = (n - 1) & h)) == null) {
//                Node<K, V> r = new ReservationNode<K, V>();
//                synchronized (r) {
//                    if (casTabAt(tab, i, null, r)) {
//                        binCount = 1;
//                        Node<K, V> node = null;
//                        try {
//                            if ((val = remappingFunction.apply(key, null)) != null) {
//                                delta = 1;
//                                node = new Node<K, V>(h, key, val, null);
//                            }
//                        } finally {
//                            setTabAt(tab, i, node);
//                        }
//                    }
//                }
//                if (binCount != 0)
//                    break;
//            } else if ((fh = f.hash) == MOVED)
//                tab = helpTransfer(tab, f);
//            else {
//                synchronized (f) {
//                    if (tabAt(tab, i) == f) {
//                        if (fh >= 0) {
//                            binCount = 1;
//                            for (Node<K, V> e = f, pred = null; ; ++binCount) {
//                                K ek;
//                                if (e.hash == h &&
//                                        ((ek = e.key) == key ||
//                                                (ek != null && key.equals(ek)))) {
//                                    val = remappingFunction.apply(key, e.val);
//                                    if (val != null)
//                                        e.val = val;
//                                    else {
//                                        delta = -1;
//                                        Node<K, V> en = e.next;
//                                        if (pred != null)
//                                            pred.next = en;
//                                        else
//                                            setTabAt(tab, i, en);
//                                    }
//                                    break;
//                                }
//                                pred = e;
//                                if ((e = e.next) == null) {
//                                    val = remappingFunction.apply(key, null);
//                                    if (val != null) {
//                                        delta = 1;
//                                        pred.next =
//                                                new Node<K, V>(h, key, val, null);
//                                    }
//                                    break;
//                                }
//                            }
//                        } else if (f instanceof TreeBin) {
//                            binCount = 1;
//                            TreeBin<K, V> t = (TreeBin<K, V>) f;
//                            TreeNode<K, V> r, p;
//                            if ((r = t.root) != null)
//                                p = r.findTreeNode(h, key, null);
//                            else
//                                p = null;
//                            V pv = (p == null) ? null : p.val;
//                            val = remappingFunction.apply(key, pv);
//                            if (val != null) {
//                                if (p != null)
//                                    p.val = val;
//                                else {
//                                    delta = 1;
//                                    t.putTreeVal(h, key, val);
//                                }
//                            } else if (p != null) {
//                                delta = -1;
//                                if (t.removeTreeNode(p))
//                                    setTabAt(tab, i, untreeify(t.first));
//                            }
//                        }
//                    }
//                }
//                if (binCount != 0) {
//                    if (binCount >= TREEIFY_THRESHOLD)
//                        treeifyBin(tab, i);
//                    break;
//                }
//            }
//        }
//        if (delta != 0)
//            addCount((long) delta, binCount);
//        return val;
//    }
//
//    /**
//     * If the specified key is not already associated with a
//     * (non-null) value, associates it with the given value.
//     * Otherwise, replaces the value with the results of the given
//     * remapping function, or removes if {@code null}. The entire
//     * method invocation is performed atomically.  Some attempted
//     * update operations on this map by other threads may be blocked
//     * while computation is in progress, so the computation should be
//     * short and simple, and must not attempt to update any other
//     * mappings of this Map.
//     *
//     * @param key               key with which the specified value is to be associated
//     * @param value             the value to use if absent
//     * @param remappingFunction the function to recompute a value if present
//     * @return the new value associated with the specified key, or null if none
//     * @throws NullPointerException if the specified key or the
//     *                              remappingFunction is null
//     * @throws RuntimeException     or Error if the remappingFunction does so,
//     *                              in which case the mapping is unchanged
//     */
//    public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
//        if (key == null || value == null || remappingFunction == null)
//            throw new NullPointerException();
//        int h = spread(key.hashCode());
//        V val = null;
//        int delta = 0;
//        int binCount = 0;
//        for (Node<K, V>[] tab = table; ; ) {
//            Node<K, V> f;
//            int n, i, fh;
//            if (tab == null || (n = tab.length) == 0)
//                tab = initTable();
//            else if ((f = tabAt(tab, i = (n - 1) & h)) == null) {
//                if (casTabAt(tab, i, null, new Node<K, V>(h, key, value, null))) {
//                    delta = 1;
//                    val = value;
//                    break;
//                }
//            } else if ((fh = f.hash) == MOVED)
//                tab = helpTransfer(tab, f);
//            else {
//                synchronized (f) {
//                    if (tabAt(tab, i) == f) {
//                        if (fh >= 0) {
//                            binCount = 1;
//                            for (Node<K, V> e = f, pred = null; ; ++binCount) {
//                                K ek;
//                                if (e.hash == h &&
//                                        ((ek = e.key) == key ||
//                                                (ek != null && key.equals(ek)))) {
//                                    val = remappingFunction.apply(e.val, value);
//                                    if (val != null)
//                                        e.val = val;
//                                    else {
//                                        delta = -1;
//                                        Node<K, V> en = e.next;
//                                        if (pred != null)
//                                            pred.next = en;
//                                        else
//                                            setTabAt(tab, i, en);
//                                    }
//                                    break;
//                                }
//                                pred = e;
//                                if ((e = e.next) == null) {
//                                    delta = 1;
//                                    val = value;
//                                    pred.next =
//                                            new Node<K, V>(h, key, val, null);
//                                    break;
//                                }
//                            }
//                        } else if (f instanceof TreeBin) {
//                            binCount = 2;
//                            TreeBin<K, V> t = (TreeBin<K, V>) f;
//                            TreeNode<K, V> r = t.root;
//                            TreeNode<K, V> p = (r == null) ? null :
//                                    r.findTreeNode(h, key, null);
//                            val = (p == null) ? value :
//                                    remappingFunction.apply(p.val, value);
//                            if (val != null) {
//                                if (p != null)
//                                    p.val = val;
//                                else {
//                                    delta = 1;
//                                    t.putTreeVal(h, key, val);
//                                }
//                            } else if (p != null) {
//                                delta = -1;
//                                if (t.removeTreeNode(p))
//                                    setTabAt(tab, i, untreeify(t.first));
//                            }
//                        }
//                    }
//                }
//                if (binCount != 0) {
//                    if (binCount >= TREEIFY_THRESHOLD)
//                        treeifyBin(tab, i);
//                    break;
//                }
//            }
//        }
//        if (delta != 0)
//            addCount((long) delta, binCount);
//        return val;
//    }
//
//    // Hashtable legacy methods
//
//    /**
//     * Legacy method testing if some key maps into the specified value
//     * in this table.  This method is identical in functionality to
//     * {@link #containsValue(Object)}, and exists solely to ensure
//     * full compatibility with class {@link java.util.Hashtable},
//     * which supported this method prior to introduction of the
//     * Java Collections framework.
//     *
//     * @param value a value to search for
//     * @return {@code true} if and only if some key maps to the
//     * {@code value} argument in this table as
//     * determined by the {@code equals} method;
//     * {@code false} otherwise
//     * @throws NullPointerException if the specified value is null
//     */
//    public boolean contains(Object value) {
//        return containsValue(value);
//    }
//
//    /**
//     * Returns an enumeration of the keys in this table.
//     *
//     * @return an enumeration of the keys in this table
//     * @see #keySet()
//     */
//    public Enumeration<K> keys() {
//        Node<K, V>[] t;
//        int f = (t = table) == null ? 0 : t.length;
//        return new KeyIterator<K, V>(t, f, 0, f, this);
//    }
//
//    /**
//     * Returns an enumeration of the values in this table.
//     *
//     * @return an enumeration of the values in this table
//     * @see #values()
//     */
//    public Enumeration<V> elements() {
//        Node<K, V>[] t;
//        int f = (t = table) == null ? 0 : t.length;
//        return new ValueIterator<K, V>(t, f, 0, f, this);
//    }
//
//    // ConcurrentHashMap-only methods
//
//    /**
//     * Returns the number of mappings. This method should be used
//     * instead of {@link #size} because a ConcurrentHashMap may
//     * contain more mappings than can be represented as an int. The
//     * value returned is an estimate; the actual count may differ if
//     * there are concurrent insertions or removals.
//     *
//     * @return the number of mappings
//     * @since 1.8
//     */
//    public long mappingCount() {
//        long n = sumCount();
//        return (n < 0L) ? 0L : n; // ignore transient negative values
//    }
//
//    /**
//     * Creates a new {@link Set} backed by a ConcurrentHashMap
//     * from the given type to {@code Boolean.TRUE}.
//     *
//     * @param <K> the element type of the returned set
//     * @return the new set
//     * @since 1.8
//     */
//    public static <K> KeySetView<K, Boolean> newKeySet() {
//        return new KeySetView<K, Boolean>
//                (new ConcurrentHashMap<K, Boolean>(), Boolean.TRUE);
//    }
//
//    /**
//     * Creates a new {@link Set} backed by a ConcurrentHashMap
//     * from the given type to {@code Boolean.TRUE}.
//     *
//     * @param initialCapacity The implementation performs internal
//     *                        sizing to accommodate this many elements.
//     * @param <K>             the element type of the returned set
//     * @return the new set
//     * @throws IllegalArgumentException if the initial capacity of
//     *                                  elements is negative
//     * @since 1.8
//     */
//    public static <K> KeySetView<K, Boolean> newKeySet(int initialCapacity) {
//        return new KeySetView<K, Boolean>
//                (new ConcurrentHashMap<K, Boolean>(initialCapacity), Boolean.TRUE);
//    }
//
//    /**
//     * Returns a {@link Set} view of the keys in this map, using the
//     * given common mapped value for any additions (i.e., {@link
//     * Collection#add} and {@link Collection#addAll(Collection)}).
//     * This is of course only appropriate if it is acceptable to use
//     * the same value for all additions from this view.
//     *
//     * @param mappedValue the mapped value to use for any additions
//     * @return the set view
//     * @throws NullPointerException if the mappedValue is null
//     */
//    public KeySetView<K, V> keySet(V mappedValue) {
//        if (mappedValue == null)
//            throw new NullPointerException();
//        return new KeySetView<K, V>(this, mappedValue);
//    }
//
//    /* ---------------- Special Nodes -------------- */
//
//    /**
//     * A node inserted at head of bins during transfer operations.
//     * 扩容期间作为桶迁移完成的标志元素
//     */
//    static final class ForwardingNode<K, V> extends Node<K, V> {
//
//        /**
//         * 扩容期间的新数组
//         * <p>
//         * todo 之所以占位Node需要保存新Node数组的引用也是因为这个，它可以支持在迁移的过程中照样不阻塞地查找值，可谓是精妙绝伦的设计。
//         */
//        final Node<K, V>[] nextTable;
//
//        /**
//         * 构造方法
//         *
//         * @param tab 新数组
//         */
//        ForwardingNode(Node<K, V>[] tab) {
//            // hash 固定为 MOVED
//            super(MOVED, null, null, null);
//            this.nextTable = tab;
//        }
//
//        /**
//         * 扩容期间查找元素，那么从扩容期间的新数组查找
//         * <p>
//         * todo 和 Redis rehash 查找有点类似，先看能否从旧桶查找，旧桶的元素类型为 ForwardingNode 说明已经迁移了，那么就需要从新的数组中查找
//         *
//         * @param h
//         * @param k
//         * @return
//         */
//        Node<K, V> find(int h, Object k) {
//            // loop to avoid arbitrarily deep recursion on forwarding nodes
//            outer:
//            for (Node<K, V>[] tab = nextTable; ; ) {
//                Node<K, V> e;
//                int n;
//
//                // 新的数组为空，或者对应的桶为空，直接返回
//                if (k == null || tab == null || (n = tab.length) == 0 ||
//                        (e = tabAt(tab, (n - 1) & h)) == null)
//                    return null;
//
//                // 定位桶查找目标元素
//                for (; ; ) {
//                    int eh;
//                    K ek;
//
//                    // 判断是否是要查找的元素
//                    if ((eh = e.hash) == h &&
//                            ((ek = e.key) == k || (ek != null && k.equals(ek))))
//                        return e;
//
//                    // 非链表的情况
//                    if (eh < 0) {
//                        // 如果元素是 ForwardingNode ，说明该桶的元素已经迁移到新的数组中，那么需要从新的数组中查找
//                        if (e instanceof ForwardingNode) {
//                            tab = ((ForwardingNode<K, V>) e).nextTable;
//                            continue outer;
//
//                            // 如果 e 元素对应的桶还没有迁移，则根据红黑树查找
//                        } else
//                            return e.find(h, k);
//                    }
//
//                    // 遍历链表
//                    if ((e = e.next) == null)
//                        return null;
//                }
//
//            }
//        }
//    }
//
//    /**
//     * A place-holder node used in computeIfAbsent and compute
//     */
//    static final class ReservationNode<K, V> extends Node<K, V> {
//        ReservationNode() {
//            super(RESERVED, null, null, null);
//        }
//
//        Node<K, V> find(int h, Object k) {
//            return null;
//        }
//    }
//
//    /* ---------------- Table Initialization and Resizing  数组初始化与调整大小 -------------- */
//
//    /**
//     * Returns the stamp bits for resizing a table of size n.
//     * 返回用于调整大小为 n 的表的标记位。
//     * <p>
//     * Must be negative when shifted left by RESIZE_STAMP_SHIFT.
//     */
//    static final int resizeStamp(int n) {
//        return Integer.numberOfLeadingZeros(n) | (1 << (RESIZE_STAMP_BITS - 1));
//    }
//
//    /**
//     * 初始化一个合适大小的数组，然后设置 sizeCtl
//     * 注意：该方法中并发问题是通过对 sizeCtl 进行一个 CAS 操作来控制的。
//     * <p>
//     * 1. 使用 自旋+CAS，控制只有一个线程初始化桶数组；
//     * 2. sizeCtl 在初始化后存储的是扩容阈值；
//     * 3. 扩容阈值固定为数组大小的 0.75 倍，桶数组大小即 map 的容量。这和 HashMap 是不同的
//     * <p>
//     * todo 总结：在初始化数组时使用了乐观锁操作来决定到底是哪个线程有资格进行初始化，其他线程均只能等待。
//     * <p>
//     * 技巧：
//     * 1. volatile 变量 sizeCtl ，保证线程间的可见性
//     * 2. CAS 操作：CAS操作保证了设置sizeCtl标记位的原子性，保证了只有一个线程能设置成功
//     */
//    private final Node<K, V>[] initTable() {
//        Node<K, V>[] tab;
//        int sc;
//
//        // 每次循环都获取最新的 Node 数组引用
//        // 初始化数组时，sizeCtl 为数组容量大小，如果 sizeClt > 0 ，那使用的是带有初始化容量的构造方法
//        while ((tab = table) == null || tab.length == 0) {
//
//            // 如果 sizeCtl < 0 ，说明其它线程正在进行初始化或扩容，那么就让出 CPU，从运行状态回到就绪状态
//            if ((sc = sizeCtl) < 0)
//                Thread.yield(); // lost initialization race; just spin 初始化失败；只是旋转
//
//                // CAS 一下，将 sizeCtl 设置为 -1，代表抢到了初始化数组的资格，当前线程进入初始化，成功后退出循环。
//                // 如果原子更新失败则说明有其它线程先一步进入初始化了，则进入下一次循环，如果下一次循环时还没初始化完毕，则sizeCtl<0进入上面if的逻辑让出CPU，如果初始化完毕退出循环；
//            else if (U.compareAndSwapInt(this, SIZECTL, sc, -1)) {
//                try {
//
//                    // 再次检查table是否为空
//                    if ((tab = table) == null || tab.length == 0) {
//
//                        // 如果 sc 为 0，则使用 DEFAULT_CAPACITY 默认初始容量是 16
//                        int n = (sc > 0) ? sc : DEFAULT_CAPACITY;
//
//                        // 创建数组，长度为 16 或初始化时提供的长度（也是 2^N)
//                        @SuppressWarnings("unchecked")
//                        Node<K, V>[] nt = (Node<K, V>[]) new Node<?, ?>[n];
//
//                        // 将创建的数组赋值给 table
//                        table = tab = nt;
//
//                        // 设置 sc 为数组长度的 0.75 倍
//                        // n - (n >>> 2) = n - n/4 = 0.75 * n
//                        // todo 可见，这里的负载因子和扩容阈值相当于都是固定了，这也正是没有 threshold 和 loadFactor 属性的原因
//                        sc = n - (n >>> 2);
//                    }
//
//                } finally {
//                    // 由于这里只会有一个线程在执行，直接赋值即可，没有线程安全问题
//                    // todo 把sc赋值给sizeCtl，这时存储的是扩容阈值
//                    sizeCtl = sc;
//                }
//                break;
//            }
//        }
//        return tab;
//    }
//
//    /**
//     * 1. 每次添加元素后，元素数量加 1 ，并判断是否达到扩容阈值，达到了则进行扩容或协助扩容；
//     * 2. 正常情况下 sizeCtl 存储着扩容阈值，扩容阈值为容量的 0.75 倍；
//     * 3. 扩容时，sizeCtl 高位存储扩容标识，低位存储扩容线程数加 1 （1+nThreads)
//     * 4. 其他线程添加元素后，如果发现存在扩容，也会加入到扩容行列中；
//     * <p>
//     * <p>
//     * <p>
//     * Adds to count, and if table is too small and not already
//     * resizing, initiates transfer.
//     * <p>
//     * If already resizing, helps perform transfer if work is available.  Rechecks occupancy
//     * after a transfer to see if another resize is already needed
//     * because resizings are lagging additions.
//     *
//     * @param x     要累加的数量
//     * @param check 如果<0，不检查调整大小；如果<= 1，只检查是否无竞争；
//     */
//    private final void addCount(long x, int check) {
//
//        /*----------------------- 1、累加元素个数  ---------------------------*/
//
//        // 这里使用的思想和 LongAdder 类很类似。
//        // 把数组的大小存储根据不同的线程存储到不同的段上（也是分段锁的思想），并且有一个 baseCount ，优先更新 baseCount，更新失败了再更新不同线程对应的段，这样可以保证尽量小的减少冲突。
//        // 1 在线程竞争不大的时候，直接使用CAS操作递增baseCount值即可，这里说的竞争不大指的是CAS操作不会失败的情况
//        // 2 若出现了CAS操作失败的情况，则证明此时有线程竞争了，计数方式从CAS方式转变为分而治之的桶计数方式
//        CounterCell[] as;
//        long b, s;
//
//        // todo 统计元素个数的操作
//        if ((as = counterCells) != null ||
//                !U.compareAndSwapLong(this, BASECOUNT, b = baseCount, s = b + x)) {
//
//            /*------------- 执行到这里，说明：
//            1.counterCells被初始化完成了，不为null
//            2.增加数量到 baseCount 失败了，存在线程竞争，接下来尝试增加到当前线程映射到的段上
//            3.先优先尝试把数量加到 baseCount 上，如果失败再加到分段的 CounterCell 上
//            ----------------*/
//            CounterCell a;
//            long v;
//            int m;
//
//            //标志是否存在竞争
//            boolean uncontended = true;
//
//            // 在计数桶数组中根据当前线程选一个计数桶，然后使用CAS操作将此计数桶中的value+1
//            // 1 先判断计数桶是否还没初始化，也就是 as==nul，进入语句
//            // 2 判断计数桶长度是否为空或，若是进入语句块
//            // 3 这里做了一个线程变量随机数，与上桶大小-1，若桶的这个位置为空，进入语句块
//            // 4 到这里说明桶已经初始化了，且随机的这个位置不为空，尝试CAS操作使桶加1，失败进入语句块
//            if (as == null || (m = as.length - 1) < 0 ||
//                    (a = as[ThreadLocalRandom.getProbe() & m]) == null ||
//                    !(uncontended =
//                            U.compareAndSwapLong(a, CELLVALUE, v = a.value, v + x))) {
//
//                //若CAS操作失败，证明有竞争，进入fullAddCount方法
//                // 失败几次，那么就对counterCells进行扩容，以减少多个线程hash到同一个段的概率
//                fullAddCount(x, uncontended);
//
//                // 返回
//                return;
//            }
//
//            if (check <= 1)
//                return;
//
//            // 计算元素个数
//            s = sumCount();
//        }
//
//        /*------------------ 2、判断是否需要进行扩容 ----------------*/
//
//        if (check >= 0) {
//            Node<K, V>[] tab, nt;
//            int n, sc;
//
//            // 如果元素个数达到了扩容阈值，则进行扩容。
//            // 注意:
//            // 1 正常情况下 sizeCtl 存储的是扩容阈值，即容量的 0.75 倍;
//            // 2 达到扩容条件时，此时sizeCtl变量用来标示正在扩容，当其准备扩容时，会将sizeCtl设置为一个负数，即 U.compareAndSwapInt(this, SIZECTL, sc, (rs << RESIZE_STAMP_SHIFT) + 2)
//            //   2.1 如果数组长度此时为 16 ，那么此时 sizeCtl 被设置为一个负数，其二进制为 1000 0000 0001 1011 0000 0000 0000 0010
//            //     2.1.1 无符号位为1，表示负数
//            //     2.1.2 高16位代表数组长度的一个位算法标示有点像epoch的作用，表示当前迁移朝代为数组长度X）
//            //     2.1.3 低16位表示有几个线程正在做迁移，刚开始为2，表示有一个线程正在迁移，如果为3，代表2个线程正在迁移以此类推…
//            //   2.2 只要数组长度足够长，就可以同时容纳足够多的线程来一起扩容，最大化并行任务，提高性能。
//            while (s >= (long) (sc = sizeCtl) && (tab = table) != null &&
//                    (n = tab.length) < MAXIMUM_CAPACITY) {
//
//                // rs 为要扩容容量为 n 的数组的一个标识，如数组容量 n=16，那 rs=32795
//                int rs = resizeStamp(n);
//
//                // sc < 0 说明有线程正在迁移
//                if (sc < 0) {
//
//                    //  判断扩容是否已经完成了，如果完成则退出循环
//                    if ((sc >>> RESIZE_STAMP_SHIFT) != rs || sc == rs + 1 ||
//                            sc == rs + MAX_RESIZERS || (nt = nextTable) == null ||
//                            transferIndex <= 0)
//                        break;
//
//                    // 扩容未完成，则当前线程加入迁移数据流程中，并把扩容线程数加 1
//                    if (U.compareAndSwapInt(this, SIZECTL, sc, sc + 1))
//                        transfer(tab, nt);
//
//                    // 不为负数，则为第一个迁移的线程，此时多了创建新数组的操作
//                    // sizeCtl 的高 16 位存储 rs 这个标识
//                    // sizeCtl 的低 16 位存储扩容线程数，刚开始为2，表示有一个线程正在迁移，如果为3，代表2个线程正在迁移以此类推…
//                } else if (U.compareAndSwapInt(this, SIZECTL, sc, (rs << RESIZE_STAMP_SHIFT) + 2))
//
//                    // todo 进入扩容并迁移元素的逻辑，传入 nextTab 为 null 就会触发创建新数组进而扩容
//                    transfer(tab, null);
//
//                // 重新计算元素个数
//                s = sumCount();
//            }
//        }
//    }
//
//
//    /**
//     * Helps transfer if a resize is in progress.
//     * 辅助扩容（迁移元素）
//     * <p>
//     * 当前桶元素迁移完了才去协助迁移其它桶元素
//     *
//     * @param tab 旧数组
//     * @param f   一般为 ForwardingNode 类型
//     */
//    final Node<K, V>[] helpTransfer(Node<K, V>[] tab, Node<K, V> f) {
//        Node<K, V>[] nextTab;
//        int sc;
//
//        // 如果桶数组不为空 && 当前桶第一个元素 f 是 ForwardingNode 类型 && nextTab（新数组） 不为空，说明当前桶已经迁移完毕了，才去帮忙迁移其它桶的元素。
//        // 扩容时会把旧桶的第一个元素置为ForwardingNode，并让其nextTab指向新桶数组。
//        if (tab != null && (f instanceof ForwardingNode) &&
//                (nextTab = ((ForwardingNode<K, V>) f).nextTable) != null) {
//
//            // 获取数组调整大小为 tab.length 的标记位
//            int rs = resizeStamp(tab.length);
//
//            // sizeCtl < 0 && nextTable 不为空，说明集合正处于扩容过程；初始化事 sizeCtl==-1，但是那个时候 nextTable ==null
//            // todo 为啥？因为扩容时，会在 java.util.concurrent.ConcurrentHashMap.addCount 中设置 sizeCtl 为负数
//            while (nextTab == nextTable && table == tab && (sc = sizeCtl) < 0) {
//
//                // 如果扩容结束，直接返回，无需协助迁移
//                if ((sc >>> RESIZE_STAMP_SHIFT) != rs || sc == rs + 1 ||
//                        sc == rs + MAX_RESIZERS || transferIndex <= 0)
//                    break;
//
//                // 扩容线程数加 1 ，标示多一个线程进来协助迁移
//                if (U.compareAndSwapInt(this, SIZECTL, sc, sc + 1)) {
//                    // 当前线程帮忙迁移元素
//                    transfer(tab, nextTab);
//                    break;
//                }
//            }
//
//            // 帮忙迁移元素，返回新数组
//            return nextTab;
//        }
//
//        // 返回旧数组
//        return table;
//    }
//
//    /**
//     * 扩容
//     */
//    private final void tryPresize(int size) {
//        // 取 2^n 值
//        int c = (size >= (MAXIMUM_CAPACITY >>> 1)) ? MAXIMUM_CAPACITY : tableSizeFor(size + (size >>> 1) + 1);
//        int sc;
//
//        // 数组没有在初始化或扩容
//        while ((sc = sizeCtl) >= 0) {
//            Node<K, V>[] tab = table;
//            int n;
//
//            // 该分支用于初始化数组，主要用于 putAll 方法添加元素的场景
//            if (tab == null || (n = tab.length) == 0) {
//                n = (sc > c) ? sc : c;
//                if (U.compareAndSwapInt(this, SIZECTL, sc, -1)) {
//                    try {
//                        if (table == tab) {
//                            @SuppressWarnings("unchecked")
//                            Node<K, V>[] nt = (Node<K, V>[]) new Node<?, ?>[n];
//                            table = nt;
//                            sc = n - (n >>> 2);
//                        }
//                    } finally {
//                        sizeCtl = sc;
//                    }
//                }
//            }
//
//            // 无需进行扩容
//            else if (c <= sc || n >= MAXIMUM_CAPACITY)
//                break;
//                // 进行扩容
//            else if (tab == table) {
//                // 扩容 table 的 epoch
//                int rs = resizeStamp(n);
//
//                // 如果处于扩容过程中
//                if (sc < 0) {
//                    Node<K, V>[] nt;
//                    // 扩容完毕了，退出循环
//                    if ((sc >>> RESIZE_STAMP_SHIFT) != rs || sc == rs + 1 ||
//                            sc == rs + MAX_RESIZERS || (nt = nextTable) == null ||
//                            transferIndex <= 0)
//                        break;
//
//                    //  CAS 将 sizeCtl 加 1 表示当前线程加入到扩容过程，然后执行 transfer 方法
//                    if (U.compareAndSwapInt(this, SIZECTL, sc, sc + 1))
//                        transfer(tab, nt);
//
//                    // CAS 设置 sizeCtl 成功后执行 transfer 方法进行扩容
//                } else if (U.compareAndSwapInt(this, SIZECTL, sc,
//                        (rs << RESIZE_STAMP_SHIFT) + 2))
//                    transfer(tab, null);
//            }
//        }
//    }
//
//    /**
//     * 扩容或迁移元素
//     * <p>
//     * 1. 扩容时容量变为两倍，并把部分元素迁移到其它桶中;
//     * 2. todo 扩容时支持多线程并发扩容，在扩容过程中同时支持 get 查数据，若有线程put数据，还会帮助一起扩容，这种无阻塞算法，将并行最大化的设计，堪称一绝。
//     *
//     *
//     * <p>
//     * Moves and/or copies the nodes in each bin to new table.
//     * See above for explanation.
//     *
//     * @param tab     旧数组
//     * @param nextTab 扩大2倍后的新数组，如果为空说明还没有创建新数组
//     */
//    private final void transfer(Node<K, V>[] tab, Node<K, V>[] nextTab) {
//        // 记录旧数组容量
//        int n = tab.length, stride;
//
//        // 根据机器CPU核心数来计算，一条线程负责Node数组中多长的迁移量，stride 就是当前线程分到的迁移量
//        // 如果 n/8/NCPU < 16 ，则只会有一个线程进行扩容，否则根据 CPU 核心数来进行分配
//        if ((stride = (NCPU > 1) ? (n >>> 3) / NCPU : n) < MIN_TRANSFER_STRIDE)
//            stride = MIN_TRANSFER_STRIDE; // subdivide range  默认每个线程 16
//
//
//        /*--------------------------------- 1、扩容 -------------------------------------*/
//
//        // nextTab 为空，说明还没开始迁移，就创建一个新桶数组
//        if (nextTab == null) {            // initiating
//            try {
//                // 新桶数组大小是原桶的两倍
//                @SuppressWarnings("unchecked")
//                Node<K, V>[] nt = (Node<K, V>[]) new Node<?, ?>[n << 1];
//                nextTab = nt;
//            } catch (Throwable ex) {      // try to cope with OOME
//                sizeCtl = Integer.MAX_VALUE;
//                return;
//            }
//
//            // 扩容期间的新数组 - volatile 保证可见性
//            nextTable = nextTab;
//
//            // 旧数组容量作为迁移下标
//            transferIndex = n;
//        }
//
//
//        /*--------------------------------- 2、迁移元素 ---------------------------------*/
//
//        // 新数组大小
//        int nextn = nextTab.length;
//
//        // 新建一个 ForwardingNode 类型的标记节点，其 hash 值为 MOVED，并把新桶数组存储在里面
//        // todo 这个对象作为旧数组某个桶迁移完毕的标志
//        ForwardingNode<K, V> fwd = new ForwardingNode<K, V>(nextTab);
//
//        // 是否可以继续推进下一个槽位，只有当前槽位数据被迁移完成之后才可以设置为 true
//        boolean advance = true;
//
//        // 是否已经完成数据迁移
//        boolean finishing = false; // to ensure sweep before committing nextTab
//
//        for (int i = 0, bound = 0; ; ) {
//            Node<K, V> f;
//            int fh;
//
//            // 2.1 确定当前线程要迁移哪些桶，比如线程1负责 1-16 ，那么对应的数组边界就是 0-15 ，然后从最后一位 15 开始迁移数据
//            // i 的值会从 n-1 依次递减，其中 n 是旧桶数组的大小。比如，i 从 15 开始一直减到 1 这样去迁移元素
//            while (advance) {
//                int nextIndex, nextBound;
//
//                // i为当前正在处理的Node数组下标，每次处理一个Node节点就会自减1
//                if (--i >= bound || finishing)
//                    advance = false;
//
//
//                else if ((nextIndex = transferIndex) <= 0) {
//                    i = -1;
//                    advance = false;
//
//                    // 通过 CAS 操作确认当前线程迁移桶的边界
//                } else if (U.compareAndSwapInt(this, TRANSFERINDEX, nextIndex, nextBound = (nextIndex > stride ? nextIndex - stride : 0))) {
//                    bound = nextBound;
//                    i = nextIndex - 1;
//                    advance = false;
//                }
//            }
//
//
//            // 2.2 对下标为 i 的桶进行处理
//            if (i < 0 || i >= n || i + n >= nextn) {
//                int sc;
//
//                // 如果整个 map 所有桶中的元素都迁移完成了，则替换旧桶数组，并设置下一次扩容阈值为新桶数组容量的 0.75 倍
//                if (finishing) {
//                    nextTable = null;
//                    table = nextTab;
//                    sizeCtl = (n << 1) - (n >>> 1);
//                    return;
//                }
//
//                // 首次进入这里，通过 CAS 将 sizeCtl 减 1 表示将扩容的线程数 -1，表示当前线程扩容完成
//                if (U.compareAndSwapInt(this, SIZECTL, sc = sizeCtl, sc - 1)) {
//                    // 判断扩容是否完成，也就是前面进入扩容前条件的反推，如果成立说明扩容完成，设置 finishing 为 true
//                    if ((sc - 2) != resizeStamp(n) << RESIZE_STAMP_SHIFT)
//                        return;
//
//                    // 标记完成
//                    finishing = advance = true;
//
//                    // i 重新赋值为 n，这样会再重新遍历一次桶数组，看看是不是都迁移完成了
//                    // 也就是第二次遍历都会走到下面的(fh = f.hash) == MOVED这个条件
//                    i = n; // recheck before commit
//                }
//
//                // 如果桶中无数据，直接在旧桶的 i 位置放入 ForwardingNode 表示该桶已迁移完
//            } else if ((f = tabAt(tab, i)) == null)
//                advance = casTabAt(tab, i, null, fwd);
//
//                // 如果桶中第一个元素的 hash 值为 MOVED ，说明它是 ForwardingNode 节点，表示该桶已迁移完
//            else if ((fh = f.hash) == MOVED)
//                advance = true; // already processed
//
//                // 迁移 i 桶中的元素
//            else {
//                // todo 锁定该桶，并迁移元素
//                synchronized (f) {
//
//                    // 再次判断当前桶第一个元素是否有修改，也就是可能其它线程先一步迁移了元素
//                    if (tabAt(tab, i) == f) {
//                        Node<K, V> ln, hn;
//
//                        // 桶中第一个元素 fh >= 0 说明桶是链表形式，这里与 HashMap 迁移算法很类似
//                        // 把一个链表分化成两个链表，规则是桶中各元素的 hash 与桶（旧的桶）大小 n 进行与操作：
//                        // 等于 0 的放到低位链表中，不等于 0 的放到高位链表中。
//                        // 低位链表迁移到新桶中的位置相对旧桶不变，高位链表迁移到新桶中位置正好是其在旧桶的位置加 n
//                        // todo 这也是为什么扩容时容量扩为 2 倍的一个原因
//                        if (fh >= 0) {
//
//                            /*
//                             和 HashNap 唯一不同的是多了一步寻找 lastRun ，这里的lastRun是提取出链表后面不用处理再特殊处理的子链表，
//                             比如所有元素的hash值与桶大小n与操作后的值分别为 0 0 4 4 0 0 0，那么最后后面三个0对应的元素肯定还是在同一个桶中，
//                             这时lastRun对应的就是倒数第三个节点
//                             */
//                            int runBit = fh & n;
//                            Node<K, V> lastRun = f;
//                            for (Node<K, V> p = f.next; p != null; p = p.next) {
//                                int b = p.hash & n;
//                                if (b != runBit) {
//                                    runBit = b;
//                                    lastRun = p;
//                                }
//                            }
//
//                            // 看看最后这几个元素归属于低位链表还是高位链表
//                            if (runBit == 0) {
//                                ln = lastRun;
//                                hn = null;
//                            } else {
//                                hn = lastRun;
//                                ln = null;
//                            }
//
//                            // 遍历链表，把hash&n为0的放在低位链表中，不为0的放在高位链表中
//                            for (Node<K, V> p = f; p != lastRun; p = p.next) {
//                                int ph = p.hash;
//                                K pk = p.key;
//                                V pv = p.val;
//                                if ((ph & n) == 0)
//                                    ln = new Node<K, V>(ph, pk, pv, ln);
//                                else
//                                    hn = new Node<K, V>(ph, pk, pv, hn);
//                            }
//
//                            // 低位链表的位置不变 - CAS操作
//                            setTabAt(nextTab, i, ln);
//                            // 高位链表的位置是原位置加n - CAS操作
//                            setTabAt(nextTab, i + n, hn);
//
//                            // todo 使用 Unsafe 的 volatile 式标记该桶已迁移，即在桶中放置 ForwardingNode 类型的元素，标记该桶迁移完成
//                            setTabAt(tab, i, fwd);
//
//                            // advance为true，返回上面进行--i操作
//                            advance = true;
//
//                            // 如果桶中第一个元素是树节点，也是一样分化成两颗树，也是根据hash&n为0放在低位树中，不为0放在高位树中。
//                            // 基本同 HashMap
//                        } else if (f instanceof TreeBin) {
//                            TreeBin<K, V> t = (TreeBin<K, V>) f;
//                            TreeNode<K, V> lo = null, loTail = null;
//                            TreeNode<K, V> hi = null, hiTail = null;
//                            int lc = 0, hc = 0;
//                            for (Node<K, V> e = t.first; e != null; e = e.next) {
//                                int h = e.hash;
//                                TreeNode<K, V> p = new TreeNode<K, V>
//                                        (h, e.key, e.val, null, null);
//                                if ((h & n) == 0) {
//                                    if ((p.prev = loTail) == null)
//                                        lo = p;
//                                    else
//                                        loTail.next = p;
//                                    loTail = p;
//                                    ++lc;
//                                } else {
//                                    if ((p.prev = hiTail) == null)
//                                        hi = p;
//                                    else
//                                        hiTail.next = p;
//                                    hiTail = p;
//                                    ++hc;
//                                }
//                            }
//
//                            // 如果分化的树中元素个数小于等于6，则退化成链表
//                            ln = (lc <= UNTREEIFY_THRESHOLD) ? untreeify(lo) :
//                                    (hc != 0) ? new TreeBin<K, V>(lo) : t;
//                            hn = (hc <= UNTREEIFY_THRESHOLD) ? untreeify(hi) :
//                                    (lc != 0) ? new TreeBin<K, V>(hi) : t;
//
//                            // 低位树的位置不变
//                            setTabAt(nextTab, i, ln);
//                            // 高位树的位置是原位置加n
//                            setTabAt(nextTab, i + n, hn);
//
//                            // todo 使用 Unsafe 的 volatile 式标记该桶已迁移，即在桶中放置 ForwardingNode 类型的元素，标记该桶迁移完成
//                            setTabAt(tab, i, fwd);
//                            // advance为true，返回上面进行--i操作
//                            advance = true;
//                        }
//
//                    }
//                }
//            }
//        }
//    }
//
//    /* ---------------- Counter support -------------- */
//
//    /**
//     * A padded cell for distributing counts.  Adapted from LongAdder
//     * and Striped64.  See their internal docs for explanation.
//     * <p>
//     * 段是由线程对应的，多个线程可以对应一个段
//     */
//    @sun.misc.Contended
//    static final class CounterCell {
//        /**
//         * 当前段对应的元素数量，可见性保证
//         */
//        volatile long value;
//
//        CounterCell(long x) {
//            value = x;
//        }
//    }
//
//    /**
//     * 元素个数
//     * <p>
//     * 1. 元素个数的存储方式类似于 LongAddr 类，存储在不同的段上，减少不同线程同时更新 size 时的冲突；
//     * 2. 计算元素个数时，把这些段的值及 baseCount 相加算出总的元素个数；
//     * <p>
//     * 3. 弱一致性（1.7 是强一致性）。这里获取 counterCells 中每个元素时没有使用可见性
//     * <p>
//     *
//     * @return
//     */
//    final long sumCount() {
//        // 计算所有计数桶及 baseCount 的数量之和
//        CounterCell[] as = counterCells;
//        CounterCell a;
//        long sum = baseCount;
//        if (as != null) {
//            // 遍历 CounterCell ，不保证可见性
//            for (int i = 0; i < as.length; ++i) {
//                // 使用 getObjectVolatile 方法保证可见性
//                if ((a = as[i]) != null)
//                    sum += a.value;
//            }
//        }
//        return sum;
//    }
//
//    // See LongAdder version for explanation
//    private final void fullAddCount(long x, boolean wasUncontended) {
//        // 线程对应的随机值
//        int h;
//        if ((h = ThreadLocalRandom.getProbe()) == 0) {
//            ThreadLocalRandom.localInit();      // force initialization
//            h = ThreadLocalRandom.getProbe();
//            wasUncontended = true;
//        }
//
//        boolean collide = false;                // True if last slot nonempty
//
//        // 自旋，更新元素个数
//        for (; ; ) {
//            CounterCell[] as;
//            CounterCell a;
//            int n;
//            long v;
//
//            // 如果计数单元桶!=null，证明已经初始化过，那么就针对当前线程映射一个计算单元并累加数量
//            if ((as = counterCells) != null && (n = as.length) > 0) {
//
//                //从计数桶数组随机选一个计数桶，若为null表示该桶位还没线程递增过
//                if ((a = as[(n - 1) & h]) == null) {
//
//                    //判断计数单元桶busy状态，如果不忙碌
//                    if (cellsBusy == 0) {            // Try to attach new Cell
//
//                        //若不busy，直接创建一个计数单元，初始值为 x
//                        CounterCell r = new CounterCell(x); // Optimistic create
//
//                        //CAS操作，标记计数桶busy中
//                        if (cellsBusy == 0 &&
//                                U.compareAndSwapInt(this, CELLSBUSY, 0, 1)) {
//                            boolean created = false;
//                            try {               // Recheck under lock
//                                CounterCell[] rs;
//                                int m, j;
//
//                                // 再检查一次计数桶为null
//                                if ((rs = counterCells) != null &&
//                                        (m = rs.length) > 0 &&
//                                        rs[j = (m - 1) & h] == null) {
//
//                                    //将刚刚创建的计数桶赋值给对应位置
//                                    rs[j] = r;
//                                    created = true;
//                                }
//                            } finally {
//                                //标示不busy了
//                                cellsBusy = 0;
//                            }
//                            if (created)
//                                break;
//
//                            continue;           // Slot is now non-empty
//                        }
//                    }
//                    collide = false;
//
//
//                } else if (!wasUncontended)       // CAS already known to fail
//                    wasUncontended = true;      // Continue after rehash
//
//                    //走到这里代表计数桶不为null，尝试递增计数桶
//                else if (U.compareAndSwapLong(a, CELLVALUE, v = a.value, v + x))
//                    break;
//
//                else if (counterCells != as || n >= NCPU)
//                    collide = false;            // At max size or stale
//
//                    //若CAS操作失败了，到了这里，会先进入一次，然后再走一次刚刚的for循环
//                    //若是第二次for循环，collide=true，则不会走进去
//                else if (!collide)
//                    collide = true;
//
//                    //计数桶扩容，一个线程若走了两次for循环，也就是进行了多次CAS操作递增计数桶失败了
//                    //则进行计数桶扩容，CAS标示计数桶busy中
//                else if (cellsBusy == 0 &&
//                        U.compareAndSwapInt(this, CELLSBUSY, 0, 1)) {
//
//                    try {
//                        //确认计数桶还是同一个
//                        if (counterCells == as) {// Expand table unless stale
//
//                            //将长度扩大到2倍
//                            CounterCell[] rs = new CounterCell[n << 1];
//
//                            //遍历旧计数桶，将引用直接搬过来
//                            for (int i = 0; i < n; ++i)
//                                rs[i] = as[i];
//                            counterCells = rs;
//                        }
//                    } finally {
//                        //取消busy状态
//                        cellsBusy = 0;
//                    }
//                    collide = false;
//                    continue;                   // Retry with expanded table
//                }
//
//                // 重新计算当前线程的随机值，用于定位对应的计数桶
//                h = ThreadLocalRandom.advanceProbe(h);
//
//
//                // 进入此语句块进行计数桶的初始化
//                // CAS设置cellsBusy=1，表示现在计数桶 busy 中
//            } else if (cellsBusy == 0 && counterCells == as &&
//                    U.compareAndSwapInt(this, CELLSBUSY, 0, 1)) {
//
//                // 若有线程同时初始化计数桶，由于CAS操作只有一个线程进入这里
//                boolean init = false;
//
//                try {
//
//                    //再次确认计数桶为空
//                    // Initialize table
//                    if (counterCells == as) {
//                        //初始化一个长度为2的计数桶
//                        CounterCell[] rs = new CounterCell[2];
//                        //h为一个随机数，与上1则代表结果为0、1中随机的一个
//                        //也就是在0、1下标中随便选一个计数桶，x=1，放入1的值代表增加1个容量
//                        rs[h & 1] = new CounterCell(x);
//                        //将初始化好的计数桶赋值给ConcurrentHashMap
//                        counterCells = rs;
//                        init = true;
//                    }
//                } finally {
//                    //最后将busy标识设置为0，表示不busy了
//                    cellsBusy = 0;
//                }
//                if (init)
//                    break;
//
//                //若有线程同时来初始化计数桶，则没有抢到busy资格的线程就先来CAS递增baseCount
//            } else if (U.compareAndSwapLong(this, BASECOUNT, v = baseCount, v + x))
//                break;                          // Fall back on using base
//        }
//    }
//
//    /* ---------------- Conversion from/to TreeBins -------------- */
//
//    /**
//     * 尝试将链表转红黑树
//     */
//    private final void treeifyBin(Node<K, V>[] tab, int index) {
//        Node<K, V> b;
//        int n, sc;
//
//        if (tab != null) {
//            // 如果数组长度小于 64 时优先进行数组扩容
//            if ((n = tab.length) < MIN_TREEIFY_CAPACITY)
//                // todo 扩容
//                tryPresize(n << 1);
//
//                // b 是头节点
//            else if ((b = tabAt(tab, index)) != null && b.hash >= 0) {
//
//                // 获取锁，下面执行链表转红黑树的操作
//                synchronized (b) {
//                    if (tabAt(tab, index) == b) {
//                        TreeNode<K, V> hd = null, tl = null;
//
//                        // 遍历链表，将链表节点数据转成 TreeNode 链表
//                        for (Node<K, V> e = b; e != null; e = e.next) {
//                            TreeNode<K, V> p =
//                                    new TreeNode<K, V>(e.hash, e.key, e.val,
//                                            null, null);
//                            if ((p.prev = tl) == null)
//                                hd = p;
//                            else
//                                tl.next = p;
//                            tl = p;
//                        }
//
//                        // 将建立的红黑树设置到数组对应的位置上
//                        setTabAt(tab, index, new TreeBin<K, V>(hd));
//                    }
//                }
//
//            }
//        }
//
//
//    }
//
//    /**
//     * Returns a list on non-TreeNodes replacing those in given list.
//     */
//    static <K, V> Node<K, V> untreeify(Node<K, V> b) {
//        Node<K, V> hd = null, tl = null;
//        for (Node<K, V> q = b; q != null; q = q.next) {
//            Node<K, V> p = new Node<K, V>(q.hash, q.key, q.val, null);
//            if (tl == null)
//                hd = p;
//            else
//                tl.next = p;
//            tl = p;
//        }
//        return hd;
//    }
//
//    /* ---------------- TreeNodes -------------- */
//
//    /**
//     * Nodes for use in TreeBins
//     */
//    static final class TreeNode<K, V> extends Node<K, V> {
//        TreeNode<K, V> parent;  // red-black tree links
//        TreeNode<K, V> left;
//        TreeNode<K, V> right;
//        TreeNode<K, V> prev;    // needed to unlink next upon deletion
//        boolean red;
//
//        TreeNode(int hash, K key, V val, Node<K, V> next,
//                 TreeNode<K, V> parent) {
//            super(hash, key, val, next);
//            this.parent = parent;
//        }
//
//        Node<K, V> find(int h, Object k) {
//            return findTreeNode(h, k, null);
//        }
//
//        /**
//         * Returns the TreeNode (or null if not found) for the given key
//         * starting at given root.
//         */
//        final TreeNode<K, V> findTreeNode(int h, Object k, Class<?> kc) {
//            if (k != null) {
//                TreeNode<K, V> p = this;
//                do {
//                    int ph, dir;
//                    K pk;
//                    TreeNode<K, V> q;
//                    TreeNode<K, V> pl = p.left, pr = p.right;
//                    if ((ph = p.hash) > h)
//                        p = pl;
//                    else if (ph < h)
//                        p = pr;
//                    else if ((pk = p.key) == k || (pk != null && k.equals(pk)))
//                        return p;
//                    else if (pl == null)
//                        p = pr;
//                    else if (pr == null)
//                        p = pl;
//                    else if ((kc != null ||
//                            (kc = comparableClassFor(k)) != null) &&
//                            (dir = compareComparables(kc, k, pk)) != 0)
//                        p = (dir < 0) ? pl : pr;
//                    else if ((q = pr.findTreeNode(h, k, kc)) != null)
//                        return q;
//                    else
//                        p = pl;
//                } while (p != null);
//            }
//            return null;
//        }
//    }
//
//    /* ---------------- TreeBins -------------- */
//
//    /**
//     * TreeNodes used at the heads of bins. TreeBins do not hold user
//     * keys or values, but instead point to list of TreeNodes and
//     * their root. They also maintain a parasitic read-write lock
//     * forcing writers (who hold bin lock) to wait for readers (who do
//     * not) to complete before tree restructuring operations.
//     */
//    static final class TreeBin<K, V> extends Node<K, V> {
//        TreeNode<K, V> root;
//        volatile TreeNode<K, V> first;
//        volatile Thread waiter;
//        volatile int lockState;
//        // values for lockState
//        static final int WRITER = 1; // set while holding write lock
//        static final int WAITER = 2; // set when waiting for write lock
//        static final int READER = 4; // increment value for setting read lock
//
//        /**
//         * Tie-breaking utility for ordering insertions when equal
//         * hashCodes and non-comparable. We don't require a total
//         * order, just a consistent insertion rule to maintain
//         * equivalence across rebalancings. Tie-breaking further than
//         * necessary simplifies testing a bit.
//         */
//        static int tieBreakOrder(Object a, Object b) {
//            int d;
//            if (a == null || b == null ||
//                    (d = a.getClass().getName().
//                            compareTo(b.getClass().getName())) == 0)
//                d = (System.identityHashCode(a) <= System.identityHashCode(b) ?
//                        -1 : 1);
//            return d;
//        }
//
//        /**
//         * Creates bin with initial set of nodes headed by b.
//         */
//        TreeBin(TreeNode<K, V> b) {
//            super(TREEBIN, null, null, null);
//            this.first = b;
//            TreeNode<K, V> r = null;
//            for (TreeNode<K, V> x = b, next; x != null; x = next) {
//                next = (TreeNode<K, V>) x.next;
//                x.left = x.right = null;
//                if (r == null) {
//                    x.parent = null;
//                    x.red = false;
//                    r = x;
//                } else {
//                    K k = x.key;
//                    int h = x.hash;
//                    Class<?> kc = null;
//                    for (TreeNode<K, V> p = r; ; ) {
//                        int dir, ph;
//                        K pk = p.key;
//                        if ((ph = p.hash) > h)
//                            dir = -1;
//                        else if (ph < h)
//                            dir = 1;
//                        else if ((kc == null &&
//                                (kc = comparableClassFor(k)) == null) ||
//                                (dir = compareComparables(kc, k, pk)) == 0)
//                            dir = tieBreakOrder(k, pk);
//                        TreeNode<K, V> xp = p;
//                        if ((p = (dir <= 0) ? p.left : p.right) == null) {
//                            x.parent = xp;
//                            if (dir <= 0)
//                                xp.left = x;
//                            else
//                                xp.right = x;
//                            r = balanceInsertion(r, x);
//                            break;
//                        }
//                    }
//                }
//            }
//            this.root = r;
//            assert checkInvariants(root);
//        }
//
//        /**
//         * Acquires write lock for tree restructuring.
//         */
//        private final void lockRoot() {
//            if (!U.compareAndSwapInt(this, LOCKSTATE, 0, WRITER))
//                contendedLock(); // offload to separate method
//        }
//
//        /**
//         * Releases write lock for tree restructuring.
//         */
//        private final void unlockRoot() {
//            lockState = 0;
//        }
//
//        /**
//         * Possibly blocks awaiting root lock.
//         */
//        private final void contendedLock() {
//            boolean waiting = false;
//            for (int s; ; ) {
//                if (((s = lockState) & ~WAITER) == 0) {
//                    if (U.compareAndSwapInt(this, LOCKSTATE, s, WRITER)) {
//                        if (waiting)
//                            waiter = null;
//                        return;
//                    }
//                } else if ((s & WAITER) == 0) {
//                    if (U.compareAndSwapInt(this, LOCKSTATE, s, s | WAITER)) {
//                        waiting = true;
//                        waiter = Thread.currentThread();
//                    }
//                } else if (waiting)
//                    LockSupport.park(this);
//            }
//        }
//
//        /**
//         * Returns matching node or null if none. Tries to search
//         * using tree comparisons from root, but continues linear
//         * search when lock not available.
//         */
//        final Node<K, V> find(int h, Object k) {
//            if (k != null) {
//                for (Node<K, V> e = first; e != null; ) {
//                    int s;
//                    K ek;
//                    if (((s = lockState) & (WAITER | WRITER)) != 0) {
//                        if (e.hash == h &&
//                                ((ek = e.key) == k || (ek != null && k.equals(ek))))
//                            return e;
//                        e = e.next;
//                    } else if (U.compareAndSwapInt(this, LOCKSTATE, s,
//                            s + READER)) {
//                        TreeNode<K, V> r, p;
//                        try {
//                            p = ((r = root) == null ? null :
//                                    r.findTreeNode(h, k, null));
//                        } finally {
//                            Thread w;
//                            if (U.getAndAddInt(this, LOCKSTATE, -READER) ==
//                                    (READER | WAITER) && (w = waiter) != null)
//                                LockSupport.unpark(w);
//                        }
//                        return p;
//                    }
//                }
//            }
//            return null;
//        }
//
//        /**
//         * Finds or adds a node.
//         *
//         * @return null if added
//         */
//        final TreeNode<K, V> putTreeVal(int h, K k, V v) {
//            Class<?> kc = null;
//            boolean searched = false;
//            for (TreeNode<K, V> p = root; ; ) {
//                int dir, ph;
//                K pk;
//                if (p == null) {
//                    first = root = new TreeNode<K, V>(h, k, v, null, null);
//                    break;
//                } else if ((ph = p.hash) > h)
//                    dir = -1;
//                else if (ph < h)
//                    dir = 1;
//                else if ((pk = p.key) == k || (pk != null && k.equals(pk)))
//                    return p;
//                else if ((kc == null &&
//                        (kc = comparableClassFor(k)) == null) ||
//                        (dir = compareComparables(kc, k, pk)) == 0) {
//                    if (!searched) {
//                        TreeNode<K, V> q, ch;
//                        searched = true;
//                        if (((ch = p.left) != null &&
//                                (q = ch.findTreeNode(h, k, kc)) != null) ||
//                                ((ch = p.right) != null &&
//                                        (q = ch.findTreeNode(h, k, kc)) != null))
//                            return q;
//                    }
//                    dir = tieBreakOrder(k, pk);
//                }
//
//                TreeNode<K, V> xp = p;
//                if ((p = (dir <= 0) ? p.left : p.right) == null) {
//                    TreeNode<K, V> x, f = first;
//                    first = x = new TreeNode<K, V>(h, k, v, f, xp);
//                    if (f != null)
//                        f.prev = x;
//                    if (dir <= 0)
//                        xp.left = x;
//                    else
//                        xp.right = x;
//                    if (!xp.red)
//                        x.red = true;
//                    else {
//                        lockRoot();
//                        try {
//                            root = balanceInsertion(root, x);
//                        } finally {
//                            unlockRoot();
//                        }
//                    }
//                    break;
//                }
//            }
//            assert checkInvariants(root);
//            return null;
//        }
//
//        /**
//         * Removes the given node, that must be present before this
//         * call.  This is messier than typical red-black deletion code
//         * because we cannot swap the contents of an interior node
//         * with a leaf successor that is pinned by "next" pointers
//         * that are accessible independently of lock. So instead we
//         * swap the tree linkages.
//         *
//         * @return true if now too small, so should be untreeified
//         */
//        final boolean removeTreeNode(TreeNode<K, V> p) {
//            TreeNode<K, V> next = (TreeNode<K, V>) p.next;
//            TreeNode<K, V> pred = p.prev;  // unlink traversal pointers
//            TreeNode<K, V> r, rl;
//            if (pred == null)
//                first = next;
//            else
//                pred.next = next;
//            if (next != null)
//                next.prev = pred;
//            if (first == null) {
//                root = null;
//                return true;
//            }
//            if ((r = root) == null || r.right == null || // too small
//                    (rl = r.left) == null || rl.left == null)
//                return true;
//            lockRoot();
//            try {
//                TreeNode<K, V> replacement;
//                TreeNode<K, V> pl = p.left;
//                TreeNode<K, V> pr = p.right;
//                if (pl != null && pr != null) {
//                    TreeNode<K, V> s = pr, sl;
//                    while ((sl = s.left) != null) // find successor
//                        s = sl;
//                    boolean c = s.red;
//                    s.red = p.red;
//                    p.red = c; // swap colors
//                    TreeNode<K, V> sr = s.right;
//                    TreeNode<K, V> pp = p.parent;
//                    if (s == pr) { // p was s's direct parent
//                        p.parent = s;
//                        s.right = p;
//                    } else {
//                        TreeNode<K, V> sp = s.parent;
//                        if ((p.parent = sp) != null) {
//                            if (s == sp.left)
//                                sp.left = p;
//                            else
//                                sp.right = p;
//                        }
//                        if ((s.right = pr) != null)
//                            pr.parent = s;
//                    }
//                    p.left = null;
//                    if ((p.right = sr) != null)
//                        sr.parent = p;
//                    if ((s.left = pl) != null)
//                        pl.parent = s;
//                    if ((s.parent = pp) == null)
//                        r = s;
//                    else if (p == pp.left)
//                        pp.left = s;
//                    else
//                        pp.right = s;
//                    if (sr != null)
//                        replacement = sr;
//                    else
//                        replacement = p;
//                } else if (pl != null)
//                    replacement = pl;
//                else if (pr != null)
//                    replacement = pr;
//                else
//                    replacement = p;
//                if (replacement != p) {
//                    TreeNode<K, V> pp = replacement.parent = p.parent;
//                    if (pp == null)
//                        r = replacement;
//                    else if (p == pp.left)
//                        pp.left = replacement;
//                    else
//                        pp.right = replacement;
//                    p.left = p.right = p.parent = null;
//                }
//
//                root = (p.red) ? r : balanceDeletion(r, replacement);
//
//                if (p == replacement) {  // detach pointers
//                    TreeNode<K, V> pp;
//                    if ((pp = p.parent) != null) {
//                        if (p == pp.left)
//                            pp.left = null;
//                        else if (p == pp.right)
//                            pp.right = null;
//                        p.parent = null;
//                    }
//                }
//            } finally {
//                unlockRoot();
//            }
//            assert checkInvariants(root);
//            return false;
//        }
//
//        /* ------------------------------------------------------------ */
//        // Red-black tree methods, all adapted from CLR
//
//        static <K, V> TreeNode<K, V> rotateLeft(TreeNode<K, V> root,
//                                                TreeNode<K, V> p) {
//            TreeNode<K, V> r, pp, rl;
//            if (p != null && (r = p.right) != null) {
//                if ((rl = p.right = r.left) != null)
//                    rl.parent = p;
//                if ((pp = r.parent = p.parent) == null)
//                    (root = r).red = false;
//                else if (pp.left == p)
//                    pp.left = r;
//                else
//                    pp.right = r;
//                r.left = p;
//                p.parent = r;
//            }
//            return root;
//        }
//
//        static <K, V> TreeNode<K, V> rotateRight(TreeNode<K, V> root,
//                                                 TreeNode<K, V> p) {
//            TreeNode<K, V> l, pp, lr;
//            if (p != null && (l = p.left) != null) {
//                if ((lr = p.left = l.right) != null)
//                    lr.parent = p;
//                if ((pp = l.parent = p.parent) == null)
//                    (root = l).red = false;
//                else if (pp.right == p)
//                    pp.right = l;
//                else
//                    pp.left = l;
//                l.right = p;
//                p.parent = l;
//            }
//            return root;
//        }
//
//        static <K, V> TreeNode<K, V> balanceInsertion(TreeNode<K, V> root,
//                                                      TreeNode<K, V> x) {
//            x.red = true;
//            for (TreeNode<K, V> xp, xpp, xppl, xppr; ; ) {
//                if ((xp = x.parent) == null) {
//                    x.red = false;
//                    return x;
//                } else if (!xp.red || (xpp = xp.parent) == null)
//                    return root;
//                if (xp == (xppl = xpp.left)) {
//                    if ((xppr = xpp.right) != null && xppr.red) {
//                        xppr.red = false;
//                        xp.red = false;
//                        xpp.red = true;
//                        x = xpp;
//                    } else {
//                        if (x == xp.right) {
//                            root = rotateLeft(root, x = xp);
//                            xpp = (xp = x.parent) == null ? null : xp.parent;
//                        }
//                        if (xp != null) {
//                            xp.red = false;
//                            if (xpp != null) {
//                                xpp.red = true;
//                                root = rotateRight(root, xpp);
//                            }
//                        }
//                    }
//                } else {
//                    if (xppl != null && xppl.red) {
//                        xppl.red = false;
//                        xp.red = false;
//                        xpp.red = true;
//                        x = xpp;
//                    } else {
//                        if (x == xp.left) {
//                            root = rotateRight(root, x = xp);
//                            xpp = (xp = x.parent) == null ? null : xp.parent;
//                        }
//                        if (xp != null) {
//                            xp.red = false;
//                            if (xpp != null) {
//                                xpp.red = true;
//                                root = rotateLeft(root, xpp);
//                            }
//                        }
//                    }
//                }
//            }
//        }
//
//        static <K, V> TreeNode<K, V> balanceDeletion(TreeNode<K, V> root,
//                                                     TreeNode<K, V> x) {
//            for (TreeNode<K, V> xp, xpl, xpr; ; ) {
//                if (x == null || x == root)
//                    return root;
//                else if ((xp = x.parent) == null) {
//                    x.red = false;
//                    return x;
//                } else if (x.red) {
//                    x.red = false;
//                    return root;
//                } else if ((xpl = xp.left) == x) {
//                    if ((xpr = xp.right) != null && xpr.red) {
//                        xpr.red = false;
//                        xp.red = true;
//                        root = rotateLeft(root, xp);
//                        xpr = (xp = x.parent) == null ? null : xp.right;
//                    }
//                    if (xpr == null)
//                        x = xp;
//                    else {
//                        TreeNode<K, V> sl = xpr.left, sr = xpr.right;
//                        if ((sr == null || !sr.red) &&
//                                (sl == null || !sl.red)) {
//                            xpr.red = true;
//                            x = xp;
//                        } else {
//                            if (sr == null || !sr.red) {
//                                if (sl != null)
//                                    sl.red = false;
//                                xpr.red = true;
//                                root = rotateRight(root, xpr);
//                                xpr = (xp = x.parent) == null ?
//                                        null : xp.right;
//                            }
//                            if (xpr != null) {
//                                xpr.red = (xp == null) ? false : xp.red;
//                                if ((sr = xpr.right) != null)
//                                    sr.red = false;
//                            }
//                            if (xp != null) {
//                                xp.red = false;
//                                root = rotateLeft(root, xp);
//                            }
//                            x = root;
//                        }
//                    }
//                } else { // symmetric
//                    if (xpl != null && xpl.red) {
//                        xpl.red = false;
//                        xp.red = true;
//                        root = rotateRight(root, xp);
//                        xpl = (xp = x.parent) == null ? null : xp.left;
//                    }
//                    if (xpl == null)
//                        x = xp;
//                    else {
//                        TreeNode<K, V> sl = xpl.left, sr = xpl.right;
//                        if ((sl == null || !sl.red) &&
//                                (sr == null || !sr.red)) {
//                            xpl.red = true;
//                            x = xp;
//                        } else {
//                            if (sl == null || !sl.red) {
//                                if (sr != null)
//                                    sr.red = false;
//                                xpl.red = true;
//                                root = rotateLeft(root, xpl);
//                                xpl = (xp = x.parent) == null ?
//                                        null : xp.left;
//                            }
//                            if (xpl != null) {
//                                xpl.red = (xp == null) ? false : xp.red;
//                                if ((sl = xpl.left) != null)
//                                    sl.red = false;
//                            }
//                            if (xp != null) {
//                                xp.red = false;
//                                root = rotateRight(root, xp);
//                            }
//                            x = root;
//                        }
//                    }
//                }
//            }
//        }
//
//        /**
//         * Recursive invariant check
//         */
//        static <K, V> boolean checkInvariants(TreeNode<K, V> t) {
//            TreeNode<K, V> tp = t.parent, tl = t.left, tr = t.right,
//                    tb = t.prev, tn = (TreeNode<K, V>) t.next;
//            if (tb != null && tb.next != t)
//                return false;
//            if (tn != null && tn.prev != t)
//                return false;
//            if (tp != null && t != tp.left && t != tp.right)
//                return false;
//            if (tl != null && (tl.parent != t || tl.hash > t.hash))
//                return false;
//            if (tr != null && (tr.parent != t || tr.hash < t.hash))
//                return false;
//            if (t.red && tl != null && tl.red && tr != null && tr.red)
//                return false;
//            if (tl != null && !checkInvariants(tl))
//                return false;
//            if (tr != null && !checkInvariants(tr))
//                return false;
//            return true;
//        }
//
//        private static final sun.misc.Unsafe U;
//        private static final long LOCKSTATE;
//
//        static {
//            try {
//                U = sun.misc.Unsafe.getUnsafe();
//                Class<?> k = TreeBin.class;
//                LOCKSTATE = U.objectFieldOffset
//                        (k.getDeclaredField("lockState"));
//            } catch (Exception e) {
//                throw new Error(e);
//            }
//        }
//    }
//
//    /* ----------------Table Traversal -------------- */
//
//    /**
//     * Records the table, its length, and current traversal index for a
//     * traverser that must process a region of a forwarded table before
//     * proceeding with current table.
//     */
//    static final class TableStack<K, V> {
//        int length;
//        int index;
//        Node<K, V>[] tab;
//        TableStack<K, V> next;
//    }
//
//    /**
//     * Encapsulates traversal for methods such as containsValue; also
//     * serves as a base class for other iterators and spliterators.
//     * <p>
//     * Method advance visits once each still-valid node that was
//     * reachable upon iterator construction. It might miss some that
//     * were added to a bin after the bin was visited, which is OK wrt
//     * consistency guarantees. Maintaining this property in the face
//     * of possible ongoing resizes requires a fair amount of
//     * bookkeeping state that is difficult to optimize away amidst
//     * volatile accesses.  Even so, traversal maintains reasonable
//     * throughput.
//     * <p>
//     * Normally, iteration proceeds bin-by-bin traversing lists.
//     * However, if the table has been resized, then all future steps
//     * must traverse both the bin at the current index as well as at
//     * (index + baseSize); and so on for further resizings. To
//     * paranoically cope with potential sharing by users of iterators
//     * across threads, iteration terminates if a bounds checks fails
//     * for a table read.
//     */
//    static class Traverser<K, V> {
//        Node<K, V>[] tab;        // current table; updated if resized
//        Node<K, V> next;         // the next entry to use
//        TableStack<K, V> stack, spare; // to save/restore on ForwardingNodes
//        int index;              // index of bin to use next
//        int baseIndex;          // current index of initial table
//        int baseLimit;          // index bound for initial table
//        final int baseSize;     // initial table size
//
//        Traverser(Node<K, V>[] tab, int size, int index, int limit) {
//            this.tab = tab;
//            this.baseSize = size;
//            this.baseIndex = this.index = index;
//            this.baseLimit = limit;
//            this.next = null;
//        }
//
//        /**
//         * Advances if possible, returning next valid node, or null if none.
//         */
//        final Node<K, V> advance() {
//            Node<K, V> e;
//            if ((e = next) != null)
//                e = e.next;
//            for (; ; ) {
//                Node<K, V>[] t;
//                int i, n;  // must use locals in checks
//                if (e != null)
//                    return next = e;
//                if (baseIndex >= baseLimit || (t = tab) == null ||
//                        (n = t.length) <= (i = index) || i < 0)
//                    return next = null;
//                if ((e = tabAt(t, i)) != null && e.hash < 0) {
//                    if (e instanceof ForwardingNode) {
//                        tab = ((ForwardingNode<K, V>) e).nextTable;
//                        e = null;
//                        pushState(t, i, n);
//                        continue;
//                    } else if (e instanceof TreeBin)
//                        e = ((TreeBin<K, V>) e).first;
//                    else
//                        e = null;
//                }
//                if (stack != null)
//                    recoverState(n);
//                else if ((index = i + baseSize) >= n)
//                    index = ++baseIndex; // visit upper slots if present
//            }
//        }
//
//        /**
//         * Saves traversal state upon encountering a forwarding node.
//         */
//        private void pushState(Node<K, V>[] t, int i, int n) {
//            TableStack<K, V> s = spare;  // reuse if possible
//            if (s != null)
//                spare = s.next;
//            else
//                s = new TableStack<K, V>();
//            s.tab = t;
//            s.length = n;
//            s.index = i;
//            s.next = stack;
//            stack = s;
//        }
//
//        /**
//         * Possibly pops traversal state.
//         *
//         * @param n length of current table
//         */
//        private void recoverState(int n) {
//            TableStack<K, V> s;
//            int len;
//            while ((s = stack) != null && (index += (len = s.length)) >= n) {
//                n = len;
//                index = s.index;
//                tab = s.tab;
//                s.tab = null;
//                TableStack<K, V> next = s.next;
//                s.next = spare; // save for reuse
//                stack = next;
//                spare = s;
//            }
//            if (s == null && (index += baseSize) >= n)
//                index = ++baseIndex;
//        }
//    }
//
//    /**
//     * Base of key, value, and entry Iterators. Adds fields to
//     * Traverser to support iterator.remove.
//     */
//    static class BaseIterator<K, V> extends Traverser<K, V> {
//        final ConcurrentHashMap<K, V> map;
//        Node<K, V> lastReturned;
//
//        BaseIterator(Node<K, V>[] tab, int size, int index, int limit,
//                     ConcurrentHashMap<K, V> map) {
//            super(tab, size, index, limit);
//            this.map = map;
//            advance();
//        }
//
//        public final boolean hasNext() {
//            return next != null;
//        }
//
//        public final boolean hasMoreElements() {
//            return next != null;
//        }
//
//        public final void remove() {
//            Node<K, V> p;
//            if ((p = lastReturned) == null)
//                throw new IllegalStateException();
//            lastReturned = null;
//            map.replaceNode(p.key, null, null);
//        }
//    }
//
//    static final class KeyIterator<K, V> extends BaseIterator<K, V>
//            implements Iterator<K>, Enumeration<K> {
//        KeyIterator(Node<K, V>[] tab, int index, int size, int limit,
//                    ConcurrentHashMap<K, V> map) {
//            super(tab, index, size, limit, map);
//        }
//
//        public final K next() {
//            Node<K, V> p;
//            if ((p = next) == null)
//                throw new NoSuchElementException();
//            K k = p.key;
//            lastReturned = p;
//            advance();
//            return k;
//        }
//
//        public final K nextElement() {
//            return next();
//        }
//    }
//
//    static final class ValueIterator<K, V> extends BaseIterator<K, V>
//            implements Iterator<V>, Enumeration<V> {
//        ValueIterator(Node<K, V>[] tab, int index, int size, int limit,
//                      ConcurrentHashMap<K, V> map) {
//            super(tab, index, size, limit, map);
//        }
//
//        public final V next() {
//            Node<K, V> p;
//            if ((p = next) == null)
//                throw new NoSuchElementException();
//            V v = p.val;
//            lastReturned = p;
//            advance();
//            return v;
//        }
//
//        public final V nextElement() {
//            return next();
//        }
//    }
//
//    static final class EntryIterator<K, V> extends BaseIterator<K, V>
//            implements Iterator<Map.Entry<K, V>> {
//        EntryIterator(Node<K, V>[] tab, int index, int size, int limit,
//                      ConcurrentHashMap<K, V> map) {
//            super(tab, index, size, limit, map);
//        }
//
//        public final Map.Entry<K, V> next() {
//            Node<K, V> p;
//            if ((p = next) == null)
//                throw new NoSuchElementException();
//            K k = p.key;
//            V v = p.val;
//            lastReturned = p;
//            advance();
//            return new MapEntry<K, V>(k, v, map);
//        }
//    }
//
//    /**
//     * Exported Entry for EntryIterator
//     */
//    static final class MapEntry<K, V> implements Map.Entry<K, V> {
//        final K key; // non-null
//        V val;       // non-null
//        final ConcurrentHashMap<K, V> map;
//
//        MapEntry(K key, V val, ConcurrentHashMap<K, V> map) {
//            this.key = key;
//            this.val = val;
//            this.map = map;
//        }
//
//        public K getKey() {
//            return key;
//        }
//
//        public V getValue() {
//            return val;
//        }
//
//        public int hashCode() {
//            return key.hashCode() ^ val.hashCode();
//        }
//
//        public String toString() {
//            return key + "=" + val;
//        }
//
//        public boolean equals(Object o) {
//            Object k, v;
//            Map.Entry<?, ?> e;
//            return ((o instanceof Map.Entry) &&
//                    (k = (e = (Map.Entry<?, ?>) o).getKey()) != null &&
//                    (v = e.getValue()) != null &&
//                    (k == key || k.equals(key)) &&
//                    (v == val || v.equals(val)));
//        }
//
//        /**
//         * Sets our entry's value and writes through to the map. The
//         * value to return is somewhat arbitrary here. Since we do not
//         * necessarily track asynchronous changes, the most recent
//         * "previous" value could be different from what we return (or
//         * could even have been removed, in which case the put will
//         * re-establish). We do not and cannot guarantee more.
//         */
//        public V setValue(V value) {
//            if (value == null) throw new NullPointerException();
//            V v = val;
//            val = value;
//            map.put(key, value);
//            return v;
//        }
//    }
//
//    static final class KeySpliterator<K, V> extends Traverser<K, V>
//            implements Spliterator<K> {
//        long est;               // size estimate
//
//        KeySpliterator(Node<K, V>[] tab, int size, int index, int limit,
//                       long est) {
//            super(tab, size, index, limit);
//            this.est = est;
//        }
//
//        public Spliterator<K> trySplit() {
//            int i, f, h;
//            return (h = ((i = baseIndex) + (f = baseLimit)) >>> 1) <= i ? null :
//                    new KeySpliterator<K, V>(tab, baseSize, baseLimit = h,
//                            f, est >>>= 1);
//        }
//
//        public void forEachRemaining(Consumer<? super K> action) {
//            if (action == null) throw new NullPointerException();
//            for (Node<K, V> p; (p = advance()) != null; )
//                action.accept(p.key);
//        }
//
//        public boolean tryAdvance(Consumer<? super K> action) {
//            if (action == null) throw new NullPointerException();
//            Node<K, V> p;
//            if ((p = advance()) == null)
//                return false;
//            action.accept(p.key);
//            return true;
//        }
//
//        public long estimateSize() {
//            return est;
//        }
//
//        public int characteristics() {
//            return Spliterator.DISTINCT | Spliterator.CONCURRENT |
//                    Spliterator.NONNULL;
//        }
//    }
//
//    static final class ValueSpliterator<K, V> extends Traverser<K, V>
//            implements Spliterator<V> {
//        long est;               // size estimate
//
//        ValueSpliterator(Node<K, V>[] tab, int size, int index, int limit,
//                         long est) {
//            super(tab, size, index, limit);
//            this.est = est;
//        }
//
//        public Spliterator<V> trySplit() {
//            int i, f, h;
//            return (h = ((i = baseIndex) + (f = baseLimit)) >>> 1) <= i ? null :
//                    new ValueSpliterator<K, V>(tab, baseSize, baseLimit = h,
//                            f, est >>>= 1);
//        }
//
//        public void forEachRemaining(Consumer<? super V> action) {
//            if (action == null) throw new NullPointerException();
//            for (Node<K, V> p; (p = advance()) != null; )
//                action.accept(p.val);
//        }
//
//        public boolean tryAdvance(Consumer<? super V> action) {
//            if (action == null) throw new NullPointerException();
//            Node<K, V> p;
//            if ((p = advance()) == null)
//                return false;
//            action.accept(p.val);
//            return true;
//        }
//
//        public long estimateSize() {
//            return est;
//        }
//
//        public int characteristics() {
//            return Spliterator.CONCURRENT | Spliterator.NONNULL;
//        }
//    }
//
//    static final class EntrySpliterator<K, V> extends Traverser<K, V>
//            implements Spliterator<Map.Entry<K, V>> {
//        final ConcurrentHashMap<K, V> map; // To export MapEntry
//        long est;               // size estimate
//
//        EntrySpliterator(Node<K, V>[] tab, int size, int index, int limit,
//                         long est, ConcurrentHashMap<K, V> map) {
//            super(tab, size, index, limit);
//            this.map = map;
//            this.est = est;
//        }
//
//        public Spliterator<Map.Entry<K, V>> trySplit() {
//            int i, f, h;
//            return (h = ((i = baseIndex) + (f = baseLimit)) >>> 1) <= i ? null :
//                    new EntrySpliterator<K, V>(tab, baseSize, baseLimit = h,
//                            f, est >>>= 1, map);
//        }
//
//        public void forEachRemaining(Consumer<? super Map.Entry<K, V>> action) {
//            if (action == null) throw new NullPointerException();
//            for (Node<K, V> p; (p = advance()) != null; )
//                action.accept(new MapEntry<K, V>(p.key, p.val, map));
//        }
//
//        public boolean tryAdvance(Consumer<? super Map.Entry<K, V>> action) {
//            if (action == null) throw new NullPointerException();
//            Node<K, V> p;
//            if ((p = advance()) == null)
//                return false;
//            action.accept(new MapEntry<K, V>(p.key, p.val, map));
//            return true;
//        }
//
//        public long estimateSize() {
//            return est;
//        }
//
//        public int characteristics() {
//            return Spliterator.DISTINCT | Spliterator.CONCURRENT |
//                    Spliterator.NONNULL;
//        }
//    }
//
//    // Parallel bulk operations
//
//    /**
//     * Computes initial batch value for bulk tasks. The returned value
//     * is approximately exp2 of the number of times (minus one) to
//     * split task by two before executing leaf action. This value is
//     * faster to compute and more convenient to use as a guide to
//     * splitting than is the depth, since it is used while dividing by
//     * two anyway.
//     */
//    final int batchFor(long b) {
//        long n;
//        if (b == Long.MAX_VALUE || (n = sumCount()) <= 1L || n < b)
//            return 0;
//        int sp = ForkJoinPool.getCommonPoolParallelism() << 2; // slack of 4
//        return (b <= 0L || (n /= b) >= sp) ? sp : (int) n;
//    }
//
//    /**
//     * Performs the given action for each (key, value).
//     *
//     * @param parallelismThreshold the (estimated) number of elements
//     *                             needed for this operation to be executed in parallel
//     * @param action               the action
//     * @since 1.8
//     */
//    public void forEach(long parallelismThreshold,
//                        BiConsumer<? super K, ? super V> action) {
//        if (action == null) throw new NullPointerException();
//        new ForEachMappingTask<K, V>
//                (null, batchFor(parallelismThreshold), 0, 0, table,
//                        action).invoke();
//    }
//
//    /**
//     * Performs the given action for each non-null transformation
//     * of each (key, value).
//     *
//     * @param parallelismThreshold the (estimated) number of elements
//     *                             needed for this operation to be executed in parallel
//     * @param transformer          a function returning the transformation
//     *                             for an element, or null if there is no transformation (in
//     *                             which case the action is not applied)
//     * @param action               the action
//     * @param <U>                  the return type of the transformer
//     * @since 1.8
//     */
//    public <U> void forEach(long parallelismThreshold,
//                            BiFunction<? super K, ? super V, ? extends U> transformer,
//                            Consumer<? super U> action) {
//        if (transformer == null || action == null)
//            throw new NullPointerException();
//        new ForEachTransformedMappingTask<K, V, U>
//                (null, batchFor(parallelismThreshold), 0, 0, table,
//                        transformer, action).invoke();
//    }
//
//    /**
//     * Returns a non-null result from applying the given search
//     * function on each (key, value), or null if none.  Upon
//     * success, further element processing is suppressed and the
//     * results of any other parallel invocations of the search
//     * function are ignored.
//     *
//     * @param parallelismThreshold the (estimated) number of elements
//     *                             needed for this operation to be executed in parallel
//     * @param searchFunction       a function returning a non-null
//     *                             result on success, else null
//     * @param <U>                  the return type of the search function
//     * @return a non-null result from applying the given search
//     * function on each (key, value), or null if none
//     * @since 1.8
//     */
//    public <U> U search(long parallelismThreshold,
//                        BiFunction<? super K, ? super V, ? extends U> searchFunction) {
//        if (searchFunction == null) throw new NullPointerException();
//        return new SearchMappingsTask<K, V, U>
//                (null, batchFor(parallelismThreshold), 0, 0, table,
//                        searchFunction, new AtomicReference<U>()).invoke();
//    }
//
//    /**
//     * Returns the result of accumulating the given transformation
//     * of all (key, value) pairs using the given reducer to
//     * combine values, or null if none.
//     *
//     * @param parallelismThreshold the (estimated) number of elements
//     *                             needed for this operation to be executed in parallel
//     * @param transformer          a function returning the transformation
//     *                             for an element, or null if there is no transformation (in
//     *                             which case it is not combined)
//     * @param reducer              a commutative associative combining function
//     * @param <U>                  the return type of the transformer
//     * @return the result of accumulating the given transformation
//     * of all (key, value) pairs
//     * @since 1.8
//     */
//    public <U> U reduce(long parallelismThreshold,
//                        BiFunction<? super K, ? super V, ? extends U> transformer,
//                        BiFunction<? super U, ? super U, ? extends U> reducer) {
//        if (transformer == null || reducer == null)
//            throw new NullPointerException();
//        return new MapReduceMappingsTask<K, V, U>
//                (null, batchFor(parallelismThreshold), 0, 0, table,
//                        null, transformer, reducer).invoke();
//    }
//
//    /**
//     * Returns the result of accumulating the given transformation
//     * of all (key, value) pairs using the given reducer to
//     * combine values, and the given basis as an identity value.
//     *
//     * @param parallelismThreshold the (estimated) number of elements
//     *                             needed for this operation to be executed in parallel
//     * @param transformer          a function returning the transformation
//     *                             for an element
//     * @param basis                the identity (initial default value) for the reduction
//     * @param reducer              a commutative associative combining function
//     * @return the result of accumulating the given transformation
//     * of all (key, value) pairs
//     * @since 1.8
//     */
//    public double reduceToDouble(long parallelismThreshold,
//                                 ToDoubleBiFunction<? super K, ? super V> transformer,
//                                 double basis,
//                                 DoubleBinaryOperator reducer) {
//        if (transformer == null || reducer == null)
//            throw new NullPointerException();
//        return new MapReduceMappingsToDoubleTask<K, V>
//                (null, batchFor(parallelismThreshold), 0, 0, table,
//                        null, transformer, basis, reducer).invoke();
//    }
//
//    /**
//     * Returns the result of accumulating the given transformation
//     * of all (key, value) pairs using the given reducer to
//     * combine values, and the given basis as an identity value.
//     *
//     * @param parallelismThreshold the (estimated) number of elements
//     *                             needed for this operation to be executed in parallel
//     * @param transformer          a function returning the transformation
//     *                             for an element
//     * @param basis                the identity (initial default value) for the reduction
//     * @param reducer              a commutative associative combining function
//     * @return the result of accumulating the given transformation
//     * of all (key, value) pairs
//     * @since 1.8
//     */
//    public long reduceToLong(long parallelismThreshold,
//                             ToLongBiFunction<? super K, ? super V> transformer,
//                             long basis,
//                             LongBinaryOperator reducer) {
//        if (transformer == null || reducer == null)
//            throw new NullPointerException();
//        return new MapReduceMappingsToLongTask<K, V>
//                (null, batchFor(parallelismThreshold), 0, 0, table,
//                        null, transformer, basis, reducer).invoke();
//    }
//
//    /**
//     * Returns the result of accumulating the given transformation
//     * of all (key, value) pairs using the given reducer to
//     * combine values, and the given basis as an identity value.
//     *
//     * @param parallelismThreshold the (estimated) number of elements
//     *                             needed for this operation to be executed in parallel
//     * @param transformer          a function returning the transformation
//     *                             for an element
//     * @param basis                the identity (initial default value) for the reduction
//     * @param reducer              a commutative associative combining function
//     * @return the result of accumulating the given transformation
//     * of all (key, value) pairs
//     * @since 1.8
//     */
//    public int reduceToInt(long parallelismThreshold,
//                           ToIntBiFunction<? super K, ? super V> transformer,
//                           int basis,
//                           IntBinaryOperator reducer) {
//        if (transformer == null || reducer == null)
//            throw new NullPointerException();
//        return new MapReduceMappingsToIntTask<K, V>
//                (null, batchFor(parallelismThreshold), 0, 0, table,
//                        null, transformer, basis, reducer).invoke();
//    }
//
//    /**
//     * Performs the given action for each key.
//     *
//     * @param parallelismThreshold the (estimated) number of elements
//     *                             needed for this operation to be executed in parallel
//     * @param action               the action
//     * @since 1.8
//     */
//    public void forEachKey(long parallelismThreshold,
//                           Consumer<? super K> action) {
//        if (action == null) throw new NullPointerException();
//        new ForEachKeyTask<K, V>
//                (null, batchFor(parallelismThreshold), 0, 0, table,
//                        action).invoke();
//    }
//
//    /**
//     * Performs the given action for each non-null transformation
//     * of each key.
//     *
//     * @param parallelismThreshold the (estimated) number of elements
//     *                             needed for this operation to be executed in parallel
//     * @param transformer          a function returning the transformation
//     *                             for an element, or null if there is no transformation (in
//     *                             which case the action is not applied)
//     * @param action               the action
//     * @param <U>                  the return type of the transformer
//     * @since 1.8
//     */
//    public <U> void forEachKey(long parallelismThreshold,
//                               Function<? super K, ? extends U> transformer,
//                               Consumer<? super U> action) {
//        if (transformer == null || action == null)
//            throw new NullPointerException();
//        new ForEachTransformedKeyTask<K, V, U>
//                (null, batchFor(parallelismThreshold), 0, 0, table,
//                        transformer, action).invoke();
//    }
//
//    /**
//     * Returns a non-null result from applying the given search
//     * function on each key, or null if none. Upon success,
//     * further element processing is suppressed and the results of
//     * any other parallel invocations of the search function are
//     * ignored.
//     *
//     * @param parallelismThreshold the (estimated) number of elements
//     *                             needed for this operation to be executed in parallel
//     * @param searchFunction       a function returning a non-null
//     *                             result on success, else null
//     * @param <U>                  the return type of the search function
//     * @return a non-null result from applying the given search
//     * function on each key, or null if none
//     * @since 1.8
//     */
//    public <U> U searchKeys(long parallelismThreshold,
//                            Function<? super K, ? extends U> searchFunction) {
//        if (searchFunction == null) throw new NullPointerException();
//        return new SearchKeysTask<K, V, U>
//                (null, batchFor(parallelismThreshold), 0, 0, table,
//                        searchFunction, new AtomicReference<U>()).invoke();
//    }
//
//    /**
//     * Returns the result of accumulating all keys using the given
//     * reducer to combine values, or null if none.
//     *
//     * @param parallelismThreshold the (estimated) number of elements
//     *                             needed for this operation to be executed in parallel
//     * @param reducer              a commutative associative combining function
//     * @return the result of accumulating all keys using the given
//     * reducer to combine values, or null if none
//     * @since 1.8
//     */
//    public K reduceKeys(long parallelismThreshold,
//                        BiFunction<? super K, ? super K, ? extends K> reducer) {
//        if (reducer == null) throw new NullPointerException();
//        return new ReduceKeysTask<K, V>
//                (null, batchFor(parallelismThreshold), 0, 0, table,
//                        null, reducer).invoke();
//    }
//
//    /**
//     * Returns the result of accumulating the given transformation
//     * of all keys using the given reducer to combine values, or
//     * null if none.
//     *
//     * @param parallelismThreshold the (estimated) number of elements
//     *                             needed for this operation to be executed in parallel
//     * @param transformer          a function returning the transformation
//     *                             for an element, or null if there is no transformation (in
//     *                             which case it is not combined)
//     * @param reducer              a commutative associative combining function
//     * @param <U>                  the return type of the transformer
//     * @return the result of accumulating the given transformation
//     * of all keys
//     * @since 1.8
//     */
//    public <U> U reduceKeys(long parallelismThreshold,
//                            Function<? super K, ? extends U> transformer,
//                            BiFunction<? super U, ? super U, ? extends U> reducer) {
//        if (transformer == null || reducer == null)
//            throw new NullPointerException();
//        return new MapReduceKeysTask<K, V, U>
//                (null, batchFor(parallelismThreshold), 0, 0, table,
//                        null, transformer, reducer).invoke();
//    }
//
//    /**
//     * Returns the result of accumulating the given transformation
//     * of all keys using the given reducer to combine values, and
//     * the given basis as an identity value.
//     *
//     * @param parallelismThreshold the (estimated) number of elements
//     *                             needed for this operation to be executed in parallel
//     * @param transformer          a function returning the transformation
//     *                             for an element
//     * @param basis                the identity (initial default value) for the reduction
//     * @param reducer              a commutative associative combining function
//     * @return the result of accumulating the given transformation
//     * of all keys
//     * @since 1.8
//     */
//    public double reduceKeysToDouble(long parallelismThreshold,
//                                     ToDoubleFunction<? super K> transformer,
//                                     double basis,
//                                     DoubleBinaryOperator reducer) {
//        if (transformer == null || reducer == null)
//            throw new NullPointerException();
//        return new MapReduceKeysToDoubleTask<K, V>
//                (null, batchFor(parallelismThreshold), 0, 0, table,
//                        null, transformer, basis, reducer).invoke();
//    }
//
//    /**
//     * Returns the result of accumulating the given transformation
//     * of all keys using the given reducer to combine values, and
//     * the given basis as an identity value.
//     *
//     * @param parallelismThreshold the (estimated) number of elements
//     *                             needed for this operation to be executed in parallel
//     * @param transformer          a function returning the transformation
//     *                             for an element
//     * @param basis                the identity (initial default value) for the reduction
//     * @param reducer              a commutative associative combining function
//     * @return the result of accumulating the given transformation
//     * of all keys
//     * @since 1.8
//     */
//    public long reduceKeysToLong(long parallelismThreshold,
//                                 ToLongFunction<? super K> transformer,
//                                 long basis,
//                                 LongBinaryOperator reducer) {
//        if (transformer == null || reducer == null)
//            throw new NullPointerException();
//        return new MapReduceKeysToLongTask<K, V>
//                (null, batchFor(parallelismThreshold), 0, 0, table,
//                        null, transformer, basis, reducer).invoke();
//    }
//
//    /**
//     * Returns the result of accumulating the given transformation
//     * of all keys using the given reducer to combine values, and
//     * the given basis as an identity value.
//     *
//     * @param parallelismThreshold the (estimated) number of elements
//     *                             needed for this operation to be executed in parallel
//     * @param transformer          a function returning the transformation
//     *                             for an element
//     * @param basis                the identity (initial default value) for the reduction
//     * @param reducer              a commutative associative combining function
//     * @return the result of accumulating the given transformation
//     * of all keys
//     * @since 1.8
//     */
//    public int reduceKeysToInt(long parallelismThreshold,
//                               ToIntFunction<? super K> transformer,
//                               int basis,
//                               IntBinaryOperator reducer) {
//        if (transformer == null || reducer == null)
//            throw new NullPointerException();
//        return new MapReduceKeysToIntTask<K, V>
//                (null, batchFor(parallelismThreshold), 0, 0, table,
//                        null, transformer, basis, reducer).invoke();
//    }
//
//    /**
//     * Performs the given action for each value.
//     *
//     * @param parallelismThreshold the (estimated) number of elements
//     *                             needed for this operation to be executed in parallel
//     * @param action               the action
//     * @since 1.8
//     */
//    public void forEachValue(long parallelismThreshold,
//                             Consumer<? super V> action) {
//        if (action == null)
//            throw new NullPointerException();
//        new ForEachValueTask<K, V>
//                (null, batchFor(parallelismThreshold), 0, 0, table,
//                        action).invoke();
//    }
//
//    /**
//     * Performs the given action for each non-null transformation
//     * of each value.
//     *
//     * @param parallelismThreshold the (estimated) number of elements
//     *                             needed for this operation to be executed in parallel
//     * @param transformer          a function returning the transformation
//     *                             for an element, or null if there is no transformation (in
//     *                             which case the action is not applied)
//     * @param action               the action
//     * @param <U>                  the return type of the transformer
//     * @since 1.8
//     */
//    public <U> void forEachValue(long parallelismThreshold,
//                                 Function<? super V, ? extends U> transformer,
//                                 Consumer<? super U> action) {
//        if (transformer == null || action == null)
//            throw new NullPointerException();
//        new ForEachTransformedValueTask<K, V, U>
//                (null, batchFor(parallelismThreshold), 0, 0, table,
//                        transformer, action).invoke();
//    }
//
//    /**
//     * Returns a non-null result from applying the given search
//     * function on each value, or null if none.  Upon success,
//     * further element processing is suppressed and the results of
//     * any other parallel invocations of the search function are
//     * ignored.
//     *
//     * @param parallelismThreshold the (estimated) number of elements
//     *                             needed for this operation to be executed in parallel
//     * @param searchFunction       a function returning a non-null
//     *                             result on success, else null
//     * @param <U>                  the return type of the search function
//     * @return a non-null result from applying the given search
//     * function on each value, or null if none
//     * @since 1.8
//     */
//    public <U> U searchValues(long parallelismThreshold,
//                              Function<? super V, ? extends U> searchFunction) {
//        if (searchFunction == null) throw new NullPointerException();
//        return new SearchValuesTask<K, V, U>
//                (null, batchFor(parallelismThreshold), 0, 0, table,
//                        searchFunction, new AtomicReference<U>()).invoke();
//    }
//
//    /**
//     * Returns the result of accumulating all values using the
//     * given reducer to combine values, or null if none.
//     *
//     * @param parallelismThreshold the (estimated) number of elements
//     *                             needed for this operation to be executed in parallel
//     * @param reducer              a commutative associative combining function
//     * @return the result of accumulating all values
//     * @since 1.8
//     */
//    public V reduceValues(long parallelismThreshold,
//                          BiFunction<? super V, ? super V, ? extends V> reducer) {
//        if (reducer == null) throw new NullPointerException();
//        return new ReduceValuesTask<K, V>
//                (null, batchFor(parallelismThreshold), 0, 0, table,
//                        null, reducer).invoke();
//    }
//
//    /**
//     * Returns the result of accumulating the given transformation
//     * of all values using the given reducer to combine values, or
//     * null if none.
//     *
//     * @param parallelismThreshold the (estimated) number of elements
//     *                             needed for this operation to be executed in parallel
//     * @param transformer          a function returning the transformation
//     *                             for an element, or null if there is no transformation (in
//     *                             which case it is not combined)
//     * @param reducer              a commutative associative combining function
//     * @param <U>                  the return type of the transformer
//     * @return the result of accumulating the given transformation
//     * of all values
//     * @since 1.8
//     */
//    public <U> U reduceValues(long parallelismThreshold,
//                              Function<? super V, ? extends U> transformer,
//                              BiFunction<? super U, ? super U, ? extends U> reducer) {
//        if (transformer == null || reducer == null)
//            throw new NullPointerException();
//        return new MapReduceValuesTask<K, V, U>
//                (null, batchFor(parallelismThreshold), 0, 0, table,
//                        null, transformer, reducer).invoke();
//    }
//
//    /**
//     * Returns the result of accumulating the given transformation
//     * of all values using the given reducer to combine values,
//     * and the given basis as an identity value.
//     *
//     * @param parallelismThreshold the (estimated) number of elements
//     *                             needed for this operation to be executed in parallel
//     * @param transformer          a function returning the transformation
//     *                             for an element
//     * @param basis                the identity (initial default value) for the reduction
//     * @param reducer              a commutative associative combining function
//     * @return the result of accumulating the given transformation
//     * of all values
//     * @since 1.8
//     */
//    public double reduceValuesToDouble(long parallelismThreshold,
//                                       ToDoubleFunction<? super V> transformer,
//                                       double basis,
//                                       DoubleBinaryOperator reducer) {
//        if (transformer == null || reducer == null)
//            throw new NullPointerException();
//        return new MapReduceValuesToDoubleTask<K, V>
//                (null, batchFor(parallelismThreshold), 0, 0, table,
//                        null, transformer, basis, reducer).invoke();
//    }
//
//    /**
//     * Returns the result of accumulating the given transformation
//     * of all values using the given reducer to combine values,
//     * and the given basis as an identity value.
//     *
//     * @param parallelismThreshold the (estimated) number of elements
//     *                             needed for this operation to be executed in parallel
//     * @param transformer          a function returning the transformation
//     *                             for an element
//     * @param basis                the identity (initial default value) for the reduction
//     * @param reducer              a commutative associative combining function
//     * @return the result of accumulating the given transformation
//     * of all values
//     * @since 1.8
//     */
//    public long reduceValuesToLong(long parallelismThreshold,
//                                   ToLongFunction<? super V> transformer,
//                                   long basis,
//                                   LongBinaryOperator reducer) {
//        if (transformer == null || reducer == null)
//            throw new NullPointerException();
//        return new MapReduceValuesToLongTask<K, V>
//                (null, batchFor(parallelismThreshold), 0, 0, table,
//                        null, transformer, basis, reducer).invoke();
//    }
//
//    /**
//     * Returns the result of accumulating the given transformation
//     * of all values using the given reducer to combine values,
//     * and the given basis as an identity value.
//     *
//     * @param parallelismThreshold the (estimated) number of elements
//     *                             needed for this operation to be executed in parallel
//     * @param transformer          a function returning the transformation
//     *                             for an element
//     * @param basis                the identity (initial default value) for the reduction
//     * @param reducer              a commutative associative combining function
//     * @return the result of accumulating the given transformation
//     * of all values
//     * @since 1.8
//     */
//    public int reduceValuesToInt(long parallelismThreshold,
//                                 ToIntFunction<? super V> transformer,
//                                 int basis,
//                                 IntBinaryOperator reducer) {
//        if (transformer == null || reducer == null)
//            throw new NullPointerException();
//        return new MapReduceValuesToIntTask<K, V>
//                (null, batchFor(parallelismThreshold), 0, 0, table,
//                        null, transformer, basis, reducer).invoke();
//    }
//
//    /**
//     * Performs the given action for each entry.
//     *
//     * @param parallelismThreshold the (estimated) number of elements
//     *                             needed for this operation to be executed in parallel
//     * @param action               the action
//     * @since 1.8
//     */
//    public void forEachEntry(long parallelismThreshold,
//                             Consumer<? super Map.Entry<K, V>> action) {
//        if (action == null) throw new NullPointerException();
//        new ForEachEntryTask<K, V>(null, batchFor(parallelismThreshold), 0, 0, table,
//                action).invoke();
//    }
//
//    /**
//     * Performs the given action for each non-null transformation
//     * of each entry.
//     *
//     * @param parallelismThreshold the (estimated) number of elements
//     *                             needed for this operation to be executed in parallel
//     * @param transformer          a function returning the transformation
//     *                             for an element, or null if there is no transformation (in
//     *                             which case the action is not applied)
//     * @param action               the action
//     * @param <U>                  the return type of the transformer
//     * @since 1.8
//     */
//    public <U> void forEachEntry(long parallelismThreshold,
//                                 Function<Map.Entry<K, V>, ? extends U> transformer,
//                                 Consumer<? super U> action) {
//        if (transformer == null || action == null)
//            throw new NullPointerException();
//        new ForEachTransformedEntryTask<K, V, U>
//                (null, batchFor(parallelismThreshold), 0, 0, table,
//                        transformer, action).invoke();
//    }
//
//    /**
//     * Returns a non-null result from applying the given search
//     * function on each entry, or null if none.  Upon success,
//     * further element processing is suppressed and the results of
//     * any other parallel invocations of the search function are
//     * ignored.
//     *
//     * @param parallelismThreshold the (estimated) number of elements
//     *                             needed for this operation to be executed in parallel
//     * @param searchFunction       a function returning a non-null
//     *                             result on success, else null
//     * @param <U>                  the return type of the search function
//     * @return a non-null result from applying the given search
//     * function on each entry, or null if none
//     * @since 1.8
//     */
//    public <U> U searchEntries(long parallelismThreshold,
//                               Function<Map.Entry<K, V>, ? extends U> searchFunction) {
//        if (searchFunction == null) throw new NullPointerException();
//        return new SearchEntriesTask<K, V, U>
//                (null, batchFor(parallelismThreshold), 0, 0, table,
//                        searchFunction, new AtomicReference<U>()).invoke();
//    }
//
//    /**
//     * Returns the result of accumulating all entries using the
//     * given reducer to combine values, or null if none.
//     *
//     * @param parallelismThreshold the (estimated) number of elements
//     *                             needed for this operation to be executed in parallel
//     * @param reducer              a commutative associative combining function
//     * @return the result of accumulating all entries
//     * @since 1.8
//     */
//    public Map.Entry<K, V> reduceEntries(long parallelismThreshold,
//                                         BiFunction<Map.Entry<K, V>, Map.Entry<K, V>, ? extends Map.Entry<K, V>> reducer) {
//        if (reducer == null) throw new NullPointerException();
//        return new ReduceEntriesTask<K, V>
//                (null, batchFor(parallelismThreshold), 0, 0, table,
//                        null, reducer).invoke();
//    }
//
//    /**
//     * Returns the result of accumulating the given transformation
//     * of all entries using the given reducer to combine values,
//     * or null if none.
//     *
//     * @param parallelismThreshold the (estimated) number of elements
//     *                             needed for this operation to be executed in parallel
//     * @param transformer          a function returning the transformation
//     *                             for an element, or null if there is no transformation (in
//     *                             which case it is not combined)
//     * @param reducer              a commutative associative combining function
//     * @param <U>                  the return type of the transformer
//     * @return the result of accumulating the given transformation
//     * of all entries
//     * @since 1.8
//     */
//    public <U> U reduceEntries(long parallelismThreshold,
//                               Function<Map.Entry<K, V>, ? extends U> transformer,
//                               BiFunction<? super U, ? super U, ? extends U> reducer) {
//        if (transformer == null || reducer == null)
//            throw new NullPointerException();
//        return new MapReduceEntriesTask<K, V, U>
//                (null, batchFor(parallelismThreshold), 0, 0, table,
//                        null, transformer, reducer).invoke();
//    }
//
//    /**
//     * Returns the result of accumulating the given transformation
//     * of all entries using the given reducer to combine values,
//     * and the given basis as an identity value.
//     *
//     * @param parallelismThreshold the (estimated) number of elements
//     *                             needed for this operation to be executed in parallel
//     * @param transformer          a function returning the transformation
//     *                             for an element
//     * @param basis                the identity (initial default value) for the reduction
//     * @param reducer              a commutative associative combining function
//     * @return the result of accumulating the given transformation
//     * of all entries
//     * @since 1.8
//     */
//    public double reduceEntriesToDouble(long parallelismThreshold,
//                                        ToDoubleFunction<Map.Entry<K, V>> transformer,
//                                        double basis,
//                                        DoubleBinaryOperator reducer) {
//        if (transformer == null || reducer == null)
//            throw new NullPointerException();
//        return new MapReduceEntriesToDoubleTask<K, V>
//                (null, batchFor(parallelismThreshold), 0, 0, table,
//                        null, transformer, basis, reducer).invoke();
//    }
//
//    /**
//     * Returns the result of accumulating the given transformation
//     * of all entries using the given reducer to combine values,
//     * and the given basis as an identity value.
//     *
//     * @param parallelismThreshold the (estimated) number of elements
//     *                             needed for this operation to be executed in parallel
//     * @param transformer          a function returning the transformation
//     *                             for an element
//     * @param basis                the identity (initial default value) for the reduction
//     * @param reducer              a commutative associative combining function
//     * @return the result of accumulating the given transformation
//     * of all entries
//     * @since 1.8
//     */
//    public long reduceEntriesToLong(long parallelismThreshold,
//                                    ToLongFunction<Map.Entry<K, V>> transformer,
//                                    long basis,
//                                    LongBinaryOperator reducer) {
//        if (transformer == null || reducer == null)
//            throw new NullPointerException();
//        return new MapReduceEntriesToLongTask<K, V>
//                (null, batchFor(parallelismThreshold), 0, 0, table,
//                        null, transformer, basis, reducer).invoke();
//    }
//
//    /**
//     * Returns the result of accumulating the given transformation
//     * of all entries using the given reducer to combine values,
//     * and the given basis as an identity value.
//     *
//     * @param parallelismThreshold the (estimated) number of elements
//     *                             needed for this operation to be executed in parallel
//     * @param transformer          a function returning the transformation
//     *                             for an element
//     * @param basis                the identity (initial default value) for the reduction
//     * @param reducer              a commutative associative combining function
//     * @return the result of accumulating the given transformation
//     * of all entries
//     * @since 1.8
//     */
//    public int reduceEntriesToInt(long parallelismThreshold,
//                                  ToIntFunction<Map.Entry<K, V>> transformer,
//                                  int basis,
//                                  IntBinaryOperator reducer) {
//        if (transformer == null || reducer == null)
//            throw new NullPointerException();
//        return new MapReduceEntriesToIntTask<K, V>
//                (null, batchFor(parallelismThreshold), 0, 0, table,
//                        null, transformer, basis, reducer).invoke();
//    }
//
//
//    /* ----------------Views -------------- */
//
//    /**
//     * Base class for views.
//     */
//    abstract static class CollectionView<K, V, E>
//            implements Collection<E>, java.io.Serializable {
//        private static final long serialVersionUID = 7249069246763182397L;
//        final ConcurrentHashMap<K, V> map;
//
//        CollectionView(ConcurrentHashMap<K, V> map) {
//            this.map = map;
//        }
//
//        /**
//         * Returns the map backing this view.
//         *
//         * @return the map backing this view
//         */
//        public ConcurrentHashMap<K, V> getMap() {
//            return map;
//        }
//
//        /**
//         * Removes all of the elements from this view, by removing all
//         * the mappings from the map backing this view.
//         */
//        public final void clear() {
//            map.clear();
//        }
//
//        public final int size() {
//            return map.size();
//        }
//
//        public final boolean isEmpty() {
//            return map.isEmpty();
//        }
//
//        // implementations below rely on concrete classes supplying these
//        // abstract methods
//
//        /**
//         * Returns an iterator over the elements in this collection.
//         *
//         * <p>The returned iterator is
//         * <a href="package-summary.html#Weakly"><i>weakly consistent</i></a>.
//         *
//         * @return an iterator over the elements in this collection
//         */
//        public abstract Iterator<E> iterator();
//
//        public abstract boolean contains(Object o);
//
//        public abstract boolean remove(Object o);
//
//        private static final String oomeMsg = "Required array size too large";
//
//        public final Object[] toArray() {
//            long sz = map.mappingCount();
//            if (sz > MAX_ARRAY_SIZE)
//                throw new OutOfMemoryError(oomeMsg);
//            int n = (int) sz;
//            Object[] r = new Object[n];
//            int i = 0;
//            for (E e : this) {
//                if (i == n) {
//                    if (n >= MAX_ARRAY_SIZE)
//                        throw new OutOfMemoryError(oomeMsg);
//                    if (n >= MAX_ARRAY_SIZE - (MAX_ARRAY_SIZE >>> 1) - 1)
//                        n = MAX_ARRAY_SIZE;
//                    else
//                        n += (n >>> 1) + 1;
//                    r = Arrays.copyOf(r, n);
//                }
//                r[i++] = e;
//            }
//            return (i == n) ? r : Arrays.copyOf(r, i);
//        }
//
//        @SuppressWarnings("unchecked")
//        public final <T> T[] toArray(T[] a) {
//            long sz = map.mappingCount();
//            if (sz > MAX_ARRAY_SIZE)
//                throw new OutOfMemoryError(oomeMsg);
//            int m = (int) sz;
//            T[] r = (a.length >= m) ? a :
//                    (T[]) java.lang.reflect.Array
//                            .newInstance(a.getClass().getComponentType(), m);
//            int n = r.length;
//            int i = 0;
//            for (E e : this) {
//                if (i == n) {
//                    if (n >= MAX_ARRAY_SIZE)
//                        throw new OutOfMemoryError(oomeMsg);
//                    if (n >= MAX_ARRAY_SIZE - (MAX_ARRAY_SIZE >>> 1) - 1)
//                        n = MAX_ARRAY_SIZE;
//                    else
//                        n += (n >>> 1) + 1;
//                    r = Arrays.copyOf(r, n);
//                }
//                r[i++] = (T) e;
//            }
//            if (a == r && i < n) {
//                r[i] = null; // null-terminate
//                return r;
//            }
//            return (i == n) ? r : Arrays.copyOf(r, i);
//        }
//
//        /**
//         * Returns a string representation of this collection.
//         * The string representation consists of the string representations
//         * of the collection's elements in the order they are returned by
//         * its iterator, enclosed in square brackets ({@code "[]"}).
//         * Adjacent elements are separated by the characters {@code ", "}
//         * (comma and space).  Elements are converted to strings as by
//         * {@link String#valueOf(Object)}.
//         *
//         * @return a string representation of this collection
//         */
//        public final String toString() {
//            StringBuilder sb = new StringBuilder();
//            sb.append('[');
//            Iterator<E> it = iterator();
//            if (it.hasNext()) {
//                for (; ; ) {
//                    Object e = it.next();
//                    sb.append(e == this ? "(this Collection)" : e);
//                    if (!it.hasNext())
//                        break;
//                    sb.append(',').append(' ');
//                }
//            }
//            return sb.append(']').toString();
//        }
//
//        public final boolean containsAll(Collection<?> c) {
//            if (c != this) {
//                for (Object e : c) {
//                    if (e == null || !contains(e))
//                        return false;
//                }
//            }
//            return true;
//        }
//
//        public final boolean removeAll(Collection<?> c) {
//            if (c == null) throw new NullPointerException();
//            boolean modified = false;
//            for (Iterator<E> it = iterator(); it.hasNext(); ) {
//                if (c.contains(it.next())) {
//                    it.remove();
//                    modified = true;
//                }
//            }
//            return modified;
//        }
//
//        public final boolean retainAll(Collection<?> c) {
//            if (c == null) throw new NullPointerException();
//            boolean modified = false;
//            for (Iterator<E> it = iterator(); it.hasNext(); ) {
//                if (!c.contains(it.next())) {
//                    it.remove();
//                    modified = true;
//                }
//            }
//            return modified;
//        }
//
//    }
//
//    /**
//     * A view of a ConcurrentHashMap as a {@link Set} of keys, in
//     * which additions may optionally be enabled by mapping to a
//     * common value.  This class cannot be directly instantiated.
//     * See {@link #keySet() keySet()},
//     * {@link #keySet(Object) keySet(V)},
//     * {@link #newKeySet() newKeySet()},
//     * {@link #newKeySet(int) newKeySet(int)}.
//     *
//     * @since 1.8
//     */
//    public static class KeySetView<K, V> extends CollectionView<K, V, K>
//            implements Set<K>, java.io.Serializable {
//        private static final long serialVersionUID = 7249069246763182397L;
//        private final V value;
//
//        KeySetView(ConcurrentHashMap<K, V> map, V value) {  // non-public
//            super(map);
//            this.value = value;
//        }
//
//        /**
//         * Returns the default mapped value for additions,
//         * or {@code null} if additions are not supported.
//         *
//         * @return the default mapped value for additions, or {@code null}
//         * if not supported
//         */
//        public V getMappedValue() {
//            return value;
//        }
//
//        /**
//         * {@inheritDoc}
//         *
//         * @throws NullPointerException if the specified key is null
//         */
//        public boolean contains(Object o) {
//            return map.containsKey(o);
//        }
//
//        /**
//         * Removes the key from this map view, by removing the key (and its
//         * corresponding value) from the backing map.  This method does
//         * nothing if the key is not in the map.
//         *
//         * @param o the key to be removed from the backing map
//         * @return {@code true} if the backing map contained the specified key
//         * @throws NullPointerException if the specified key is null
//         */
//        public boolean remove(Object o) {
//            return map.remove(o) != null;
//        }
//
//        /**
//         * @return an iterator over the keys of the backing map
//         */
//        public Iterator<K> iterator() {
//            Node<K, V>[] t;
//            ConcurrentHashMap<K, V> m = map;
//            int f = (t = m.table) == null ? 0 : t.length;
//            return new KeyIterator<K, V>(t, f, 0, f, m);
//        }
//
//        /**
//         * Adds the specified key to this set view by mapping the key to
//         * the default mapped value in the backing map, if defined.
//         *
//         * @param e key to be added
//         * @return {@code true} if this set changed as a result of the call
//         * @throws NullPointerException          if the specified key is null
//         * @throws UnsupportedOperationException if no default mapped value
//         *                                       for additions was provided
//         */
//        public boolean add(K e) {
//            V v;
//            if ((v = value) == null)
//                throw new UnsupportedOperationException();
//            return map.putVal(e, v, true) == null;
//        }
//
//        /**
//         * Adds all of the elements in the specified collection to this set,
//         * as if by calling {@link #add} on each one.
//         *
//         * @param c the elements to be inserted into this set
//         * @return {@code true} if this set changed as a result of the call
//         * @throws NullPointerException          if the collection or any of its
//         *                                       elements are {@code null}
//         * @throws UnsupportedOperationException if no default mapped value
//         *                                       for additions was provided
//         */
//        public boolean addAll(Collection<? extends K> c) {
//            boolean added = false;
//            V v;
//            if ((v = value) == null)
//                throw new UnsupportedOperationException();
//            for (K e : c) {
//                if (map.putVal(e, v, true) == null)
//                    added = true;
//            }
//            return added;
//        }
//
//        public int hashCode() {
//            int h = 0;
//            for (K e : this)
//                h += e.hashCode();
//            return h;
//        }
//
//        public boolean equals(Object o) {
//            Set<?> c;
//            return ((o instanceof Set) &&
//                    ((c = (Set<?>) o) == this ||
//                            (containsAll(c) && c.containsAll(this))));
//        }
//
//        public Spliterator<K> spliterator() {
//            Node<K, V>[] t;
//            ConcurrentHashMap<K, V> m = map;
//            long n = m.sumCount();
//            int f = (t = m.table) == null ? 0 : t.length;
//            return new KeySpliterator<K, V>(t, f, 0, f, n < 0L ? 0L : n);
//        }
//
//        public void forEach(Consumer<? super K> action) {
//            if (action == null) throw new NullPointerException();
//            Node<K, V>[] t;
//            if ((t = map.table) != null) {
//                Traverser<K, V> it = new Traverser<K, V>(t, t.length, 0, t.length);
//                for (Node<K, V> p; (p = it.advance()) != null; )
//                    action.accept(p.key);
//            }
//        }
//    }
//
//    /**
//     * A view of a ConcurrentHashMap as a {@link Collection} of
//     * values, in which additions are disabled. This class cannot be
//     * directly instantiated. See {@link #values()}.
//     */
//    static final class ValuesView<K, V> extends CollectionView<K, V, V>
//            implements Collection<V>, java.io.Serializable {
//        private static final long serialVersionUID = 2249069246763182397L;
//
//        ValuesView(ConcurrentHashMap<K, V> map) {
//            super(map);
//        }
//
//        public final boolean contains(Object o) {
//            return map.containsValue(o);
//        }
//
//        public final boolean remove(Object o) {
//            if (o != null) {
//                for (Iterator<V> it = iterator(); it.hasNext(); ) {
//                    if (o.equals(it.next())) {
//                        it.remove();
//                        return true;
//                    }
//                }
//            }
//            return false;
//        }
//
//        public final Iterator<V> iterator() {
//            ConcurrentHashMap<K, V> m = map;
//            Node<K, V>[] t;
//            int f = (t = m.table) == null ? 0 : t.length;
//            return new ValueIterator<K, V>(t, f, 0, f, m);
//        }
//
//        public final boolean add(V e) {
//            throw new UnsupportedOperationException();
//        }
//
//        public final boolean addAll(Collection<? extends V> c) {
//            throw new UnsupportedOperationException();
//        }
//
//        public Spliterator<V> spliterator() {
//            Node<K, V>[] t;
//            ConcurrentHashMap<K, V> m = map;
//            long n = m.sumCount();
//            int f = (t = m.table) == null ? 0 : t.length;
//            return new ValueSpliterator<K, V>(t, f, 0, f, n < 0L ? 0L : n);
//        }
//
//        public void forEach(Consumer<? super V> action) {
//            if (action == null) throw new NullPointerException();
//            Node<K, V>[] t;
//            if ((t = map.table) != null) {
//                Traverser<K, V> it = new Traverser<K, V>(t, t.length, 0, t.length);
//                for (Node<K, V> p; (p = it.advance()) != null; )
//                    action.accept(p.val);
//            }
//        }
//    }
//
//    /**
//     * A view of a ConcurrentHashMap as a {@link Set} of (key, value)
//     * entries.  This class cannot be directly instantiated. See
//     * {@link #entrySet()}.
//     */
//    static final class EntrySetView<K, V> extends CollectionView<K, V, Map.Entry<K, V>>
//            implements Set<Map.Entry<K, V>>, java.io.Serializable {
//        private static final long serialVersionUID = 2249069246763182397L;
//
//        EntrySetView(ConcurrentHashMap<K, V> map) {
//            super(map);
//        }
//
//        public boolean contains(Object o) {
//            Object k, v, r;
//            Map.Entry<?, ?> e;
//            return ((o instanceof Map.Entry) &&
//                    (k = (e = (Map.Entry<?, ?>) o).getKey()) != null &&
//                    (r = map.get(k)) != null &&
//                    (v = e.getValue()) != null &&
//                    (v == r || v.equals(r)));
//        }
//
//        public boolean remove(Object o) {
//            Object k, v;
//            Map.Entry<?, ?> e;
//            return ((o instanceof Map.Entry) &&
//                    (k = (e = (Map.Entry<?, ?>) o).getKey()) != null &&
//                    (v = e.getValue()) != null &&
//                    map.remove(k, v));
//        }
//
//        /**
//         * @return an iterator over the entries of the backing map
//         */
//        public Iterator<Map.Entry<K, V>> iterator() {
//            ConcurrentHashMap<K, V> m = map;
//            Node<K, V>[] t;
//            int f = (t = m.table) == null ? 0 : t.length;
//            return new EntryIterator<K, V>(t, f, 0, f, m);
//        }
//
//        public boolean add(Entry<K, V> e) {
//            return map.putVal(e.getKey(), e.getValue(), false) == null;
//        }
//
//        public boolean addAll(Collection<? extends Entry<K, V>> c) {
//            boolean added = false;
//            for (Entry<K, V> e : c) {
//                if (add(e))
//                    added = true;
//            }
//            return added;
//        }
//
//        public final int hashCode() {
//            int h = 0;
//            Node<K, V>[] t;
//            if ((t = map.table) != null) {
//                Traverser<K, V> it = new Traverser<K, V>(t, t.length, 0, t.length);
//                for (Node<K, V> p; (p = it.advance()) != null; ) {
//                    h += p.hashCode();
//                }
//            }
//            return h;
//        }
//
//        public final boolean equals(Object o) {
//            Set<?> c;
//            return ((o instanceof Set) &&
//                    ((c = (Set<?>) o) == this ||
//                            (containsAll(c) && c.containsAll(this))));
//        }
//
//        public Spliterator<Map.Entry<K, V>> spliterator() {
//            Node<K, V>[] t;
//            ConcurrentHashMap<K, V> m = map;
//            long n = m.sumCount();
//            int f = (t = m.table) == null ? 0 : t.length;
//            return new EntrySpliterator<K, V>(t, f, 0, f, n < 0L ? 0L : n, m);
//        }
//
//        public void forEach(Consumer<? super Map.Entry<K, V>> action) {
//            if (action == null) throw new NullPointerException();
//            Node<K, V>[] t;
//            if ((t = map.table) != null) {
//                Traverser<K, V> it = new Traverser<K, V>(t, t.length, 0, t.length);
//                for (Node<K, V> p; (p = it.advance()) != null; )
//                    action.accept(new MapEntry<K, V>(p.key, p.val, map));
//            }
//        }
//
//    }
//
//    // -------------------------------------------------------
//
//    /**
//     * Base class for bulk tasks. Repeats some fields and code from
//     * class Traverser, because we need to subclass CountedCompleter.
//     */
//    @SuppressWarnings("serial")
//    abstract static class BulkTask<K, V, R> extends CountedCompleter<R> {
//        Node<K, V>[] tab;        // same as Traverser
//        Node<K, V> next;
//        TableStack<K, V> stack, spare;
//        int index;
//        int baseIndex;
//        int baseLimit;
//        final int baseSize;
//        int batch;              // split control
//
//        BulkTask(BulkTask<K, V, ?> par, int b, int i, int f, Node<K, V>[] t) {
//            super(par);
//            this.batch = b;
//            this.index = this.baseIndex = i;
//            if ((this.tab = t) == null)
//                this.baseSize = this.baseLimit = 0;
//            else if (par == null)
//                this.baseSize = this.baseLimit = t.length;
//            else {
//                this.baseLimit = f;
//                this.baseSize = par.baseSize;
//            }
//        }
//
//        /**
//         * Same as Traverser version
//         */
//        final Node<K, V> advance() {
//            Node<K, V> e;
//            if ((e = next) != null)
//                e = e.next;
//            for (; ; ) {
//                Node<K, V>[] t;
//                int i, n;
//                if (e != null)
//                    return next = e;
//                if (baseIndex >= baseLimit || (t = tab) == null ||
//                        (n = t.length) <= (i = index) || i < 0)
//                    return next = null;
//                if ((e = tabAt(t, i)) != null && e.hash < 0) {
//                    if (e instanceof ForwardingNode) {
//                        tab = ((ForwardingNode<K, V>) e).nextTable;
//                        e = null;
//                        pushState(t, i, n);
//                        continue;
//                    } else if (e instanceof TreeBin)
//                        e = ((TreeBin<K, V>) e).first;
//                    else
//                        e = null;
//                }
//                if (stack != null)
//                    recoverState(n);
//                else if ((index = i + baseSize) >= n)
//                    index = ++baseIndex;
//            }
//        }
//
//        private void pushState(Node<K, V>[] t, int i, int n) {
//            TableStack<K, V> s = spare;
//            if (s != null)
//                spare = s.next;
//            else
//                s = new TableStack<K, V>();
//            s.tab = t;
//            s.length = n;
//            s.index = i;
//            s.next = stack;
//            stack = s;
//        }
//
//        private void recoverState(int n) {
//            TableStack<K, V> s;
//            int len;
//            while ((s = stack) != null && (index += (len = s.length)) >= n) {
//                n = len;
//                index = s.index;
//                tab = s.tab;
//                s.tab = null;
//                TableStack<K, V> next = s.next;
//                s.next = spare; // save for reuse
//                stack = next;
//                spare = s;
//            }
//            if (s == null && (index += baseSize) >= n)
//                index = ++baseIndex;
//        }
//    }
//
//    /*
//     * Task classes. Coded in a regular but ugly format/style to
//     * simplify checks that each variant differs in the right way from
//     * others. The null screenings exist because compilers cannot tell
//     * that we've already null-checked task arguments, so we force
//     * simplest hoisted bypass to help avoid convoluted traps.
//     */
//    @SuppressWarnings("serial")
//    static final class ForEachKeyTask<K, V>
//            extends BulkTask<K, V, Void> {
//        final Consumer<? super K> action;
//
//        ForEachKeyTask
//                (BulkTask<K, V, ?> p, int b, int i, int f, Node<K, V>[] t,
//                 Consumer<? super K> action) {
//            super(p, b, i, f, t);
//            this.action = action;
//        }
//
//        public final void compute() {
//            final Consumer<? super K> action;
//            if ((action = this.action) != null) {
//                for (int i = baseIndex, f, h; batch > 0 &&
//                        (h = ((f = baseLimit) + i) >>> 1) > i; ) {
//                    addToPendingCount(1);
//                    new ForEachKeyTask<K, V>
//                            (this, batch >>>= 1, baseLimit = h, f, tab,
//                                    action).fork();
//                }
//                for (Node<K, V> p; (p = advance()) != null; )
//                    action.accept(p.key);
//                propagateCompletion();
//            }
//        }
//    }
//
//    @SuppressWarnings("serial")
//    static final class ForEachValueTask<K, V>
//            extends BulkTask<K, V, Void> {
//        final Consumer<? super V> action;
//
//        ForEachValueTask
//                (BulkTask<K, V, ?> p, int b, int i, int f, Node<K, V>[] t,
//                 Consumer<? super V> action) {
//            super(p, b, i, f, t);
//            this.action = action;
//        }
//
//        public final void compute() {
//            final Consumer<? super V> action;
//            if ((action = this.action) != null) {
//                for (int i = baseIndex, f, h; batch > 0 &&
//                        (h = ((f = baseLimit) + i) >>> 1) > i; ) {
//                    addToPendingCount(1);
//                    new ForEachValueTask<K, V>
//                            (this, batch >>>= 1, baseLimit = h, f, tab,
//                                    action).fork();
//                }
//                for (Node<K, V> p; (p = advance()) != null; )
//                    action.accept(p.val);
//                propagateCompletion();
//            }
//        }
//    }
//
//    @SuppressWarnings("serial")
//    static final class ForEachEntryTask<K, V>
//            extends BulkTask<K, V, Void> {
//        final Consumer<? super Entry<K, V>> action;
//
//        ForEachEntryTask
//                (BulkTask<K, V, ?> p, int b, int i, int f, Node<K, V>[] t,
//                 Consumer<? super Entry<K, V>> action) {
//            super(p, b, i, f, t);
//            this.action = action;
//        }
//
//        public final void compute() {
//            final Consumer<? super Entry<K, V>> action;
//            if ((action = this.action) != null) {
//                for (int i = baseIndex, f, h; batch > 0 &&
//                        (h = ((f = baseLimit) + i) >>> 1) > i; ) {
//                    addToPendingCount(1);
//                    new ForEachEntryTask<K, V>
//                            (this, batch >>>= 1, baseLimit = h, f, tab,
//                                    action).fork();
//                }
//                for (Node<K, V> p; (p = advance()) != null; )
//                    action.accept(p);
//                propagateCompletion();
//            }
//        }
//    }
//
//    @SuppressWarnings("serial")
//    static final class ForEachMappingTask<K, V>
//            extends BulkTask<K, V, Void> {
//        final BiConsumer<? super K, ? super V> action;
//
//        ForEachMappingTask
//                (BulkTask<K, V, ?> p, int b, int i, int f, Node<K, V>[] t,
//                 BiConsumer<? super K, ? super V> action) {
//            super(p, b, i, f, t);
//            this.action = action;
//        }
//
//        public final void compute() {
//            final BiConsumer<? super K, ? super V> action;
//            if ((action = this.action) != null) {
//                for (int i = baseIndex, f, h; batch > 0 &&
//                        (h = ((f = baseLimit) + i) >>> 1) > i; ) {
//                    addToPendingCount(1);
//                    new ForEachMappingTask<K, V>
//                            (this, batch >>>= 1, baseLimit = h, f, tab,
//                                    action).fork();
//                }
//                for (Node<K, V> p; (p = advance()) != null; )
//                    action.accept(p.key, p.val);
//                propagateCompletion();
//            }
//        }
//    }
//
//    @SuppressWarnings("serial")
//    static final class ForEachTransformedKeyTask<K, V, U>
//            extends BulkTask<K, V, Void> {
//        final Function<? super K, ? extends U> transformer;
//        final Consumer<? super U> action;
//
//        ForEachTransformedKeyTask
//                (BulkTask<K, V, ?> p, int b, int i, int f, Node<K, V>[] t,
//                 Function<? super K, ? extends U> transformer, Consumer<? super U> action) {
//            super(p, b, i, f, t);
//            this.transformer = transformer;
//            this.action = action;
//        }
//
//        public final void compute() {
//            final Function<? super K, ? extends U> transformer;
//            final Consumer<? super U> action;
//            if ((transformer = this.transformer) != null &&
//                    (action = this.action) != null) {
//                for (int i = baseIndex, f, h; batch > 0 &&
//                        (h = ((f = baseLimit) + i) >>> 1) > i; ) {
//                    addToPendingCount(1);
//                    new ForEachTransformedKeyTask<K, V, U>
//                            (this, batch >>>= 1, baseLimit = h, f, tab,
//                                    transformer, action).fork();
//                }
//                for (Node<K, V> p; (p = advance()) != null; ) {
//                    U u;
//                    if ((u = transformer.apply(p.key)) != null)
//                        action.accept(u);
//                }
//                propagateCompletion();
//            }
//        }
//    }
//
//    @SuppressWarnings("serial")
//    static final class ForEachTransformedValueTask<K, V, U>
//            extends BulkTask<K, V, Void> {
//        final Function<? super V, ? extends U> transformer;
//        final Consumer<? super U> action;
//
//        ForEachTransformedValueTask
//                (BulkTask<K, V, ?> p, int b, int i, int f, Node<K, V>[] t,
//                 Function<? super V, ? extends U> transformer, Consumer<? super U> action) {
//            super(p, b, i, f, t);
//            this.transformer = transformer;
//            this.action = action;
//        }
//
//        public final void compute() {
//            final Function<? super V, ? extends U> transformer;
//            final Consumer<? super U> action;
//            if ((transformer = this.transformer) != null &&
//                    (action = this.action) != null) {
//                for (int i = baseIndex, f, h; batch > 0 &&
//                        (h = ((f = baseLimit) + i) >>> 1) > i; ) {
//                    addToPendingCount(1);
//                    new ForEachTransformedValueTask<K, V, U>
//                            (this, batch >>>= 1, baseLimit = h, f, tab,
//                                    transformer, action).fork();
//                }
//                for (Node<K, V> p; (p = advance()) != null; ) {
//                    U u;
//                    if ((u = transformer.apply(p.val)) != null)
//                        action.accept(u);
//                }
//                propagateCompletion();
//            }
//        }
//    }
//
//    @SuppressWarnings("serial")
//    static final class ForEachTransformedEntryTask<K, V, U>
//            extends BulkTask<K, V, Void> {
//        final Function<Map.Entry<K, V>, ? extends U> transformer;
//        final Consumer<? super U> action;
//
//        ForEachTransformedEntryTask
//                (BulkTask<K, V, ?> p, int b, int i, int f, Node<K, V>[] t,
//                 Function<Map.Entry<K, V>, ? extends U> transformer, Consumer<? super U> action) {
//            super(p, b, i, f, t);
//            this.transformer = transformer;
//            this.action = action;
//        }
//
//        public final void compute() {
//            final Function<Map.Entry<K, V>, ? extends U> transformer;
//            final Consumer<? super U> action;
//            if ((transformer = this.transformer) != null &&
//                    (action = this.action) != null) {
//                for (int i = baseIndex, f, h; batch > 0 &&
//                        (h = ((f = baseLimit) + i) >>> 1) > i; ) {
//                    addToPendingCount(1);
//                    new ForEachTransformedEntryTask<K, V, U>
//                            (this, batch >>>= 1, baseLimit = h, f, tab,
//                                    transformer, action).fork();
//                }
//                for (Node<K, V> p; (p = advance()) != null; ) {
//                    U u;
//                    if ((u = transformer.apply(p)) != null)
//                        action.accept(u);
//                }
//                propagateCompletion();
//            }
//        }
//    }
//
//    @SuppressWarnings("serial")
//    static final class ForEachTransformedMappingTask<K, V, U>
//            extends BulkTask<K, V, Void> {
//        final BiFunction<? super K, ? super V, ? extends U> transformer;
//        final Consumer<? super U> action;
//
//        ForEachTransformedMappingTask
//                (BulkTask<K, V, ?> p, int b, int i, int f, Node<K, V>[] t,
//                 BiFunction<? super K, ? super V, ? extends U> transformer,
//                 Consumer<? super U> action) {
//            super(p, b, i, f, t);
//            this.transformer = transformer;
//            this.action = action;
//        }
//
//        public final void compute() {
//            final BiFunction<? super K, ? super V, ? extends U> transformer;
//            final Consumer<? super U> action;
//            if ((transformer = this.transformer) != null &&
//                    (action = this.action) != null) {
//                for (int i = baseIndex, f, h; batch > 0 &&
//                        (h = ((f = baseLimit) + i) >>> 1) > i; ) {
//                    addToPendingCount(1);
//                    new ForEachTransformedMappingTask<K, V, U>
//                            (this, batch >>>= 1, baseLimit = h, f, tab,
//                                    transformer, action).fork();
//                }
//                for (Node<K, V> p; (p = advance()) != null; ) {
//                    U u;
//                    if ((u = transformer.apply(p.key, p.val)) != null)
//                        action.accept(u);
//                }
//                propagateCompletion();
//            }
//        }
//    }
//
//    @SuppressWarnings("serial")
//    static final class SearchKeysTask<K, V, U>
//            extends BulkTask<K, V, U> {
//        final Function<? super K, ? extends U> searchFunction;
//        final AtomicReference<U> result;
//
//        SearchKeysTask
//                (BulkTask<K, V, ?> p, int b, int i, int f, Node<K, V>[] t,
//                 Function<? super K, ? extends U> searchFunction,
//                 AtomicReference<U> result) {
//            super(p, b, i, f, t);
//            this.searchFunction = searchFunction;
//            this.result = result;
//        }
//
//        public final U getRawResult() {
//            return result.get();
//        }
//
//        public final void compute() {
//            final Function<? super K, ? extends U> searchFunction;
//            final AtomicReference<U> result;
//            if ((searchFunction = this.searchFunction) != null &&
//                    (result = this.result) != null) {
//                for (int i = baseIndex, f, h; batch > 0 &&
//                        (h = ((f = baseLimit) + i) >>> 1) > i; ) {
//                    if (result.get() != null)
//                        return;
//                    addToPendingCount(1);
//                    new SearchKeysTask<K, V, U>
//                            (this, batch >>>= 1, baseLimit = h, f, tab,
//                                    searchFunction, result).fork();
//                }
//                while (result.get() == null) {
//                    U u;
//                    Node<K, V> p;
//                    if ((p = advance()) == null) {
//                        propagateCompletion();
//                        break;
//                    }
//                    if ((u = searchFunction.apply(p.key)) != null) {
//                        if (result.compareAndSet(null, u))
//                            quietlyCompleteRoot();
//                        break;
//                    }
//                }
//            }
//        }
//    }
//
//    @SuppressWarnings("serial")
//    static final class SearchValuesTask<K, V, U>
//            extends BulkTask<K, V, U> {
//        final Function<? super V, ? extends U> searchFunction;
//        final AtomicReference<U> result;
//
//        SearchValuesTask
//                (BulkTask<K, V, ?> p, int b, int i, int f, Node<K, V>[] t,
//                 Function<? super V, ? extends U> searchFunction,
//                 AtomicReference<U> result) {
//            super(p, b, i, f, t);
//            this.searchFunction = searchFunction;
//            this.result = result;
//        }
//
//        public final U getRawResult() {
//            return result.get();
//        }
//
//        public final void compute() {
//            final Function<? super V, ? extends U> searchFunction;
//            final AtomicReference<U> result;
//            if ((searchFunction = this.searchFunction) != null &&
//                    (result = this.result) != null) {
//                for (int i = baseIndex, f, h; batch > 0 &&
//                        (h = ((f = baseLimit) + i) >>> 1) > i; ) {
//                    if (result.get() != null)
//                        return;
//                    addToPendingCount(1);
//                    new SearchValuesTask<K, V, U>
//                            (this, batch >>>= 1, baseLimit = h, f, tab,
//                                    searchFunction, result).fork();
//                }
//                while (result.get() == null) {
//                    U u;
//                    Node<K, V> p;
//                    if ((p = advance()) == null) {
//                        propagateCompletion();
//                        break;
//                    }
//                    if ((u = searchFunction.apply(p.val)) != null) {
//                        if (result.compareAndSet(null, u))
//                            quietlyCompleteRoot();
//                        break;
//                    }
//                }
//            }
//        }
//    }
//
//    @SuppressWarnings("serial")
//    static final class SearchEntriesTask<K, V, U>
//            extends BulkTask<K, V, U> {
//        final Function<Entry<K, V>, ? extends U> searchFunction;
//        final AtomicReference<U> result;
//
//        SearchEntriesTask
//                (BulkTask<K, V, ?> p, int b, int i, int f, Node<K, V>[] t,
//                 Function<Entry<K, V>, ? extends U> searchFunction,
//                 AtomicReference<U> result) {
//            super(p, b, i, f, t);
//            this.searchFunction = searchFunction;
//            this.result = result;
//        }
//
//        public final U getRawResult() {
//            return result.get();
//        }
//
//        public final void compute() {
//            final Function<Entry<K, V>, ? extends U> searchFunction;
//            final AtomicReference<U> result;
//            if ((searchFunction = this.searchFunction) != null &&
//                    (result = this.result) != null) {
//                for (int i = baseIndex, f, h; batch > 0 &&
//                        (h = ((f = baseLimit) + i) >>> 1) > i; ) {
//                    if (result.get() != null)
//                        return;
//                    addToPendingCount(1);
//                    new SearchEntriesTask<K, V, U>
//                            (this, batch >>>= 1, baseLimit = h, f, tab,
//                                    searchFunction, result).fork();
//                }
//                while (result.get() == null) {
//                    U u;
//                    Node<K, V> p;
//                    if ((p = advance()) == null) {
//                        propagateCompletion();
//                        break;
//                    }
//                    if ((u = searchFunction.apply(p)) != null) {
//                        if (result.compareAndSet(null, u))
//                            quietlyCompleteRoot();
//                        return;
//                    }
//                }
//            }
//        }
//    }
//
//    @SuppressWarnings("serial")
//    static final class SearchMappingsTask<K, V, U>
//            extends BulkTask<K, V, U> {
//        final BiFunction<? super K, ? super V, ? extends U> searchFunction;
//        final AtomicReference<U> result;
//
//        SearchMappingsTask
//                (BulkTask<K, V, ?> p, int b, int i, int f, Node<K, V>[] t,
//                 BiFunction<? super K, ? super V, ? extends U> searchFunction,
//                 AtomicReference<U> result) {
//            super(p, b, i, f, t);
//            this.searchFunction = searchFunction;
//            this.result = result;
//        }
//
//        public final U getRawResult() {
//            return result.get();
//        }
//
//        public final void compute() {
//            final BiFunction<? super K, ? super V, ? extends U> searchFunction;
//            final AtomicReference<U> result;
//            if ((searchFunction = this.searchFunction) != null &&
//                    (result = this.result) != null) {
//                for (int i = baseIndex, f, h; batch > 0 &&
//                        (h = ((f = baseLimit) + i) >>> 1) > i; ) {
//                    if (result.get() != null)
//                        return;
//                    addToPendingCount(1);
//                    new SearchMappingsTask<K, V, U>
//                            (this, batch >>>= 1, baseLimit = h, f, tab,
//                                    searchFunction, result).fork();
//                }
//                while (result.get() == null) {
//                    U u;
//                    Node<K, V> p;
//                    if ((p = advance()) == null) {
//                        propagateCompletion();
//                        break;
//                    }
//                    if ((u = searchFunction.apply(p.key, p.val)) != null) {
//                        if (result.compareAndSet(null, u))
//                            quietlyCompleteRoot();
//                        break;
//                    }
//                }
//            }
//        }
//    }
//
//    @SuppressWarnings("serial")
//    static final class ReduceKeysTask<K, V>
//            extends BulkTask<K, V, K> {
//        final BiFunction<? super K, ? super K, ? extends K> reducer;
//        K result;
//        ReduceKeysTask<K, V> rights, nextRight;
//
//        ReduceKeysTask
//                (BulkTask<K, V, ?> p, int b, int i, int f, Node<K, V>[] t,
//                 ReduceKeysTask<K, V> nextRight,
//                 BiFunction<? super K, ? super K, ? extends K> reducer) {
//            super(p, b, i, f, t);
//            this.nextRight = nextRight;
//            this.reducer = reducer;
//        }
//
//        public final K getRawResult() {
//            return result;
//        }
//
//        public final void compute() {
//            final BiFunction<? super K, ? super K, ? extends K> reducer;
//            if ((reducer = this.reducer) != null) {
//                for (int i = baseIndex, f, h; batch > 0 &&
//                        (h = ((f = baseLimit) + i) >>> 1) > i; ) {
//                    addToPendingCount(1);
//                    (rights = new ReduceKeysTask<K, V>
//                            (this, batch >>>= 1, baseLimit = h, f, tab,
//                                    rights, reducer)).fork();
//                }
//                K r = null;
//                for (Node<K, V> p; (p = advance()) != null; ) {
//                    K u = p.key;
//                    r = (r == null) ? u : u == null ? r : reducer.apply(r, u);
//                }
//                result = r;
//                CountedCompleter<?> c;
//                for (c = firstComplete(); c != null; c = c.nextComplete()) {
//                    @SuppressWarnings("unchecked")
//                    ReduceKeysTask<K, V>
//                            t = (ReduceKeysTask<K, V>) c,
//                            s = t.rights;
//                    while (s != null) {
//                        K tr, sr;
//                        if ((sr = s.result) != null)
//                            t.result = (((tr = t.result) == null) ? sr :
//                                    reducer.apply(tr, sr));
//                        s = t.rights = s.nextRight;
//                    }
//                }
//            }
//        }
//    }
//
//    @SuppressWarnings("serial")
//    static final class ReduceValuesTask<K, V>
//            extends BulkTask<K, V, V> {
//        final BiFunction<? super V, ? super V, ? extends V> reducer;
//        V result;
//        ReduceValuesTask<K, V> rights, nextRight;
//
//        ReduceValuesTask
//                (BulkTask<K, V, ?> p, int b, int i, int f, Node<K, V>[] t,
//                 ReduceValuesTask<K, V> nextRight,
//                 BiFunction<? super V, ? super V, ? extends V> reducer) {
//            super(p, b, i, f, t);
//            this.nextRight = nextRight;
//            this.reducer = reducer;
//        }
//
//        public final V getRawResult() {
//            return result;
//        }
//
//        public final void compute() {
//            final BiFunction<? super V, ? super V, ? extends V> reducer;
//            if ((reducer = this.reducer) != null) {
//                for (int i = baseIndex, f, h; batch > 0 &&
//                        (h = ((f = baseLimit) + i) >>> 1) > i; ) {
//                    addToPendingCount(1);
//                    (rights = new ReduceValuesTask<K, V>
//                            (this, batch >>>= 1, baseLimit = h, f, tab,
//                                    rights, reducer)).fork();
//                }
//                V r = null;
//                for (Node<K, V> p; (p = advance()) != null; ) {
//                    V v = p.val;
//                    r = (r == null) ? v : reducer.apply(r, v);
//                }
//                result = r;
//                CountedCompleter<?> c;
//                for (c = firstComplete(); c != null; c = c.nextComplete()) {
//                    @SuppressWarnings("unchecked")
//                    ReduceValuesTask<K, V>
//                            t = (ReduceValuesTask<K, V>) c,
//                            s = t.rights;
//                    while (s != null) {
//                        V tr, sr;
//                        if ((sr = s.result) != null)
//                            t.result = (((tr = t.result) == null) ? sr :
//                                    reducer.apply(tr, sr));
//                        s = t.rights = s.nextRight;
//                    }
//                }
//            }
//        }
//    }
//
//    @SuppressWarnings("serial")
//    static final class ReduceEntriesTask<K, V>
//            extends BulkTask<K, V, Map.Entry<K, V>> {
//        final BiFunction<Map.Entry<K, V>, Map.Entry<K, V>, ? extends Map.Entry<K, V>> reducer;
//        Map.Entry<K, V> result;
//        ReduceEntriesTask<K, V> rights, nextRight;
//
//        ReduceEntriesTask
//                (BulkTask<K, V, ?> p, int b, int i, int f, Node<K, V>[] t,
//                 ReduceEntriesTask<K, V> nextRight,
//                 BiFunction<Entry<K, V>, Map.Entry<K, V>, ? extends Map.Entry<K, V>> reducer) {
//            super(p, b, i, f, t);
//            this.nextRight = nextRight;
//            this.reducer = reducer;
//        }
//
//        public final Map.Entry<K, V> getRawResult() {
//            return result;
//        }
//
//        public final void compute() {
//            final BiFunction<Map.Entry<K, V>, Map.Entry<K, V>, ? extends Map.Entry<K, V>> reducer;
//            if ((reducer = this.reducer) != null) {
//                for (int i = baseIndex, f, h; batch > 0 &&
//                        (h = ((f = baseLimit) + i) >>> 1) > i; ) {
//                    addToPendingCount(1);
//                    (rights = new ReduceEntriesTask<K, V>
//                            (this, batch >>>= 1, baseLimit = h, f, tab,
//                                    rights, reducer)).fork();
//                }
//                Map.Entry<K, V> r = null;
//                for (Node<K, V> p; (p = advance()) != null; )
//                    r = (r == null) ? p : reducer.apply(r, p);
//                result = r;
//                CountedCompleter<?> c;
//                for (c = firstComplete(); c != null; c = c.nextComplete()) {
//                    @SuppressWarnings("unchecked")
//                    ReduceEntriesTask<K, V>
//                            t = (ReduceEntriesTask<K, V>) c,
//                            s = t.rights;
//                    while (s != null) {
//                        Map.Entry<K, V> tr, sr;
//                        if ((sr = s.result) != null)
//                            t.result = (((tr = t.result) == null) ? sr :
//                                    reducer.apply(tr, sr));
//                        s = t.rights = s.nextRight;
//                    }
//                }
//            }
//        }
//    }
//
//    @SuppressWarnings("serial")
//    static final class MapReduceKeysTask<K, V, U>
//            extends BulkTask<K, V, U> {
//        final Function<? super K, ? extends U> transformer;
//        final BiFunction<? super U, ? super U, ? extends U> reducer;
//        U result;
//        MapReduceKeysTask<K, V, U> rights, nextRight;
//
//        MapReduceKeysTask
//                (BulkTask<K, V, ?> p, int b, int i, int f, Node<K, V>[] t,
//                 MapReduceKeysTask<K, V, U> nextRight,
//                 Function<? super K, ? extends U> transformer,
//                 BiFunction<? super U, ? super U, ? extends U> reducer) {
//            super(p, b, i, f, t);
//            this.nextRight = nextRight;
//            this.transformer = transformer;
//            this.reducer = reducer;
//        }
//
//        public final U getRawResult() {
//            return result;
//        }
//
//        public final void compute() {
//            final Function<? super K, ? extends U> transformer;
//            final BiFunction<? super U, ? super U, ? extends U> reducer;
//            if ((transformer = this.transformer) != null &&
//                    (reducer = this.reducer) != null) {
//                for (int i = baseIndex, f, h; batch > 0 &&
//                        (h = ((f = baseLimit) + i) >>> 1) > i; ) {
//                    addToPendingCount(1);
//                    (rights = new MapReduceKeysTask<K, V, U>
//                            (this, batch >>>= 1, baseLimit = h, f, tab,
//                                    rights, transformer, reducer)).fork();
//                }
//                U r = null;
//                for (Node<K, V> p; (p = advance()) != null; ) {
//                    U u;
//                    if ((u = transformer.apply(p.key)) != null)
//                        r = (r == null) ? u : reducer.apply(r, u);
//                }
//                result = r;
//                CountedCompleter<?> c;
//                for (c = firstComplete(); c != null; c = c.nextComplete()) {
//                    @SuppressWarnings("unchecked")
//                    MapReduceKeysTask<K, V, U>
//                            t = (MapReduceKeysTask<K, V, U>) c,
//                            s = t.rights;
//                    while (s != null) {
//                        U tr, sr;
//                        if ((sr = s.result) != null)
//                            t.result = (((tr = t.result) == null) ? sr :
//                                    reducer.apply(tr, sr));
//                        s = t.rights = s.nextRight;
//                    }
//                }
//            }
//        }
//    }
//
//    @SuppressWarnings("serial")
//    static final class MapReduceValuesTask<K, V, U>
//            extends BulkTask<K, V, U> {
//        final Function<? super V, ? extends U> transformer;
//        final BiFunction<? super U, ? super U, ? extends U> reducer;
//        U result;
//        MapReduceValuesTask<K, V, U> rights, nextRight;
//
//        MapReduceValuesTask
//                (BulkTask<K, V, ?> p, int b, int i, int f, Node<K, V>[] t,
//                 MapReduceValuesTask<K, V, U> nextRight,
//                 Function<? super V, ? extends U> transformer,
//                 BiFunction<? super U, ? super U, ? extends U> reducer) {
//            super(p, b, i, f, t);
//            this.nextRight = nextRight;
//            this.transformer = transformer;
//            this.reducer = reducer;
//        }
//
//        public final U getRawResult() {
//            return result;
//        }
//
//        public final void compute() {
//            final Function<? super V, ? extends U> transformer;
//            final BiFunction<? super U, ? super U, ? extends U> reducer;
//            if ((transformer = this.transformer) != null &&
//                    (reducer = this.reducer) != null) {
//                for (int i = baseIndex, f, h; batch > 0 &&
//                        (h = ((f = baseLimit) + i) >>> 1) > i; ) {
//                    addToPendingCount(1);
//                    (rights = new MapReduceValuesTask<K, V, U>
//                            (this, batch >>>= 1, baseLimit = h, f, tab,
//                                    rights, transformer, reducer)).fork();
//                }
//                U r = null;
//                for (Node<K, V> p; (p = advance()) != null; ) {
//                    U u;
//                    if ((u = transformer.apply(p.val)) != null)
//                        r = (r == null) ? u : reducer.apply(r, u);
//                }
//                result = r;
//                CountedCompleter<?> c;
//                for (c = firstComplete(); c != null; c = c.nextComplete()) {
//                    @SuppressWarnings("unchecked")
//                    MapReduceValuesTask<K, V, U>
//                            t = (MapReduceValuesTask<K, V, U>) c,
//                            s = t.rights;
//                    while (s != null) {
//                        U tr, sr;
//                        if ((sr = s.result) != null)
//                            t.result = (((tr = t.result) == null) ? sr :
//                                    reducer.apply(tr, sr));
//                        s = t.rights = s.nextRight;
//                    }
//                }
//            }
//        }
//    }
//
//    @SuppressWarnings("serial")
//    static final class MapReduceEntriesTask<K, V, U>
//            extends BulkTask<K, V, U> {
//        final Function<Map.Entry<K, V>, ? extends U> transformer;
//        final BiFunction<? super U, ? super U, ? extends U> reducer;
//        U result;
//        MapReduceEntriesTask<K, V, U> rights, nextRight;
//
//        MapReduceEntriesTask
//                (BulkTask<K, V, ?> p, int b, int i, int f, Node<K, V>[] t,
//                 MapReduceEntriesTask<K, V, U> nextRight,
//                 Function<Map.Entry<K, V>, ? extends U> transformer,
//                 BiFunction<? super U, ? super U, ? extends U> reducer) {
//            super(p, b, i, f, t);
//            this.nextRight = nextRight;
//            this.transformer = transformer;
//            this.reducer = reducer;
//        }
//
//        public final U getRawResult() {
//            return result;
//        }
//
//        public final void compute() {
//            final Function<Map.Entry<K, V>, ? extends U> transformer;
//            final BiFunction<? super U, ? super U, ? extends U> reducer;
//            if ((transformer = this.transformer) != null &&
//                    (reducer = this.reducer) != null) {
//                for (int i = baseIndex, f, h; batch > 0 &&
//                        (h = ((f = baseLimit) + i) >>> 1) > i; ) {
//                    addToPendingCount(1);
//                    (rights = new MapReduceEntriesTask<K, V, U>
//                            (this, batch >>>= 1, baseLimit = h, f, tab,
//                                    rights, transformer, reducer)).fork();
//                }
//                U r = null;
//                for (Node<K, V> p; (p = advance()) != null; ) {
//                    U u;
//                    if ((u = transformer.apply(p)) != null)
//                        r = (r == null) ? u : reducer.apply(r, u);
//                }
//                result = r;
//                CountedCompleter<?> c;
//                for (c = firstComplete(); c != null; c = c.nextComplete()) {
//                    @SuppressWarnings("unchecked")
//                    MapReduceEntriesTask<K, V, U>
//                            t = (MapReduceEntriesTask<K, V, U>) c,
//                            s = t.rights;
//                    while (s != null) {
//                        U tr, sr;
//                        if ((sr = s.result) != null)
//                            t.result = (((tr = t.result) == null) ? sr :
//                                    reducer.apply(tr, sr));
//                        s = t.rights = s.nextRight;
//                    }
//                }
//            }
//        }
//    }
//
//    @SuppressWarnings("serial")
//    static final class MapReduceMappingsTask<K, V, U>
//            extends BulkTask<K, V, U> {
//        final BiFunction<? super K, ? super V, ? extends U> transformer;
//        final BiFunction<? super U, ? super U, ? extends U> reducer;
//        U result;
//        MapReduceMappingsTask<K, V, U> rights, nextRight;
//
//        MapReduceMappingsTask
//                (BulkTask<K, V, ?> p, int b, int i, int f, Node<K, V>[] t,
//                 MapReduceMappingsTask<K, V, U> nextRight,
//                 BiFunction<? super K, ? super V, ? extends U> transformer,
//                 BiFunction<? super U, ? super U, ? extends U> reducer) {
//            super(p, b, i, f, t);
//            this.nextRight = nextRight;
//            this.transformer = transformer;
//            this.reducer = reducer;
//        }
//
//        public final U getRawResult() {
//            return result;
//        }
//
//        public final void compute() {
//            final BiFunction<? super K, ? super V, ? extends U> transformer;
//            final BiFunction<? super U, ? super U, ? extends U> reducer;
//            if ((transformer = this.transformer) != null &&
//                    (reducer = this.reducer) != null) {
//                for (int i = baseIndex, f, h; batch > 0 &&
//                        (h = ((f = baseLimit) + i) >>> 1) > i; ) {
//                    addToPendingCount(1);
//                    (rights = new MapReduceMappingsTask<K, V, U>
//                            (this, batch >>>= 1, baseLimit = h, f, tab,
//                                    rights, transformer, reducer)).fork();
//                }
//                U r = null;
//                for (Node<K, V> p; (p = advance()) != null; ) {
//                    U u;
//                    if ((u = transformer.apply(p.key, p.val)) != null)
//                        r = (r == null) ? u : reducer.apply(r, u);
//                }
//                result = r;
//                CountedCompleter<?> c;
//                for (c = firstComplete(); c != null; c = c.nextComplete()) {
//                    @SuppressWarnings("unchecked")
//                    MapReduceMappingsTask<K, V, U>
//                            t = (MapReduceMappingsTask<K, V, U>) c,
//                            s = t.rights;
//                    while (s != null) {
//                        U tr, sr;
//                        if ((sr = s.result) != null)
//                            t.result = (((tr = t.result) == null) ? sr :
//                                    reducer.apply(tr, sr));
//                        s = t.rights = s.nextRight;
//                    }
//                }
//            }
//        }
//    }
//
//    @SuppressWarnings("serial")
//    static final class MapReduceKeysToDoubleTask<K, V>
//            extends BulkTask<K, V, Double> {
//        final ToDoubleFunction<? super K> transformer;
//        final DoubleBinaryOperator reducer;
//        final double basis;
//        double result;
//        MapReduceKeysToDoubleTask<K, V> rights, nextRight;
//
//        MapReduceKeysToDoubleTask
//                (BulkTask<K, V, ?> p, int b, int i, int f, Node<K, V>[] t,
//                 MapReduceKeysToDoubleTask<K, V> nextRight,
//                 ToDoubleFunction<? super K> transformer,
//                 double basis,
//                 DoubleBinaryOperator reducer) {
//            super(p, b, i, f, t);
//            this.nextRight = nextRight;
//            this.transformer = transformer;
//            this.basis = basis;
//            this.reducer = reducer;
//        }
//
//        public final Double getRawResult() {
//            return result;
//        }
//
//        public final void compute() {
//            final ToDoubleFunction<? super K> transformer;
//            final DoubleBinaryOperator reducer;
//            if ((transformer = this.transformer) != null &&
//                    (reducer = this.reducer) != null) {
//                double r = this.basis;
//                for (int i = baseIndex, f, h; batch > 0 &&
//                        (h = ((f = baseLimit) + i) >>> 1) > i; ) {
//                    addToPendingCount(1);
//                    (rights = new MapReduceKeysToDoubleTask<K, V>
//                            (this, batch >>>= 1, baseLimit = h, f, tab,
//                                    rights, transformer, r, reducer)).fork();
//                }
//                for (Node<K, V> p; (p = advance()) != null; )
//                    r = reducer.applyAsDouble(r, transformer.applyAsDouble(p.key));
//                result = r;
//                CountedCompleter<?> c;
//                for (c = firstComplete(); c != null; c = c.nextComplete()) {
//                    @SuppressWarnings("unchecked")
//                    MapReduceKeysToDoubleTask<K, V>
//                            t = (MapReduceKeysToDoubleTask<K, V>) c,
//                            s = t.rights;
//                    while (s != null) {
//                        t.result = reducer.applyAsDouble(t.result, s.result);
//                        s = t.rights = s.nextRight;
//                    }
//                }
//            }
//        }
//    }
//
//    @SuppressWarnings("serial")
//    static final class MapReduceValuesToDoubleTask<K, V>
//            extends BulkTask<K, V, Double> {
//        final ToDoubleFunction<? super V> transformer;
//        final DoubleBinaryOperator reducer;
//        final double basis;
//        double result;
//        MapReduceValuesToDoubleTask<K, V> rights, nextRight;
//
//        MapReduceValuesToDoubleTask
//                (BulkTask<K, V, ?> p, int b, int i, int f, Node<K, V>[] t,
//                 MapReduceValuesToDoubleTask<K, V> nextRight,
//                 ToDoubleFunction<? super V> transformer,
//                 double basis,
//                 DoubleBinaryOperator reducer) {
//            super(p, b, i, f, t);
//            this.nextRight = nextRight;
//            this.transformer = transformer;
//            this.basis = basis;
//            this.reducer = reducer;
//        }
//
//        public final Double getRawResult() {
//            return result;
//        }
//
//        public final void compute() {
//            final ToDoubleFunction<? super V> transformer;
//            final DoubleBinaryOperator reducer;
//            if ((transformer = this.transformer) != null &&
//                    (reducer = this.reducer) != null) {
//                double r = this.basis;
//                for (int i = baseIndex, f, h; batch > 0 &&
//                        (h = ((f = baseLimit) + i) >>> 1) > i; ) {
//                    addToPendingCount(1);
//                    (rights = new MapReduceValuesToDoubleTask<K, V>
//                            (this, batch >>>= 1, baseLimit = h, f, tab,
//                                    rights, transformer, r, reducer)).fork();
//                }
//                for (Node<K, V> p; (p = advance()) != null; )
//                    r = reducer.applyAsDouble(r, transformer.applyAsDouble(p.val));
//                result = r;
//                CountedCompleter<?> c;
//                for (c = firstComplete(); c != null; c = c.nextComplete()) {
//                    @SuppressWarnings("unchecked")
//                    MapReduceValuesToDoubleTask<K, V>
//                            t = (MapReduceValuesToDoubleTask<K, V>) c,
//                            s = t.rights;
//                    while (s != null) {
//                        t.result = reducer.applyAsDouble(t.result, s.result);
//                        s = t.rights = s.nextRight;
//                    }
//                }
//            }
//        }
//    }
//
//    @SuppressWarnings("serial")
//    static final class MapReduceEntriesToDoubleTask<K, V>
//            extends BulkTask<K, V, Double> {
//        final ToDoubleFunction<Map.Entry<K, V>> transformer;
//        final DoubleBinaryOperator reducer;
//        final double basis;
//        double result;
//        MapReduceEntriesToDoubleTask<K, V> rights, nextRight;
//
//        MapReduceEntriesToDoubleTask
//                (BulkTask<K, V, ?> p, int b, int i, int f, Node<K, V>[] t,
//                 MapReduceEntriesToDoubleTask<K, V> nextRight,
//                 ToDoubleFunction<Map.Entry<K, V>> transformer,
//                 double basis,
//                 DoubleBinaryOperator reducer) {
//            super(p, b, i, f, t);
//            this.nextRight = nextRight;
//            this.transformer = transformer;
//            this.basis = basis;
//            this.reducer = reducer;
//        }
//
//        public final Double getRawResult() {
//            return result;
//        }
//
//        public final void compute() {
//            final ToDoubleFunction<Map.Entry<K, V>> transformer;
//            final DoubleBinaryOperator reducer;
//            if ((transformer = this.transformer) != null &&
//                    (reducer = this.reducer) != null) {
//                double r = this.basis;
//                for (int i = baseIndex, f, h; batch > 0 &&
//                        (h = ((f = baseLimit) + i) >>> 1) > i; ) {
//                    addToPendingCount(1);
//                    (rights = new MapReduceEntriesToDoubleTask<K, V>
//                            (this, batch >>>= 1, baseLimit = h, f, tab,
//                                    rights, transformer, r, reducer)).fork();
//                }
//                for (Node<K, V> p; (p = advance()) != null; )
//                    r = reducer.applyAsDouble(r, transformer.applyAsDouble(p));
//                result = r;
//                CountedCompleter<?> c;
//                for (c = firstComplete(); c != null; c = c.nextComplete()) {
//                    @SuppressWarnings("unchecked")
//                    MapReduceEntriesToDoubleTask<K, V>
//                            t = (MapReduceEntriesToDoubleTask<K, V>) c,
//                            s = t.rights;
//                    while (s != null) {
//                        t.result = reducer.applyAsDouble(t.result, s.result);
//                        s = t.rights = s.nextRight;
//                    }
//                }
//            }
//        }
//    }
//
//    @SuppressWarnings("serial")
//    static final class MapReduceMappingsToDoubleTask<K, V>
//            extends BulkTask<K, V, Double> {
//        final ToDoubleBiFunction<? super K, ? super V> transformer;
//        final DoubleBinaryOperator reducer;
//        final double basis;
//        double result;
//        MapReduceMappingsToDoubleTask<K, V> rights, nextRight;
//
//        MapReduceMappingsToDoubleTask
//                (BulkTask<K, V, ?> p, int b, int i, int f, Node<K, V>[] t,
//                 MapReduceMappingsToDoubleTask<K, V> nextRight,
//                 ToDoubleBiFunction<? super K, ? super V> transformer,
//                 double basis,
//                 DoubleBinaryOperator reducer) {
//            super(p, b, i, f, t);
//            this.nextRight = nextRight;
//            this.transformer = transformer;
//            this.basis = basis;
//            this.reducer = reducer;
//        }
//
//        public final Double getRawResult() {
//            return result;
//        }
//
//        public final void compute() {
//            final ToDoubleBiFunction<? super K, ? super V> transformer;
//            final DoubleBinaryOperator reducer;
//            if ((transformer = this.transformer) != null &&
//                    (reducer = this.reducer) != null) {
//                double r = this.basis;
//                for (int i = baseIndex, f, h; batch > 0 &&
//                        (h = ((f = baseLimit) + i) >>> 1) > i; ) {
//                    addToPendingCount(1);
//                    (rights = new MapReduceMappingsToDoubleTask<K, V>
//                            (this, batch >>>= 1, baseLimit = h, f, tab,
//                                    rights, transformer, r, reducer)).fork();
//                }
//                for (Node<K, V> p; (p = advance()) != null; )
//                    r = reducer.applyAsDouble(r, transformer.applyAsDouble(p.key, p.val));
//                result = r;
//                CountedCompleter<?> c;
//                for (c = firstComplete(); c != null; c = c.nextComplete()) {
//                    @SuppressWarnings("unchecked")
//                    MapReduceMappingsToDoubleTask<K, V>
//                            t = (MapReduceMappingsToDoubleTask<K, V>) c,
//                            s = t.rights;
//                    while (s != null) {
//                        t.result = reducer.applyAsDouble(t.result, s.result);
//                        s = t.rights = s.nextRight;
//                    }
//                }
//            }
//        }
//    }
//
//    @SuppressWarnings("serial")
//    static final class MapReduceKeysToLongTask<K, V>
//            extends BulkTask<K, V, Long> {
//        final ToLongFunction<? super K> transformer;
//        final LongBinaryOperator reducer;
//        final long basis;
//        long result;
//        MapReduceKeysToLongTask<K, V> rights, nextRight;
//
//        MapReduceKeysToLongTask
//                (BulkTask<K, V, ?> p, int b, int i, int f, Node<K, V>[] t,
//                 MapReduceKeysToLongTask<K, V> nextRight,
//                 ToLongFunction<? super K> transformer,
//                 long basis,
//                 LongBinaryOperator reducer) {
//            super(p, b, i, f, t);
//            this.nextRight = nextRight;
//            this.transformer = transformer;
//            this.basis = basis;
//            this.reducer = reducer;
//        }
//
//        public final Long getRawResult() {
//            return result;
//        }
//
//        public final void compute() {
//            final ToLongFunction<? super K> transformer;
//            final LongBinaryOperator reducer;
//            if ((transformer = this.transformer) != null &&
//                    (reducer = this.reducer) != null) {
//                long r = this.basis;
//                for (int i = baseIndex, f, h; batch > 0 &&
//                        (h = ((f = baseLimit) + i) >>> 1) > i; ) {
//                    addToPendingCount(1);
//                    (rights = new MapReduceKeysToLongTask<K, V>
//                            (this, batch >>>= 1, baseLimit = h, f, tab,
//                                    rights, transformer, r, reducer)).fork();
//                }
//                for (Node<K, V> p; (p = advance()) != null; )
//                    r = reducer.applyAsLong(r, transformer.applyAsLong(p.key));
//                result = r;
//                CountedCompleter<?> c;
//                for (c = firstComplete(); c != null; c = c.nextComplete()) {
//                    @SuppressWarnings("unchecked")
//                    MapReduceKeysToLongTask<K, V>
//                            t = (MapReduceKeysToLongTask<K, V>) c,
//                            s = t.rights;
//                    while (s != null) {
//                        t.result = reducer.applyAsLong(t.result, s.result);
//                        s = t.rights = s.nextRight;
//                    }
//                }
//            }
//        }
//    }
//
//    @SuppressWarnings("serial")
//    static final class MapReduceValuesToLongTask<K, V>
//            extends BulkTask<K, V, Long> {
//        final ToLongFunction<? super V> transformer;
//        final LongBinaryOperator reducer;
//        final long basis;
//        long result;
//        MapReduceValuesToLongTask<K, V> rights, nextRight;
//
//        MapReduceValuesToLongTask
//                (BulkTask<K, V, ?> p, int b, int i, int f, Node<K, V>[] t,
//                 MapReduceValuesToLongTask<K, V> nextRight,
//                 ToLongFunction<? super V> transformer,
//                 long basis,
//                 LongBinaryOperator reducer) {
//            super(p, b, i, f, t);
//            this.nextRight = nextRight;
//            this.transformer = transformer;
//            this.basis = basis;
//            this.reducer = reducer;
//        }
//
//        public final Long getRawResult() {
//            return result;
//        }
//
//        public final void compute() {
//            final ToLongFunction<? super V> transformer;
//            final LongBinaryOperator reducer;
//            if ((transformer = this.transformer) != null &&
//                    (reducer = this.reducer) != null) {
//                long r = this.basis;
//                for (int i = baseIndex, f, h; batch > 0 &&
//                        (h = ((f = baseLimit) + i) >>> 1) > i; ) {
//                    addToPendingCount(1);
//                    (rights = new MapReduceValuesToLongTask<K, V>
//                            (this, batch >>>= 1, baseLimit = h, f, tab,
//                                    rights, transformer, r, reducer)).fork();
//                }
//                for (Node<K, V> p; (p = advance()) != null; )
//                    r = reducer.applyAsLong(r, transformer.applyAsLong(p.val));
//                result = r;
//                CountedCompleter<?> c;
//                for (c = firstComplete(); c != null; c = c.nextComplete()) {
//                    @SuppressWarnings("unchecked")
//                    MapReduceValuesToLongTask<K, V>
//                            t = (MapReduceValuesToLongTask<K, V>) c,
//                            s = t.rights;
//                    while (s != null) {
//                        t.result = reducer.applyAsLong(t.result, s.result);
//                        s = t.rights = s.nextRight;
//                    }
//                }
//            }
//        }
//    }
//
//    @SuppressWarnings("serial")
//    static final class MapReduceEntriesToLongTask<K, V>
//            extends BulkTask<K, V, Long> {
//        final ToLongFunction<Map.Entry<K, V>> transformer;
//        final LongBinaryOperator reducer;
//        final long basis;
//        long result;
//        MapReduceEntriesToLongTask<K, V> rights, nextRight;
//
//        MapReduceEntriesToLongTask
//                (BulkTask<K, V, ?> p, int b, int i, int f, Node<K, V>[] t,
//                 MapReduceEntriesToLongTask<K, V> nextRight,
//                 ToLongFunction<Map.Entry<K, V>> transformer,
//                 long basis,
//                 LongBinaryOperator reducer) {
//            super(p, b, i, f, t);
//            this.nextRight = nextRight;
//            this.transformer = transformer;
//            this.basis = basis;
//            this.reducer = reducer;
//        }
//
//        public final Long getRawResult() {
//            return result;
//        }
//
//        public final void compute() {
//            final ToLongFunction<Map.Entry<K, V>> transformer;
//            final LongBinaryOperator reducer;
//            if ((transformer = this.transformer) != null &&
//                    (reducer = this.reducer) != null) {
//                long r = this.basis;
//                for (int i = baseIndex, f, h; batch > 0 &&
//                        (h = ((f = baseLimit) + i) >>> 1) > i; ) {
//                    addToPendingCount(1);
//                    (rights = new MapReduceEntriesToLongTask<K, V>
//                            (this, batch >>>= 1, baseLimit = h, f, tab,
//                                    rights, transformer, r, reducer)).fork();
//                }
//                for (Node<K, V> p; (p = advance()) != null; )
//                    r = reducer.applyAsLong(r, transformer.applyAsLong(p));
//                result = r;
//                CountedCompleter<?> c;
//                for (c = firstComplete(); c != null; c = c.nextComplete()) {
//                    @SuppressWarnings("unchecked")
//                    MapReduceEntriesToLongTask<K, V>
//                            t = (MapReduceEntriesToLongTask<K, V>) c,
//                            s = t.rights;
//                    while (s != null) {
//                        t.result = reducer.applyAsLong(t.result, s.result);
//                        s = t.rights = s.nextRight;
//                    }
//                }
//            }
//        }
//    }
//
//    @SuppressWarnings("serial")
//    static final class MapReduceMappingsToLongTask<K, V>
//            extends BulkTask<K, V, Long> {
//        final ToLongBiFunction<? super K, ? super V> transformer;
//        final LongBinaryOperator reducer;
//        final long basis;
//        long result;
//        MapReduceMappingsToLongTask<K, V> rights, nextRight;
//
//        MapReduceMappingsToLongTask
//                (BulkTask<K, V, ?> p, int b, int i, int f, Node<K, V>[] t,
//                 MapReduceMappingsToLongTask<K, V> nextRight,
//                 ToLongBiFunction<? super K, ? super V> transformer,
//                 long basis,
//                 LongBinaryOperator reducer) {
//            super(p, b, i, f, t);
//            this.nextRight = nextRight;
//            this.transformer = transformer;
//            this.basis = basis;
//            this.reducer = reducer;
//        }
//
//        public final Long getRawResult() {
//            return result;
//        }
//
//        public final void compute() {
//            final ToLongBiFunction<? super K, ? super V> transformer;
//            final LongBinaryOperator reducer;
//            if ((transformer = this.transformer) != null &&
//                    (reducer = this.reducer) != null) {
//                long r = this.basis;
//                for (int i = baseIndex, f, h; batch > 0 &&
//                        (h = ((f = baseLimit) + i) >>> 1) > i; ) {
//                    addToPendingCount(1);
//                    (rights = new MapReduceMappingsToLongTask<K, V>
//                            (this, batch >>>= 1, baseLimit = h, f, tab,
//                                    rights, transformer, r, reducer)).fork();
//                }
//                for (Node<K, V> p; (p = advance()) != null; )
//                    r = reducer.applyAsLong(r, transformer.applyAsLong(p.key, p.val));
//                result = r;
//                CountedCompleter<?> c;
//                for (c = firstComplete(); c != null; c = c.nextComplete()) {
//                    @SuppressWarnings("unchecked")
//                    MapReduceMappingsToLongTask<K, V>
//                            t = (MapReduceMappingsToLongTask<K, V>) c,
//                            s = t.rights;
//                    while (s != null) {
//                        t.result = reducer.applyAsLong(t.result, s.result);
//                        s = t.rights = s.nextRight;
//                    }
//                }
//            }
//        }
//    }
//
//    @SuppressWarnings("serial")
//    static final class MapReduceKeysToIntTask<K, V>
//            extends BulkTask<K, V, Integer> {
//        final ToIntFunction<? super K> transformer;
//        final IntBinaryOperator reducer;
//        final int basis;
//        int result;
//        MapReduceKeysToIntTask<K, V> rights, nextRight;
//
//        MapReduceKeysToIntTask
//                (BulkTask<K, V, ?> p, int b, int i, int f, Node<K, V>[] t,
//                 MapReduceKeysToIntTask<K, V> nextRight,
//                 ToIntFunction<? super K> transformer,
//                 int basis,
//                 IntBinaryOperator reducer) {
//            super(p, b, i, f, t);
//            this.nextRight = nextRight;
//            this.transformer = transformer;
//            this.basis = basis;
//            this.reducer = reducer;
//        }
//
//        public final Integer getRawResult() {
//            return result;
//        }
//
//        public final void compute() {
//            final ToIntFunction<? super K> transformer;
//            final IntBinaryOperator reducer;
//            if ((transformer = this.transformer) != null &&
//                    (reducer = this.reducer) != null) {
//                int r = this.basis;
//                for (int i = baseIndex, f, h; batch > 0 &&
//                        (h = ((f = baseLimit) + i) >>> 1) > i; ) {
//                    addToPendingCount(1);
//                    (rights = new MapReduceKeysToIntTask<K, V>
//                            (this, batch >>>= 1, baseLimit = h, f, tab,
//                                    rights, transformer, r, reducer)).fork();
//                }
//                for (Node<K, V> p; (p = advance()) != null; )
//                    r = reducer.applyAsInt(r, transformer.applyAsInt(p.key));
//                result = r;
//                CountedCompleter<?> c;
//                for (c = firstComplete(); c != null; c = c.nextComplete()) {
//                    @SuppressWarnings("unchecked")
//                    MapReduceKeysToIntTask<K, V>
//                            t = (MapReduceKeysToIntTask<K, V>) c,
//                            s = t.rights;
//                    while (s != null) {
//                        t.result = reducer.applyAsInt(t.result, s.result);
//                        s = t.rights = s.nextRight;
//                    }
//                }
//            }
//        }
//    }
//
//    @SuppressWarnings("serial")
//    static final class MapReduceValuesToIntTask<K, V>
//            extends BulkTask<K, V, Integer> {
//        final ToIntFunction<? super V> transformer;
//        final IntBinaryOperator reducer;
//        final int basis;
//        int result;
//        MapReduceValuesToIntTask<K, V> rights, nextRight;
//
//        MapReduceValuesToIntTask
//                (BulkTask<K, V, ?> p, int b, int i, int f, Node<K, V>[] t,
//                 MapReduceValuesToIntTask<K, V> nextRight,
//                 ToIntFunction<? super V> transformer,
//                 int basis,
//                 IntBinaryOperator reducer) {
//            super(p, b, i, f, t);
//            this.nextRight = nextRight;
//            this.transformer = transformer;
//            this.basis = basis;
//            this.reducer = reducer;
//        }
//
//        public final Integer getRawResult() {
//            return result;
//        }
//
//        public final void compute() {
//            final ToIntFunction<? super V> transformer;
//            final IntBinaryOperator reducer;
//            if ((transformer = this.transformer) != null &&
//                    (reducer = this.reducer) != null) {
//                int r = this.basis;
//                for (int i = baseIndex, f, h; batch > 0 &&
//                        (h = ((f = baseLimit) + i) >>> 1) > i; ) {
//                    addToPendingCount(1);
//                    (rights = new MapReduceValuesToIntTask<K, V>
//                            (this, batch >>>= 1, baseLimit = h, f, tab,
//                                    rights, transformer, r, reducer)).fork();
//                }
//                for (Node<K, V> p; (p = advance()) != null; )
//                    r = reducer.applyAsInt(r, transformer.applyAsInt(p.val));
//                result = r;
//                CountedCompleter<?> c;
//                for (c = firstComplete(); c != null; c = c.nextComplete()) {
//                    @SuppressWarnings("unchecked")
//                    MapReduceValuesToIntTask<K, V>
//                            t = (MapReduceValuesToIntTask<K, V>) c,
//                            s = t.rights;
//                    while (s != null) {
//                        t.result = reducer.applyAsInt(t.result, s.result);
//                        s = t.rights = s.nextRight;
//                    }
//                }
//            }
//        }
//    }
//
//    @SuppressWarnings("serial")
//    static final class MapReduceEntriesToIntTask<K, V>
//            extends BulkTask<K, V, Integer> {
//        final ToIntFunction<Map.Entry<K, V>> transformer;
//        final IntBinaryOperator reducer;
//        final int basis;
//        int result;
//        MapReduceEntriesToIntTask<K, V> rights, nextRight;
//
//        MapReduceEntriesToIntTask
//                (BulkTask<K, V, ?> p, int b, int i, int f, Node<K, V>[] t,
//                 MapReduceEntriesToIntTask<K, V> nextRight,
//                 ToIntFunction<Map.Entry<K, V>> transformer,
//                 int basis,
//                 IntBinaryOperator reducer) {
//            super(p, b, i, f, t);
//            this.nextRight = nextRight;
//            this.transformer = transformer;
//            this.basis = basis;
//            this.reducer = reducer;
//        }
//
//        public final Integer getRawResult() {
//            return result;
//        }
//
//        public final void compute() {
//            final ToIntFunction<Map.Entry<K, V>> transformer;
//            final IntBinaryOperator reducer;
//            if ((transformer = this.transformer) != null &&
//                    (reducer = this.reducer) != null) {
//                int r = this.basis;
//                for (int i = baseIndex, f, h; batch > 0 &&
//                        (h = ((f = baseLimit) + i) >>> 1) > i; ) {
//                    addToPendingCount(1);
//                    (rights = new MapReduceEntriesToIntTask<K, V>
//                            (this, batch >>>= 1, baseLimit = h, f, tab,
//                                    rights, transformer, r, reducer)).fork();
//                }
//                for (Node<K, V> p; (p = advance()) != null; )
//                    r = reducer.applyAsInt(r, transformer.applyAsInt(p));
//                result = r;
//                CountedCompleter<?> c;
//                for (c = firstComplete(); c != null; c = c.nextComplete()) {
//                    @SuppressWarnings("unchecked")
//                    MapReduceEntriesToIntTask<K, V>
//                            t = (MapReduceEntriesToIntTask<K, V>) c,
//                            s = t.rights;
//                    while (s != null) {
//                        t.result = reducer.applyAsInt(t.result, s.result);
//                        s = t.rights = s.nextRight;
//                    }
//                }
//            }
//        }
//    }
//
//    @SuppressWarnings("serial")
//    static final class MapReduceMappingsToIntTask<K, V>
//            extends BulkTask<K, V, Integer> {
//        final ToIntBiFunction<? super K, ? super V> transformer;
//        final IntBinaryOperator reducer;
//        final int basis;
//        int result;
//        MapReduceMappingsToIntTask<K, V> rights, nextRight;
//
//        MapReduceMappingsToIntTask
//                (BulkTask<K, V, ?> p, int b, int i, int f, Node<K, V>[] t,
//                 MapReduceMappingsToIntTask<K, V> nextRight,
//                 ToIntBiFunction<? super K, ? super V> transformer,
//                 int basis,
//                 IntBinaryOperator reducer) {
//            super(p, b, i, f, t);
//            this.nextRight = nextRight;
//            this.transformer = transformer;
//            this.basis = basis;
//            this.reducer = reducer;
//        }
//
//        public final Integer getRawResult() {
//            return result;
//        }
//
//        public final void compute() {
//            final ToIntBiFunction<? super K, ? super V> transformer;
//            final IntBinaryOperator reducer;
//            if ((transformer = this.transformer) != null &&
//                    (reducer = this.reducer) != null) {
//                int r = this.basis;
//                for (int i = baseIndex, f, h; batch > 0 &&
//                        (h = ((f = baseLimit) + i) >>> 1) > i; ) {
//                    addToPendingCount(1);
//                    (rights = new MapReduceMappingsToIntTask<K, V>
//                            (this, batch >>>= 1, baseLimit = h, f, tab,
//                                    rights, transformer, r, reducer)).fork();
//                }
//                for (Node<K, V> p; (p = advance()) != null; )
//                    r = reducer.applyAsInt(r, transformer.applyAsInt(p.key, p.val));
//                result = r;
//                CountedCompleter<?> c;
//                for (c = firstComplete(); c != null; c = c.nextComplete()) {
//                    @SuppressWarnings("unchecked")
//                    MapReduceMappingsToIntTask<K, V>
//                            t = (MapReduceMappingsToIntTask<K, V>) c,
//                            s = t.rights;
//                    while (s != null) {
//                        t.result = reducer.applyAsInt(t.result, s.result);
//                        s = t.rights = s.nextRight;
//                    }
//                }
//            }
//        }
//    }
//
//    // Unsafe mechanics
//    /*
//      使用 CAS 操作系列变量
//     */
//    private static final sun.misc.Unsafe U;
//    private static final long SIZECTL;
//    private static final long TRANSFERINDEX;
//    private static final long BASECOUNT;
//    private static final long CELLSBUSY;
//    private static final long CELLVALUE;
//    private static final long ABASE;
//    private static final int ASHIFT;
//
//    static {
//        try {
//            U = sun.misc.Unsafe.getUnsafe();
//
//            // 针对 ConcurrentHashMap 类中的属性
//            Class<?> k = ConcurrentHashMap.class;
//            SIZECTL = U.objectFieldOffset
//                    (k.getDeclaredField("sizeCtl"));
//            TRANSFERINDEX = U.objectFieldOffset
//                    (k.getDeclaredField("transferIndex"));
//            BASECOUNT = U.objectFieldOffset
//                    (k.getDeclaredField("baseCount"));
//            CELLSBUSY = U.objectFieldOffset
//                    (k.getDeclaredField("cellsBusy"));
//
//            // 针对 CounterCell 类中的属性
//            Class<?> ck = CounterCell.class;
//            CELLVALUE = U.objectFieldOffset
//                    (ck.getDeclaredField("value"));
//
//            // 针对 Node[] 数组
//            Class<?> ak = Node[].class;
//            ABASE = U.arrayBaseOffset(ak);
//            int scale = U.arrayIndexScale(ak);
//            if ((scale & (scale - 1)) != 0)
//                throw new Error("data type scale not a power of two");
//            ASHIFT = 31 - Integer.numberOfLeadingZeros(scale);
//        } catch (Exception e) {
//            throw new Error(e);
//        }
//    }
//}
