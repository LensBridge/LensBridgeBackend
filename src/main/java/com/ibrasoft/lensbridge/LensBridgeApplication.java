package com.ibrasoft.lensbridge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@EnableJpaRepositories(basePackages = "com.ibrasoft.lensbridge.repository.sql")
@EnableMongoRepositories(basePackages = "com.ibrasoft.lensbridge.repository.mongo")
public class LensBridgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(LensBridgeApplication.class, args);
    }

}
