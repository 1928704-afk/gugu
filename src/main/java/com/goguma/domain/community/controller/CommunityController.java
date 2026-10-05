package com.goguma.domain.community.controller;

import com.goguma.domain.community.dto.PostAddRequest;
import com.goguma.domain.community.entity.Post;
import com.goguma.domain.community.repository.PostRepository;
import com.goguma.domain.user.controller.UserController;
import com.goguma.domain.user.entity.User;
import com.goguma.domain.user.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CommunityController {

    private final PostRepository postRepository;
    private final UserRepository userRepository;

    @GetMapping("/posts")
    public ResponseEntity<List<Map<String, Object>>> getPosts() {
        List<Post> posts = postRepository.findAllByOrderByIdDesc();
        List<Map<String, Object>> result = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        for (Post p : posts) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", p.getId());
            map.put("category", p.getCategory());
            map.put("title", p.getTitle());
            map.put("content", p.getContent());
            map.put("writer", p.getUser().getName());
            map.put("department", p.getUser().getDepartment().getDescription());
            map.put("createdAt", p.getCreatedAt() != null ? p.getCreatedAt().format(formatter) : "");
            map.put("likesCount", p.getLikes().size());
            map.put("commentsCount", p.getComments().size());
            map.put("isLiked", false);
            map.put("hasImage", p.getImageData() != null && !p.getImageData().isEmpty());
            result.add(map);
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/posts/add")
    public ResponseEntity<Map<String, Object>> addPost(@RequestBody PostAddRequest request, HttpSession session) {
        Long userId = (Long) session.getAttribute(UserController.SESSION_USER_ID);
        User user = (userId != null) ? userRepository.findById(userId).orElse(null) : null;
        if (user == null) {
            user = userRepository.findAll().stream().findFirst().orElse(null);
        }

        if (user == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "로그인이 필요합니다."));
        }

        Post post = Post.builder()
                .user(user)
                .category(request.getCategory() != null ? request.getCategory() : "출석인사")
                .title(request.getTitle() != null ? request.getTitle().trim() : "제목 없음")
                .content(request.getContent() != null ? request.getContent().trim() : "")
                .imageData(request.getImageData())
                .build();

        postRepository.save(post);
        return ResponseEntity.ok(Map.of("ok", true, "id", post.getId()));
    }

    @PostMapping("/reward/spin")
    public ResponseEntity<Map<String, Object>> spinRoulette() {
        return ResponseEntity.ok(Map.of("ok", true, "reward", "영양제 (+5 HP)", "rewardHp", 5));
    }
}
