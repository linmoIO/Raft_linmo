package src;

import lib.Global;
import lombok.SneakyThrows;

import java.io.IOException;
import java.net.Socket;

import static lib.Global.*;

/*
 *  @project:   raft
 *  @author:    Linmo
 *  @create:    2022/5/25
 *  @e-mail:    linmo@hnu.edu.cn
 *  @school:    HNU
 */

/* Deal处理类 */
public class Deal {
    /****************** 属性变量 ******************/
    String s = null;
    Socket socket = null;

    public Deal(String s,Socket socket) {
        this.s = s;
        this.socket = socket;
    }

    /****************** deal处理信息函数组 ******************/
    public void deal_with_message() throws IOException {
        // 对于信息处理，分内部和外部
        if(s.equals("")){return;}
        if(s.startsWith("*")){
            // 处理外界信息
            deal_outer();
        }
        else{
            // 处理系统内部通信
            deal_inner();
        }
    }
    public void deal_outer() throws IOException {   // 外部信息处理
        if(server.type!=Server_t.LEADER){   // 若不是LEADER，无需回复客户端的信息
            socket.close();
            return;
        }
        // 将读取到的信息转化为Inst
        Inst inst = new RESP().RESP2Inst(s);
        // 根据不同指令分情况处理
        if(inst.getOpt()==Opt.SET){
            Entry entry = new Entry(server.term,inst);
            server.log.add(entry);
            server.OK_num.putIfAbsent(server.log.indexOf(entry),1);
            server.TXQ.put(server.log.indexOf(entry),socket);
        }
        else if (inst.getOpt()==Opt.GET){
            // 处理GET（直接判断并返回即可，无需加入操作日志）
            String value = server.KV.get(inst.getKey());
            String mes = null;
            if(value==null){
                mes = new NIL_RESP().get_message();
            }
            else{
                String[] pair = value.split(" ");
                mes = new RESP().Pair2RESP(pair);
            }
            Thread thread_send = new Thread(new Send(socket,mes));
            thread_send.start();
        }
        else if (inst.getOpt()==Opt.DEL){
            Entry entry = new Entry(server.term,inst);
            server.log.add(entry);
            server.OK_num.putIfAbsent(server.log.indexOf(entry),1);
            server.TXQ.put(server.log.indexOf(entry),socket);
        }
        else{
            // ignore();
        }
    }
    @SneakyThrows
    public void deal_inner(){       // 内部信息处理
        // 系统内部通信
        String[] pair = s.split(":");   // 分割以获得term, IP, Port
        int term = Integer.parseInt(pair[0]);
        if(term>server.getTerm()){          // 若收到的信息任期大于自己
            // 强制变为FOLLOWER，更新信息，并进行后续处理
            server.setType(Global.Server_t.FOLLOWER);
            server.setTerm(term);
            if(pair[3].equals("AE")){  // AppendEntries
                deal_AppendEntries(pair[1],Integer.parseInt(pair[2]),pair[4]);
            }
            else if(pair[3].equals("RV")){
                Global.IP_Port from = new Global.IP_Port(pair[1]+":"+pair[2]);
                deal_RequestVote(pair[4],term,from);
            }
            else{
                // ignore();
            }
        }
        else if(term == server.getTerm()){  // 若等于，即正常信息
            // 分状态进行分别处理
            if(server.getType().equals(Global.Server_t.LEADER)){    // 若为LEADER
                // leader对报文的处理
                if(pair[3].equals("RJ")){       // 若收到Reject
                    IP_Port from = new Global.IP_Port(pair[1]+":"+pair[2]);
                    deal_RejectMessage(pair[4],from);
                }
                else if(pair[3].equals("OK")){  // 若收到OK
                    IP_Port from = new Global.IP_Port(pair[1]+":"+pair[2]);
                    deal_OKMessage(pair[4],from);
                }
                else{
                    // ignore();
                }
            }
            else if(server.getType()== Global.Server_t.FOLLOWER){   // 若为FOLLOWER
                if(pair[3].equals("AE")){       // 若收到心跳AppendEntries
                    deal_AppendEntries(pair[1],Integer.parseInt(pair[2]),pair[4]);
                }
                else if(pair[3].equals("RV")){  // 若收到请求选票
                    Global.IP_Port from = new Global.IP_Port(pair[1]+":"+pair[2]);
                    deal_RequestVote(pair[4],term,from);
                }
                else{
                    // ignore();
                }
            }
            else if(server.getType()== Global.Server_t.CANDIDATE){  // 若为CANDIDATE
                if(pair[3].equals("AE")){       // 若收到心跳
                    server.setType(Global.Server_t.FOLLOWER);
                    server.setTerm(term);
                    deal_AppendEntries(pair[1],Integer.parseInt(pair[2]),pair[4]);
                }
                else if(pair[3].equals("VT")){  // 若收到选票
                    info_p("收到选票来自"+pair[1]+":"+pair[2]);
                    server.vote_cnt++;
                    info_p("当前获得选票数为"+server.vote_cnt);
                    if(server.vote_cnt>=(num_servers/2+1)){
                        server.setType(Server_t.LEADER);
                        flush_time();
                    }
                }
                else{
                    // ignore();
                }
            }
            else{
                // ignore();
            }
        }
        else{
            // ignore();
        }
    }
    // 处理Reject信息
    public void deal_RejectMessage(String s,IP_Port from){
        // 更新nextIndex
        String[] pair = s.split("\\|");
        int con_index = Integer.parseInt(pair[0]);
        server.nextIndex.replace(from,con_index);
    }
    // 处理OK信息
    public void deal_OKMessage(String s,IP_Port from)throws IOException{
        // 收集OK_num，判断是否可以提交
        // 注意，处理顺序为：满足提交条件->进行应用修改->进行提交
        String[] pair = s.split("\\|");
        int OK_index = Integer.parseInt(pair[0]);
        server.nextIndex.replace(from,OK_index+1);
        if(OK_index>server.committed){
            if(server.OK_num.get(OK_index)!=null){
                server.OK_num.replace(OK_index,server.OK_num.get(OK_index)+1);
                if(server.OK_num.get(OK_index)>=(num_servers/2+1)){
                    // info_p(server.OK_num.get(OK_index).toString());
                    // 应用目录并进行客户端反馈
                    do_log(OK_index);
                    server.committed = OK_index;
                    server.OK_num.remove(OK_index);
                }
            }
        }
    }
    // 处理心跳信息
    public void deal_AppendEntries(String ip,int port,String s) throws IOException {
        flush_time();   // 刷新时间
        // 更新Leader
        Global.IP_Port leader = new Global.IP_Port(ip,port);
        server.setLeader(leader);
        // 处理AppendEntries
        String[] pair = s.split("\\|");
        // 寻找上一条条目是否符合（保持一致性）
        int l_index =Integer.parseInt(pair[0]);
        int l_term = Integer.parseInt(pair[1]);
        Check_triple check_triple = check_last(l_index,l_term);

        if(check_triple.b==true){   // 若符合
            int index = l_index+1;
            if(server.log.size()>index){
                // 需要删除多余部分
                while(server.log.size()>index){
                    server.log.remove(index);
                }
                // 更新完毕，打印log
                server.print_log();
            }
            if(pair.length>2){
                index = Integer.parseInt(pair[2]);
                int term = Integer.parseInt(pair[3]);
                if(index!=0){
                    // 进行日记更新操作
                    String[] pair_inst = pair[4].split("_");
                    Global.Inst inst = new Global.Inst(pair_inst[0],pair_inst[1],pair_inst[2]);
                    while(server.log.size()>index){
                        server.log.remove(index);
                    }
                    server.log.add(index,new Entry(term,inst));
                }
                // 更新完毕，打印log
                server.print_log();
                // 发送OK
                // info_p("我发送了OK");
                Global.OKMessage okMessage = new Global.OKMessage(index);
                String mes = server.package_m(okMessage.get_message());
                Thread thread_send = new Thread(new Send(server.leader,mes));
                thread_send.start();
            }
        }
        else{   // 若不符合，则发送Reject
            Global.RejectMessage rejectMessage = new Global.RejectMessage(check_triple.index);
            String mes = server.package_m(rejectMessage.get_message());
            Thread thread_send = new Thread(new Send(server.leader,mes));
            thread_send.start();
        }
    }
    // 处理RequestVote
    public void deal_RequestVote(String s, int term, Global.IP_Port ip_port) throws IOException {
        flush_time();   // 刷新时间
        // 判断是否值得投票(比自己新)
        String[] pair = s.split("\\|");
        int index_t = Integer.parseInt(pair[0]);
        int term_t = Integer.parseInt(pair[1]);
        int term_o = server.log.lastElement().getTerm();
        int index_o = server.log.size()-1;
        // 若比自己新则投票，反之不投
        if((term_t>term_o)||(term_t==term_o&&index_t>=index_o)){
            Global.VoteMessage voteMessage = new Global.VoteMessage();
            String ms = server.package_m(voteMessage.get_message());
            Thread thread_send = new Thread((new Send(ip_port,ms)));
            thread_send.start();
            server.vote_term=term;
        }
        else{
            // ignore();
        }
    }
    
    /****************** 主要操作函数 ******************/
    public void do_log(int OK_index) throws IOException {   // 应用日志到KV
        for(int i=server.committed+1;i<=OK_index;i++){  // 遍历，处理每一个可应用的日志
            if(server.TXQ.get(i)!=null){
                Inst inst = server.log.get(i).getInst();
                int res = server.do_inst(inst); // 执行
                // 对客户端进行反馈
                if(inst.getOpt()==Opt.SET){
                    String mes = new OK_RESP().get_message();
                    Thread thread_send = new Thread(new Send(server.TXQ.get(i),mes));
                    thread_send.start();
                }
                else if(inst.getOpt()==Opt.DEL){
                    String mes = new DEL_Response(res).get_message();
                    Thread thread_send = new Thread(new Send(server.TXQ.get(i),mes));
                    thread_send.start();
                }
                // server.TXQ.remove(i);
            }
        }
    }
    public Check_triple check_last(int l_index, int l_term){ // 判断上一条条目是否符合
        // 若为初始，则直接返回
        if(l_index<0){return new Check_triple(0,0,true);}
        // 查找是否符合
        int term = 0;
        int index = 0;
        boolean b = true;
        if(server.log.size()<=l_index){
            index = server.log.size()-1;
            term = server.log.lastElement().getTerm();
            b=false;
        }
        else{
            if(server.log.get(l_index).getTerm()!=l_term){
                index = l_index;
                term = server.log.get(l_index).getTerm();
                b=false;
            }
        }
        // 若不符合，则找到冲突任期的第一条index
        if(term!=0){
            while(server.log.get(index).getTerm()==term){
                index--;
            }
            index++;
        }
        // 将冲突条目信息(若有)和判断值进行返回
        return new Check_triple(index,term,b);
    }
    
    /****************** 判断三元组 ******************/
    class Check_triple {
        public int index = 0;       // 冲突索引
        public int term = 0;        // 冲突任期
        public boolean b = false;   // check标记

        public Check_triple(int index, int term, boolean b) {
            this.index = index;
            this.term = term;
            this.b = b;
        }
    }
}
