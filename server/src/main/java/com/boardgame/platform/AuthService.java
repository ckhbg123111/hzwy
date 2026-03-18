package com.boardgame.platform;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class AuthService {

    private final PlatformStore store;

    AuthService(PlatformStore store) {
        this.store = store;
    }

    synchronized AuthSessionResponse loginGuest(GuestAuthRequest request) {
        String deviceId = request.deviceId() == null || request.deviceId().isBlank()
                ? "device-" + UUID.randomUUID().toString().substring(0, 8)
                : request.deviceId().trim();
        if (store.bannedDevices.containsKey(deviceId)) {
            throw PlatformException.forbidden(
                    "DEVICE_BANNED",
                    "This device is banned: " + store.bannedDevices.get(deviceId));
        }

        String userId = "u_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String displayName = request.displayName() == null || request.displayName().isBlank()
                ? "Guest-" + userId.substring(userId.length() - 5)
                : request.displayName().trim();

        PlatformStore.User user = new PlatformStore.User(
                userId,
                Instant.now(),
                PlatformStore.UserKind.GUEST,
                displayName,
                deviceId);
        store.users.put(user.id, user);

        String token = issueTokenFor(user);
        return toSessionResponse(token, user);
    }

    synchronized AuthSessionResponse bindPhone(String token, BindPhoneRequest request) {
        PlatformStore.User user = requireUser(token);
        String phoneNumber = request.phoneNumber().trim();
        if (!phoneNumber.matches("\\+?[0-9]{7,15}")) {
            throw PlatformException.badRequest("PHONE_INVALID", "Phone number must contain 7 to 15 digits.");
        }
        String existingUserId = store.userIdByPhone.get(phoneNumber);
        if (existingUserId != null && !existingUserId.equals(user.id)) {
            throw PlatformException.conflict("PHONE_ALREADY_BOUND", "This phone number is already bound.");
        }

        user.kind = PlatformStore.UserKind.PHONE;
        user.phoneNumber = phoneNumber;
        store.userIdByPhone.put(phoneNumber, user.id);
        return toSessionResponse(token, user);
    }

    PlatformStore.User requireUser(String token) {
        PlatformStore.Session session = store.sessionsByToken.get(token);
        if (session == null) {
            throw PlatformException.unauthorized("AUTH_INVALID", "Authentication token is invalid or expired.");
        }
        PlatformStore.User user = store.users.get(session.userId());
        if (user == null) {
            throw PlatformException.unauthorized("AUTH_INVALID", "Authentication token is invalid or expired.");
        }
        return user;
    }

    PlatformStore.User findUser(String userId) {
        PlatformStore.User user = store.users.get(userId);
        if (user == null) {
            throw PlatformException.notFound("USER_NOT_FOUND", "User not found: " + userId);
        }
        return user;
    }

    synchronized void banDevice(String deviceId, String reason) {
        store.bannedDevices.put(deviceId, reason);
    }

    private String issueTokenFor(PlatformStore.User user) {
        String token = "tok_" + UUID.randomUUID().toString().replace("-", "");
        PlatformStore.Session session = new PlatformStore.Session(token, user.id, user.deviceId, Instant.now());
        store.sessionsByToken.put(token, session);
        return token;
    }

    private AuthSessionResponse toSessionResponse(String token, PlatformStore.User user) {
        return new AuthSessionResponse(token, ApiViews.toUserView(user));
    }
}

record GuestAuthRequest(String deviceId, String displayName) {
}

record BindPhoneRequest(@NotBlank String phoneNumber) {
}

record AuthSessionResponse(String token, UserView user) {
}
