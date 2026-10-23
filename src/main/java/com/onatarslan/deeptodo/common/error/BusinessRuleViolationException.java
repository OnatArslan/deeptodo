package com.onatarslan.deeptodo.common.error;

// İstek geçerli ama mevcut durumla çakışıyor (ör. son OWNER'ı düşürmek, zaten üye olanı eklemek).
public class BusinessRuleViolationException extends RuntimeException {

    public BusinessRuleViolationException(String message) {
        super(message);
    }
}
