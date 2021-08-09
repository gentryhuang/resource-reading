package com.code.threadpool.tool;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.*;


/**
 * ThreadPoolDemo
 *
 * @author <a href="mailto:libao.huang@yunhutech.com">shunhua</a>
 * @since 2020/11/28
 * <p>
 * desc：
 */
@Slf4j
public class ThreadPoolDemo {

    /**
     * 门栓
     */
    private static final CountDownLatch COUNT_DOWN_LATCH = new CountDownLatch(3);
    /**
     * 启动不同线程池执行任务
     */
    private final static ExecutorService WORKER = Executors.newCachedThreadPool();

    /**
     * 任务数量: 5，20，50，100，1000，100000
     */
    private final static int COUNT = 3005;
    /**
     * 休眠时间: 1，100，1000，3000
     */
    private final static long MILLIS = 1000L;


    private static final ExecutorService SINGLE_THREAD_EXECUTOR = Executors.newSingleThreadExecutor();

    private static final ExecutorService FIXED_THREAD_POOL = Executors.newFixedThreadPool(10);

    private static final ExecutorService CACHED_THREAD_POOL = Executors.newCachedThreadPool();

    public static ExecutorService newFixedThreadPool(int nThreads) {
        return new ThreadPoolExecutor(nThreads, nThreads * 2,
                0, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(10));
    }

    /**
     * 任务体
     */
    static class Task implements Runnable {

        /**
         * poolName
         */
        private final String name;
        /**
         * num
         */
        private final Integer num;

        public Task(String name, Integer num) {
            this.name = name;
            this.num = num;
        }

        @SneakyThrows
        @Override
        public void run() {
            // 执行业务逻辑
            System.out.println(name + " - " + Thread.currentThread().getName() + " - 任务" + num);
            Thread.sleep(MILLIS);
        }
    }


    /**
     * 执行任务
     *
     * @param executorService
     */
    private static void work(ExecutorService executorService, String threadPoolName) {
        WORKER.execute(() -> {
            try {
                COUNT_DOWN_LATCH.await();
            } catch (InterruptedException exception) {
                exception.printStackTrace();
            }

            for (int i = 1; i <= COUNT; i++) {
                executorService.execute(new Task(threadPoolName, i));
            }
            executorService.shutdown();
        });
    }


    /**
     * 一起测试
     *
     * @throws InterruptedException
     */
    public static void testAll() throws InterruptedException {
        // singleThreadExecutor
        work(SINGLE_THREAD_EXECUTOR, "singleThreadExecutor");
        Thread.sleep(1000);
        COUNT_DOWN_LATCH.countDown();

        // fixedThreadPool
        work(FIXED_THREAD_POOL, "fixedThreadPool");
        Thread.sleep(1000);
        COUNT_DOWN_LATCH.countDown();

        // cachedThreadPool
        work(CACHED_THREAD_POOL, "cachedThreadPool");
        Thread.sleep(1000);
        COUNT_DOWN_LATCH.countDown();
        System.out.println("wait 醒来，启动线程池执行任务！");

    }

    /**
     * 测试 singleThreadExecutor
     */
    public static void testSingleThreadExecutor() {
        for (int i = 1; i <= COUNT; i++) {
            SINGLE_THREAD_EXECUTOR.execute(new Task("singleThreadExecutor", i));
        }
        SINGLE_THREAD_EXECUTOR.shutdown();
    }

    /**
     * 测试 fixedThreadPool
     */
    public static void testFixThreadExecutor() {
        for (int i = 1; i <= COUNT; i++) {
            FIXED_THREAD_POOL.execute(new Task("fixedThreadPool", i));
        }
        FIXED_THREAD_POOL.shutdown();
    }

    /**
     * 测试 cachedThreadPool
     */
    public static void testCacheThreadExecutor() {
        for (int i = 1; i <= COUNT; i++) {
            CACHED_THREAD_POOL.execute(new Task("cachedThreadPool", i));
        }
        CACHED_THREAD_POOL.shutdown();
    }

    /**
     * 测试自定义线程池
     */
    public static void testDefineThreadPool() {
        ExecutorService executorService = newFixedThreadPool(10);
        for (int i = 1; i <= 100; i++) {
            executorService.execute(new Task("自定义", i));
        }
        executorService.shutdown();

    }

    /**
     * 测试
     *
     * @param num 序号
     * @throws InterruptedException
     */
    public static void test(int num) throws InterruptedException {
        switch (num) {
            case 1:
                // 1. 测试all
                testAll();
                break;
            case 2:
                // 2. 测试 cachedThreadPool
                testCacheThreadExecutor();
                break;
            case 3:
                // 3. 测试 fixedThreadPool
                testFixThreadExecutor();
                break;
            case 4:
                // 4. 测试 singleThreadExecutor
                testSingleThreadExecutor();
                break;
            case 5:
                // 5. 测试自定义线程池
                testDefineThreadPool();
                break;
            default:
                break;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        // 1. 测试all
        // 2. 测试 cachedThreadPool
        // 3. 测试 fixedThreadPool
        // 4. 测试 singleThreadExecutor
        // 5. 测试自定义线程池

        test(5);

    }


}


