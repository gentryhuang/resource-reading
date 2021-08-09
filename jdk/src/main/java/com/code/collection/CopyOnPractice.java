package com.code.collection;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * CopyonPractice
 *
 * @author <a href="mailto:libao.huang@yunhutech.com">shunhua</a>
 * @since 2021/08/04
 * <p>
 * desc：
 */
public class CopyOnPractice {
    public static void main(String[] args) {

        // CopyOnWriteArrayList
        CopyOnWriteArrayList<String> copyOnWriteArrayList = new CopyOnWriteArrayList<>();
        copyOnWriteArrayList.add("hlb");
        String result = copyOnWriteArrayList.get(0);
        System.out.println(result);


        // CopyOnWriteArraySet
        CopyOnWriteArraySet<String> copyOnWriteArraySet = new CopyOnWriteArraySet<>();




        Student a ,b;

        Student student1 = new Student();

        a = student1;

        b = a;

        System.out.println(a == b);

        Student student2 = new Student();
        a = student2;


        System.out.println(a == b);







    }
}
