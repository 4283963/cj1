package main

import (
	"context"
	"fmt"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"charging-collector/internal/config"
	"charging-collector/internal/db"
	"charging-collector/internal/handler"
	mqttclient "charging-collector/internal/mqtt"
)

func main() {
	log.Println("[Main] Starting charging collector service...")

	cfg, err := config.LoadConfig()
	if err != nil {
		log.Fatalf("[Main] Failed to load config: %v", err)
	}
	log.Println("[Main] Configuration loaded successfully")

	database, err := db.InitDB(cfg.DB)
	if err != nil {
		log.Fatalf("[Main] Failed to initialize database: %v", err)
	}
	defer func() {
		log.Println("[Main] Closing database connection...")
		if err := database.Close(); err != nil {
			log.Printf("[Main] Error closing database: %v", err)
		}
	}()
	log.Println("[Main] Database initialized successfully")

	mqtt, err := mqttclient.InitMQTT(cfg.MQTT, database, cfg.Server.StationID)
	if err != nil {
		log.Fatalf("[Main] Failed to initialize MQTT client: %v", err)
	}
	defer func() {
		log.Println("[Main] Disconnecting MQTT client...")
		mqtt.Disconnect()
	}()
	log.Println("[Main] MQTT client initialized successfully")

	h := handler.NewHandler()
	router := handler.SetupRouter(h)

	addr := fmt.Sprintf(":%d", cfg.Server.Port)
	srv := &http.Server{
		Addr:         addr,
		Handler:      router,
		ReadTimeout:  15 * time.Second,
		WriteTimeout: 15 * time.Second,
		IdleTimeout:  60 * time.Second,
	}

	go func() {
		log.Printf("[Main] HTTP server starting on port %d", cfg.Server.Port)
		if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			log.Fatalf("[Main] HTTP server failed: %v", err)
		}
	}()

	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
	sig := <-quit
	log.Printf("[Main] Received signal: %v, shutting down...", sig)

	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	if err := srv.Shutdown(ctx); err != nil {
		log.Printf("[Main] HTTP server shutdown error: %v", err)
	}

	log.Println("[Main] Service stopped gracefully")
}
