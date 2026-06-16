package com.charging.scheduler.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PileLimitDTO {

    @JsonProperty("pile_id")
    private String pileId;

    @JsonProperty("max_current_amps")
    private Double maxCurrentAmps;
}
