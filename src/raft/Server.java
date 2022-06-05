package raft;

import lib.Global;
import lombok.SneakyThrows;

import java.io.*;
import java.net.Socket;
import java.util.*;

import static java.lang.Thread.sleep;
import static lib.Global.*;

/*
 *  @project:   raft
 *  @author:    Linmo
 *  @create:    2022/5/25
 *  @e-mail:    linmo@hnu.edu.cn
 *  @school:    HNU
 */

/* Server服务器类 */
public class Server {
    /****************** 属性变量 ******************/
    Global.Server_t type = Global.Server_t.FOLLOWER;    // 类型
    int term = 1;       // 任期
    int vote_cnt = 0;   // 收到的票计数
    int vote_term = 0;  // 已投过票的任期（保证每一个任期仅最先申请的获得投票）
    int committed = 0;  // 提交指针位置
    HashMap<IP_Port,Integer> nextIndex = new HashMap<>();   // 每一个follower的下一个要发送的条目索引位置
    Global.IP_Port self = new Global.IP_Port();             // 自己的IP端口
    Global.IP_Port leader = new Global.IP_Port();           // leader的IP端口
    Vector<Global.IP_Port> others = new Vector<IP_Port>();        // 其他服务器的IP端口
    Vector<Entry> log = new Vector<Entry>();                    // 日志
    HashMap<Integer,Integer> OK_num = new HashMap<>();      // 收到的OK的数量（以索引为键）
    HashMap<String,String> KV = new HashMap<>();            // Key-Value存储
    HashMap<Integer, Socket> TXQ = new HashMap<>();         // 事务队列，处理等待回复的信息
    
    /****************** 初始化 ******************/
    public void init(File file) throws IOException {        // 服务器初始化
        info_p("初始化server");
        type = Global.Server_t.FOLLOWER;    // 设置类型
        term = 1;                           // 初始化任期为1
        timer = new Timer();                // 初始化定时器
        get_info(file);                     // 读入IP_Port
        log_init();                         // 初始化日志
        for(IP_Port i:others){              // 初始化nextIndex
           nextIndex.putIfAbsent(i,0);
        }
        // HashMap进行清空
        OK_num.clear();
        KV.clear();
        TXQ.clear();
    }
    
    /****************** 运行 ******************/
    public void run() throws InterruptedException, IOException {    // 主运行函数
        info_p("运行server");
        create_listen();            // 创建监听线程
        create_print_info();        // 创建打印当前状态的线程
        while(true){                // 永真循环，保持运行
            flush_time();           // 刷新时间
            info_p("当前为："+type+"  任期为："+term);  // 汇报任期
            // print_log();            // 打印日志
            // 根据不同类型进行对应处理
            if(type == Global.Server_t.LEADER){
                leader();
            }
            else if(type == Global.Server_t.CANDIDATE){
                candidate();
            }
            else if(type == Global.Server_t.FOLLOWER){
                follower();
            }
            else{
                error_p("状态错误");
            }
        }
    }

    /****************** 不同状态运行 ******************/
    public void leader() throws IOException, InterruptedException {     // 若为LEADER
        committed = log.size()-1;
        for(IP_Port i:others){
            nextIndex.replace(i,committed+1);
        }
        KV_init();              // 初始化键值存储
        create_heart_beat();    // 创建发送心跳（保持LEADER地位）
        while(type == Server_t.LEADER){ // 永真运行
            sleep(1);
        }
    }
    public void candidate() throws InterruptedException, IOException {  // 若为CANDIDATE
        vote_cnt = 1;
        term++;
        vote();     // 进入投票阶段（请求投票并收集选票）
    }
    public void follower() throws InterruptedException {                // 若为FOLLOWER
        vote_term = 0;
        while(type == Server_t.FOLLOWER){   // 永真运行
            ele_wait();
        }
    }
    
    /****************** 读入配置文件 ******************/
    public void get_info(File file) throws IOException {
        // 具体配置文件格式详见README.md
        info_p("读取IP:Port配置文件");
        BufferedReader br = new BufferedReader(new FileReader(file));
        String s = null;
        while((s = br.readLine())!=null){
            if(s.charAt(0)=='!'){continue;}         // 若读入注释
            else if(s.startsWith("self_info")){     // 读入自己的IP端口
                String[] ss = s.split(" ");
                self.setIP_Port(ss[1]);
            }
            else if(s.startsWith("other_info")){    // 读入其他服务器的IP端口
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
        // 校验读入的信息
        if(self == null||others.size()<1){
            error_p("读入的参数过少");
        }
        if(num_servers==-1){
            num_servers=others.size();
        }
        else{
            if(num_servers!=others.size()){
                error_p("参数读入数量错误");
            }
        }
        // 打印读入的信息
        info_p("当前IP:Port为："+self.getString());
        info_p("服务器总数为："+num_servers);
        for(IP_Port i:others){
            info_p("其他IP:Port为："+i.getString());
        }
    }
    
    /****************** 初始化 ******************/
    public void log_init(){ // 日志初始化
        log.clear();
        log.add(0,new Entry(0,new Inst("init","null","null")));
        // 以下为测试样例（即Debug操作）
        for(int i=1;i<put_in_origin_data;i++){
            log.add(i,new Entry(i/2+1,new Inst("SET","key"+i,"value"+i)));
        }
    }
    public void KV_init(){  // KV初始化
        // 此处为保证一致性，删除KV原始内容
        // 可增加通过文件等输入KV的内容
        KV.clear();
        for (Entry entry : log) {
            do_inst(entry.getInst());
        }
    }

    /****************** Create函数 ******************/
    public void create_listen() throws IOException {    // 创建监听
        info_p("创建监听线程");
        // 创建监听线程
        Thread thread_receive = new Thread(new Listen(self));
        thread_receive.start();
    }
    public void create_print_info(){                    // 创建信息打印
        Thread thread_print_info = new Thread(new Print_Server());
        thread_print_info.start();
    }
    public void create_heart_beat() throws IOException {// 创建心跳
        flush_time();
        timer = new Timer();
        timer.schedule(new Heart_Beat(),0,heart_beat_t);
    }

    /****************** 主要操作函数 ******************/
    public void vote() throws InterruptedException, IOException {   // 选举
        info_p("进行选举");
        for(IP_Port i:others){  // 对每一个其他的服务器发送请求选票（收集操作在Deal.java中）
            if(i.equals(self)){continue;}
            Global.RequestVote requestVote = new Global.RequestVote(log.size()-1,log.lastElement().getTerm());
            String s = package_m(requestVote.get_message());
            Thread thread_send = new Thread(new Send(i,s));
            thread_send.start();
            info_p("发送选票给："+i.getString());
        }
        ele_wait();
    }

    /****************** 其他操作函数 ******************/
    public int do_inst(Inst inst){  // 执行inst指令，将其处理到KV中
        int res = 0;
        Opt opt = inst.getOpt();
        String k = inst.getKey();
        String v = inst.getValue();
        // 分类处理
        if(opt == Opt.SET){
            if(KV.get(k)!=null){
                KV.replace(k,v);
            }
            else{KV.put(k,v);}
        }
        else if(opt == Opt.DEL){
            // DEL可能一次性删除多个
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
    public void ele_wait() throws InterruptedException {    // 定时器等待（选举等待）
        // 随机化生成等待时间
        int ele_time = new Random().nextInt(ele_time_r-ele_time_l+1)+ele_time_l;
        flush_time();   // 刷新时间
        // info_p("follower进入等待，等待时间为：" + ele_time);
        try {
            timer = new Timer();
            timer.schedule(new ToCandidate(), ele_time);    // 添加定时任务
            wait_lock = true;
            while(wait_lock){   // 进入等待，等待获取锁
                sleep(1);
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    public String package_m(String s){  // 信息包装函数，用于给要发送的信息增加任期和IP端口
        String res = "";
        res = Integer.toString(term) + ':' + self.getString() + ':' + s;
        return res;
    }

    /****************** 定时器执行任务类 ******************/
    /* 心跳类 */
    public class Heart_Beat extends TimerTask{  // 继承于定时器任务类
        @SneakyThrows
        @Override
        public void run() {
            // info_p("心跳一次"+new Date());
            for(IP_Port i:others){  // 对每一个其他服务器，根据nextIndex进行发送心跳
                if(i.equals(self)){continue;}
                int now_index = nextIndex.get(i);
                int last_index = now_index - 1;
                int last_term = 0;
                if(last_index>=0){
                    last_term = log.get(last_index).getTerm();
                }
                if(now_index == log.size()){    // 若已更新完毕，则发送日常心跳即可
                    Global.AppendEntries appendEntries
                            = new Global.AppendEntries(last_index,last_term);
                    String s = package_m(appendEntries.get_message());
                    Thread thread_send = new Thread(new Send(i,s));
                    thread_send.start();
                }
                else{   // 若未更新完毕（未和LEADER保持一致），则发送需要更新的内容
                    int now_term = log.get(now_index).getTerm();
                    Global.Inst inst = log.get(now_index).getInst();
                    Global.AppendEntries appendEntries
                            = new Global.AppendEntries(last_index,last_term,now_index,now_term,inst);
                    String s = package_m(appendEntries.get_message());
                    Thread thread_send = new Thread(new Send(i,s));
                    thread_send.start();
                }
            }
        }
    }
    /* 变为候选者类 */
    public class ToCandidate extends TimerTask{
        @Override
        public void run() {
            info_p("由于超时，成为了候选者"+new Date());
            type = Server_t.CANDIDATE;
            wait_lock = false;
        }
    }

    /****************** 打印函数 ******************/
    public void print_log(){            // 打印日志信息
        System.out.println("log:");
        if(log.size()>10000){
            for(int i=log.size()-10000;i<log.size();i++){
                System.out.println("    {index="+i+", "+log.get(i).toString()+"}");
            }
        }
        else{
            for(int i=0;i<log.size();i++){
                System.out.println("    {index="+i+", "+log.get(i).toString()+"}");
            }
        }
    }
    
    /****************** GET和SET函数 ******************/
    public Global.Server_t getType() { return type; }
    public void setType(Global.Server_t type) { this.type = type; }
    public int getTerm() { return term; }
    public void setTerm(int term) { this.term = term; }
    public Global.IP_Port getSelf() { return self; }
    public void setSelf(Global.IP_Port self) { this.self = self; }
    public Global.IP_Port getLeader() { return leader; }
    public void setLeader(Global.IP_Port leader) { this.leader = leader; }
    public Vector<Global.IP_Port> getOthers() { return others; }
    public void setOthers(Vector<Global.IP_Port> others) { this.others = others; }
}
