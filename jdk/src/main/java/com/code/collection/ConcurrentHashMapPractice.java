package com.code.collection;

import java.util.concurrent.ConcurrentHashMap;

/**
 * ConcurrentHashMapPractice
 *
 * @author <a href="mailto:libao.huang@yunhutech.com">shunhua</a>
 * @since 2021/07/16
 * <p>
 * desc：
 */
public class ConcurrentHashMapPractice {
    public static void main(String[] args) {
        ConcurrentHashMap<String,Object> concurrentHashMap = new ConcurrentHashMap<>();
        concurrentHashMap.put("name","hlb");

        System.out.println(concurrentHashMap);

    }
}
