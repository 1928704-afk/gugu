package com.goguma.domain.mission.controller;

import com.goguma.domain.goguma.entity.GogumaAction;
import com.goguma.domain.goguma.repository.GogumaActionRepository;
import com.goguma.domain.goguma.repository.GogumaRepository;
import com.goguma.domain.user.controller.UserController;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/missions")
@RequiredArgsConstructor
public class MissionController {

    private final GogumaActionRepository gogumaActionRepository;
    private final GogumaRepository gogumaRepository;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getMissions(HttpSession session) {
        Long userId = (Long) session.getAttribute(UserController.SESSION_USER_ID);
        if (userId == null) userId = 1L;

        LocalDate today = LocalDate.now();
        List<String> requiredActions = List.of("bible", "prayer", "contact");
        Set<String> completedSet = new HashSet<>();

        // 오늘 수행한 액션 조회
        var gogumas = gogumaRepository.findByUserIdOrderByIdAsc(userId);
        if (!gogumas.isEmpty()) {
            List<GogumaAction> actions = gogumaActionRepository.findByGogumaIdOrderByCreatedAtDesc(gogumas.get(0).getId());
            for (GogumaAction a : actions) {
                if (a.getActionDate().isEqual(today)) {
                    completedSet.add(a.getActionType().getKey());
                }
            }
        }

        List<String> completedActions = new ArrayList<>();
        for (String req : requiredActions) {
            if (completedSet.contains(req)) {
                completedActions.add(req);
            }
        }

        int currentCount = completedActions.size();
        boolean completed = currentCount >= requiredActions.size();

        Map<String, Object> daily = new HashMap<>();
        daily.put("key", "daily_core3");
        daily.put("label", "오늘의 3종 미션");
        daily.put("periodKey", today.toString());
        daily.put("rewardHp", 2);
        daily.put("requiredActions", requiredActions);
        daily.put("completedActions", completedActions);
        daily.put("progress", Map.of("current", currentCount, "total", requiredActions.size()));
        daily.put("completed", completed);
        daily.put("claimed", false);

        Map<String, Object> weekly = new HashMap<>();
        weekly.put("key", "weekly_w1_post3");
        weekly.put("label", "주간 미션 (1주차)");
        weekly.put("description", "게시판 작성 3회 달성");
        weekly.put("periodKey", today.toString());
        weekly.put("rewardHp", 6);
        weekly.put("weekStart", today.toString());
        weekly.put("weekEnd", today.plusDays(6).toString());
        weekly.put("progress", Map.of("current", 1, "total", 3));
        weekly.put("completed", false);
        weekly.put("claimed", false);

        Map<String, Object> result = new HashMap<>();
        result.put("daily", daily);
        result.put("weekly", weekly);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{key}/claim")
    public ResponseEntity<Map<String, Object>> claimReward(
            @PathVariable("key") String key,
            HttpSession session
    ) {
        Long userId = (Long) session.getAttribute(UserController.SESSION_USER_ID);
        if (userId != null) {
            var gogumas = gogumaRepository.findByUserIdOrderByIdAsc(userId);
            if (!gogumas.isEmpty()) {
                var g = gogumas.get(0);
                g.addHp(2);
                gogumaRepository.save(g);
            }
        }
        return ResponseEntity.ok(Map.of("ok", true, "rewardHp", 2));
    }
}
