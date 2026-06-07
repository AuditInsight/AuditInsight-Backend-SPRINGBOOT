package com.diana.auditinsightbackendspringboot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AuditInsightBackendSpringbootApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuditInsightBackendSpringbootApplication.class, args);
    }

}
