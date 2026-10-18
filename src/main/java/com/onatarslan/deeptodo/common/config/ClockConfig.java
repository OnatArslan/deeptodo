package com.onatarslan.deeptodo.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

// Zamana göre karar veren kod (token expiry, cookie Max-Age) "şimdi"yi buradan alır;
// testte sabit bir Clock verilerek bu kararlar deterministik hale gelir.
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
