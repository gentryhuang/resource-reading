package com.code.juc.tool;


import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
public class CountDownLatchDemo {

    /**
     * 固定线程数线程池
     */
    public static ExecutorService service = Executors.newFixedThreadPool(5);

    /**
     * 产品质量检测
     *
     * @param args
     * @throws InterruptedException
     */
    public static void main(String[] args) throws InterruptedException {

        // 需要3个工人进行检测，就用3来初始化一个 CountDownLatch
        CountDownLatch latch = new CountDownLatch(3);

        for (int i = 1; i <= 3; i++) {
            final int no = i;
            service.submit(() -> {
                        try {
                            // 检测
                            Thread.sleep((long) (Math.random() * 10000));
                            log.info("No." + no + " 完成检测。");
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        } finally {
                            // 调用 countDown() 代表完成。这里指某个员工完成检测任务
                            latch.countDown();
                        }
                    }
            );
        }

        log.info("产品质量检测中.....");
        // 调用await() 代表线程阻塞等待其它线程完成，即同步状态 state 减为 0。这里指产品等待检测完成
        latch.await();

        log.info("产品质量检测完毕，进入下一个环节。");
    }
}