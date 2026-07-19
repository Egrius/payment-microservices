package by.egrius.api_gateway.repository;

import by.egrius.api_gateway.entity.Account;
import by.egrius.api_gateway.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByPublicId(UUID publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM accounts a WHERE id = :id")
    Optional<Account> findByIdPessimistic(@Param("id") Long id);

    Optional<Account> findByUser(User user);

    List<Account> findAllByUser_PublicIdOrderByCreatedAtDesc(UUID userPublicId);

    boolean existsByPublicIdAndUser_PublicId(UUID accountPublicId, UUID userPublicId);

    boolean existsByPublicId(UUID accountPublicId);
}