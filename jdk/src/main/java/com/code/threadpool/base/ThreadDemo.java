package com.code.threadpool.base;

import lombok.extern.slf4j.Slf4j;

/**
 * ThreadDemo
 *
 * desc：
 */
@Slf4j
public class ThreadDemo extends Thread {
    /**
     * 线程名
     */
    private String threadName;

    public ThreadDemo(String threadName) {
        this.threadName = threadName;
    }

    /**
     * @see java.lang.Thread.State
     */
    @Override
    public void run() {
        log.info(this.threadName + " is running !");
    }

}
