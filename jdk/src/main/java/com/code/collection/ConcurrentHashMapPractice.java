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

    }
}
