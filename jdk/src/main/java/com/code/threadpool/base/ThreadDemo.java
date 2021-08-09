package com.code.threadpool.base;

import lombok.extern.slf4j.Slf4j;

/**
 * ThreadDemo
 *
 * @author <a href="mailto:libao.huang@yunhutech.com">shunhua</a>
 * @since 2020/11/20
 * <p>
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
