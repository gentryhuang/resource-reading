package com.code.loader;

import com.sun.crypto.provider.DESKeyFactory;

/**
 * ClassLoader
 * desc：
 */
public class ClassLoaderPrint {
    public static void main(String[] args) {

        // 1 启动类加载器，是使用 C++ 代码实现的，在 Java 中没有对象，因此为 null
        System.out.println(String.class.getClassLoader());

        // 2 扩容类加载器
        System.out.println(DESKeyFactory.class.getClassLoader().getClass().getName());

        // 3 应用类加载器
        System.out.println(ClassLoaderPrint.class.getClassLoader().getClass().getName());

        // 4 当前使用的类加载器
        System.out.println(java.lang.ClassLoader.getSystemClassLoader().getClass().getName());


    }
}
