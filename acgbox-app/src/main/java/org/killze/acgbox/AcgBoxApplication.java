package org.killze.acgbox;

import config.SecurityConfig;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@MapperScan("org.killze.acgbox.mapper")
@SpringBootApplication
@Import(SecurityConfig.class)
public class AcgBoxApplication {

    public static void main(String[] args) {
        SpringApplication.run(AcgBoxApplication.class, args);
    }

}
