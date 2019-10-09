package com.code.dubbo.spi.provider.service;

/**
 * DemoService dubbo要暴露的接口
 *
 *
 * @author shunhua
 * @date 2019-10-09
 */
public interface DemoService {

    /**
     * 远程调用方法
     * @param name
     * @return
     */
    String sayHello(String name);
}
