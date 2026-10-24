package com.luciojr.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class PaymentEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentEngineApplication.class, args);
    }
}
