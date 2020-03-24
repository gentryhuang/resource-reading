package com.code.config.spring.schema;

import org.springframework.beans.factory.xml.NamespaceHandlerSupport;

/**
 * NamespaceHandler
 *
 * @author shunhua
 * @date 2020-03-24
 */
public class NamespaceHandler extends NamespaceHandlerSupport {

    /**
     * 将目标组件注册到Spring容器中
     */
    @Override
    public void init() {
        registerBeanDefinitionParser("dog",new DogBeanDefinitionParser());
    }
}