package com.code.concurrent.thread;

import lombok.extern.slf4j.Slf4j;

/**
 * CustomThread
 *
 * desc：
 */
@Slf4j
public class CustomThread {

    public static void main(String[] args) {
        // 1 创建线程 Thread
        Thread thread = new Thread(()->{
            log.info(Thread.currentThread().getName() + "is running!");
        });

        // 2 启动线程
        thread.start();
    }

}
