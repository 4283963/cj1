package com.charging.scheduler.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LimitRequest {

    @NotNull(message = "最大总功率不能为空")
    @DecimalMin(value = "0.1", message = "最大总功率必须大于0")
    private Double maxTotalPowerKw;

    @Valid
    @Builder.Default
    private List<PileLimitDTO> pileLimits = new ArrayList<>();
}
