package com.goguma.domain.mission.controller;

import com.goguma.domain.community.repository.PostRepository;
import com.goguma.domain.goguma.dto.GogumaResponse;
import com.goguma.domain.goguma.entity.Goguma;
import com.goguma.domain.goguma.entity.GogumaAction;
import com.goguma.domain.goguma.repository.GogumaActionRepository;
import com.goguma.domain.goguma.repository.GogumaRepository;
import com.goguma.domain.goguma.service.GogumaService;
import com.goguma.domain.mission.entity.MissionReward;
import com.goguma.domain.mission.repository.MissionRewardRepository;
import com.goguma.domain.user.controller.UserController;
import com.goguma.domain.user.entity.User;
import com.goguma.domain.user.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/missions")
@RequiredArgsConstructor
public class MissionController {

    private final GogumaActionRepository gogumaActionRepository;
    private final GogumaRepository gogumaRepository;
    private final MissionRewardRepository missionRewardRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final GogumaService gogumaService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getMissions(
            @RequestParam(value = "userName", required = false) String userName,
            @RequestParam(value = "userId", required = false) Long paramUserId,
            HttpSession session
    ) {
        User user = resolveUser(userName, paramUserId, session);

        LocalDate today = LocalDate.now();
        List<String> requiredActions = List.of("bible", "prayer", "contact");
        Set<String> completedSet = new HashSet<>();

        if (user != null) {
            List<Goguma> gogumas = gogumaRepository.findByUserIdOrderByIdAsc(user.getId());
            for (Goguma g : gogumas) {
                List<GogumaAction> actions = gogumaActionRepository.findByGogumaIdOrderByCreatedAtDesc(g.getId());
                for (GogumaAction a : actions) {
                    if (a.getActionDate().isEqual(today)) {
                        completedSet.add(a.getActionType().getKey());
                    }
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

        String dailyPeriodKey = today.toString();
        boolean dailyClaimed = user != null && missionRewardRepository.existsByUserIdAndMissionKeyAndPeriodKey(user.getId(), "daily_core3", dailyPeriodKey);

        Map<String, Object> daily = new HashMap<>();
        daily.put("key", "daily_core3");
        daily.put("label", "오늘의 3종 미션");
        daily.put("periodKey", dailyPeriodKey);
        daily.put("rewardHp", 2);
        daily.put("requiredActions", requiredActions);
        daily.put("completedActions", completedActions);
        daily.put("progress", Map.of("current", currentCount, "total", requiredActions.size()));
        daily.put("completed", completed);
        daily.put("claimed", dailyClaimed);

        // 주간 미션 (게시판 작성 3회)
        long postCount = user != null ? postRepository.countByUserId(user.getId()) : 0;
        int currentPosts = (int) Math.min(postCount, 3);
        boolean weeklyCompleted = currentPosts >= 3;
        LocalDate weekStart = today.with(DayOfWeek.MONDAY);
        LocalDate weekEnd = weekStart.plusDays(6);
        String weeklyPeriodKey = weekStart.toString();
        boolean weeklyClaimed = user != null && missionRewardRepository.existsByUserIdAndMissionKeyAndPeriodKey(user.getId(), "weekly_w1_post3", weeklyPeriodKey);

        Map<String, Object> weekly = new HashMap<>();
        weekly.put("key", "weekly_w1_post3");
        weekly.put("label", "주간 미션 (1주차)");
        weekly.put("description", "게시판 작성 3회 달성");
        weekly.put("periodKey", weeklyPeriodKey);
        weekly.put("rewardHp", 6);
        weekly.put("weekStart", weekStart.toString());
        weekly.put("weekEnd", weekEnd.toString());
        weekly.put("progress", Map.of("current", currentPosts, "total", 3));
        weekly.put("completed", weeklyCompleted);
        weekly.put("claimed", weeklyClaimed);

        Map<String, Object> result = new HashMap<>();
        result.put("daily", daily);
        result.put("weekly", weekly);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{key}/claim")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<Map<String, Object>> claimReward(
            @PathVariable("key") String key,
            @RequestBody(required = false) Map<String, Object> body,
            HttpSession session
    ) {
        String userName = body != null && body.get("userName") != null ? String.valueOf(body.get("userName")) : null;
        Long paramUserId = body != null && body.get("userId") != null ? Long.valueOf(String.valueOf(body.get("userId"))) : null;
        User user = resolveUser(userName, paramUserId, session);

        int rewardHp = "weekly_w1_post3".equals(key) ? 6 : 2;
        LocalDate today = LocalDate.now();
        String periodKey = "weekly_w1_post3".equals(key)
                ? today.with(DayOfWeek.MONDAY).toString()
                : today.toString();

        List<GogumaResponse> updatedGogumas = Collections.emptyList();

        if (user != null) {
            boolean alreadyClaimed = missionRewardRepository.existsByUserIdAndMissionKeyAndPeriodKey(user.getId(), key, periodKey);
            if (!alreadyClaimed) {
                missionRewardRepository.save(MissionReward.builder()
                        .user(user)
                        .missionKey(key)
                        .periodKey(periodKey)
                        .rewardHp(rewardHp)
                        .build());

                List<Goguma> gogumas = gogumaRepository.findByUserIdOrderByIdAsc(user.getId());
                for (Goguma g : gogumas) {
                    g.addHp(rewardHp);
                    gogumaRepository.save(g);
                }
            }
            updatedGogumas = gogumaService.getMyGogumas(user.getId());
        }

        Map<String, Object> response = new HashMap<>();
        response.put("ok", true);
        response.put("rewardHp", rewardHp);
        response.put("gogumas", updatedGogumas);
        return ResponseEntity.ok(response);
    }

    private User resolveUser(String userName, Long paramUserId, HttpSession session) {
        if (userName != null && !userName.trim().isEmpty()) {
            Optional<User> byName = userRepository.findByName(userName.trim());
            if (byName.isPresent()) return byName.get();
        }
        Long userId = (Long) session.getAttribute(UserController.SESSION_USER_ID);
        if (userId == null && paramUserId != null) userId = paramUserId;
        if (userId != null) {
            Optional<User> byId = userRepository.findById(userId);
            if (byId.isPresent()) return byId.get();
        }
        return userRepository.findAll().stream().findFirst().orElse(null);
    }
}
