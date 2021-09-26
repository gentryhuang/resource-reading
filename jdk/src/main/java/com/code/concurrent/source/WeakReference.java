//import java.lang.ref.Reference;
//import java.lang.ref.ReferenceQueue;
//
//public class WeakReference<T> extends Reference<T> {
//
//    /**
//     * 创建一个指向给定对象的弱引用
//     *
//     * @param referent 给定对象的引用
//     */
//    public WeakReference(T referent) {
//        super(referent);
//    }
//
//    /**
//     * 创建一个弱引用，该引用指向对象注册到给定的队列中。
//     *
//     * @param referent 给定对象的引用
//     * @param q 对象被回收后会把弱引用对象，也就是WeakReference对象或者其子类的对象，放入队列ReferenceQueue中，注意不是被弱引用的对象，被弱引用的对象已经被回收了。
//     */
//    public WeakReference(T referent, ReferenceQueue<? super T> q) {
//        super(referent, q);
//    }
//
//}