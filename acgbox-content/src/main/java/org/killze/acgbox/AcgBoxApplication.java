package org.killze.acgbox;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("org.killze.acgbox.mapper")
@SpringBootApplication
public class AcgBoxApplication {

    public static void main(String[] args) {
        SpringApplication.run(AcgBoxApplication.class, args);
    }

}
