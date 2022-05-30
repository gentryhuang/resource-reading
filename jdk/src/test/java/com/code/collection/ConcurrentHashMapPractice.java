package com.code.collection;

import java.util.concurrent.ConcurrentHashMap;

/**
 * ConcurrentHashMapPractice
 *
 * desc：
 */
public class ConcurrentHashMapPractice {
    public static void main(String[] args) {

        // 1 创建
        ConcurrentHashMap<String,Object> concurrentHashMap = new ConcurrentHashMap<>();

        // 2 新增数据
        Object put = concurrentHashMap.put("name", "hlb");
        System.out.println(put);
        Object put1 = concurrentHashMap.put("name", "shunhua");
        System.out.println(put1);


        // 3 获取数据
        Object name = concurrentHashMap.get("name");
        System.out.println(name);

        // 4 移除数据
        Object name1 = concurrentHashMap.remove("name");
        System.out.println(name1);


        System.out.println(concurrentHashMap);

        System.out.println((Integer.toBinaryString(((resizeStamp(16) << 16)+2))));






        System.out.println((resizeStamp(16)<<16)>>>16);

    }

    static int resizeStamp(int n) {
         int RESIZE_STAMP_BITS = 16;
        return Integer.numberOfLeadingZeros(n) | (1 << (RESIZE_STAMP_BITS - 1));
    }
}
