package com.code.dubbo.spi.provider.service;


/**
 * DemoServiceImpl
 *
 * @author shunhua
 * @date 2019-10-09
 */
public class DemoServiceImpl implements DemoService {

    @Override
    public String sayHello(String name) {
        return "hello " + name;
    }
}