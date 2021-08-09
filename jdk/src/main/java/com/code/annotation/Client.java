package com.code.annotation;

import java.io.IOException;

/**
 * Client
 *
 * @author <a href="mailto:libao.huang@yunhutech.com">shunhua</a>
 * @since 2020/12/31
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
