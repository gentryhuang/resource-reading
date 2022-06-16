package com.code.aqs;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;

/**
 * AQSTest
 * desc：
 */
public class AQSTest {

    static ExecutorService threadPoolExecutor = Executors.newFixedThreadPool(100);
    static ReentrantLock reentrantLock = new ReentrantLock();
    static Semaphore semaphore = new Semaphore(2);


    private static void testShare() {
        threadPoolExecutor.execute(() -> {
            try {
                semaphore.acquire();
                System.out.println(Thread.currentThread().getName() + " 获取到许可证...");
                semaphore.release();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        threadPoolExecutor.execute(() -> {
            try {
                semaphore.acquire();
                System.out.println(Thread.currentThread().getName() + " 获取到许可证...");
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });


        threadPoolExecutor.execute(() -> {
            try {
                semaphore.acquire();
                System.out.println(Thread.currentThread().getName() + " 获取到许可证...");
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });


        threadPoolExecutor.execute(() -> {
            try {
                semaphore.acquire();
                System.out.println(Thread.currentThread().getName() + " 获取到许可证...");
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });


        threadPoolExecutor.execute(() -> {
            try {
                semaphore.acquire();
                System.out.println(Thread.currentThread().getName() + " 获取到许可证...");
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

    }


    private static void testExclusive() {
        threadPoolExecutor.execute(() -> {
            reentrantLock.lock();
            System.out.println(Thread.currentThread().getName() + " 获取到锁了...");
            reentrantLock.unlock();
        });

        threadPoolExecutor.execute(() -> {
            reentrantLock.lock();
            System.out.println(Thread.currentThread().getName() + " 获取到锁了...");
        });


        threadPoolExecutor.execute(() -> {
            reentrantLock.lock();
            System.out.println(Thread.currentThread().getName() + " 获取到锁了...");
        });

        threadPoolExecutor.execute(() -> {
            reentrantLock.lock();
            System.out.println(Thread.currentThread().getName() + " 获取到锁了...");
        });

        threadPoolExecutor.execute(() -> {
            reentrantLock.lock();
            System.out.println(Thread.currentThread().getName() + " 获取到锁了...");
        });
    }

    public static void main(String[] args) throws IOException {

        // 1 测试独占模式
        testExclusive();

        // 2 测试共享模式
        testShare();

        System.in.read();

    }


}
