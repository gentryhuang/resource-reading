package com.code.loader;

import java.lang.reflect.Method;

/**
 * Client
 * desc：
 */
public class Client {
    public static void main(String[] args) throws Exception {
        // UserData 类所在的目录
        MyClassLoader classLoader = new MyClassLoader("/Users/huanglibao/code/resource-reading/tempFile");

        // 加载 UserData 类
        // 最终通过 findClass 方法加载
        Class<?> aClass = classLoader.loadClass("com.code.loader.UserData1");

        // 反射创建 UserData 对象
        Object o = aClass.newInstance();

        // 获取 UserData 中的 print 方法并调用
        Method print = aClass.getDeclaredMethod("print", null);
        print.invoke(o, null);

        // 打印加载 UserData 类的类加载器
        System.out.println(aClass.getClassLoader().getClass().getName());
    }
}
