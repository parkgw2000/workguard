package com.example.workguard.Config;

import com.example.workguard.Dto.UserDTO;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
public class LoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        try {
            // IP 추출
            String ip = request.getHeader("X-Forwarded-For");
            if (ip == null || ip.isEmpty()) {
                ip = request.getRemoteAddr();
            }
            MDC.put("ip", ip);

            // 세션에서 유저 정보 꺼내기
            HttpSession session = request.getSession(false); // 세션이 없으면 null 반환
            String userNum = "anonymous";
            if (session != null) {
                UserDTO userDTO = (UserDTO) session.getAttribute("user");
                if (userDTO != null && userDTO.getUsernum() != null) {
                    userNum = userDTO.getUsernum().toString();
                }
            }
            MDC.put("userNum", userNum);

            // 로그 출력
            log.info("Incoming request: {} from IP: {} UserNum: {}", request.getRequestURI(), ip, userNum);

            filterChain.doFilter(request, response);
        } finally {
            MDC.clear(); // 메모리 누수 방지
        }
    }
}
