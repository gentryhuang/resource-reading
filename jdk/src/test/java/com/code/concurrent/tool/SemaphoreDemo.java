package com.code.concurrent.tool;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

/**
 * SemaphoreDemo
 *
 * desc：
 */
@Slf4j
public class SemaphoreDemo {

    /**
     * 固定线程数线程池
     */
    public static ExecutorService service = Executors.newFixedThreadPool(10);

    public static void main(String[] args) {

        // 有 3 个许可证书，每个加工厂公平获取。
        Semaphore semaphore = new Semaphore(3, true);

        // 有 6 个加工厂想要获取
        for (int i = 0; i < 6; i++) {
            service.submit(() -> {
                try {
                    // 获取许可证
                    semaphore.acquire(1);
                    log.info("拿到了许可证");

                    // 处理任务
                    log.warn("凭借许可证处理任务...");
                    Thread.sleep((long) (Math.random() * 10000));

                } catch (InterruptedException e) {
                    e.printStackTrace();

                } finally {
                    log.info("归还许可证");
                    semaphore.release(1);
                }
            });
        }

    }

}
