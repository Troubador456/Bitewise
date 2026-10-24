package com.example.foodsafety.auth;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.time.Instant;

public interface AuthActionTokenRepository extends JpaRepository<AuthActionToken, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from AuthActionToken t join fetch t.user where t.tokenHash = :tokenHash")
    Optional<AuthActionToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    long deleteAllByExpiresAtBefore(Instant instant);
}
