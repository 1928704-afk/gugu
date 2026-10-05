package com.goguma.domain.goguma.repository;

import com.goguma.domain.goguma.entity.Goguma;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GogumaRepository extends JpaRepository<Goguma, Long> {
    List<Goguma> findByUserIdOrderByIdAsc(Long userId);
}
