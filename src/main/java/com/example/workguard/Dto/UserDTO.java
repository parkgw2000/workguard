package com.example.workguard.Dto;

import com.example.workguard.Entity.Users;
import com.example.workguard.Entity.Users.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDTO {
    private Long usernum;
    private String email;
    private String name;
    private Role role;
    private String password; // 보안상 출력 X, 필요하면 SignupDTO로 따로 분리
    private LocalDateTime createdAt;

//    public UserDTO(users user) {
//        this.usernum = user.getUsernum();
//        this.email = user.getEmail();
//        this.name = user.getName();
//        this.role = user.getRole();
//        this.password = user.getPassword();
//        this.createdAt = user.getCreatedAt();
//    }
    // UserDTO → users 변환
    public Users toEntity() {
        return Users.builder()
                .usernum(this.usernum)
                .email(this.email)
                .name(this.name)
                .role(this.role)
                .password(this.password) // 해싱은 서비스 단에서 처리
                .build();
    }
}
