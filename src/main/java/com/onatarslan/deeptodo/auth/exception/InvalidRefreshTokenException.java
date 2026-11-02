package com.onatarslan.deeptodo.auth.exception;

// Refresh token kabul edilmedi: cookie yok, bilinmiyor, kullanılmış, revoke edilmiş, süresi dolmuş ya da kullanıcı
// artık ACTIVE değil. İstemciye hepsi aynı 401 (AuthExceptionHandler); hangisi olduğunu bilmek yalnız saldırgana yarar.
public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException(String reason) {
        super(reason);
    }
}
