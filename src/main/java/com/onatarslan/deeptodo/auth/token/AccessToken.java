package com.onatarslan.deeptodo.auth.token;

import java.time.Duration;

// İmzalı JWT (header.payload.signature) ve ne kadar geçerli olduğu. expiresIn istemci içindir: token'ı açmadan
// ne zaman yenilemesi gerektiğini bilir (OAuth2'nin expires_in alanıyla aynı fikir).
public record AccessToken(String value, Duration expiresIn) {
}
