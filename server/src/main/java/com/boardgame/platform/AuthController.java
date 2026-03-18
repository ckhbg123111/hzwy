package com.boardgame.platform;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    private final AuthService authService;

    AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/guest")
    AuthSessionResponse guestLogin(@Valid @RequestBody GuestAuthRequest request) {
        return authService.loginGuest(request);
    }

    @PostMapping("/bind-phone")
    AuthSessionResponse bindPhone(
            @RequestHeader(name = "X-Auth-Token", required = false) String xAuthToken,
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @Valid @RequestBody BindPhoneRequest request) {
        return authService.bindPhone(RequestTokens.resolve(xAuthToken, authorization), request);
    }
}
