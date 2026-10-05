package com.goguma.domain.user.controller;

import com.goguma.domain.user.dto.StartRequest;
import com.goguma.domain.user.dto.UserResponse;
import com.goguma.domain.user.service.UserService;
import com.goguma.global.common.ApiResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    public static final String SESSION_USER_ID = "USER_ID";

    @PostMapping("/start")
    public ApiResponse<UserResponse> start(@Valid @RequestBody StartRequest request, HttpSession session) {
        UserResponse response = userService.getOrCreateUser(request);
        session.setAttribute(SESSION_USER_ID, response.getId());
        return ApiResponse.ok(response, "환영합니다! 로그인이 완료되었습니다.");
    }

    @GetMapping("/me")
    public ApiResponse<UserResponse> getMe(HttpSession session) {
        Long userId = (Long) session.getAttribute(SESSION_USER_ID);
        if (userId == null) {
            return ApiResponse.error("로그인이 필요합니다.");
        }
        return ApiResponse.ok(userService.getMe(userId));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpSession session) {
        session.invalidate();
        return ApiResponse.ok(null, "로그아웃 되었습니다.");
    }
}
