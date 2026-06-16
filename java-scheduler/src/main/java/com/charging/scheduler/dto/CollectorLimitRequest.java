package com.charging.scheduler.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CollectorLimitRequest {

    @Builder.Default
    @JsonProperty("pile_limits")
    private List<PileLimitDTO> pileLimits = new ArrayList<>();

    @JsonProperty("max_total_power_kw")
    private Double maxTotalPowerKw;

    private String reason;

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
}
