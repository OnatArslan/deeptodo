package com.onatarslan.deeptodo.user.domain;

import jakarta.persistence.QueryHint;
import org.hibernate.jpa.HibernateHints;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

public interface UserRepository extends JpaRepository<User, UUID> {

    List<User> findByStatusAndDisplayNameContainingIgnoreCase(UserStatus status, String displayName);


    List<User> findByStatusNotAndDisplayNameContainingAndEmailContainingIgnoreCaseOrderByStatusDescCreatedAtAsc(UserStatus status, String subName, String subEmail);

    @QueryHints(@QueryHint(name = HibernateHints.HINT_FETCH_SIZE, value = "500"))
    Stream<User> streamByStatusNot(UserStatus status); // select * from users u where u.status <> :status;

    @Query("""
                select u from User u where u.status = :status
            """)
    Optional<User> findByStatusAndName(@Param("status") UserStatus status);

    @Query("""
            select u from User u
            where u.createdAt between :start and :end
            and u.status = :status
            order by u.createdAt desc
            limit 1
            """)
    List<User> findUserExample(@Param("start")Instant start, @Param("end") Instant end, @Param("status") UserStatus status);


    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
