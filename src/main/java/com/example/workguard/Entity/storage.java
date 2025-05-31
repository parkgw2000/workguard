package com.example.workguard.Entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "storage")
@Getter
@Setter
@NoArgsConstructor
public class storage {

    @Id
    @Column(name = "storage_id")
    private Long storageId;

    @Column(name = "file_url")
    private String fileUrl;

    @Column(name = "version")
    private Integer version = 1;

    @Column(name = "uploaded_at")
    private LocalDateTime uploadedAt = LocalDateTime.now();

    @Column(name = "is_latest")
    private Boolean isLatest = true;
}