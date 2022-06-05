package tester;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;

/*
 *  @project:   raft
 *  @author:    Linmo
 *  @create:    2022/5/25
 *  @e-mail:    linmo@hnu.edu.cn
 *  @school:    HNU
 */

/* Tester的发送信息类 */
public class SendTester implements Runnable{
    String s = null;
    Socket socket = null;

    public SendTester(Socket socket, String s)throws IOException{
        this.s = s;
        this.socket = socket;
    }

    public void run() {     // 线程具体执行的代码
        if(socket == null){return;}
        OutputStream output = null;
        try {
            output = socket.getOutputStream();
            output.write(s.getBytes());
            // info_p("我给"+ socket.toString()+"发送了"+s);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
