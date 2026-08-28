package by.egrius.payment_service.repository;

import by.egrius.payment_service.entity.Transfer;
import by.egrius.payment_service.entity.TransferStatus;
import by.egrius.payment_service.repository.projection.AdminSenderLeaderboardProjection;
import by.egrius.payment_service.repository.projection.TransferProjection;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransferRepository extends JpaRepository<Transfer, Long> {

    @Query("SELECT t FROM Transfer t " +
            "JOIN FETCH t.fromAccount fAcc " +
            "JOIN FETCH t.toAccount tAcc " +
            "WHERE t.publicId = :publicTransferId AND fAcc.userId = :publicUserId")
    Optional<Transfer> findByTransferPublicId_and_UserPublicId(@Param("publicTransferId") UUID publicTransferId,
                                                               @Param("publicUserId") UUID publicUserId);

    @Query("SELECT t FROM Transfer t WHERE t.id = :id AND t.status = :status")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Transfer> findByIdAndStatusWithLock(@Param("id") Long id, @Param("status") TransferStatus status);

    @Modifying
    @Query("UPDATE Transfer t SET t.status = :status, t.reason = :reason, t.processedAt = :processedAt WHERE t.id = :id")
    int updateStatus(Long id, TransferStatus status, String reason, LocalDateTime processedAt);


    long countByStatus(TransferStatus status);

    @Modifying
    @Query("UPDATE Transfer t SET t.status = :status, t.processedAt = :processedAt WHERE t.publicId = :publicId AND t.status = 'PENDING'")
    int updateTransferStatus(@Param("publicId") UUID publicId,
                             @Param("status") TransferStatus status,
                             @Param("processedAt") LocalDateTime processedAt);

    // TODO count query may be optimized
    @Query(
            value = """
        SELECT
            t.public_id AS id,
            from_acc.public_id AS fromAccountId,
            to_acc.public_id AS toAccountId,
            t.amount AS amount,
            t.status AS status,
            t.created_at AS createdAt,
            t.processed_at AS processedAt,
            t.reason AS reason
        FROM transfers t
        JOIN accounts from_acc ON t.from_account_id = from_acc.id
        JOIN accounts to_acc ON t.to_account_id = to_acc.id
        JOIN accounts a ON t.from_account_id = a.id OR t.to_account_id = a.id
        WHERE a.user_id = :userPublicId
        ORDER BY t.created_at DESC
    """,
            countQuery = """
        SELECT COUNT(*)
        FROM transfers t
        JOIN accounts from_acc ON t.from_account_id = from_acc.id
        JOIN accounts to_acc ON t.to_account_id = to_acc.id
        JOIN accounts a ON t.from_account_id = a.id OR t.to_account_id = a.id
        WHERE a.user_id = :userPublicId
    """,
            nativeQuery = true
    )
    Page<TransferProjection> findByUserIdOrderByCreatedAtDesc(
            @Param("userPublicId") UUID userId,
            Pageable pageable
    );


    @Query(value = """
            SELECT 
                a.user_id AS userPublicId, 
                a.public_id AS accountId, 
                COUNT(*) as transfersCount
            FROM transfers t
            JOIN accounts a ON a.id = t.from_account_id
            WHERE\s
            	 t.created_at >= NOW()::date - (:days * INTERVAL '1 day')
            	 AND status = 'COMPLETED'
            GROUP BY a.user_id, a.public_id
            ORDER BY transfersCount DESC
            LIMIT :limit
            """,
            nativeQuery = true)
    List<AdminSenderLeaderboardProjection> findTopUsersByTransferCount(
            @Param("limit") int limit,
            @Param("days") int days
    );
}
