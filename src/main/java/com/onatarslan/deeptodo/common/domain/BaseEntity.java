package com.onatarslan.deeptodo.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@MappedSuperclass
public abstract class BaseEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Version
    @Column(nullable = false)
    private Long version;

    public UUID getId() {
        return id;
    }

    public Long getVersion() {
        return version;
    }
}
