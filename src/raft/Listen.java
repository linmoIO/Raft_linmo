package raft;

import lib.Global;
import lombok.SneakyThrows;

import static java.lang.Thread.sleep;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

/*
 *  @project:   raft
 *  @author:    Linmo
 *  @create:    2022/5/25
 *  @e-mail:    linmo@hnu.edu.cn
 *  @school:    HNU
 */

/* 监听线程类 */
public class Listen implements Runnable {
    /****************** 属性变量 ******************/
    Global.IP_Port IP_Port = null;

    public Listen(Global.IP_Port IP_Port) throws IOException {
        this.IP_Port = IP_Port;
    }

    @SneakyThrows
    public void run(){     // 线程具体执行的代码
        ServerSocket serverSocket = new ServerSocket(IP_Port.getPort());
        // 服务器保持运行
        while (true){
            Socket socket = serverSocket.accept();  // 若捕获到socket
            if(socket!=null){
                // 创建接收线程
                Thread thread_receive = new Thread(new Receive(socket));
                thread_receive.start();
            }
            sleep(1);
        }
    }
}