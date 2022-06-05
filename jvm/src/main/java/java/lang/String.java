package java.lang;

/**
 * String
 *
 * desc：
 * 1 错误: 在类 java.lang.String 中找不到 main 方法, 请将 main 方法定义为:
 *    public static void main(String[] args)
 * 否则 JavaFX 应用程序类必须扩展javafx.application.Application
 *
 * 即 String 类不是通过应用类加载的，因此加载到的 String 中是没有 main 方法的
 *
 * 2 String 类不允许定义非 java.lang 包下
 */
public class String {
    public static void main(String[] args) {
        System.out.println("kk");
    }
}
