package com.nestcart.repository;

import com.nestcart.entity.AlertRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AlertRecordRepository extends JpaRepository<AlertRecord, UUID> {

    List<AlertRecord> findByCartIdOrderByCreatedAtDesc(UUID cartId);

    List<AlertRecord> findByAcknowledgedFalseOrderByCreatedAtDesc();

    List<AlertRecord> findByAlertTypeOrderByCreatedAtDesc(String alertType);

    long countByAcknowledgedFalse();
}
