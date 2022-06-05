package raft;

import lib.Global;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;

import static lib.Global.*;

/*
 *  @project:   raft
 *  @author:    Linmo
 *  @create:    2022/5/25
 *  @e-mail:    linmo@hnu.edu.cn
 *  @school:    HNU
 */

/* 信息发送类 */
public class Send implements Runnable{
    /****************** 属性变量 ******************/
    Global.IP_Port ip_port = null;
    String s = null;
    Socket socket = null;

    // 支持两种初始化（分别对应知道IP端口的内部通信和不知道IP端口的外部通信）
    public Send(Global.IP_Port ip_port, String s) throws IOException {
        this.ip_port = ip_port;
        this.s = s;
    }
    public Send(Socket socket,String s)throws IOException{
        this.s = s;
        this.socket = socket;
    }

    public void run() {     // 线程具体执行的代码
        while(socket == null){  // 尝试获取socket
            if(ip_port.equals(server.self)){
                return;
            }
            try {
                socket = new Socket(ip_port.getIp(), ip_port.getPort());
            } catch (IOException e) {
                // e.printStackTrace();
                // info_p("目标Server"+ ip_port.getString()+"不存在");
                return;
            }
        }
        OutputStream output = null;
        try {   // 发送数据
            output = socket.getOutputStream();
            output.write(s.getBytes());
            // info_p("我给"+ IP_Port.getString()+"发送了"+s);
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            try {
                if(output!=null) output.close();
                if(socket!=null) socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
