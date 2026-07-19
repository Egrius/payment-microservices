package by.egrius.api_gateway.repository;

import by.egrius.api_gateway.entity.Transfer;
import by.egrius.api_gateway.entity.TransferStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransferRepository extends JpaRepository<Transfer, Long> {

    Optional<Transfer> findByPublicId(UUID publicId);

    // Get account's transfer history (incoming and outgoing transfers)
    @Query()
    List<Transfer> findAllByAccountPublicId(UUID accountPublicId);

    List<Transfer> findAllByStatusOrderByCreatedAtAsc(TransferStatus status);

    // 4. Взять перевод на обработку с блокировкой (чтобы 2 воркера не взяли один)
    @Query("SELECT t FROM Transfer t WHERE t.id = :id AND t.status = 'PENDING'")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Transfer> findByIdAndStatusWithLock(Long id, TransferStatus status);

    // 5. Обновить статус перевода (при завершении)
    @Modifying
    @Query("UPDATE Transfer t SET t.status = :status, t.reason = :reason, t.processedAt = :processedAt WHERE t.id = :id")
    int updateStatus(Long id, TransferStatus status, String reason, LocalDateTime processedAt);

    long countByStatus(TransferStatus status);
}