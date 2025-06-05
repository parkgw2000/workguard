package com.example.workguard.Controller;

import com.example.workguard.Dto.LoginDTO;
import com.example.workguard.Dto.UserDTO;
import com.example.workguard.Service.UsersService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/users")
public class UsersController {
    @Autowired
    private UsersService usersService;

    @PostMapping("/signup")
    public UserDTO createUser(@RequestBody UserDTO userDTO) {
        boolean isVerified = usersService.isEmailVerified(userDTO.getEmail());
        if (!isVerified) {
            throw new RuntimeException("이메일 인증이 필요합니다.");
        }
        return usersService.createUser(userDTO);
    }

    @DeleteMapping("/delete/{email}")
    public String deleteUser(@PathVariable String email) {
        usersService.deleteUser(email);
        return "User successfully deleted.";
    }

    @PostMapping("/login")
    public UserDTO login(@RequestBody LoginDTO loginDTO, HttpSession session) {
        UserDTO userDTO = usersService.loginUser(loginDTO);
        // 세션에 사용자 정보 저장
        session.setAttribute("user", userDTO);
        return userDTO;
    }

    @GetMapping("/find/{email}")
    public Optional<UserDTO> getUserById(@PathVariable String email) {
        return usersService.getUserByemail(email);
    }

    //비밀번호 재설정
    @PostMapping("/reset-password")
    public String resetPassword(@RequestParam String email, @RequestParam String newPassword) {
        boolean isVerified = usersService.isEmailVerified(email);
        if (!isVerified) {
            throw new RuntimeException("이메일 인증이 필요합니다.");
        }
        usersService.updatePassword(email, newPassword);
        return "비밀번호가 성공적으로 변경되었습니다.";
    }
}
