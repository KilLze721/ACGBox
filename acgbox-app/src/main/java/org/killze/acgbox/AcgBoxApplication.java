package org.killze.acgbox;

import org.killze.acgbox.config.SecurityConfig;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

// 管理端与用户端使用同一个数据源，分别扫描各自的 Mapper。
@MapperScan({"org.killze.acgbox.mapper", "org.killze.acgbox.web.mapper"})
@SpringBootApplication
@Import(SecurityConfig.class)
public class AcgBoxApplication {

    public static void main(String[] args) {
        SpringApplication.run(AcgBoxApplication.class, args);
    }

}
