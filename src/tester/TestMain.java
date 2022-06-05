package tester;

import java.io.File;
import java.io.IOException;

import static lib.Global.*;

/*
 *  @project:   raft
 *  @author:    Linmo
 *  @create:    2022/5/25
 *  @e-mail:    linmo@hnu.edu.cn
 *  @school:    HNU
 */

/* Tester的Main */
public class TestMain {
    public static void main(String[] args) throws IOException, InterruptedException {
        info_p("读入运行参数中");
        if(args.length!=2||!args[0].equals("--config_path")){
            error_p("please check!");
            return;
        }
        // 创建并初始化和读入配置文件
        File file = new File(args[1]);
        Tester tester = new Tester();
        tester.init();
        tester.get_info(file);
        // 运行测试器tester
        tester.run();
    }

}
