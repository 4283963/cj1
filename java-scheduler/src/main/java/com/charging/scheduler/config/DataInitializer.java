package com.charging.scheduler.config;

import com.charging.scheduler.model.ChargingPile;
import com.charging.scheduler.repository.ChargingPileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final ChargingPileRepository chargingPileRepository;

    @Override
    public void run(String... args) {
        if (chargingPileRepository.count() > 0) {
            log.info("Charging piles already exist, skipping initialization");
            return;
        }

        List<ChargingPile> defaultPiles = Arrays.asList(
                ChargingPile.builder()
                        .pileId("pile-001")
                        .stationId("station-001")
                        .maxCurrentAmps(250.0)
                        .status(ChargingPile.PileStatus.ONLINE)
                        .build(),
                ChargingPile.builder()
                        .pileId("pile-002")
                        .stationId("station-001")
                        .maxCurrentAmps(250.0)
                        .status(ChargingPile.PileStatus.ONLINE)
                        .build(),
                ChargingPile.builder()
                        .pileId("pile-003")
                        .stationId("station-001")
                        .maxCurrentAmps(250.0)
                        .status(ChargingPile.PileStatus.ONLINE)
                        .build(),
                ChargingPile.builder()
                        .pileId("pile-004")
                        .stationId("station-001")
                        .maxCurrentAmps(250.0)
                        .status(ChargingPile.PileStatus.ONLINE)
                        .build(),
                ChargingPile.builder()
                        .pileId("pile-005")
                        .stationId("station-001")
                        .maxCurrentAmps(250.0)
                        .status(ChargingPile.PileStatus.ONLINE)
                        .build(),
                ChargingPile.builder()
                        .pileId("pile-006")
                        .stationId("station-001")
                        .maxCurrentAmps(250.0)
                        .status(ChargingPile.PileStatus.OFFLINE)
                        .build()
        );

        chargingPileRepository.saveAll(defaultPiles);
        log.info("Initialized {} default charging piles", defaultPiles.size());
    }
}
