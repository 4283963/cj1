package com.charging.scheduler.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "charging_metrics")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PowerMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "station_id")
    private String stationId;

    @Column(name = "pile_id", nullable = false)
    private String pileId;

    @Column(name = "current_amps")
    private Double current;

    @Column(name = "voltage")
    private Double voltage;

    @Column(name = "power_kw", insertable = false, updatable = false)
    private Double power;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (power == null && current != null && voltage != null) {
            power = (current * voltage) / 1000.0;
        }
    }
}
