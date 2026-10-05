package com.goguma.domain.user.service;

import com.goguma.domain.user.dto.StartRequest;
import com.goguma.domain.user.dto.UserResponse;
import com.goguma.domain.user.entity.Department;
import com.goguma.domain.user.entity.User;
import com.goguma.domain.user.repository.UserRepository;
import com.goguma.global.error.BusinessException;
import com.goguma.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    @Transactional
    public UserResponse getOrCreateUser(StartRequest request) {
        String trimmedName = request.getName().trim();
        Department dept = Department.from(request.getDepartment());

        User user = userRepository.findByName(trimmedName)
                .orElseGet(() -> userRepository.save(
                        User.builder()
                                .name(trimmedName)
                                .department(dept)
                                .build()
                ));

        user.recordVisit(LocalDate.now());
        return new UserResponse(user);
    }

    public UserResponse getMe(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return new UserResponse(user);
    }
}
