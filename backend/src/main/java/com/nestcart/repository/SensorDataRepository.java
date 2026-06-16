package com.nestcart.repository;

import com.nestcart.entity.SensorData;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface SensorDataRepository extends JpaRepository<SensorData, UUID> {

    List<SensorData> findByCartIdOrderByTimestampDesc(UUID cartId);

    List<SensorData> findByCartIdAndTimestampBetweenOrderByTimestampDesc(
            UUID cartId, OffsetDateTime start, OffsetDateTime end);

    @Query("SELECT sd FROM SensorData sd WHERE sd.cartId = :cartId ORDER BY sd.timestamp DESC")
    List<SensorData> findLatestByCartId(@Param("cartId") UUID cartId, Pageable pageable);

    @Query("SELECT sd FROM SensorData sd WHERE sd.cartId = :cartId AND sd.timestamp >= :since ORDER BY sd.timestamp ASC")
    List<SensorData> findSinceByCartId(@Param("cartId") UUID cartId, @Param("since") OffsetDateTime since);
}
