package com.boardgame.platform;

final class RequestTokens {

    private RequestTokens() {
    }

    static String resolve(String xAuthToken, String authorization) {
        if (xAuthToken != null && !xAuthToken.isBlank()) {
            return xAuthToken.trim();
        }
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring("Bearer ".length()).trim();
        }
        throw PlatformException.unauthorized("AUTH_TOKEN_MISSING", "Missing authentication token.");
    }
}
