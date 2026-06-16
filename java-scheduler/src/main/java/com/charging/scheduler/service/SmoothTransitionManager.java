package com.charging.scheduler.service;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@Slf4j
@Component
public class SmoothTransitionManager {

    private static final int STEP_INTERVAL_SECONDS = 10;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "smooth-transition-scheduler");
        t.setDaemon(true);
        return t;
    });

    private final AtomicReference<TransitionState> currentState = new AtomicReference<>();
    private final AtomicReference<ScheduledFuture<?>> scheduledTask = new AtomicReference<>();
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    private Consumer<Double> stepCallback;

    @Data
    @Builder
    public static class TransitionState {
        private double startPowerKw;
        private double targetPowerKw;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private int totalSteps;
        private int currentStep;
        private boolean inProgress;
        private String reason;
    }

    public void setStepCallback(Consumer<Double> callback) {
        this.stepCallback = callback;
    }

    public synchronized void startTransition(double startPowerKw, double targetPowerKw, int transitionMinutes, String reason) {
        if (transitionMinutes <= 0) {
            log.info("Transition time is 0, applying target power immediately");
            if (stepCallback != null) {
                stepCallback.accept(targetPowerKw);
            }
            currentState.set(TransitionState.builder()
                    .startPowerKw(targetPowerKw)
                    .targetPowerKw(targetPowerKw)
                    .startTime(LocalDateTime.now())
                    .endTime(LocalDateTime.now())
                    .totalSteps(1)
                    .currentStep(1)
                    .inProgress(false)
                    .reason(reason)
                    .build());
            return;
        }

        cancelCurrentTransition();

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endTime = now.plusMinutes(transitionMinutes);
        long totalSeconds = (long) transitionMinutes * 60;
        int totalSteps = Math.max((int) (totalSeconds / STEP_INTERVAL_SECONDS), 1);

        TransitionState state = TransitionState.builder()
                .startPowerKw(startPowerKw)
                .targetPowerKw(targetPowerKw)
                .startTime(now)
                .endTime(endTime)
                .totalSteps(totalSteps)
                .currentStep(0)
                .inProgress(true)
                .reason(reason)
                .build();

        currentState.set(state);
        isRunning.set(true);

        log.info("Starting smooth transition: {}kW -> {}kW over {} minutes ({} steps)",
                startPowerKw, targetPowerKw, transitionMinutes, totalSteps);

        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
                () -> executeStep(),
                STEP_INTERVAL_SECONDS,
                STEP_INTERVAL_SECONDS,
                TimeUnit.SECONDS
        );
        scheduledTask.set(future);

        if (stepCallback != null) {
            stepCallback.accept(startPowerKw);
        }
    }

    private void executeStep() {
        try {
            TransitionState state = currentState.get();
            if (state == null || !state.isInProgress()) {
                return;
            }

            int nextStep = state.getCurrentStep() + 1;
            double progress = (double) nextStep / state.getTotalSteps();
            progress = Math.min(progress, 1.0);

            double easedProgress = easeInOutCubic(progress);
            double currentPower = state.getStartPowerKw() +
                    (state.getTargetPowerKw() - state.getStartPowerKw()) * easedProgress;

            state.setCurrentStep(nextStep);

            if (nextStep >= state.getTotalSteps()) {
                currentPower = state.getTargetPowerKw();
                state.setInProgress(false);
                state.setCurrentStep(state.getTotalSteps());
                isRunning.set(false);
                cancelScheduledTask();
                log.info("Smooth transition completed: reached target power {}kW", state.getTargetPowerKw());
            }

            log.debug("Transition step {}/{}: progress={}, currentPower={}kW",
                    nextStep, state.getTotalSteps(),
                    String.format("%.2f%%", progress * 100),
                    String.format("%.2f", currentPower));

            if (stepCallback != null) {
                stepCallback.accept(currentPower);
            }

        } catch (Exception e) {
            log.error("Error executing smooth transition step", e);
        }
    }

    public synchronized void cancelCurrentTransition() {
        cancelScheduledTask();
        TransitionState state = currentState.get();
        if (state != null && state.isInProgress()) {
            state.setInProgress(false);
            log.info("Smooth transition cancelled at step {}/{}", state.getCurrentStep(), state.getTotalSteps());
        }
        isRunning.set(false);
    }

    private void cancelScheduledTask() {
        ScheduledFuture<?> future = scheduledTask.getAndSet(null);
        if (future != null && !future.isDone()) {
            future.cancel(false);
        }
    }

    public TransitionState getCurrentState() {
        return currentState.get();
    }

    public boolean isTransitionInProgress() {
        return isRunning.get();
    }

    public List<Double> generateForecastPoints(int numPoints) {
        TransitionState state = currentState.get();
        List<Double> points = new ArrayList<>();

        if (state == null || numPoints <= 0) {
            return points;
        }

        for (int i = 0; i <= numPoints; i++) {
            double progress = (double) i / numPoints;
            double easedProgress = easeInOutCubic(progress);
            double power = state.getStartPowerKw() +
                    (state.getTargetPowerKw() - state.getStartPowerKw()) * easedProgress;
            points.add(power);
        }

        return points;
    }

    private double easeInOutCubic(double t) {
        return t < 0.5
                ? 4 * t * t * t
                : 1 - Math.pow(-2 * t + 2, 3) / 2;
    }
}
