package by.Egrius.notification_service.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Getter
@Setter
@Builder
@Table(name = "subscriptions")
@NoArgsConstructor
@AllArgsConstructor
public class Subscription {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false)
    private UUID publicId;

    @Column(name = "public_user_id", nullable = false)
    private UUID publicUserId;

    @Column(name = "user_email", nullable = false)
    private String userEmail;

    @Column(name = "message", nullable = false)
    private String message;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;


    public Subscription(UUID publicUserId, String userEmail, String message) {
        this.publicUserId = publicUserId;
        this.userEmail = userEmail;
        this.message = message;
    }

    @PrePersist
    private void init() {
        if(this.publicId == null) {
            publicId = UUID.randomUUID();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Subscription that)) return false;
        return Objects.equals(getId(), that.getId()) && Objects.equals(getPublicId(), that.getPublicId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId(), getPublicId());
    }
}
