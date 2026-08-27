package kh.edu.istad.ite.features.social;

import jakarta.validation.Valid;
import kh.edu.istad.ite.features.social.dto.FacebookWebAppAuthRequest;
import kh.edu.istad.ite.features.social.dto.FacebookWebAppAuthResponse;
import kh.edu.istad.ite.features.social.service.FacebookWebAppAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public by necessity — this endpoint IS how a Messenger webview visitor gets a token in the first place, verified via signed_request rather than a bearer token nobody has yet. */
@RestController
@RequestMapping("/api/v1/facebook-webapp")
@RequiredArgsConstructor
public class FacebookWebAppAuthController {

    private final FacebookWebAppAuthService facebookWebAppAuthService;

    @PostMapping("/auth")
    public FacebookWebAppAuthResponse authenticate(@Valid @RequestBody FacebookWebAppAuthRequest request) {
        return facebookWebAppAuthService.authenticate(request);
    }
}
