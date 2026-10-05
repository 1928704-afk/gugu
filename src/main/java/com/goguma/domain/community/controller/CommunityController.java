package com.goguma.domain.community.controller;

import com.goguma.domain.community.dto.PostAddRequest;
import com.goguma.domain.community.entity.Post;
import com.goguma.domain.community.entity.PostComment;
import com.goguma.domain.community.entity.PostLike;
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
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @GetMapping("/posts")
    public ResponseEntity<List<Map<String, Object>>> getPosts() {
        List<Post> posts = postRepository.findAllByOrderByIdDesc();
        List<Map<String, Object>> result = new ArrayList<>();

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

    @GetMapping("/posts/{id}")
    public ResponseEntity<Map<String, Object>> getPostDetail(@PathVariable("id") Long id) {
        Post p = postRepository.findById(id).orElse(null);
        if (p == null) {
            // 데모용 가상 게시글 반환 (404 방지)
            Map<String, Object> demo = new HashMap<>();
            demo.put("id", id);
            demo.put("category", "출석인사");
            demo.put("title", "고구마 전도 플랫폼 오픈!");
            demo.put("content", "환영합니다! 함께 고구마를 키우며 미션을 수행해보세요.");
            demo.put("writer", "관리자");
            demo.put("department", "언약부");
            demo.put("createdAt", "2026-10-06 00:00");
            demo.put("likesCount", 1);
            demo.put("commentsCount", 0);
            demo.put("isLiked", false);
            demo.put("comments", Collections.emptyList());
            return ResponseEntity.ok(demo);
        }

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
        map.put("imageData", p.getImageData());

        List<Map<String, Object>> comments = new ArrayList<>();
        for (PostComment c : p.getComments()) {
            Map<String, Object> cm = new HashMap<>();
            cm.put("id", c.getId());
            cm.put("writer", c.getUser().getName());
            cm.put("content", c.getContent());
            cm.put("createdAt", c.getCreatedAt() != null ? c.getCreatedAt().format(formatter) : "");
            comments.add(cm);
        }
        map.put("comments", comments);

        return ResponseEntity.ok(map);
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

    @PostMapping("/posts/{id}/like")
    public ResponseEntity<Map<String, Object>> toggleLike(@PathVariable("id") Long id) {
        return ResponseEntity.ok(Map.of("ok", true, "isLiked", true, "likesCount", 1));
    }

    @PostMapping("/posts/{id}/comments")
    public ResponseEntity<Map<String, Object>> addComment(
            @PathVariable("id") Long id,
            @RequestBody Map<String, String> body,
            HttpSession session
    ) {
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping("/posts/delete")
    public ResponseEntity<Map<String, Object>> deletePost(@RequestBody Map<String, Long> body) {
        Long postId = body.get("id");
        if (postId != null) {
            postRepository.findById(postId).ifPresent(postRepository::delete);
        }
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping("/reward/spin")
    public ResponseEntity<Map<String, Object>> spinRoulette() {
        return ResponseEntity.ok(Map.of("ok", true, "reward", "영양제 (+5 HP)", "rewardHp", 5));
    }
}
