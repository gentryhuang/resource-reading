package com.code.concurrent;

import lombok.extern.slf4j.Slf4j;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;

/**
 * com.code.mixture.Client
 *
 * @author <a href="mailto:libao.huang@yunhutech.com">shunhua</a>
 * @since 2021/08/26
 * <p>
 * desc：
 */
@Slf4j
public class Client {
    public static void main(String[] args) throws InterruptedException {

        // 强引用
        Fruit fruit = new Fruit("小芒果");

        // 创建弱引用对象及引用队列
        ReferenceQueue<Fruit> referenceQueue = new ReferenceQueue<>();
        Animal animal = new Animal(fruit, referenceQueue);

        // 通过 WeakReference.get() 方法获取对应的对象
        log.info("Fruit 对象信息: " + animal.get());

        // GC 前
        log.info("GC 前");
        if (referenceQueue.poll() == null) {
            log.info("没有回收被弱引用的对象，不会加入队列中");
        }

        // 记录弱引用对象地址，用于回收前后对比
        log.info("弱引用对象地址："+ animal.toString());
        // 消除强引用，确保只有弱引用（不消除强引用，不会回收）
        fruit = null;

        // 触发垃圾回收
        log.info("GC 中");
        System.gc();
        // 保证 GC 的发生
        Thread.sleep(5000);

        // GC 后
        log.info("GC 后");
        // 小芒果被回收 - 因此只有弱引用
        log.info(animal.get() == null ? " Fruit is Cleared" : animal.get().toString());

        Reference reference = null;
        if ((reference = referenceQueue.poll()) != null) {
            log.info("回收被弱引用的对象，弱引用对象加入队列中，地址为：" + reference.toString());
        }
    }

    /**
     * 继承 WeakReference ，持有 Fruit 的弱引用。
     * 当垃圾回收时，回收的是弱引用 referent 指向的对象，而非 Animal
     */
    static class Animal extends WeakReference<Fruit> {
        public Animal(Fruit referent, ReferenceQueue referenceQueue) {
            super(referent, referenceQueue);
        }
    }
}


