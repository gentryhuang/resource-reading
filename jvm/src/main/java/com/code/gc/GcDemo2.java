package com.code.gc;

import java.util.ArrayList;
import java.util.List;

/**
 * GcDemo2
 * desc：
 * JVM 设置：‐Xms10M ‐Xmx10M ‐XX:+PrintGCDetails ‐XX:+HeapDumpOnOutOfMemoryError ‐XX:HeapDumpPath=/Users/jvm.dump
 */
public class GcDemo2 {

    public static void main(String[] args) {
        List<Object> list = new ArrayList<>();

        while (true){
            list.add(new Object());
        }

    }

}
