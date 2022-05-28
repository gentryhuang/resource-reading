package com.code.concurrent.condition;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * BoundedBuffer
 *
 * @author <a href="mailto:libao.huang@yunhutech.com">shunhua</a>
 * @since 2020/12/27
 * <p>
 * desc：
 */
class BoundedBuffer {
    /**
     * 锁
     */
    final Lock lock = new ReentrantLock();
    /**
     * notFull Condition
     */
    final Condition notFull = lock.newCondition();
    /**
     * notEmpty Condition
     */
    final Condition notEmpty = lock.newCondition();
    /**
     * 数组，大小为 100
     */
    final Object[] items = new Object[100];

    /**
     * 分别为添加的下标、移除的下标和数组当前数量
     */
    int putptr, takeptr, count;

    /**
     * 生产
     * 如果数组满了，则添加线程进入等待状态，直到有空位才能生产
     *
     * @param x item
     * @throws InterruptedException
     */
    public void put(Object x) throws InterruptedException {
        lock.lock();
        try {
            // 元素数量等于数组长度，线程等待
            while (count == items.length)
                notFull.await();

            // 添加元素
            items[putptr] = x;
            // 添加下标 putptr 递增，和移除的下标 takeptr 对应。
            if (++putptr == items.length) putptr = 0;
            // 数组元素个数递增
            ++count;

            // 生产后通知消费
            notEmpty.signal();
        } finally {
            lock.unlock();
        }
    }

    /**
     * 消费
     * 如果数组为空，则消费线程进入等待状态，直到数组中有元素才能继续消费
     *
     * @return item
     * @throws InterruptedException
     */
    public Object take() throws InterruptedException {
        lock.lock();
        try {
            // 数组为空，线程等待
            while (count == 0)
                notEmpty.await();

            // 取出元素
            Object x = items[takeptr];
            // 移除下标递增
            if (++takeptr == items.length) takeptr = 0;
            // 数组元素个数递减
            --count;

            // 消费后通知生产
            notFull.signal();
            return x;
        } finally {
            lock.unlock();
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {

        ExecutorService executorService = Executors.newCachedThreadPool();
        BoundedBuffer boundedBuffer = new BoundedBuffer();

        for (int i = 0; i < 1000; i++) {
            Object obj = i;
            Thread.sleep(500);
            executorService.execute(() -> {
                try {
                    System.out.println("put item: " + obj);
                    boundedBuffer.put(obj);
                } catch (InterruptedException exception) {
                    exception.printStackTrace();
                }
            });

            executorService.execute(() -> {
                try {
                    System.out.println("take item: " + boundedBuffer.take());
                } catch (InterruptedException exception) {
                    exception.printStackTrace();
                }
            });
        }


        System.in.read();


    }
}
