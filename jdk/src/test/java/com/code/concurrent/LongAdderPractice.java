package com.code.concurrent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * LongAdderPractice
 * <p>
 * desc：
 */
public class LongAdderPractice {

    /**
     * 并发模拟
     */
    public static ExecutorService threadPoolExecutor = Executors.newFixedThreadPool(100);

    /**
     * 测试 AtomicLong 的性能
     *
     * @param times
     */
    public static void testAtomicLong(int times) {
        AtomicLong atomicLong = new AtomicLong(0);
        long starTime = System.currentTimeMillis();
        List<Future<Long>> resultFutureList = new ArrayList<>();
        for (int i = 0; i < times; i++) {
            Future<Long> submit = threadPoolExecutor.submit(atomicLong::incrementAndGet);
            resultFutureList.add(submit);
        }

        resultFutureList.forEach(longFuture -> {
            if (longFuture.isDone()) {
                return;
            }
            try {
                longFuture.get();
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        });

        System.out.println("AtomicLong cost time: " + (System.currentTimeMillis() - starTime));
    }

    /**
     * 测试 LongAdder 性能
     *
     * @param times
     */
    public static void testLongAdder(int times) {
        LongAdder longAdder = new LongAdder();
        long starTime = System.currentTimeMillis();
        List<Future<Long>> resultFutureList = new ArrayList<>();
        for (int i = 0; i < times; i++) {
            Future<Long> submit = threadPoolExecutor.submit(() -> {
                longAdder.increment();
                return 0L;
            });
            resultFutureList.add(submit);
        }

        resultFutureList.forEach(longFuture -> {
            if (longFuture.isDone()) {
                return;
            }
            try {
                longFuture.get();
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        });

        System.out.println("LongAdder cost time: " + (System.currentTimeMillis() - starTime));

    }

    public static void test(int times) {
        System.out.println("*--------------测试 " + times + " 次累加值开始！-----------------*");
        testAtomicLong(times);
        testLongAdder(times);
        System.out.println("*--------------测试 " + times + " 次累加值结束！-----------------*");
        System.out.println();
    }


    public static void main(String[] args) {
        // 1、1000 次
        test(1000);

        // 2、10000 次
        test(10000);

        // 3、100000 次
        test(100000);

        // 4、10000000 次
        test(10000000);

        // 5、100 000 000
        test(10000000);
    }
}
