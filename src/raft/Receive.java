package raft;

import lib.Global;
import lombok.SneakyThrows;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;

import static lib.Global.info_p;

/*
 *  @project:   raft
 *  @author:    Linmo
 *  @create:    2022/5/25
 *  @e-mail:    linmo@hnu.edu.cn
 *  @school:    HNU
 */

/* 接收信息类 */
public class Receive implements Runnable {
    /****************** 属性变量 ******************/
    Socket socket = null;
    String s = null;

    public Receive(Socket socket) throws IOException {
        this.socket = socket;
    }

    @SneakyThrows
    public void run(){     // 线程具体执行的代码
        InputStream input = null;
        try {
            input = socket.getInputStream();
            byte[] data = new byte[128];
            int len = input.read(data);
            if(len<=0){return;}
            s = new String(data, 0, len);
            // info_p("我收到了"+s);
            // 调用Deal进行具体处理
            Deal deal = new Deal(s,socket);
            deal.deal_with_message();
        } catch (IOException e) {
            if(s!=null){
                if(s.startsWith("*")){  // 针对外部信息，进行ERROR发送
                    s = new Global.ERROR_RESP().get_message();
                    Thread thread_send = new Thread(new Send(socket,s));
                    thread_send.start();
                    info_p("我发送了"+s);
                }
            }
            e.printStackTrace();
        }finally {
            try {
                // 由于在内部为节省资源，选择了非持续连接
                // 而外部信息的接收需要短暂的持续连接
                // 为保持和外部连接保持连通，分开处理
                if(!s.startsWith("*")){
                    if(input!=null) input.close();
                    if(socket!=null) socket.close();
                }
                if(s.startsWith("*")){
                    while(!socket.isClosed()){
                        // info_p("1");
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
