package com.arteva.medbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
@ConfigurationPropertiesScan("com.arteva.medbot.config")
public class MedicalLicenseBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(MedicalLicenseBotApplication.class, args);
    }
}
