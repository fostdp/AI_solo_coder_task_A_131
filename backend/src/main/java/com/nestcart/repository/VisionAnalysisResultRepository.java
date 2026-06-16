package com.nestcart.repository;

import com.nestcart.entity.VisionAnalysisResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface VisionAnalysisResultRepository extends JpaRepository<VisionAnalysisResult, UUID> {

    List<VisionAnalysisResult> findByCartIdOrderByCreatedAtDesc(UUID cartId);

    List<VisionAnalysisResult> findByCartIdAndHeight(UUID cartId, Double height);
}
