package com.personalink.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class PersonaLinkServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(PersonaLinkServerApplication.class, args);
    }
}
