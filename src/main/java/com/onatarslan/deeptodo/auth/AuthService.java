package com.onatarslan.deeptodo.auth;

import com.onatarslan.deeptodo.auth.domain.AppUserDetails;
import com.onatarslan.deeptodo.auth.exception.EmailAlreadyExistException;
import com.onatarslan.deeptodo.auth.jwt.JwtProperties;
import com.onatarslan.deeptodo.auth.jwt.TokenService;
import com.onatarslan.deeptodo.auth.web.dto.LoginRequest;
import com.onatarslan.deeptodo.auth.web.dto.RegisterRequest;
import com.onatarslan.deeptodo.auth.web.dto.TokenResponse;
import com.onatarslan.deeptodo.user.domain.User;
import com.onatarslan.deeptodo.user.domain.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

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

    @Transactional
    public UUID register(RegisterRequest request) {

        // Login tarafı (AppUserDetailsService) ile aynı normalizasyon kuralı.
        var normalizedEmail = User.normalizeEmail(request.email());
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyExistException();
        }
        var hashedPassword = passwordEncoder.encode(request.password());

        var user = User.register(normalizedEmail, hashedPassword, request.displayName());

        try {
            return userRepository.saveAndFlush(user).getId();
        } catch (DataIntegrityViolationException e) {
            throw new EmailAlreadyExistException();
        }
    }
    // Transactional yok sebebi iceride hash islemi olmasi
    public TokenResponse login(LoginRequest loginRequest) {
        UsernamePasswordAuthenticationToken auth = UsernamePasswordAuthenticationToken.unauthenticated(loginRequest.email(), loginRequest.password());

        var result = authenticationManager.authenticate(auth);

        var userDetails = (AppUserDetails) result.getPrincipal();

        var authorities = userDetails.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        var accessToken = tokenService.issueAccessToken(userDetails.id(), authorities);

        return new TokenResponse(accessToken.value(), "Bearer", jwtProperties.accessTokenTtl().toSeconds());
    }
}
