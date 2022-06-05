package conf;

import java.io.*;

import static lib.Global.error_p;
import static lib.Global.info_p;

/*
 *  @PROJECT:   Raft
 *  @AUTHOR:    Linmo
 *  @VERSION:   v1.0
 *  @create:    2022/6/4
 *  @e-mail:    linmo@hnu.edu.cn
 *  @school:    HNU
 */
public class Config {
    /****************** main ******************/
    public static void main(String[] args) throws IOException {
        // 判断输入参数
        info_p("读入运行参数中");
        if(args.length!=2||!args[0].equals("-n")){
            error_p("please check!");
            return;
        }
        // 删除原有的配置文件
        info_p("删除原有其他配置文件");
        File dir = new File(System.getProperty("user.dir"));
        File[] files = dir.listFiles(); // 该文件目录下文件全部放入数组
        if (files != null) {
            for (int i = 0; i < files.length; i++) {
                String fileName = files[i].getName();
                if(fileName.toLowerCase().endsWith(".conf")){
                    files[i].delete();
                    info_p("删除了文件 "+files[i].getName());
                }
            }
        }
        // 批量生成配置文件
        info_p("生成新的配置文件");
        int n = Integer.parseInt(args[1]);
        for(int i=1;i<=n;i++){
            String path = "server"+String.format("%03d", i)+".conf";
            File file = new File(path);
            if(!file.exists()){     /* 若文件不存在，则创建 */
                file.createNewFile();
            }
            OutputStream os = new FileOutputStream(path);
            PrintWriter pw = new PrintWriter(os);
            pw.println("! Server configuration");
            pw.println("! myself IP:Port");
            pw.println("self_info 127.0.0.1:"+(12345+i));
            pw.println("! others IP:Port");
            for(int j=1;j<=n;j++){
                pw.println("other_info 127.0.0.1:"+(12345+j));
            }
            info_p("配置文件 "+ path +" 生成完毕");
            pw.close();
            os.close();
        }
        // 生成Tester配置文件
        info_p("生成Tester的配置文件");
        String path = "tester.conf";
        File file = new File(path);
        if(!file.exists()){     /* 若文件不存在，则创建 */
            file.createNewFile();
        }
        OutputStream os = new FileOutputStream(path);
        PrintWriter pw = new PrintWriter(os);
        pw.println("! Tester configuration");
        for(int i=1;i<=n;i++){
            pw.println("other_info 127.0.0.1:"+(12345+i));
        }
        pw.close();
        os.close();
        info_p("配置文件 "+ path +" 生成完毕");
    }
    public static void deleteDir(File dir){
        String[] content = dir.list();  // 取得当前目录下所有文件和文件夹
        for(String name : content){     // 遍历
            File temp = new File(dir, name);
            if(temp.isDirectory()){//判断是否是目录
                deleteDir(new File(temp.getAbsolutePath()));//递归调用，删除目录里的内容
                temp.delete();//删除空目录
            }else{
                if(!temp.delete()){//直接删除文件
                    System.err.println("Failed to delete " + name);
                }
            }
        }
    }
}
