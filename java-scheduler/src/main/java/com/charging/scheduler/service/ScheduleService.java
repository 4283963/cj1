package com.charging.scheduler.service;

import com.charging.scheduler.dto.*;
import com.charging.scheduler.model.ChargingPile;
import com.charging.scheduler.model.PowerMetric;
import com.charging.scheduler.repository.ChargingPileRepository;
import com.charging.scheduler.repository.PowerMetricRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ScheduleService {

    private static final double DEFAULT_VOLTAGE = 400.0;
    private static final double FALLBACK_MAX_POWER = 1000.0;

    private final ChargingPileRepository chargingPileRepository;
    private final PowerMetricRepository powerMetricRepository;
    private final RestTemplate restTemplate;

    @Value("${scheduler.default-max-total-power-kw:1000}")
    private double defaultMaxTotalPowerKw;

    @Value("${collector.url:http://localhost:8081}")
    private String collectorUrl;

    private volatile Double currentMaxTotalPowerKw;

    private final Map<String, Double> activePileLimits = new ConcurrentHashMap<>();

    private volatile String lastEmergencyReason = null;

    public ScheduleService(ChargingPileRepository chargingPileRepository,
                           PowerMetricRepository powerMetricRepository,
                           RestTemplate restTemplate) {
        this.chargingPileRepository = chargingPileRepository;
        this.powerMetricRepository = powerMetricRepository;
        this.restTemplate = restTemplate;
    }

    @PostConstruct
    public void init() {
        this.currentMaxTotalPowerKw = defaultMaxTotalPowerKw > 0 ? defaultMaxTotalPowerKw : FALLBACK_MAX_POWER;
        log.info("ScheduleService initialized with default max total power: {}kW", this.currentMaxTotalPowerKw);
    }

    public Map<String, Double> getCurrentTotalPower() {
        LocalDateTime since = LocalDateTime.now().minusSeconds(10);
        List<ChargingPile> onlinePiles = chargingPileRepository.findAllOnlinePiles();
        Map<String, Double> pileMaxPower = new HashMap<>();

        for (ChargingPile pile : onlinePiles) {
            String pileId = pile.getPileId();
            if (pileId == null || pileId.trim().isEmpty()) {
                continue;
            }
            List<PowerMetric> metrics = powerMetricRepository.findMaxPowerByPileIdSince(pileId, since);
            if (!metrics.isEmpty() && metrics.get(0) != null) {
                Double maxPower = metrics.get(0).getPower();
                if (maxPower != null) {
                    pileMaxPower.put(pileId, Math.max(maxPower, 0.0));
                } else {
                    pileMaxPower.put(pileId, 0.0);
                }
            } else {
                pileMaxPower.put(pileId, 0.0);
            }
        }

        if (pileMaxPower.isEmpty() && !onlinePiles.isEmpty()) {
            for (ChargingPile pile : onlinePiles) {
                if (pile.getPileId() != null) {
                    pileMaxPower.put(pile.getPileId(), 0.0);
                }
            }
        }

        return pileMaxPower;
    }

    public ScheduleResponse allocatePower(Double maxTotalPowerKw, List<PileLimitDTO> pileLimits, String reason) {
        double effectiveMax;
        if (maxTotalPowerKw != null) {
            effectiveMax = maxTotalPowerKw;
        } else if (currentMaxTotalPowerKw != null) {
            effectiveMax = currentMaxTotalPowerKw;
        } else {
            effectiveMax = defaultMaxTotalPowerKw > 0 ? defaultMaxTotalPowerKw : FALLBACK_MAX_POWER;
        }

        if (effectiveMax <= 0) {
            effectiveMax = FALLBACK_MAX_POWER;
            log.warn("Effective max power was <= 0, using fallback: {}kW", effectiveMax);
        }

        Map<String, Double> currentPilePowers = getCurrentTotalPower();
        double totalCurrentPower = currentPilePowers.values().stream()
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum();

        List<ChargingPile> onlinePiles = chargingPileRepository.findAllOnlinePiles();
        Map<String, ChargingPile> pileMap = onlinePiles.stream()
                .filter(p -> p != null && p.getPileId() != null)
                .collect(Collectors.toMap(ChargingPile::getPileId, p -> p, (p1, p2) -> p1));

        Map<String, Double> originalMaxCurrentMap = new HashMap<>();
        Map<String, Double> limitedMaxCurrentMap = new HashMap<>();

        for (ChargingPile pile : onlinePiles) {
            originalMaxCurrentMap.put(pile.getPileId(), pile.getMaxCurrentAmps());
        }

        if (pileLimits != null && !pileLimits.isEmpty()) {
            for (PileLimitDTO limit : pileLimits) {
                if (pileMap.containsKey(limit.getPileId()) && limit.getMaxCurrentAmps() != null) {
                    limitedMaxCurrentMap.put(limit.getPileId(),
                            Math.min(limit.getMaxCurrentAmps(), originalMaxCurrentMap.get(limit.getPileId())));
                }
            }
        }

        if (totalCurrentPower > effectiveMax && totalCurrentPower > 0) {
            double scaleFactor = effectiveMax / totalCurrentPower;
            log.info("Total power {}kW exceeds limit {}kW, applying scale factor: {}",
                    totalCurrentPower, effectiveMax, scaleFactor);

            for (ChargingPile pile : onlinePiles) {
                String pileId = pile.getPileId();
                Double pilePower = currentPilePowers.getOrDefault(pileId, 0.0);
                double originalMaxCurrent = originalMaxCurrentMap.get(pileId);
                double limitedCurrent;

                if (pilePower > 0) {
                    double targetPower = pilePower * scaleFactor;
                    limitedCurrent = (targetPower * 1000.0) / DEFAULT_VOLTAGE;
                } else {
                    limitedCurrent = originalMaxCurrent * scaleFactor;
                }

                limitedCurrent = Math.max(0, Math.min(limitedCurrent, originalMaxCurrent));
                limitedMaxCurrentMap.merge(pileId, limitedCurrent, Math::min);
            }
        }

        List<PileLimitDTO> limitDtos = new ArrayList<>();
        List<ScheduleResponse.PileStatusDTO> pileStatusDtos = new ArrayList<>();

        for (ChargingPile pile : onlinePiles) {
            String pileId = pile.getPileId();
            double original = originalMaxCurrentMap.get(pileId);
            double limited = limitedMaxCurrentMap.getOrDefault(pileId, original);
            boolean isLimited = Math.abs(limited - original) > 0.01;

            limitDtos.add(PileLimitDTO.builder()
                    .pileId(pileId)
                    .maxCurrentAmps(limited)
                    .build());

            pileStatusDtos.add(ScheduleResponse.PileStatusDTO.builder()
                    .pileId(pileId)
                    .currentPowerKw(currentPilePowers.getOrDefault(pileId, 0.0))
                    .originalMaxCurrentAmps(original)
                    .limitedMaxCurrentAmps(limited)
                    .isLimited(isLimited)
                    .build());

            activePileLimits.put(pileId, limited);
        }

        CollectorLimitRequest collectorRequest = CollectorLimitRequest.builder()
                .pileLimits(limitDtos)
                .maxTotalPowerKw(effectiveMax)
                .reason(reason)
                .timestamp(LocalDateTime.now())
                .build();

        notifyCollector(collectorRequest);

        List<String> alerts = new ArrayList<>();
        if (totalCurrentPower > effectiveMax) {
            alerts.add(String.format("WARNING: Total power %.2fkW exceeds limit %.2fkW, power reduction applied",
                    totalCurrentPower, effectiveMax));
        }
        if (reason != null && !reason.isEmpty()) {
            alerts.add("EMERGENCY: " + reason);
        }

        currentMaxTotalPowerKw = effectiveMax;

        return ScheduleResponse.builder()
                .success(true)
                .message(totalCurrentPower > effectiveMax ? "Power limits applied" : "Within power limit")
                .maxTotalPowerKw(effectiveMax)
                .currentTotalPowerKw(totalCurrentPower)
                .pileStatuses(pileStatusDtos)
                .alerts(alerts)
                .timestamp(LocalDateTime.now())
                .build();
    }

    private void notifyCollector(CollectorLimitRequest request) {
        if (request == null) {
            log.warn("notifyCollector called with null request, skipping");
            return;
        }
        if (collectorUrl == null || collectorUrl.trim().isEmpty()) {
            log.warn("collectorUrl is not configured, skipping notification");
            return;
        }
        try {
            String url = collectorUrl + "/api/v1/limit";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<CollectorLimitRequest> entity = new HttpEntity<>(request, headers);

            log.info("Notifying collector at {} with {} pile limits, maxTotalPower={}kW",
                    url, request.getPileLimits() != null ? request.getPileLimits().size() : 0,
                    request.getMaxTotalPowerKw());

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, entity, String.class);

            log.info("Collector notified successfully, status: {}, response: {}",
                    response.getStatusCode(),
                    response.getBody() != null ? response.getBody().substring(0, Math.min(200, response.getBody().length())) : "empty");
        } catch (Exception e) {
            log.error("Failed to notify collector at {}: {}", collectorUrl, e.getMessage(), e);
        }
    }

    @Scheduled(fixedRate = 5000)
    public void schedule() {
        log.debug("Running scheduled power allocation...");
        try {
            allocatePower(currentMaxTotalPowerKw, null, null);
        } catch (Exception e) {
            log.error("Scheduled power allocation failed", e);
        }
    }

    public ScheduleResponse emergencyLimit(EmergencyLimitRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Emergency limit request cannot be null");
        }
        if (request.getMaxTotalPowerKw() == null || request.getMaxTotalPowerKw() <= 0) {
            throw new IllegalArgumentException("Invalid maxTotalPowerKw: " + request.getMaxTotalPowerKw());
        }
        log.warn("Emergency limit activated: {}kW, reason: {}", request.getMaxTotalPowerKw(), request.getReason());
        lastEmergencyReason = request.getReason();
        return allocatePower(request.getMaxTotalPowerKw(), null, request.getReason());
    }

    public ScheduleResponse manualLimit(LimitRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Limit request cannot be null");
        }
        if (request.getMaxTotalPowerKw() == null || request.getMaxTotalPowerKw() <= 0) {
            throw new IllegalArgumentException("Invalid maxTotalPowerKw: " + request.getMaxTotalPowerKw());
        }
        log.info("Manual limit triggered: {}kW", request.getMaxTotalPowerKw());
        lastEmergencyReason = null;
        return allocatePower(request.getMaxTotalPowerKw(), request.getPileLimits(), null);
    }

    public ScheduleResponse getStatus() {
        Map<String, Double> currentPilePowers = getCurrentTotalPower();
        double totalCurrentPower = currentPilePowers.values().stream()
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .sum();
        double effectiveMax;
        if (currentMaxTotalPowerKw != null) {
            effectiveMax = currentMaxTotalPowerKw;
        } else {
            effectiveMax = defaultMaxTotalPowerKw > 0 ? defaultMaxTotalPowerKw : FALLBACK_MAX_POWER;
        }

        List<ChargingPile> onlinePiles = chargingPileRepository.findAllOnlinePiles();
        List<ScheduleResponse.PileStatusDTO> pileStatusDtos = new ArrayList<>();

        for (ChargingPile pile : onlinePiles) {
            String pileId = pile.getPileId();
            double original = pile.getMaxCurrentAmps();
            double limited = activePileLimits.getOrDefault(pileId, original);
            boolean isLimited = Math.abs(limited - original) > 0.01;

            pileStatusDtos.add(ScheduleResponse.PileStatusDTO.builder()
                    .pileId(pileId)
                    .currentPowerKw(currentPilePowers.getOrDefault(pileId, 0.0))
                    .originalMaxCurrentAmps(original)
                    .limitedMaxCurrentAmps(limited)
                    .isLimited(isLimited)
                    .build());
        }

        List<String> alerts = new ArrayList<>();
        if (totalCurrentPower > effectiveMax) {
            alerts.add(String.format("WARNING: Total power %.2fkW exceeds limit %.2fkW",
                    totalCurrentPower, effectiveMax));
        }
        if (lastEmergencyReason != null) {
            alerts.add("EMERGENCY ACTIVE: " + lastEmergencyReason);
        }
        long offlineCount = chargingPileRepository.count() - onlinePiles.size();
        if (offlineCount > 0) {
            alerts.add(String.format("INFO: %d piles are offline/fault", offlineCount));
        }

        return ScheduleResponse.builder()
                .success(true)
                .message("Current status retrieved")
                .maxTotalPowerKw(effectiveMax)
                .currentTotalPowerKw(totalCurrentPower)
                .pileStatuses(pileStatusDtos)
                .alerts(alerts)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public void resetToDefault() {
        double effectiveDefault = defaultMaxTotalPowerKw > 0 ? defaultMaxTotalPowerKw : FALLBACK_MAX_POWER;
        currentMaxTotalPowerKw = effectiveDefault;
        lastEmergencyReason = null;
        activePileLimits.clear();
        log.info("Scheduler reset to default max total power: {}kW", effectiveDefault);
    }
}
