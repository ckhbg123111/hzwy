package com.boardgame.platform;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
class UserController {

    private final AuthService authService;

    UserController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/me")
    UserView me(
            @RequestHeader(name = "X-Auth-Token", required = false) String xAuthToken,
            @RequestHeader(name = "Authorization", required = false) String authorization) {
        return ApiViews.toUserView(authService.requireUser(RequestTokens.resolve(xAuthToken, authorization)));
    }

    @GetMapping("/{userId}")
    UserView getUser(
            @RequestHeader(name = "X-Auth-Token", required = false) String xAuthToken,
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable String userId) {
        authService.requireUser(RequestTokens.resolve(xAuthToken, authorization));
        return ApiViews.toUserView(authService.findUser(userId));
    }
}
