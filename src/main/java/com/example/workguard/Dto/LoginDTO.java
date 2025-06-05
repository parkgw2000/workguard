package com.example.workguard.Dto;

import com.example.workguard.Entity.Users;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LoginDTO {
    private String email;
    private String password;

    public LoginDTO(Users user) {
        this.email = user.getEmail();
        this.password = user.getPassword();
    }
}
