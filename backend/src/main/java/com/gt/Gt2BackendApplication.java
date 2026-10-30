package com.gt;

import com.gt.config.Gt2Properties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(Gt2Properties.class)
public class Gt2BackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(Gt2BackendApplication.class, args);
    }
}
