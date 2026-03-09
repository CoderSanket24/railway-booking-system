package com.vit.railway_os.repository;

import com.vit.railway_os.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    // Spring Boot writes the SQL to find a user by their login name
    Optional<User> findByUsername(String username);
}