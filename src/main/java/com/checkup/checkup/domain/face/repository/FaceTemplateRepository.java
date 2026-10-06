package com.checkup.checkup.domain.face.repository;

import com.checkup.checkup.domain.face.entity.FaceTemplate;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 학생별 얼굴 등록 템플릿(대표 벡터). 학생마다 하나다. */
public interface FaceTemplateRepository extends JpaRepository<FaceTemplate, Long> {
    boolean existsByStudent_Id(Long studentId);

    Optional<FaceTemplate> findByStudent_Id(Long studentId);

    @EntityGraph(attributePaths = "student")
    List<FaceTemplate> findAllByOrderByStudent_IdAsc();

    @EntityGraph(attributePaths = "student")
    List<FaceTemplate> findAllByStudent_IdIn(Collection<Long> studentIds);

    void deleteByStudent_Id(Long studentId);
}
