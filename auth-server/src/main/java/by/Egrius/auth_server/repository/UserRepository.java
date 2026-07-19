package by.Egrius.auth_server.repository;

import by.Egrius.auth_server.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, Long> { // ← Long — это тип id

    Optional<User> findByPublicId(UUID publicId);

    Optional<User> findByExternalId(String externalId);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}