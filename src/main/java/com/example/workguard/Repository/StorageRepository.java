package com.example.workguard.Repository;


import com.example.workguard.Entity.Storage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface StorageRepository extends JpaRepository<Storage, Long> {
    List<Storage> findByUser_Usernum(Long usernum);
}