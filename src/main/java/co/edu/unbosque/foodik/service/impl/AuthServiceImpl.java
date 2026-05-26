package co.edu.unbosque.foodik.service.impl;

import co.edu.unbosque.foodik.domain.dto.request.LoginRequest;
import co.edu.unbosque.foodik.domain.dto.request.RefreshTokenRequest;
import co.edu.unbosque.foodik.domain.dto.request.RegisterRequest;
import co.edu.unbosque.foodik.domain.dto.response.AuthResponse;
import co.edu.unbosque.foodik.domain.dto.response.UserResponse;
import co.edu.unbosque.foodik.domain.entity.User;
import co.edu.unbosque.foodik.domain.enums.Role;
import co.edu.unbosque.foodik.exception.ConflictException;
import co.edu.unbosque.foodik.exception.UnauthorizedException;
import co.edu.unbosque.foodik.repository.UserRepository;
import co.edu.unbosque.foodik.security.JwtUtil;
import co.edu.unbosque.foodik.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;

    public AuthServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder,
                           JwtUtil jwtUtil, AuthenticationManager authenticationManager,
                           UserDetailsService userDetailsService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.authenticationManager = authenticationManager;
        this.userDetailsService = userDetailsService;
    }

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email()))
            throw new ConflictException("Email already registered: " + request.email());

        User user = new User();
        user.setName(request.name());
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setPhone(request.phone());
        user.setRole(Role.USER);
        userRepository.save(user);

        log.info("New user registered: {}", user.getEmail());

        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        return AuthResponse.of(
                jwtUtil.generateToken(userDetails),
                jwtUtil.generateRefreshToken(userDetails),
                toUserResponse(user));
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new UnauthorizedException("User not found"));

        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        return AuthResponse.of(
                jwtUtil.generateToken(userDetails),
                jwtUtil.generateRefreshToken(userDetails),
                toUserResponse(user));
    }

    @Override
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String email = jwtUtil.extractUsername(request.refreshToken());
        UserDetails userDetails = userDetailsService.loadUserByUsername(email);

        if (!jwtUtil.isTokenValid(request.refreshToken(), userDetails))
            throw new UnauthorizedException("Invalid or expired refresh token");

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("User not found"));

        return AuthResponse.of(
                jwtUtil.generateToken(userDetails),
                jwtUtil.generateRefreshToken(userDetails),
                toUserResponse(user));
    }

    @Override
    public void logout(String token) {
        // Sin Redis: el token expira naturalmente según su TTL configurado en JWT.
        // Para invalidación inmediata en el futuro, reintegrar Redis o usar una
        // tabla de tokens revocados en PostgreSQL.
        log.info("Logout requested — token will expire on its own TTL");
    }

    private UserResponse toUserResponse(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(),
                user.getPhone(), user.getRole(), user.getCreatedAt());
    }
}
