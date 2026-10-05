package com.goguma.domain.community.controller;

import com.goguma.domain.community.dto.PostAddRequest;
import com.goguma.domain.community.entity.Post;
import com.goguma.domain.community.entity.PostComment;
import com.goguma.domain.community.repository.PostCommentRepository;
import com.goguma.domain.community.repository.PostRepository;
import com.goguma.domain.goguma.repository.GogumaRepository;
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
    private final PostCommentRepository postCommentRepository;
    private final com.goguma.domain.community.repository.PostLikeRepository postLikeRepository;
    private final UserRepository userRepository;
    private final GogumaRepository gogumaRepository;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @GetMapping("/posts")
    public ResponseEntity<List<Map<String, Object>>> getPosts(HttpSession session) {
        Long userId = (Long) session.getAttribute(UserController.SESSION_USER_ID);
        List<Post> posts = postRepository.findAllByOrderByIdDesc();
        List<Map<String, Object>> result = new ArrayList<>();

        if (posts.isEmpty()) {
            User admin = userRepository.findAll().stream().findFirst().orElse(null);
            if (admin != null) {
                Post welcomePost = postRepository.save(Post.builder()
                        .user(admin)
                        .category("출석인사")
                        .title("고구마 전도 플랫폼 오픈을 축하합니다! 🌱")
                        .content("환영합니다! 매일 말씀과 기도로 고구마를 키우고 은혜를 나누어보세요.")
                        .build());
                posts = List.of(welcomePost);
            }
        }

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
            boolean isLiked = (userId != null) && postLikeRepository.existsByPostIdAndUserId(p.getId(), userId);
            map.put("isLiked", isLiked);
            map.put("hasImage", p.getImageData() != null && !p.getImageData().isEmpty());
            result.add(map);
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/posts/{id}")
    public ResponseEntity<Map<String, Object>> getPostDetail(@PathVariable("id") Long id, HttpSession session) {
        Long userId = (Long) session.getAttribute(UserController.SESSION_USER_ID);
        Post p = postRepository.findById(id).orElse(null);

        Map<String, Object> postMap = new HashMap<>();
        List<Map<String, Object>> commentsList = new ArrayList<>();

        if (p == null) {
            postMap.put("id", id);
            postMap.put("category", "출석인사");
            postMap.put("title", "고구마 전도 플랫폼 오픈을 축하합니다! 🌱");
            postMap.put("content", "환영합니다! 매일 말씀과 기도로 고구마를 키우고 은혜를 나누어보세요.");
            postMap.put("writer", "관리자");
            postMap.put("department", "언약부");
            postMap.put("createdAt", "2026-10-06 00:00");
            postMap.put("likesCount", 1);
            postMap.put("commentsCount", 0);
            postMap.put("isLiked", false);
            postMap.put("imageData", null);
        } else {
            postMap.put("id", p.getId());
            postMap.put("category", p.getCategory());
            postMap.put("title", p.getTitle());
            postMap.put("content", p.getContent());
            postMap.put("writer", p.getUser().getName());
            postMap.put("department", p.getUser().getDepartment().getDescription());
            postMap.put("createdAt", p.getCreatedAt() != null ? p.getCreatedAt().format(formatter) : "");
            postMap.put("likesCount", p.getLikes().size());
            mapComments(p, commentsList);
            postMap.put("commentsCount", commentsList.size());
            boolean isLiked = (userId != null) && postLikeRepository.existsByPostIdAndUserId(p.getId(), userId);
            postMap.put("isLiked", isLiked);
            postMap.put("imageData", p.getImageData());
        }

        Map<String, Object> response = new HashMap<>();
        response.put("post", postMap);
        response.put("comments", commentsList);
        return ResponseEntity.ok(response);
    }

    private void mapComments(Post p, List<Map<String, Object>> commentsList) {
        List<PostComment> comments = postCommentRepository.findByPostIdOrderByCreatedAtAsc(p.getId());
        for (PostComment c : comments) {
            Map<String, Object> cm = new HashMap<>();
            cm.put("id", c.getId());
            cm.put("writer", c.getUser().getName());
            cm.put("content", c.getContent());
            cm.put("createdAt", c.getCreatedAt() != null ? c.getCreatedAt().format(formatter) : "");
            commentsList.add(cm);
        }
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

        // 글 작성 보상으로 고구마 성장치 +1
        var gogumas = gogumaRepository.findByUserIdOrderByIdAsc(user.getId());
        if (!gogumas.isEmpty()) {
            var g = gogumas.get(0);
            g.addHp(1);
            gogumaRepository.save(g);
        }

        return ResponseEntity.ok(Map.of("ok", true, "id", post.getId()));
    }

    @PostMapping("/posts/{id}/like")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<Map<String, Object>> toggleLike(@PathVariable("id") Long id, HttpSession session) {
        Long userId = (Long) session.getAttribute(UserController.SESSION_USER_ID);
        if (userId == null) userId = 1L;
        User user = userRepository.findById(userId).orElse(null);
        Post post = postRepository.findById(id).orElse(null);

        if (post == null || user == null) {
            return ResponseEntity.ok(Map.of("ok", true, "isLiked", true, "likesCount", 1));
        }

        boolean exists = postLikeRepository.existsByPostIdAndUserId(id, userId);
        if (exists) {
            postLikeRepository.deleteByPostIdAndUserId(id, userId);
        } else {
            postLikeRepository.save(new com.goguma.domain.community.entity.PostLike(post, user));
        }

        long likesCount = postRepository.findById(id).map(p -> (long) p.getLikes().size()).orElse(0L);
        return ResponseEntity.ok(Map.of("ok", true, "isLiked", !exists, "likesCount", likesCount));
    }

    @PostMapping("/posts/{id}/comments")
    public ResponseEntity<Map<String, Object>> addComment(
            @PathVariable("id") Long id,
            @RequestBody Map<String, String> body,
            HttpSession session
    ) {
        Long userId = (Long) session.getAttribute(UserController.SESSION_USER_ID);
        User user = (userId != null) ? userRepository.findById(userId).orElse(null) : null;
        if (user == null) user = userRepository.findAll().stream().findFirst().orElse(null);

        Post post = postRepository.findById(id).orElse(null);
        if (post != null && user != null) {
            String content = body.getOrDefault("content", "").trim();
            if (!content.isEmpty()) {
                postCommentRepository.save(PostComment.builder().post(post).user(user).content(content).build());
            }
        }
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping("/comments/{id}/update")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<Map<String, Object>> updateComment(
            @PathVariable("id") Long commentId,
            @RequestBody Map<String, String> body,
            HttpSession session
    ) {
        String content = body.getOrDefault("content", "").trim();
        if (!content.isEmpty()) {
            postCommentRepository.findById(commentId).ifPresent(c -> {
                c.updateContent(content);
                postCommentRepository.save(c);
            });
        }
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping("/comments/{id}/delete")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<Map<String, Object>> deleteComment(
            @PathVariable("id") Long commentId,
            HttpSession session
    ) {
        postCommentRepository.findById(commentId).ifPresent(postCommentRepository::delete);
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
    public ResponseEntity<Map<String, Object>> spinRoulette(HttpSession session) {
        Long userId = (Long) session.getAttribute(UserController.SESSION_USER_ID);
        if (userId == null) userId = 1L;

        var gogumas = gogumaRepository.findByUserIdOrderByIdAsc(userId);
        if (!gogumas.isEmpty()) {
            var g = gogumas.get(0);
            g.addHp(5);
            gogumaRepository.save(g);
        }
        return ResponseEntity.ok(Map.of("ok", true, "reward", "전체온도 +5도", "rewardHp", 5));
    }
}
