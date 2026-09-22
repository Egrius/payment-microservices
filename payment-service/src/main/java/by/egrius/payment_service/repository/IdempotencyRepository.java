package by.egrius.payment_service.repository;

import by.egrius.payment_service.entity.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface IdempotencyRepository extends JpaRepository<IdempotencyKey, Long> {

    @Modifying
    @Query(value = """
            INSERT INTO idempotency_keys (value, user_id, request_hash, created_at, expires_at) 
            VALUES (:value, :userId, :requestHash, :createdAt, :expiresAt) 
            ON CONFLICT (value, user_id) DO NOTHING
            """, nativeQuery = true)
    int tryInsert( @Param("value") UUID value,
                   @Param("userId") UUID userId,
                   @Param("requestHash") String requestHash,
                   @Param("createdAt") LocalDateTime createdAt,
                   @Param("expiresAt") LocalDateTime expiresAt);

    Optional<IdempotencyKey> findByValueAndUserId(UUID value, UUID userId);

    boolean deleteByValueAndUserId(UUID value, UUID userId);
}
