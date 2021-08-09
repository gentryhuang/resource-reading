package com.code.threadpool.base;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ThreadPoolException
 *
 * @author <a href="mailto:libao.huang@yunhutech.com">shunhua</a>
 * @since 2020/12/24
 * <p>
 * desc：
 */
public class ThreadPoolException {
    public static void main(String[] args) {

        ExecutorService executorService = Executors.newFixedThreadPool(2);

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                System.out.println("测试异常");
                throw new RuntimeException();
            }
        };

        executorService.execute(runnable);

        try{
           // int a = 1/0;
           // throw new RuntimeException();
        }finally {
            System.out.println("....");
        }

    }
}
