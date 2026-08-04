package kh.edu.istad.ite.features.auth;

import jakarta.validation.Valid;
import kh.edu.istad.ite.features.auth.dto.RegisterRequest;
import kh.edu.istad.ite.features.auth.dto.RegisterResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import kh.edu.istad.ite.features.auth.dto.RoleEnum;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/register/business")
    public RegisterResponse registerBusiness(@Valid @RequestBody RegisterRequest registerRequest) {
        return authService.register(registerRequest, RoleEnum.BUSINESS.name());
    }

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/register/customer")
    public RegisterResponse registerCustomer(@Valid @RequestBody RegisterRequest registerRequest) {
        return authService.register(registerRequest, RoleEnum.GLOBAL_CUSTOMER.name());
    }
}
