package by.Egrius.notification_service.repository;

import by.Egrius.notification_service.entity.Notification;
import by.Egrius.notification_service.entity.NotificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Query("select n from Notification n " +
            "where n.userId = :userId AND n.status = :status " +
            "order by n.createdAt ")
    List<Notification> findAllByStatusAndUserIdWithDateOrdering(@Param("userId") UUID userId,
                                                                @Param("status") NotificationStatus status);
}
