package com.luciojr.payment.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Event-Driven Payment Engine API")
                        .version("1.0.0")
                        .description("Motor assíncrono de pagamentos de alta performance baseado em eventos com Java 21, Apache Kafka/Redpanda, PostgreSQL, Idempotência rígida e tratamento de Dead Letter Queue (DLQ).")
                        .contact(new Contact()
                                .name("Lucio Junior")
                                .email("luciojr2110@gmail.com")
                                .url("https://linkedin.com/in/lucio-dev"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")));
    }
}
