package com.code.tuning;

import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Demo_1
 * <p>
 * desc：
 */
public class Demo_1 {
    public static void main(String[] args) {

        Executors.newScheduledThreadPool(1).scheduleAtFixedRate(() -> {
            new Thread(() -> {
                for (int i = 0; i < 150; i++) {
                    try {

                        // 创建 512K 的小对象
                        byte[] bytes = new byte[1024 * 512];

                        // 随机休眠 1s 内
                        // todo 模拟吞吐量优先的
                        Thread.sleep(new Random().nextInt(1000));
                    } catch (Exception exception) {
                        exception.printStackTrace();
                    }
                }

            }).start();

        }, 100, 100, TimeUnit.MILLISECONDS);


    }
}
