package com.charging.scheduler.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmergencyLimitRequest {

    @NotNull(message = "紧急最大总功率不能为空")
    @DecimalMin(value = "0.1", message = "紧急最大总功率必须大于0")
    private Double maxTotalPowerKw;

    @NotBlank(message = "紧急限电原因不能为空")
    private String reason;

    @Min(value = 0, message = "过渡时间不能为负数")
    @Builder.Default
    private Integer transitionMinutes = 0;
}
