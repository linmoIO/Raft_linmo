package lib;

import raft.Server;

import java.util.*;

/*
 *  @project:   raft
 *  @author:    Linmo
 *  @create:    2022/5/25
 *  @e-mail:    linmo@hnu.edu.cn
 *  @school:    HNU
 */

/* 全局静态遍历区 */
public class Global {
    /****************** 初始变量设置 ******************/
    public static boolean wait_lock = false;    // 等待锁（用于锁follower的等待）
    public static int ele_time_l = 100;         // 等待时间的左范围，单位ms（用于限制随机生成等待时间的范围）
    public static int ele_time_r = 200;         // 等待时间的右范围，单位ms（用于限制随机生成等待时间的范围）
    public static int heart_beat_t = 30;        // 心跳基准时间，单位ms
    public static int num_servers = -1;         // 服务器数量
    public static Timer timer = null;           // 定时器
    public static Server server = null;         // 当前服务器
    public static int put_in_origin_data = 0;   // 放入多少数据用于测试（debug专用）

    /****************** 枚举类型 ******************/
    /* 服务器类型 */
    public enum Server_t{
        LEADER,FOLLOWER,CANDIDATE
    }
    /* 指令操作类型 */
    public enum Opt{
        SET, GET, DEL ,UNKNOWN
    }

    /****************** server的属性类及属性子类 ******************/
    /* 指令类 */
    public static class Inst {
        Opt opt = Opt.UNKNOWN;      // 操作
        String key = null;          // 键
        String value = null;        // 值

        public Inst(){}
        public Inst(String opt, String key, String value) { // 构造函数
            switch (opt) {
                case "SET":
                    this.opt = Opt.SET;
                    break;
                case "GET":
                    this.opt = Opt.GET;
                    break;
                case "DEL":
                    this.opt = Opt.DEL;
                    break;
                default:
                    break;
            }
            this.key = key;
            this.value = value;
        }

        public void setOpt(Opt opt) {
            this.opt = opt;
        }
        public void setKey(String key) {
            this.key = key;
        }
        public void setValue(String value) {
            this.value = value;
        }
        public Opt getOpt() { return opt; }
        public String getKey() { return key; }
        public String getValue() { return value; }
        public String get_String(){ // 将其转换为字符串
            return opt+"_"+key+"_"+value;
        }
    }
    /* IP端口类 */
    public static class IP_Port{
        String IP;      // IP
        int Port;       // 端口

        public IP_Port(){
            IP = null;
            Port = 0;
        }
        public IP_Port(String IP, int port) {
            this.IP = IP;
            Port = port;
        }
        public IP_Port(String s){          // 字符串转为端口号
            // 根据形如127.0.0.1:8002的字符串进行构建IP
            String[] pair = s.split(":");
            IP = pair[0];
            Port = Integer.parseInt(pair[1]);
        }

        public void setIP_Port(String s){  // 字符串转为端口号
            // 根据形如127.0.0.1:8002的字符串进行构建IP
            String[] pair = s.split(":");
            IP = pair[0];
            Port = Integer.parseInt(pair[1]);
        }

        public String getString(){          // 转换为字符串
            if(IP==null){return null;}
            return IP + ':' + Port;
        }

        @Override
        public boolean equals(Object o) {   // 重载equals函数，用于利用IP和Port进行端口比较
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            IP_Port ip_port = (IP_Port) o;
            return Port == ip_port.Port &&
                    Objects.equals(IP, ip_port.IP);
        }

        @Override
        public int hashCode() {             // 重载hashCode，用于HashMap中
            return Objects.hash(IP, Port);
        }

        public String getIp(){return IP;}
        public int getPort(){return Port;}
    }
    /* 条目类（表示日志中的一条条目） */
    public static class Entry{
        int term;   // 任期
        Inst inst;  // 指令

        public Entry(int t,Inst inst){
            term = t;
            this.inst = inst;
        }

        public String toString() {
            if(inst!=null){
                return "term=" + term +
                        ", inst=" + inst.get_String();
            }
            else{
                return "term=" + term +
                        ", inst=null";
            }
        }

        public int getTerm() {
            return term;
        }
        public Inst getInst() {
            return inst;
        }
    }

    /****************** 内部交互报文 ******************/
    /* AppendEntries类 */
    public static class AppendEntries {
        int last_index;     // 上一条索引
        int last_term;      // 上一条任期
        int index = 0;      // 索引
        int term = 0;       // 任期
        Inst inst = null;   // 指令

        public AppendEntries(int last_index, int last_term) {
            this.last_index = last_index;
            this.last_term = last_term;
        }
        public AppendEntries(int last_index, int last_term, int index, int term, Inst inst) {
            this.last_index = last_index;
            this.last_term = last_term;
            this.index = index;
            this.term = term;
            this.inst = inst;
        }

        public String get_message(){
            // AppendEntries：term:IP:Port:AE:last_index|last_term|index|term|inst
            String res = "AE:";
            res = res + last_index + '|';
            res = res + last_term + '|';
            if(inst!=null){
                res = res + index + '|';
                res = res + term + '|';
                res = res + inst.get_String();
            }
            return res;
        }
    }
    /* RejectMessage类 */
    public static class RejectMessage {
        int index;      // 冲突任期的第一条索引

        public RejectMessage(int index){
            this.index = index;
        }

        public String get_message(){
            // RequestVote：2:127.0.0.1:12345:RV:last_index|last_term|
            String res = "RJ:";
            res = res + index + '|';
            return res;
        }
    }
    /* OKMessage类 */
    public static class OKMessage {
        int index;  // 表示处理完毕的索引

        public OKMessage(int index){
            this.index = index;
        }

        public String get_message(){
            // OK：term:IP:Port:OK:index
            String res = "OK:";
            res = res + index + '|';
            return res;
        }
    }
    /* RequestVote类 */
    public static class RequestVote {
        int index;      // 目录中的最新索引
        int term;       // 目录中的最新任期

        public RequestVote(int index,int term){
            this.index = index;
            this.term = term;
        }

        public String get_message(){
            // RequestVote：2:127.0.0.1:12345:RV:last_index|last_term|
            String res = "RV:";
            res = res + index + '|';
            res = res + term + '|';
            return res;
        }
    }
    /* VoteMessage类 */
    public static class VoteMessage {
        public String get_message(){
            return "VT:";
        }
    }

    /****************** RESP格式的报文（即外部交互报文） ******************/
    /* OK_RESP类 */
    public static class OK_RESP {
        public String get_message(){
            return "+OK\r\n";
        }
    }
    /* ERROR_RESP类 */
    public static class ERROR_RESP{
        public String get_message(){
            return "-ERROR\r\n";
        }
    }
    /* NIL_RESP类 */
    public static class NIL_RESP{
        public String get_message(){
            return "*1\r\n$3\r\nnil\r\n";
        }
    }
    /* DEL_Response类 */
    public static class DEL_Response{
        int n;  // DEL成功的数量
        public DEL_Response(int n){
            this.n = n;
        }
        public String get_message(){
            return ":"+n+"\r\n";
        }
    }
    /* RESP类 */
    public static class RESP{
        public String Pair2RESP(String[] pair){ // 字符串数组转RESP
            Vector<String> res = new Vector<>(Arrays.asList(pair));
            return Vector2RESP(res);
        }
        public String[] RESP2Pair(String s){    // RESP转字符串数组
            Vector<String> res = RESP2Vector(s);
            return (String[]) res.toArray();
        }
        public String Vector2RESP(Vector<String> pair){    // 字符串向量转RESP
            StringBuilder res= new StringBuilder("*" + pair.size() + "\r\n");
            for (String s : pair) {
                res.append("$").append(s.length()).append("\r\n");
                res.append(s).append("\r\n");
            }
            return res.toString();
        }
        public Vector<String> RESP2Vector(String s){    // RESP转字符串向量
            String[] tmp=s.split("(\\$\\d+\r\n)|(\\*\\d+\r\n)|(\r\n)"); // 用正则表达式进行分割
            Vector<String> res =new Vector<>();
            for (String value : tmp) {
                if (!value.equals("")) {
                    res.add(value);
                }
            }
            return res;
        }
        public Inst RESP2Inst(String s){                // RESP转指令
            Vector<String> pair = RESP2Vector(s);
            Inst res=new Inst();
            switch (pair.get(0)) {
                case "SET":
                    res.setOpt(Opt.SET);
                    pair.remove(0);
                    res.setKey(pair.get(0));
                    pair.remove(0);
                    res.setValue(String.join(" ", pair));
                    break;
                case "GET":
                    res.setOpt(Opt.GET);
                    res.setKey(pair.get(1));
                    break;
                case "DEL":
                    res.setOpt(Opt.DEL);
                    pair.remove(0);
                    res.setKey(String.join(" ", pair));
                    break;
            }
            return res;
        }
        public String Inst2RESP(Inst inst){             // 指令转RESP
            Vector<String> pair = new Vector<>();
            if(inst.getOpt()==Opt.SET){
                pair.add("SET");
                pair.add(inst.getKey());
                String[] tmp=inst.getValue().split(" ");
                pair.addAll(Arrays.asList(tmp));
            }
            else if(inst.getOpt()==Opt.GET){
                pair.add("GET");
                pair.add(inst.getKey());
            }
            else if(inst.getOpt()==Opt.DEL){
                pair.add("DEL");
                String[] tmp=inst.getKey().split(" ");
                pair.addAll(Arrays.asList(tmp));
            }
            return Vector2RESP(pair);
        }
    }

    /****************** 信息打印 ******************/
    public static void error_p(String s){   // 错误信息打印
        System.out.println("[ERROR]:"+ s);
    }
    public static void info_p(String s){    // 输出信息打印
        System.out.println(s);
    }

    /****************** 定时器操作 ******************/
    public static void flush_time(){        // 刷新时间操作，清除定时器中的任务并释放资源，释放锁
        if(timer!=null){
            timer.purge();
        }
        if(timer!=null){
            timer.cancel();
        }
        timer = null;
        wait_lock=false;
    }
}
