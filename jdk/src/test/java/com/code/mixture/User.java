package com.code.mixture;

/**
 * com.code.mixture.User
 * <p>
 * desc：
 */
public class User {
    String name;

    public static int a = 1;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public static int getA() {
        return a;
    }

    public static void setA(int a) {
        User.a = a;
    }
}
