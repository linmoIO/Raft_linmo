package src;

import java.io.File;
import java.io.IOException;
import java.util.Scanner;

import static lib.Global.*;
import static lib.Global.error_p;
import static lib.Global.info_p;

/*
 *  @project:   raft
 *  @author:    Linmo
 *  @create:    2022/5/25
 *  @e-mail:    linmo@hnu.edu.cn
 *  @school:    HNU
 */

/* Main函数 */
public class Main {
    /****************** main ******************/
    public static void main(String[] args) throws IOException, InterruptedException {
        // my_debug();  // Debug初始化函数
        // 判断输入参数
        info_p("读入运行参数中");
        if(args.length!=2||!args[0].equals("--config_path")){
            error_p("please check!");
            return;
        }
        // 取出文件
        File file = new File(args[1]);
        // 创建Server并初始化
        server = new Server();
        server.init(file);
        // 运行
        server.run();
    }

    /****************** Debug设置 ******************/
    /* debug设置函数 */
    static void my_debug() throws InterruptedException, IOException {
        int n = 1;      // 放慢倍率（用于放慢时间，便于观察）
        // 对时间进行乘倍操作
        ele_time_l*=n;
        ele_time_r*=n;
        heart_beat_t*=n;

        // 设置放入的初始数据的数量（从控制台输入），用于测试
        info_p("请输入需要初始放入的数据的数量（仅DEBUG用）");
        Scanner reader = new Scanner(System.in);
        put_in_origin_data = reader.nextInt();
    }
}

