package test;

import lombok.SneakyThrows;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;

import static test.Tester.if_receive;
import static test.Tester.receive_s;

/*
 *  @project:   raft
 *  @author:    Linmo
 *  @create:    2022/5/25
 *  @e-mail:    linmo@hnu.edu.cn
 *  @school:    HNU
 */

/* Tester的接收信息类 */
public class ReceiveTester implements Runnable {
    Socket socket = null;

    public ReceiveTester(Socket socket) throws IOException {
        this.socket = socket;
    }

    @SneakyThrows
    public void run(){     /* 线程具体执行的代码 */
        InputStream input = null;
        try {
            while(!if_receive){
                input = socket.getInputStream();
                byte[] data = new byte[128];
                int len = input.read(data);
                if(len<=0){return;}
                if_receive = true;  // 释放锁
                String s = new String(data, 0, len);
                receive_s = s;
                // info_p("我收到了"+s);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            // 释放资源
            try {
                if(input!=null) input.close();
                if(socket!=null) socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
