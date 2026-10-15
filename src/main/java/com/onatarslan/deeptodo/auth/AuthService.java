package com.onatarslan.deeptodo.auth;

import com.onatarslan.deeptodo.auth.exception.EmailAlreadyExistException;
import com.onatarslan.deeptodo.auth.exception.InvalidRefreshTokenException;
import com.onatarslan.deeptodo.auth.jwt.JwtProperties;
import com.onatarslan.deeptodo.auth.jwt.TokenService;
import com.onatarslan.deeptodo.auth.web.dto.LoginRequest;
import com.onatarslan.deeptodo.auth.web.dto.RegisterRequest;
import com.onatarslan.deeptodo.user.domain.User;
import com.onatarslan.deeptodo.user.domain.UserRepository;
import com.onatarslan.deeptodo.user.domain.UserStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;
    private final JwtProperties jwtProperties;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, AuthenticationManager authenticationManager, TokenService tokenService, JwtProperties jwtProperties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
        this.jwtProperties = jwtProperties;
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
        return issueTokens(AppUserDetails.from(user));
    }

    // Transactional yok sebebi iceride hash islemi olmasi
    public AuthTokens login(LoginRequest loginRequest) {
        UsernamePasswordAuthenticationToken auth = UsernamePasswordAuthenticationToken.unauthenticated(loginRequest.email(), loginRequest.password());

        var result = authenticationManager.authenticate(auth);

        var userDetails = (AppUserDetails) result.getPrincipal();

        return issueTokens(userDetails);
    }

    // Transactional yok: rotation kendi transaction'ında commit edilmeli (reuse'da revoke kalıcı olsun),
    // kullanıcı kontrolü ondan sonra ayrı bir okuma.
    public AuthTokens refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new InvalidRefreshTokenException("missing refresh token");
        }

        var rotated = tokenService.rotateRefreshToken(rawRefreshToken);

        // Spec §7.1: deactivated user refresh yapamaz. Yeni üretilen halef dahil aile revoke edilir.
        var user = userRepository.findById(rotated.userId())
                .filter(u -> u.getStatus() == UserStatus.ACTIVE)
                .orElse(null);
        if (user == null) {
            tokenService.revokeFamily(rotated.familyId());
            throw new InvalidRefreshTokenException("user is not active");
        }

        var accessToken = tokenService.issueAccessToken(user.getId(), authoritiesOf(AppUserDetails.from(user)));
        return new AuthTokens(
                user.getId(),
                accessToken.value(),
                jwtProperties.accessTokenTtl().toSeconds(),
                rotated.value(),
                rotated.expiresAt()
        );
    }

    // Cookie yoksa ya da token bilinmiyorsa da başarılı sayılır: logout idempotent.
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            tokenService.revokeFamily(rawRefreshToken);
        }
    }

    private AuthTokens issueTokens(AppUserDetails userDetails) {
        var accessToken = tokenService.issueAccessToken(userDetails.id(), authoritiesOf(userDetails));
        var refreshToken = tokenService.issueRefreshToken(userDetails.id());

        return new AuthTokens(
                userDetails.id(),
                accessToken.value(),
                jwtProperties.accessTokenTtl().toSeconds(),
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
