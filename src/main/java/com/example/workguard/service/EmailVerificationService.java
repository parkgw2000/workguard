package com.example.workguard.Service;

import com.example.workguard.Entity.EmailVerification;
import com.example.workguard.Repository.EmailVerificationRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private final EmailVerificationRepository repository;
    private final MailService mailService;

    // 인증코드 발송
    @Transactional
    public void sendVerificationCode(String email) {
        String code = generateCode();
        LocalDateTime now = LocalDateTime.now();

        EmailVerification verification = repository.findByEmail(email)
                .orElse(new EmailVerification());

        verification.setEmail(email);
        verification.setVerificationCode(code);
        verification.setCreatedAt(now);
        verification.setExpiresAt(now.plusMinutes(5));
        verification.setVerified(false);

        repository.save(verification);

        // 메일 발송
        String subject = "이메일 인증 코드입니다.";
        String body = "<h3>인증코드: <strong>" + code + "</strong></h3><p>5분 내에 입력해주세요.</p>";
        mailService.sendMail(email, subject, body);
    }

    // 인증 코드 검증
    @Transactional
    public boolean verifyCode(String email, String code) {
        return repository.findByEmailAndVerificationCode(email, code)
                .filter(v -> !v.isVerified())
                .filter(v -> v.getExpiresAt().isAfter(LocalDateTime.now()))
                .map(v -> {
                    v.setVerified(true);
                    repository.save(v);
                    return true;
                }).orElse(false);
    }

    private String generateCode() {
        return String.format("%06d", new Random().nextInt(999999)); // 6자리 숫자
    }
}
