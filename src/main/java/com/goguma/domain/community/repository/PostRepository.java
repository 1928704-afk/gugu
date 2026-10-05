package com.goguma.domain.community.repository;

import com.goguma.domain.community.entity.Post;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PostRepository extends JpaRepository<Post, Long> {
    List<Post> findAllByOrderByIdDesc();
    long countByUserId(Long userId);
}
