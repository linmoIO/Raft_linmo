package test;

import lib.Global;

import java.io.*;
import java.net.Socket;
import java.util.HashMap;
import java.util.Random;
import java.util.Scanner;
import java.util.Vector;

import static java.lang.Thread.sleep;
import static lib.Global.*;
import static lib.Global.info_p;

/*
 *  @project:   raft
 *  @author:    Linmo
 *  @create:    2022/5/25
 *  @e-mail:    linmo@hnu.edu.cn
 *  @school:    HNU
 */

/* 测试器类 */
public class Tester {
    public static boolean detail = true;           // 是否显示细节

    /****************** 属性变量 ******************/
    public static boolean if_receive = false;       // 锁，用于等待服务器组响应
    public static String receive_s = "";            // 接收到的字符串
    public static int total = 0;                    // 总的测试指令数
    Vector<Global.IP_Port> others = new Vector<IP_Port>();// 服务器的IP端口
    HashMap<String,String> KV = new HashMap<>();    // 用于验证的Key-Value存储区
    int RIGHT_check_num = 0;    // 正确的数量
    int WRONG_check_num =0;     // 错误的数量
    int ERROR_check_num = 0;    // 回复ERROR的数量

    /****************** 初始化******************/
    public void init(){
        if_receive = false;
        receive_s ="";
        others.clear();
        KV.clear();
        RIGHT_check_num = 0;
        WRONG_check_num = 0;
        ERROR_check_num = 0;
    }
    
    /****************** 运行 ******************/
    public void run() throws IOException, InterruptedException {
        Inst inst = null;
        while(true){    // 永真循环
            // 从控制台读入本次需要测试的指令数
            info_p("请输入此次需要测试的指令数：");
            Scanner reader = new Scanner(System.in);
            int num = reader.nextInt();
            total+=num;
            // 对每一个指令进行处理
            for(int i=0;i<num;i++){
                if_receive = false;     // 设置未接收到信息
                inst = create_inst();   // 随机化生成指令
                if(detail){             // 根据是否显示细节进行打印
                    info_p(inst.get_String());
                }
                String s = new RESP().Inst2RESP(inst);
                for(IP_Port ip_port:others){    // 对服务器组群中的每一个服务器发送请求
                    // 尝试获取socket
                    Socket socket = null;
                    try{
                        socket = new Socket(ip_port.getIp(),ip_port.getPort());
                    } catch (Exception e){
                        info_p("目标Server"+ip_port.getString()+"不存在");
                        continue;
                    }
                    if(socket==null){continue;}
                    // 根据获取的到socket进行发送和等待信息
                    Thread thread_send = new Thread(new SendTester(socket,s));
                    thread_send.start();
                    Thread thread_receive = new Thread(new ReceiveTester(socket));
                    thread_receive.start();
                }
                while(!if_receive){ // 等待，直到接收到信息
                    sleep(1);
                }
                check_inst(inst);   // 进行判断是否正确
            }
            // 输出本次测试的结果
            info_p("total: "+total);
            info_p("RIGHT: "+RIGHT_check_num);
            info_p("WRONG: "+WRONG_check_num);
            info_p("ERROR: "+ERROR_check_num);
        }
    }

    /****************** 读取配置文件 ******************/
    public void get_info(File file) throws IOException {
        info_p("读取IP:Port配置文件");
        BufferedReader br = new BufferedReader(new FileReader(file));
        String s = null;
        while((s = br.readLine())!=null){
            if(s.charAt(0)=='!'){continue;}
            else if(s.startsWith("other_info")){
                String[] ss = s.split(" ");
                Global.IP_Port tmp = new Global.IP_Port(ss[1]);
                others.add(tmp);
            }
            else{
                error_p("info文件格式不符");
                System.exit(1);
            }
        }
        br.close();
        for(IP_Port i:others){
            info_p("服务器集群IP:Port为："+i.getString());
        }
    }

    /****************** 测试指令的随机生成 ******************/
    public Inst create_inst() {
        // 根据随机数，随机生成指令和操作数
        Inst res = new Inst();
        int i = new Random().nextInt(3)+1;
        if(i==1){
            res.setOpt(Opt.SET);
            i = new Random().nextInt(10)+1;
            res.setKey("key"+i);
            i = new Random().nextInt(10)+1;
            res.setValue("value"+i);
        }
        else if(i==2){
            res.setOpt(Opt.DEL);
            int num = new Random().nextInt(5)+1;
            res.setKey("");
            for(int k=0;k<num;k++){
                i = new Random().nextInt(3)+k*4+1;
                res.setKey(res.getKey()+" key"+i);
            }
        }
        else{
            res.setOpt(Opt.GET);
            i = new Random().nextInt(10)+1;
            res.setKey("key"+i);
        }
        return res;
    }
    
    /****************** 指令正误判断 ******************/
    public void check_inst(Inst inst){
        if(detail){
            // info_p(receive_s);
        }
        // 处理回复ERROR的情况
        if(receive_s.equals(new ERROR_RESP().get_message())){
            ERROR_check_num++;
            return;
        }
        // 模拟执行指令，并将结果和收到信息进行比对，进行更新
        int res = do_inst(inst);    // 获得执行结果
        // 分情况判断
        if(inst.getOpt()==Opt.SET){
            if(receive_s.equals(new OK_RESP().get_message())){
                RIGHT_check_num++;
                return;
            }
        }
        else if(inst.getOpt()==Opt.GET){
            String value = KV.get(inst.getKey());
            String mes = null;
            if(value==null){
                mes = new NIL_RESP().get_message();
            }
            else{
                String[] pair = value.split(" ");
                mes = new RESP().Pair2RESP(pair);
            }
            if(mes.equals(receive_s)){
                RIGHT_check_num++;
                return;
            }
        }
        else if(inst.getOpt()==Opt.DEL){
            if(receive_s.equals(new DEL_Response(res).get_message())){
                RIGHT_check_num++;
                return;
            }
        }
        // 若均不符合，则为错误
        WRONG_check_num++;
    }

    /****************** 指令的模拟执行 ******************/
    public int do_inst(Inst inst){
        int res=0;
        Opt opt = inst.getOpt();
        String k = inst.getKey();
        String v = inst.getValue();
        if(opt == Opt.SET){
            if(KV.get(k)!=null){
                KV.replace(k,v);
            }
            else{KV.put(k,v);}
        }
        else if(opt == Opt.DEL){
            String[] k_pair = k.split(" ");
            for(String i:k_pair){
                if(KV.get(i)!=null){
                    KV.remove(i);
                    res++;
                }
            }
        }
        return res;
    }
}
