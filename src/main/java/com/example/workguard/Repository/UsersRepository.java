package com.example.workguard.Repository;

import com.example.workguard.Entity.Users;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UsersRepository extends JpaRepository<Users, Long>{
    Optional<Users> findByemail(String email);
    void deleteByemail(String email);
}
