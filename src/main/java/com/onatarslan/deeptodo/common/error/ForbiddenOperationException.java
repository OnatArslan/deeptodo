package com.onatarslan.deeptodo.common.error;

// Kaynağı görebiliyorsun ama bu işlemi yapma yetkin yok (ör. MEMBER bir workspace'i archive etmeye çalışıyor).
public class ForbiddenOperationException extends RuntimeException {

    public ForbiddenOperationException(String message) {
        super(message);
    }
}
