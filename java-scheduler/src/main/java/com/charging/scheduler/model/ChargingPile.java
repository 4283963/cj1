package com.charging.scheduler.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "charging_piles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChargingPile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pile_id", unique = true, nullable = false)
    private String pileId;

    @Column(name = "station_id")
    private String stationId;

    @Column(name = "max_current_amps")
    @Builder.Default
    private Double maxCurrentAmps = 250.0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    @Builder.Default
    private PileStatus status = PileStatus.OFFLINE;

    @Column(name = "last_heartbeat")
    private LocalDateTime lastHeartbeat;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (lastHeartbeat == null) {
            lastHeartbeat = LocalDateTime.now();
        }
    }

    public enum PileStatus {
        ONLINE,
        OFFLINE,
        FAULT
    }
}
