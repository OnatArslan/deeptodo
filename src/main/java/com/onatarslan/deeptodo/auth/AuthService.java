package com.onatarslan.deeptodo.auth;

import com.onatarslan.deeptodo.auth.exception.EmailAlreadyExistException;
import com.onatarslan.deeptodo.auth.exception.InvalidRefreshTokenException;
import com.onatarslan.deeptodo.auth.jwt.AccessTokenService;
import com.onatarslan.deeptodo.auth.refresh.RefreshTokenService;
import com.onatarslan.deeptodo.auth.refresh.RefreshTokenService.IssuedRefreshToken;
import com.onatarslan.deeptodo.auth.refresh.RotationResult.Rejected;
import com.onatarslan.deeptodo.auth.refresh.RotationResult.Rotated;
import com.onatarslan.deeptodo.auth.web.dto.LoginRequest;
import com.onatarslan.deeptodo.auth.web.dto.RegisterRequest;
import com.onatarslan.deeptodo.user.domain.User;
import com.onatarslan.deeptodo.user.domain.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// Kullanım senaryolarını yönetir: register / login / refresh / logout.
// Token üretiminin ayrıntıları AccessTokenService (JWT) ve RefreshTokenService (opaque, DB) içinde.
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final AccessTokenService accessTokenService;
    private final RefreshTokenService refreshTokenService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       AccessTokenService accessTokenService,
                       RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.accessTokenService = accessTokenService;
        this.refreshTokenService = refreshTokenService;
    }

    // User INSERT ve ilk refresh token INSERT aynı transaction'da: biri başarısız olursa ikisi de geri alınır.
    @Transactional
    public AuthTokens register(RegisterRequest request) {

        // Login tarafı (AppUserDetailsService) ile aynı normalizasyon kuralı.
        var normalizedEmail = User.normalizeEmail(request.email());
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyExistException();
        }
        var hashedPassword = passwordEncoder.encode(request.password());

        var user = new User(normalizedEmail, hashedPassword, request.displayName());

        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new EmailAlreadyExistException();
        }
        return issueTokens(AppUserDetails.from(user), refreshTokenService.issue(user.getId()));
    }

    // Transactional yok sebebi iceride hash islemi olmasi
    public AuthTokens login(LoginRequest loginRequest) {
        var auth = UsernamePasswordAuthenticationToken.unauthenticated(loginRequest.email(), loginRequest.password());

        var userDetails = (AppUserDetails) authenticationManager.authenticate(auth).getPrincipal();

        return issueTokens(userDetails, refreshTokenService.issue(userDetails.id()));
    }

    // Transactional yok: rotate kendi transaction'ında commit edilir (reddedilse bile revoke kalıcı olur);
    // exception ondan sonra, transaction dışında fırlatılır.
    public AuthTokens refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new InvalidRefreshTokenException("missing refresh token");
        }

        return switch (refreshTokenService.rotate(rawRefreshToken)) {
            case Rotated(var refreshToken, var user) -> issueTokens(AppUserDetails.from(user), refreshToken);
            case Rejected(var reason) -> throw new InvalidRefreshTokenException(reason.name());
        };
    }

    // Cookie yoksa ya da token bilinmiyorsa da başarılı sayılır: logout idempotent.
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            refreshTokenService.revokeFamilyOf(rawRefreshToken);
        }
    }

    private AuthTokens issueTokens(AppUserDetails userDetails, IssuedRefreshToken refreshToken) {
        var accessToken = accessTokenService.issue(userDetails.id(), authoritiesOf(userDetails));

        return new AuthTokens(
                userDetails.id(),
                accessToken.value(),
                accessToken.expiresInSeconds(),
                refreshToken.value(),
                refreshToken.expiresAt()
        );
    }

    private static List<String> authoritiesOf(AppUserDetails userDetails) {
        return userDetails.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
    }
}
