package com.goguma.domain.user.controller;

import com.goguma.domain.goguma.dto.GogumaResponse;
import com.goguma.domain.goguma.service.GogumaService;
import com.goguma.domain.user.dto.StartRequest;
import com.goguma.domain.user.dto.UserResponse;
import com.goguma.domain.user.service.UserService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final GogumaService gogumaService;
    public static final String SESSION_USER_ID = "USER_ID";

    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> start(@RequestBody StartRequest request, HttpSession session) {
        String name = request.getEffectiveName();
        if (name == null || name.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "이름을 입력해 주세요."));
        }

        UserResponse user = userService.getOrCreateUser(request);
        session.setAttribute(SESSION_USER_ID, user.getId());

        List<GogumaResponse> gogumas = gogumaService.getMyGogumas(user.getId());

        Map<String, Object> response = new HashMap<>();
        response.put("ok", true);
        response.put("user", user);
        response.put("gogumas", gogumas);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getMe(HttpSession session) {
        Long userId = (Long) session.getAttribute(SESSION_USER_ID);
        if (userId == null) {
            return ResponseEntity.ok(Map.of("user", false, "gogumas", Collections.emptyList()));
        }

        try {
            UserResponse user = userService.getMe(userId);
            List<GogumaResponse> gogumas = gogumaService.getMyGogumas(userId);

            Map<String, Object> response = new HashMap<>();
            response.put("user", user);
            response.put("gogumas", gogumas);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            session.removeAttribute(SESSION_USER_ID);
            return ResponseEntity.ok(Map.of("user", false, "gogumas", Collections.emptyList()));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(HttpSession session) {
        session.invalidate();
        return ResponseEntity.ok(Map.of("ok", true));
    }
}
