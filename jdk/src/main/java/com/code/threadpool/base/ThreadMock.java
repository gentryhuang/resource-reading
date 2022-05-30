package com.code.threadpool.base;

import lombok.extern.slf4j.Slf4j;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ThreadMock
 *
 * desc：
 */
@Slf4j
public class ThreadMock {

    /**
     * 循环次数
     */
    private static final int COUNT = 100000;

    public static void main(String[] args) {

        // 计时开始
        long start = System.currentTimeMillis();

        AtomicInteger sum = new AtomicInteger();
        Random random = new Random(10);
        for (int i = 0; i < COUNT; i++) {
            int num = random.nextInt();
            new Thread(() -> sum.addAndGet(num)).start();
        }

        // 打印结果和消耗时间
        System.out.println("result: " + sum + " cost_time:" + (System.currentTimeMillis() - start));
    }
}
