package com.ptit.backend.service.impl;

import java.util.List;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.ptit.backend.dto.request.ChangePasswordRequest;
import com.ptit.backend.dto.request.LoginRequest;
import com.ptit.backend.dto.request.RegisterRequest;
import com.ptit.backend.dto.response.LoginResponse;
import com.ptit.backend.dto.response.UserResponse;
import com.ptit.backend.entity.User;
import com.ptit.backend.exception.AppException;
import com.ptit.backend.exception.ErrorCode;
import com.ptit.backend.mapper.UserMapper;
import com.ptit.backend.repository.RoleRepository;
import com.ptit.backend.repository.UserRepository;
import com.ptit.backend.security.JwtTokenProvider;
import com.ptit.backend.service.AuthService;

import lombok.RequiredArgsConstructor;

/**
 * AuthServiceImpl
 */

@Service
@RequiredArgsConstructor
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
    public LoginResponse loginWithGoogle(String firebaseIdToken) {
        // TODO Auto-generated method stub
        return null;
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

}
