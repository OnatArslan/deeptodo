package com.onatarslan.deeptodo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

// ConfigurationPropertiesScan: JwtProperties, RefreshTokenProperties gibi @ConfigurationProperties record'larını bulur.
@SpringBootApplication
@ConfigurationPropertiesScan
public class DeeptodoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DeeptodoApplication.class, args);
    }

}
