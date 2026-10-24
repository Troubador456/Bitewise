package com.example.foodsafety.auth;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AuthSession s join fetch s.user where s.tokenHash = :tokenHash")
    Optional<AuthSession> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AuthSession s where s.familyId = :familyId")
    List<AuthSession> findByFamilyIdForUpdate(@Param("familyId") UUID familyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AuthSession s where s.user.id = :userId and s.revokedAt is null")
    List<AuthSession> findActiveByUserIdForUpdate(@Param("userId") UUID userId);

    long deleteAllByExpiresAtBefore(Instant instant);
}
