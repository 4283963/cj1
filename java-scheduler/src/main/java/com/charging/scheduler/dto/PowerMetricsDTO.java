package com.charging.scheduler.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PowerMetricsDTO {

    private String pileId;

    private Double current;

    private Double voltage;

    private Double power;

    private LocalDateTime timestamp;
}
