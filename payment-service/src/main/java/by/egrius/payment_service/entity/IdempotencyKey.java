package by.egrius.payment_service.entity;

import by.egrius.payment_service.dto.transfer.TransferReadDto;
import by.egrius.payment_service.exception.handler.ErrorResponse;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Entity
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "idempotency_keys", uniqueConstraints = {
        @UniqueConstraint(
                name = "value_user_id_unique",
                columnNames = {"value", "user_id"}
        )}
)
public class IdempotencyKey {

    public IdempotencyKey(UUID value, UUID userId, String requestHash, LocalDateTime createdAt,
                          long ttlAmount, TimeUnit ttlTimeUnit) {
        this.value = value;
        this.userId = userId;
        this.requestHash = requestHash;
        this.createdAt = createdAt;
        this.expiresAt = createdAt.plus(ttlAmount, ttlTimeUnit.toChronoUnit());
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private UUID value;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "request_hash", nullable = false)
    private String requestHash;

    @Column(name = "response_status")
    private Integer responseStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_body", columnDefinition = "jsonb")
    private TransferReadDto responseBody;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "error_response", columnDefinition = "jsonb")
    private ErrorResponse errorResponse;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IdempotencyKey that)) return false;
        return Objects.equals(getValue(), that.getValue())
                && Objects.equals(getUserId(), that.getUserId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getValue(), getUserId());
    }
}
