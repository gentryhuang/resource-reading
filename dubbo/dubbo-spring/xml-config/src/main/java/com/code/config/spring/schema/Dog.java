package com.code.config.spring.schema;

import lombok.Data;

import java.io.Serializable;

/**
 * Dog
 *
 * @author shunhua
 * @date 2020-03-24
 */
@Data
public class Dog implements Serializable {

    private static final long serialVersionUID = -1676813073747273445L;
    /**
     * 小狗崽名称
     */
    private String name;
    /**
     * 喜欢吃的食物
     */
    private String food;
}