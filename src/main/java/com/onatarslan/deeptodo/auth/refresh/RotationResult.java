package com.onatarslan.deeptodo.auth.refresh;

import com.onatarslan.deeptodo.user.domain.User;

// Rotation'ın iki olası sonucu. Exception yerine sonuç döndürmenin sebebi:
// reddedilen durumlarda (ör. reuse) yapılan revoke'un transaction ile birlikte normal şekilde commit edilmesi.
// Exception'ı transaction bittikten sonra AuthService fırlatır.
public sealed interface RotationResult {

    // user: yeni access token'ın sub ve authorities'i için; rotation içinde zaten ACTIVE olduğu kontrol edildi.
    record Rotated(RefreshTokenService.IssuedRefreshToken token, User user) implements RotationResult {}

    record Rejected(Reason reason) implements RotationResult {}

    enum Reason {
        UNKNOWN,        // böyle bir token yok
        REVOKED,        // logout ya da önceki bir reuse ile iptal edilmiş
        EXPIRED,        // süresi dolmuş
        REUSED,         // daha önce kullanılmış token tekrar geldi → aile revoke edildi
        USER_INACTIVE   // kullanıcı DEACTIVATED → aile revoke edildi
    }
}
