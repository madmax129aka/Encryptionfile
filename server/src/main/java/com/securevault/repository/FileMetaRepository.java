package com.securevault.repository;

import com.securevault.entity.FileMeta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FileMetaRepository extends JpaRepository<FileMeta, Long> {
    List<FileMeta> findByUserIdOrderByUploadedAtDesc(Long userId);

    Optional<FileMeta> findByIdAndUserId(Long id, Long userId);
}
