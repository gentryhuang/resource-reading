package com.code.annotation;

import java.io.IOException;

/**
 * Client
 * <p>
 * desc：
 */
public class Client {
    public static void main(String[] args) throws IOException {
        MyAnnotation annotation = TestAnnotation.class.getAnnotation(MyAnnotation.class);
        String time = annotation.time();

        System.out.println(time);

        System.in.read();


    }
}
