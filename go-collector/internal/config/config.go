package config

import (
	"os"
	"strconv"

	"github.com/joho/godotenv"
)

type DBConfig struct {
	Host     string
	Port     int
	User     string
	Password string
	Name     string
}

type MQTTConfig struct {
	Broker   string
	User     string
	Password string
}

type ServerConfig struct {
	Port      int
	StationID string
}

type Config struct {
	DB     DBConfig
	MQTT   MQTTConfig
	Server ServerConfig
}

func LoadConfig() (*Config, error) {
	_ = godotenv.Load()

	dbPort, err := strconv.Atoi(getEnv("DB_PORT", "5432"))
	if err != nil {
		return nil, err
	}

	serverPort, err := strconv.Atoi(getEnv("SERVER_PORT", "8081"))
	if err != nil {
		return nil, err
	}

	cfg := &Config{
		DB: DBConfig{
			Host:     getEnv("DB_HOST", "localhost"),
			Port:     dbPort,
			User:     getEnv("DB_USER", "postgres"),
			Password: getEnv("DB_PASS", ""),
			Name:     getEnv("DB_NAME", "charging"),
		},
		MQTT: MQTTConfig{
			Broker:   getEnv("MQTT_BROKER", "tcp://localhost:1883"),
			User:     getEnv("MQTT_USER", ""),
			Password: getEnv("MQTT_PASS", ""),
		},
		Server: ServerConfig{
			Port:      serverPort,
			StationID: getEnv("STATION_ID", "station-001"),
		},
	}

	return cfg, nil
}

func getEnv(key, fallback string) string {
	if value, ok := os.LookupEnv(key); ok {
		return value
	}
	return fallback
}
