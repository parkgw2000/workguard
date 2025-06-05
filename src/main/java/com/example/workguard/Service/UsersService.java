package com.example.workguard.Service;


import com.example.workguard.Dto.UserDTO;
import com.example.workguard.Dto.LoginDTO;
import com.example.workguard.Entity.EmailVerification;
import com.example.workguard.Entity.Users;
import com.example.workguard.Repository.EmailVerificationRepository;
import com.example.workguard.Repository.UsersRepository;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UsersService {

    private final EmailVerificationRepository emailVerificationRepository;
    private final UsersRepository usersRepository;
    private final ModelMapper modelMapper;
    private final PasswordEncoder passwordEncoder;


    public UserDTO createUser(UserDTO userDTO) {
        // 비밀번호 인코딩
        String encodedPassword = passwordEncoder.encode(userDTO.getPassword());

        // UserDTO → Users 엔티티로 변환
        Users user = modelMapper.map(userDTO, Users.class);

        // 인코딩된 비밀번호 적용
        user.setPassword(encodedPassword);

        // Role 기본값 처리
        if (user.getRole() == null) {
            user.setRole(Users.Role.USER);
        }

        // 저장
        Users savedUser = usersRepository.save(user);

        // 다시 DTO로 변환하여 반환
        return modelMapper.map(savedUser, UserDTO.class);
    }

    public void deleteUser(String email) {
        usersRepository.deleteByemail(email);
    }


    public UserDTO loginUser(LoginDTO loginDTO) {
        // 사용자 아이디로 조회
        Optional<Users> userOptional = usersRepository.findByemail(loginDTO.getEmail());

        if (userOptional.isPresent()) {
            Users user = userOptional.get();

            // 입력된 비밀번호와 저장된 비밀번호 비교
            if (passwordEncoder.matches(loginDTO.getPassword(), user.getPassword())) {
                // 비밀번호가 맞으면 UserDTO 반환
                return modelMapper.map(user, UserDTO.class);
            } else {
                throw new RuntimeException("Invalid credentials"); // 비밀번호 불일치
            }
        } else {
            throw new RuntimeException("User not found"); // 아이디를 찾을 수 없음
        }
    }
    public Optional<UserDTO> getUserByemail(String email) {
        return usersRepository.findByemail(email)
                .map(user -> modelMapper.map(user, UserDTO.class));
    }

    public boolean isEmailVerified(String email) {
        return emailVerificationRepository.findByEmail(email)
                .map(EmailVerification::isVerified)
                .orElse(false);
    }

    public void updatePassword(String email, String newPassword) {
        Users user = usersRepository.findByemail(email)
                .orElseThrow(() -> new RuntimeException("해당 이메일의 사용자가 없습니다."));
        user.setPassword(passwordEncoder.encode(newPassword));
        usersRepository.save(user);
    }
}

