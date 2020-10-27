package com.github.yue9944882.kubernetes;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScans;

@SpringBootApplication
@ComponentScans(
        {
                @ComponentScan("io.kubernetes.client.spring.extended.controller"),
                @ComponentScan("com.github.yue9944882.kubernetes.config")
        }
)
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

}
