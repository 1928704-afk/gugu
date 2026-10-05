package com.goguma.domain.goguma.controller;

import com.goguma.domain.goguma.dto.ActionHistoryResponse;
import com.goguma.domain.goguma.dto.GogumaAddRequest;
import com.goguma.domain.goguma.dto.GogumaGrowRequest;
import com.goguma.domain.goguma.dto.GogumaResponse;
import com.goguma.domain.goguma.service.GogumaService;
import com.goguma.domain.user.controller.UserController;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/goguma")
@RequiredArgsConstructor
public class GogumaController {

    private final GogumaService gogumaService;

    @GetMapping
    public ResponseEntity<List<GogumaResponse>> getMyGogumas(HttpSession session) {
        Long userId = getLoginUserId(session);
        return ResponseEntity.ok(gogumaService.getMyGogumas(userId));
    }

    @PostMapping("/add")
    public ResponseEntity<Map<String, Object>> addGoguma(
            @RequestBody GogumaAddRequest request,
            HttpSession session
    ) {
        Long userId = getLoginUserId(session);
        GogumaResponse goguma = gogumaService.addGoguma(userId, request);
        List<GogumaResponse> gogumas = gogumaService.getMyGogumas(userId);

        Map<String, Object> result = new HashMap<>();
        result.put("ok", true);
        result.put("goguma", goguma);
        result.put("gogumas", gogumas);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/grow")
    public ResponseEntity<Map<String, Object>> grow(
            @RequestBody GogumaGrowRequest request,
            HttpSession session
    ) {
        Long userId = getLoginUserId(session);
        GogumaResponse goguma = gogumaService.grow(userId, request);

        Map<String, Object> result = new HashMap<>();
        result.put("ok", true);
        result.put("hp", goguma.getHp());
        result.put("actionScores", goguma.getActionScores());
        result.put("todayActions", goguma.getTodayActions());
        result.put("dominantAction", goguma.getDominantAction());
        result.put("stage2ActionLock", goguma.getStage2ActionLock());
        result.put("stage3ActionLock", goguma.getStage3ActionLock());
        result.put("stage4ActionLock", goguma.getStage4ActionLock());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<Map<String, Object>> getHistory(
            @PathVariable("id") Long gogumaId,
            HttpSession session
    ) {
        Long userId = getLoginUserId(session);
        List<ActionHistoryResponse> history = gogumaService.getHistory(userId, gogumaId);
        return ResponseEntity.ok(Map.of("history", history));
    }

    @PostMapping("/remove")
    public ResponseEntity<Map<String, Object>> remove(
            @RequestBody Map<String, Long> payload,
            HttpSession session
    ) {
        Long userId = getLoginUserId(session);
        Long gogumaId = payload.get("id");
        if (gogumaId != null) {
            gogumaService.removeGoguma(userId, gogumaId);
        }
        List<GogumaResponse> gogumas = gogumaService.getMyGogumas(userId);
        return ResponseEntity.ok(Map.of("ok", true, "gogumas", gogumas));
    }

    private Long getLoginUserId(HttpSession session) {
        Long userId = (Long) session.getAttribute(UserController.SESSION_USER_ID);
        if (userId == null) {
            return 1L; // 비회원 또는 세션 유실 시 1번 기본 유저로 폴백
        }
        return userId;
    }
}
