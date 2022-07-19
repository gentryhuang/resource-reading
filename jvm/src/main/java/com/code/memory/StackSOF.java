package com.code.memory;

/**
 * StackSOF
 * <p>
 * desc：-Xss128k
 */
public class StackSOF {


    public void stackLeak() {
        stackLeak();
    }



    public static void main(String[] args) {
        StackSOF stackSOF = new StackSOF();
        try {
            stackSOF.stackLeak();
        }catch (Throwable e){
            throw e;
        }
    }










}
