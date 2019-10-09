package com.code.dubbo.spi.consumer;

import com.code.dubbo.spi.provider.service.DemoService;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Consumer
 *
 * @author shunhua
 * @date 2019-10-09
 */
public class Consumer {

    public static void main(String[] args) {
        ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("consumer.xml");
        context.start();
        // 获取远程服务代理
        DemoService demoService = (DemoService) context.getBean("demoService");
        // 执行远程方法
        String hello = demoService.sayHello("world");
        // 显示调用结果
        System.out.println(hello);
    }
}