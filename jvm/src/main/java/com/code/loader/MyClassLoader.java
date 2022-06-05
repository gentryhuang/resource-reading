package com.code.loader;

import java.io.FileInputStream;

/**
 * MyClassLoader
 * desc：
 */
public class MyClassLoader extends ClassLoader {

    /**
     * 类所在的全路径
     */
    private String classPath;

    public MyClassLoader(String classPath) {
        this.classPath = classPath;
    }

    /**
     * 打破双亲委派机制的关键，改变加载流程。-- 重写 loadClass 方法
     *
     * @param name
     * @return
     * @throws ClassNotFoundException
     */
    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        return super.loadClass(name);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        try {

            // 读取类的字节流
            byte[] data = loadByte(name);
            // 将字节数组转为 Class 对象，这个字节数组是 class 文件读取后最终的字节数组
            return defineClass(name, data, 0, data.length);

        } catch (Exception ex) {
            ex.printStackTrace();
            throw new ClassNotFoundException();
        }
    }


    private byte[] loadByte(String className) throws Exception {
        className = className.replaceAll("\\.", "/");

        // 读取磁盘文件中的 UserData.class 文件
        FileInputStream fileInputStream = new FileInputStream(classPath + "/" + className + ".class");
        int len = fileInputStream.available();
        byte[] data = new byte[len];
        fileInputStream.close();

        // 返回字节流
        return data;
    }
}
