
import org.junit.Test;

import java.io.IOException;

/**
 * Client
 *
 * @author <a href="mailto:libao.huang@yunhutech.com">shunhua</a>
 * @since 2020/10/10
 * <p>
 * desc：
 */
public class Client {

    private User user;

    private User temp;

    @Test
    public void test() throws IOException {

        temp = user = new User();
        temp.setName("hello");
        System.out.println("user-old:" + System.identityHashCode(user));
        System.out.println("temp-old:" + System.identityHashCode(temp));
        System.out.println("temp" + temp);
        System.out.println("user" + user);


        try {
            Thread.sleep(3000);
        } catch (Exception ex) {

        }


        user = new User();
        user.setName("hi");

        System.out.println("user-new:" + System.identityHashCode(user));
        System.out.println("temp-new:" + System.identityHashCode(temp));
        System.out.println("temp" + temp);
        System.out.println("user" + user);


        new Thread(() -> {
            while (true) {

                User user1 = new User();
                User user2 = user1;
                user1 = new User();
                System.out.println(user1 == user2);
            }
        }).start();






        while (true) {

            User user1 = new User();
            User user2 = user1;
            user1 = new User();
            System.out.println(user1 == user2);
        }


    }
}
