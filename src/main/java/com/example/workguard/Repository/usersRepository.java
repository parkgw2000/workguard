package com.example.workguard.Repository;

import com.example.workguard.Entity.users;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface usersRepository extends JpaRepository<users, Long>{
    Optional<users> findByuserid(String userid);
    void deleteByuserid(String userid);
}
