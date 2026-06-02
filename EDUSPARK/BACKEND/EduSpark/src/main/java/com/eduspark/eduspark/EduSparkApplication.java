package com.eduspark.eduspark;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@MapperScan("com.eduspark.eduspark.mapper")
@EnableAsync
public class EduSparkApplication {

    public static void main(String[] args) {
        SpringApplication.run(EduSparkApplication.class, args);
    }

}
