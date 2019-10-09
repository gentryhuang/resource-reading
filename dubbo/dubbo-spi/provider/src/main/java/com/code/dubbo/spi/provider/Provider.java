package com.code.dubbo.spi.provider;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.io.IOException;

/**
 * Provider
 *
 * @author shunhua
 * @date 2019-10-09
 */
public class Provider {

    public static void main(String[] args) throws IOException {

        // Spring容器加载dubbo
        ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("provider.xml");
        context.start();

        // 按任意键退出
        System.in.read();


    }

}