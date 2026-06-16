package com.charging.scheduler.controller;

import com.charging.scheduler.dto.EmergencyLimitRequest;
import com.charging.scheduler.dto.LimitRequest;
import com.charging.scheduler.dto.ScheduleResponse;
import com.charging.scheduler.model.ChargingPile;
import com.charging.scheduler.repository.ChargingPileRepository;
import com.charging.scheduler.service.ScheduleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;
    private final ChargingPileRepository chargingPileRepository;

    @PostMapping("/schedule/limit")
    public ResponseEntity<ScheduleResponse> manualLimit(@Valid @RequestBody LimitRequest request) {
        log.info("Received manual limit request: {}kW", request.getMaxTotalPowerKw());
        ScheduleResponse response = scheduleService.manualLimit(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/schedule/emergency")
    public ResponseEntity<ScheduleResponse> emergencyLimit(@Valid @RequestBody EmergencyLimitRequest request) {
        log.warn("Received emergency limit request: {}kW, reason: {}", request.getMaxTotalPowerKw(), request.getReason());
        ScheduleResponse response = scheduleService.emergencyLimit(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/schedule/reset")
    public ResponseEntity<ScheduleResponse> resetSchedule() {
        log.info("Received schedule reset request");
        scheduleService.resetToDefault();
        return ResponseEntity.ok(scheduleService.getStatus());
    }

    @GetMapping("/schedule/status")
    public ResponseEntity<ScheduleResponse> getStatus() {
        ScheduleResponse response = scheduleService.getStatus();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/piles")
    public ResponseEntity<List<ChargingPile>> getAllPiles() {
        List<ChargingPile> piles = chargingPileRepository.findAll();
        return ResponseEntity.ok(piles);
    }

    @PostMapping("/piles")
    public ResponseEntity<ChargingPile> createPile(@RequestBody ChargingPile pile) {
        if (chargingPileRepository.existsByPileId(pile.getPileId())) {
            return ResponseEntity.badRequest().build();
        }
        ChargingPile saved = chargingPileRepository.save(pile);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/piles/{pileId}")
    public ResponseEntity<ChargingPile> updatePile(@PathVariable String pileId, @RequestBody ChargingPile pile) {
        return chargingPileRepository.findByPileId(pileId)
                .map(existing -> {
                    existing.setMaxCurrentAmps(pile.getMaxCurrentAmps());
                    existing.setStatus(pile.getStatus());
                    existing.setStationId(pile.getStationId());
                    ChargingPile saved = chargingPileRepository.save(existing);
                    return ResponseEntity.ok(saved);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/piles/{pileId}")
    public ResponseEntity<Void> deletePile(@PathVariable String pileId) {
        if (!chargingPileRepository.existsByPileId(pileId)) {
            return ResponseEntity.notFound().build();
        }
        chargingPileRepository.deleteByPileId(pileId);
        return ResponseEntity.noContent().build();
    }
}
