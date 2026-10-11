package com.onatarslan.deeptodo.user.domain;

import jakarta.persistence.PrePersist;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class UserExampleListener {

    private static final Logger log = LoggerFactory.getLogger(UserExampleListener.class);

    @PrePersist
    void beforeInsert(User user) {
        log.info("logging smth");
    }

}
