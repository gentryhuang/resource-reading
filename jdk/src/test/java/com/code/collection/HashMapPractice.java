package com.code.collection;

import java.util.HashMap;

/**
 * HashMapPractice
 *
 * desc：
 */
public class HashMapPractice {

    public static void main(String[] args) {
        HashMap<String, Object> hashMap = new HashMap(2, 1);

        for (int i = 0; i < 100; i++) {

            hashMap.put(i + "", "gentryhuang");

            System.out.println("size:" + hashMap.size());

            System.out.println("-------");

        }


        System.out.println(hashMap);

    }





}
