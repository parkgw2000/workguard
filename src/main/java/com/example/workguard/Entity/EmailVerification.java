package com.example.workguard.Entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "email_verification")
@Getter
@Setter
@NoArgsConstructor
public class EmailVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
//인증대상 이메일 주소
    @Column(nullable = false, unique = true)
    private String email;
//인증용 숫자,문자 코드
    @Column(nullable = false)
    private String verificationCode;
//생성시간
    @Column(nullable = false)
    private LocalDateTime createdAt;
//만료시간
    @Column(nullable = false)
    private LocalDateTime expiresAt;
//인증 성공 여부
    @Column(nullable = false)
    private boolean verified = false;
}
