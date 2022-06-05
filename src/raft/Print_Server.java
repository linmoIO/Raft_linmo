package raft;

import lombok.SneakyThrows;

import static java.lang.Thread.sleep;
import static lib.Global.*;

/*
 *  @project:   raft
 *  @author:    Linmo
 *  @create:    2022/5/25
 *  @e-mail:    linmo@hnu.edu.cn
 *  @school:    HNU
 */

/* 服务器信息输出类 */
public class Print_Server implements Runnable{
    @SneakyThrows
    public void run(){     // 线程具体执行的代码
        while (true){
            info_p("committer:" + String.valueOf(server.committed));
            // 打印日志信息
            server.print_log();
            // 设置多少时间打印一次
            sleep(10000);
        }
    }
}
