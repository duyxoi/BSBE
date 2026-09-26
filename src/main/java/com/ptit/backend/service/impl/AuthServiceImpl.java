package com.ptit.backend.service.impl;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.google.firebase.FirebaseException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.ptit.backend.dto.request.ChangePasswordRequest;
import com.ptit.backend.dto.request.LoginRequest;
import com.ptit.backend.dto.request.RegisterRequest;
import com.ptit.backend.dto.response.LoginResponse;
import com.ptit.backend.dto.response.UserResponse;
import com.ptit.backend.entity.Role;
import com.ptit.backend.entity.User;
import com.ptit.backend.exception.AppException;
import com.ptit.backend.exception.ErrorCode;
import com.ptit.backend.mapper.UserMapper;
import com.ptit.backend.repository.RoleRepository;
import com.ptit.backend.repository.UserRepository;
import com.ptit.backend.security.JwtTokenProvider;
import com.ptit.backend.service.AuthService;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * AuthServiceImpl
 */

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    // private final UserService userService;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    @Override
    public void changePassword(Authentication authentication, ChangePasswordRequest request) {
        // TODO Auto-generated method stub

    }

    @Override
    public LoginResponse login(LoginRequest request) {
        // TODO Auto-generated method stub
        try {
            String identifier = request.getUsername().trim();
            Authentication auth = authenticationManager
                    .authenticate(new UsernamePasswordAuthenticationToken(identifier, request.getPassword()));

            String resolveUsername = auth.getName();
            User user = userRepository.findByUsername(resolveUsername)
                    .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

            String primaryRole = user.getRole() != null ? user.getRole().getRoleName() : "USER";
            if (primaryRole.startsWith("ROLE_")) {
                primaryRole = primaryRole.substring(5);
            }

            String token = jwtTokenProvider.generateToken(user);
            List<String> roles = List.of("ROLE_" + primaryRole);

            return LoginResponse.builder()
                    .accessToken(token)
                    .tokenType("Bearer")
                    .expiresIn(jwtTokenProvider.getExpirationMs() / 1000)
                    .username(user.getUsername())
                    .roles(roles)
                    .build();
        } catch (BadCredentialsException e) {
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
            // TODO: handle exception
        }
    }

    @Override
    @Transactional
    public LoginResponse loginWithGoogle(String firebaseIdToken) {
        FirebaseToken token = verifyFirebseToken(firebaseIdToken);
        String email = token.getEmail();
        String fullName = token.getName();
        
        User user = userRepository.findByEmail(email).orElseGet(() -> createUserFromGoogle(email, fullName));

        String primaryRole = user.getRole() != null ? user.getRole().getRoleName() : "USER"; 
        if(primaryRole.startsWith("ROLE_"))
            primaryRole = primaryRole.substring(5);

        String tokenJwt = jwtTokenProvider.generateToken(user);
        List<String> roles = List.of("ROLE_" + primaryRole);

        return LoginResponse.builder()
            .accessToken(tokenJwt)
            .tokenType("Bearer")
            .expiresIn(jwtTokenProvider.getExpirationMs() / 1000)
            .username(user.getUsername())
            .roles(roles)
            .build();
        
    }

    @Override
    public UserResponse me(Authentication authentication) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public UserResponse register(RegisterRequest request) {
        // TODO Auto-generated method stub
        return null;
    }

    private FirebaseToken verifyFirebseToken(String firebaseIdToken) {
        if (!StringUtils.hasText(firebaseIdToken)) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Firebase id khong duoc de trong");
        }
        try {
            return FirebaseAuth.getInstance().verifyIdToken(firebaseIdToken);
        } catch (FirebaseException e) {
            throw new AppException(ErrorCode.INVALID_TOKEN, "token khong hop le");
            // TODO: handle exception
        }
    }

    private User createUserFromGoogle(String email, String fullName) {
        String username = generateUsername(email);
        Role role = roleRepository.findByRoleName("USER")
                .or(() -> roleRepository.findByRoleName("ROLE_USER"))
                .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_FOUND, "Khong tim thay vai tro USER"));

        User user = User.builder()
                .role(role)
                .username(username)
                .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                .email(email)
                .fullName(fullName)
                .status(1)
                .totalPoints(0)
                .build();

        return userRepository.save(user);
    }

    private String generateUsername(String email) {
        String username = email.replace(".gmail", "");
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        return email + time;
    }
}
