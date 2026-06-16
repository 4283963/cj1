package com.charging.scheduler.repository;

import com.charging.scheduler.model.ChargingPile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChargingPileRepository extends JpaRepository<ChargingPile, Long> {

    Optional<ChargingPile> findByPileId(String pileId);

    List<ChargingPile> findByStatus(ChargingPile.PileStatus status);

    @Query("SELECT p FROM ChargingPile p WHERE p.status = 'ONLINE'")
    List<ChargingPile> findAllOnlinePiles();

    boolean existsByPileId(String pileId);

    void deleteByPileId(String pileId);
}
