package com.charging.scheduler.repository;

import com.charging.scheduler.model.PowerMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PowerMetricRepository extends JpaRepository<PowerMetric, Long> {

    List<PowerMetric> findByPileIdAndCreatedAtAfterOrderByCreatedAtDesc(String pileId, LocalDateTime createdAt);

    @Query("SELECT pm FROM PowerMetric pm WHERE pm.createdAt >= :since ORDER BY pm.createdAt DESC")
    List<PowerMetric> findAllSince(@Param("since") LocalDateTime since);

    @Query("SELECT pm FROM PowerMetric pm WHERE pm.pileId = :pileId AND pm.createdAt >= :since ORDER BY pm.power DESC")
    List<PowerMetric> findMaxPowerByPileIdSince(@Param("pileId") String pileId, @Param("since") LocalDateTime since);
}
