package kh.edu.istad.ite.features.auth;

import kh.edu.istad.ite.features.auth.dto.RegisterRequest;

public interface AuthService {
    kh.edu.istad.ite.features.auth.dto.RegisterResponse register(RegisterRequest registerRequest);
}
