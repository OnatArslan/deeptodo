package com.onatarslan.deeptodo.auth;

import com.onatarslan.deeptodo.user.domain.User;
import com.onatarslan.deeptodo.user.domain.UserStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

// Spring Security'nin kullanıcıyı gördüğü şekil (UserDetails). Entity'nin kendisi bu interface'i uygulamaz:
// domain Spring Security'yi bilmez, entity de request boyunca security tarafında dolaşmaz.
// Spring'in hazır User sınıfı yerine kendi tipimiz: id'yi taşır (R4'te token'ın sub claim'i).
// Doğrulanmış bir kimlik değil, doğrulama adayıdır: provider onu yükler, parolası ve durumu sonra kontrol edilir.
public record AppUserDetails(UUID id, String email, String passwordHash, boolean active) implements UserDetails {

    public static AppUserDetails from(User user) {
        return new AppUserDetails(user.getId(), user.getEmail(), user.getPasswordHash(),
                user.getStatus() == UserStatus.ACTIVE);
    }

    // Record değiştirilemez; hash'i yükseltilmiş hali yeni bir kopyadır. Tek kullanıcısı
    // UserDetailsPasswordService.updatePassword: sözleşmesi "yeni parolalı UserDetails'i döndür" der.
    public AppUserDetails withPasswordHash(String newPasswordHash) {
        return new AppUserDetails(id, email, newPasswordHash, active);
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    // Yetki rol değil veridir (üyelik, sahiplik; spec §9). Kullanıcıya bağlı rol yok.
    // Başarılı login'den sonra Spring kendisi FACTOR_PASSWORD authority'sini ekler ("parolayla doğrulandı").
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    // false ise DaoAuthenticationProvider DisabledException fırlatır (DEACTIVATED kullanıcı login olamaz).
    @Override
    public boolean isEnabled() {
        return active;
    }
}
