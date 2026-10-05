package com.goguma.domain.goguma.controller;

import com.goguma.domain.goguma.dto.ActionHistoryResponse;
import com.goguma.domain.goguma.dto.GogumaAddRequest;
import com.goguma.domain.goguma.dto.GogumaGrowRequest;
import com.goguma.domain.goguma.dto.GogumaResponse;
import com.goguma.domain.goguma.service.GogumaService;
import com.goguma.domain.user.controller.UserController;
import com.goguma.global.common.ApiResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/goguma")
@RequiredArgsConstructor
public class GogumaController {

    private final GogumaService gogumaService;

    @GetMapping
    public ApiResponse<List<GogumaResponse>> getMyGogumas(HttpSession session) {
        Long userId = getLoginUserId(session);
        return ApiResponse.ok(gogumaService.getMyGogumas(userId));
    }

    @PostMapping("/add")
    public ApiResponse<GogumaResponse> addGoguma(
            @Valid @RequestBody GogumaAddRequest request,
            HttpSession session
    ) {
        Long userId = getLoginUserId(session);
        return ApiResponse.ok(gogumaService.addGoguma(userId, request), "새로운 고구마가 등록되었습니다! 🌱");
    }

    @PostMapping("/grow")
    public ApiResponse<GogumaResponse> grow(
            @Valid @RequestBody GogumaGrowRequest request,
            HttpSession session
    ) {
        Long userId = getLoginUserId(session);
        return ApiResponse.ok(gogumaService.grow(userId, request), "고구마가 무럭무럭 자랐습니다! ✨");
    }

    @GetMapping("/{id}/history")
    public ApiResponse<List<ActionHistoryResponse>> getHistory(
            @PathVariable("id") Long gogumaId,
            HttpSession session
    ) {
        Long userId = getLoginUserId(session);
        return ApiResponse.ok(gogumaService.getHistory(userId, gogumaId));
    }

    @PostMapping("/remove")
    public ApiResponse<Void> remove(
            @RequestBody Map<String, Long> payload,
            HttpSession session
    ) {
        Long userId = getLoginUserId(session);
        Long gogumaId = payload.get("id");
        gogumaService.removeGoguma(userId, gogumaId);
        return ApiResponse.ok(null, "고구마가 삭제되었습니다.");
    }

    private Long getLoginUserId(HttpSession session) {
        Long userId = (Long) session.getAttribute(UserController.SESSION_USER_ID);
        if (userId == null) {
            throw new IllegalStateException("로그인이 필요한 서비스입니다.");
        }
        return userId;
    }
}
