package cn.minims.desk;

import cn.minims.desk.modules.system.backup.RestoreBootstrap;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.quartz.QuartzAutoConfiguration;
import org.springframework.cache.annotation.EnableCaching;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * MiniDesk 后端启动类。
 */
@EnableCaching
@MapperScan("cn.minims.desk.modules.**.mapper")
// 调度器由 QuartzConfig 自行装配（RAMJobStore + 库里的定义），排除 Boot 的自动配置避免两个 Scheduler
@SpringBootApplication(exclude = QuartzAutoConfiguration.class)
public class DeskApplication {

    public static void main(String[] args) {
        // SQLite 不会自动创建父目录，必须在连接池建立前把数据目录准备好
        ensureDataDir(args);
        // 恢复包必须在连接池建立之前应用，否则旧句柄会指向被替换掉的文件
        RestoreBootstrap.applyIfPending();
        SpringApplication.run(DeskApplication.class, args);
    }

    /**
     * 与 {@code minidesk.data-dir} 的取值顺序保持一致：命令行参数 &gt; JVM 系统属性 &gt; 环境变量 &gt; 默认值。
     * 单 Jar 放到空目录直接启动时，{@code ./data} 尚不存在，不预先创建会导致 Hikari 拿不到连接。
     */
    private static void ensureDataDir(String[] args) {
        String dir = System.getProperty("minidesk.data-dir",
                System.getenv().getOrDefault("MINIDESK_DATA_DIR", "./data"));
        for (String arg : args) {
            if (arg.startsWith("--minidesk.data-dir=")) {
                dir = arg.substring("--minidesk.data-dir=".length());
            }
        }
        try {
            Files.createDirectories(Path.of(dir).toAbsolutePath().normalize());
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create data directory: " + dir, e);
        }
    }
}
