package com.example.workguard.Controller;

import com.example.workguard.Service.EmailVerificationService;
import com.example.workguard.Service.MailService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/send")
@RequiredArgsConstructor
public class MailController {
    private final EmailVerificationService emailService;
//    private final MailService mailService;

// 테스트용
//    @PostMapping("/mail")
//    public ResponseEntity<String> sendMail(@RequestParam String to) {
//        mailService.sendMail(to, "테스트 메일", "<h3>메일 본문입니다.</h3>");
//        return ResponseEntity.ok("메일 전송 완료");
//    }

    // 인증 메일 전송
    @PostMapping("/send")
    public ResponseEntity<String> sendCode(@RequestParam String email) {
        emailService.sendVerificationCode(email);
        return ResponseEntity.ok("인증 메일을 전송했습니다.");
    }

    // 인증 코드 확인
    @PostMapping("/verify")
    public ResponseEntity<String> verifyCode(
            @RequestParam String email,
            @RequestParam String code) {

        boolean result = emailService.verifyCode(email, code);
        return result
                ? ResponseEntity.ok("인증 성공")
                : ResponseEntity.badRequest().body("인증 실패 또는 만료된 코드");
    }
}

