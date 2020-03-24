package com.code.config.spring.schema;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * TestSchema
 *
 * @author shunhua
 * @date 2020-03-24
 */
public class TestSchema {

    public static void main(String[] args) {
        ApplicationContext applicationContext = new ClassPathXmlApplicationContext("application.xml");
        Dog dog = (Dog) applicationContext.getBean("td");
        System.out.println(dog);
    }

}