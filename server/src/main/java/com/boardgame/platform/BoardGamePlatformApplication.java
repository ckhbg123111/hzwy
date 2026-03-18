package com.boardgame.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BoardGamePlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(BoardGamePlatformApplication.class, args);
    }
}
