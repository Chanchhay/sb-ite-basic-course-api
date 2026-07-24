package kh.edu.istad.ite.features.auth;

import jakarta.validation.Valid;
import kh.edu.istad.ite.features.auth.dto.RegisterRequest;
import kh.edu.istad.ite.features.auth.dto.RegisterResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/register")
    public RegisterResponse register(@Valid @RequestBody RegisterRequest registerRequest) {
        return authService.register(registerRequest);
    }
}
