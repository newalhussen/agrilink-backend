package com.agrilink;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AgriLinkApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgriLinkApplication.class, args);
    }
}
