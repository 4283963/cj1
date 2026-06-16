package com.charging.scheduler.dto;

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
public class ScheduleResponse {

    @Builder.Default
    private Boolean success = true;

    private String message;

    private Double maxTotalPowerKw;

    private Double currentTotalPowerKw;

    @Builder.Default
    private List<PileStatusDTO> pileStatuses = new ArrayList<>();

    @Builder.Default
    private List<String> alerts = new ArrayList<>();

    private LocalDateTime timestamp;

    private TransitionInfoDTO transition;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PileStatusDTO {

        private String pileId;

        private Double currentPowerKw;

        private Double originalMaxCurrentAmps;

        private Double limitedMaxCurrentAmps;

        private Boolean isLimited;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransitionInfoDTO {
        private boolean inProgress;
        private Double startPowerKw;
        private Double targetPowerKw;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private Integer currentStep;
        private Integer totalSteps;
        private Double progressPercent;
        private List<Double> forecastPowers;
        private String reason;
    }
}
