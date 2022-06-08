package com.code.memory;

import java.util.ArrayList;

/**
 * Heap
 * desc：
 * <p>
 * 1. 启动JDK命令 jvisualvm
 * 2. 观察堆内存情况
 */
public class Heap {

    /**
     * 确保至少 100KB
     */
    byte[] a = new byte[1024 * 100];

    public static void main(String[] args) throws InterruptedException {
        ArrayList<Heap> heapArrayList = new ArrayList<>();
        while (true) {
            heapArrayList.add(new Heap());
            Thread.sleep(50);
        }
    }
}
