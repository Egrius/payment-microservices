package by.egrius.api_gateway.repository;

import by.egrius.api_gateway.entity.Account;
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

    @Query(value = "SELECT * FROM accounts WHERE public_id = :publicAccountId AND user_id = :publicUserId",
            nativeQuery = true)
    Optional<Account> findByPublicIdAndUserId(@Param("publicAccountId") UUID publicAccountId,
                                                         @Param("publicUserId") UUID publicUserId);

    List<Account> findAllAccountsByUserId(UUID publicUserId);

    Optional<Account> findByPublicId(UUID publicAccountId);

    Optional<Account> findByName(String name);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id = :id")
    Optional<Account> findByPublicIdPessimistic(@Param("id") Long id);
;
}