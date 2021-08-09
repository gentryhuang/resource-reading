package com.code.annotation.compile;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import java.util.Set;

/**
 * PrintProcessor
 *
 * @author <a href="mailto:libao.huang@yunhutech.com">shunhua</a>
 * @since 2020/12/31
 * <p>
 * desc：
 */
@SupportedAnnotationTypes({"com.code.annotation.compile.PrintLog"}) // 声明注解全名
@SupportedSourceVersion(SourceVersion.RELEASE_8) // JDK 版本
public class PrintProcessor extends AbstractProcessor {

    /**
     * @param annotations 所有打了注解的类
     * @param roundEnv    环境
     * @return
     */
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {

        /**
         * 编译器逻辑处理：
         * 1. 操作代码，生成类、方法 ，如 lombok,hibernate
         */
        System.out.println("annotation size: " + annotations.size());

        Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(PrintLog.class);
        elements.forEach(currentElement -> {
            PrintLog annotation = currentElement.getAnnotation(PrintLog.class);
            if (annotation.content().equals("error")) {
                throw new RuntimeException("define annotation error message !");
            }
        });


        return Boolean.TRUE;
    }
}
