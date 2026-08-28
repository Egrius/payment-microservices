package by.Egrius.notification_service.repository;

import by.Egrius.notification_service.entity.Subscription;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

   // @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(value = """
            SELECT * FROM subscriptions s
            WHERE s.public_user_id = :userId AND is_active IS TRUE
            ORDER BY created_at DESC 
            LIMIT 1
            """, nativeQuery = true)
    Optional<Subscription> findLatestActiveByUserId(@Param("userId") UUID userId);

    @Modifying
    @Query(value = """
            UPDATE subscriptions
            SET is_active = :isActive
            WHERE id = :subscriptionId
            """, nativeQuery = true)
    int setSubscriptionStatus(@Param("subscriptionId") Long subscriptionId,
                              @Param("isActive") boolean isActive);
}
