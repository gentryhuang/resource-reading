package com.code.annotation;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.TypeElement;
import java.util.Set;

/**
 * MyAnnotationProcessor
 *
 * <p>
 * desc：
 */
public class MyAnnotationProcessor extends AbstractProcessor {


    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        System.out.println("MyAnnotationProcessor");
        return Boolean.TRUE;
    }

    public int test(String a){
        return 0;
    }

    public int test(){
        return 0;
    }
}
