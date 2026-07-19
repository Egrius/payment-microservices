package by.egrius.api_gateway.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // Internal ID, not for outside usage

    @Column(name = "public_id", unique = true, nullable = false, updatable = false)
    private UUID publicId; // External ID, give it to a client

    @Column(name = "user_id", nullable = false)
    private UUID userId; // Relation with User from auth-server

    @Column(name = "balance", precision = 15, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(name = "currency", length = 3)
    private String currency; // RUB, USD, EUR

    @Column(name = "name")
    private String name;

    @Version
    @Column(name = "version")
    private Long version;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "fromAccount")
    private List<Transfer> outgoingTransfers;

    @OneToMany(mappedBy = "toAccount")
    private List<Transfer> incomingTransfers;

    @PrePersist
    public void prePersist() {
        if (publicId == null) {
            publicId = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Account account)) return false;
        return Objects.equals(getId(), account.getId()) && Objects.equals(getPublicId(), account.getPublicId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), getPublicId());
    }
}