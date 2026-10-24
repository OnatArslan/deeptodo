package com.onatarslan.deeptodo.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

// Zamana göre karar veren ya da zaman yazan kod (ör. üyelik joined_at) "şimdi"yi buradan alır;
// testte sabit bir Clock verilerek bu kararlar deterministik hale gelir.
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
