package com.example.foodsafety.auth;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class AuthTokenCleanupService {
    private final AuthSessionRepository sessionRepository;
    private final AuthActionTokenRepository actionTokenRepository;

    public AuthTokenCleanupService(
            AuthSessionRepository sessionRepository,
            AuthActionTokenRepository actionTokenRepository
    ) {
        this.sessionRepository = sessionRepository;
        this.actionTokenRepository = actionTokenRepository;
    }

    @Scheduled(fixedDelayString = "${app.auth.cleanup-interval-ms:3600000}")
    @Transactional
    public void removeExpiredTokens() {
        Instant now = Instant.now();
        sessionRepository.deleteAllByExpiresAtBefore(now);
        actionTokenRepository.deleteAllByExpiresAtBefore(now);
    }
}
