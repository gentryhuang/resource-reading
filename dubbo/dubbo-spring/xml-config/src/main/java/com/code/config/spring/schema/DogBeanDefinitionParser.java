package com.code.config.spring.schema;

import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.AbstractSingleBeanDefinitionParser;
import org.springframework.util.StringUtils;
import org.w3c.dom.Element;

/**
 * DogBeanDefinitionParser
 *
 * @author shunhua
 * @date 2020-03-24
 */
public class DogBeanDefinitionParser extends AbstractSingleBeanDefinitionParser {

    @Override
    protected  Class getBeanClass(Element element){
       return Dog.class;

    }

    @Override
    protected void doParse(Element element, BeanDefinitionBuilder builder) {
        // 获取标签属性值
        String name = element.getAttribute("name");
        String food = element.getAttribute("food");

        // 加入到Bean定义的builder
        if (StringUtils.hasText(name)){
            builder.addPropertyValue("name",name);
        }
        if(StringUtils.hasText(food)){
            builder.addPropertyValue("food",food);
        }
    }
}