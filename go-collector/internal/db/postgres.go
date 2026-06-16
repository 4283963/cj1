package db

import (
	"database/sql"
	"fmt"
	"time"

	"charging-collector/internal/config"

	_ "github.com/lib/pq"
)

type PostgresDB struct {
	conn *sql.DB
}

type ChargingMetric struct {
	ID          int64
	StationID   string
	PileID      string
	CurrentAmps float64
	Voltage     float64
	PowerKW     float64
	CreatedAt   time.Time
}

var createTableSQL = `
CREATE TABLE IF NOT EXISTS charging_metrics (
	id SERIAL PRIMARY KEY,
	station_id VARCHAR(255) NOT NULL,
	pile_id VARCHAR(255) NOT NULL,
	current_amps FLOAT NOT NULL,
	voltage FLOAT NOT NULL,
	power_kw FLOAT GENERATED ALWAYS AS (current_amps * voltage / 1000) STORED,
	created_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_charging_metrics_pile_id ON charging_metrics(pile_id);
CREATE INDEX IF NOT EXISTS idx_charging_metrics_created_at ON charging_metrics(created_at);
`

func InitDB(cfg config.DBConfig) (*PostgresDB, error) {
	dsn := fmt.Sprintf(
		"host=%s port=%d user=%s password=%s dbname=%s sslmode=disable",
		cfg.Host, cfg.Port, cfg.User, cfg.Password, cfg.Name,
	)

	conn, err := sql.Open("postgres", dsn)
	if err != nil {
		return nil, fmt.Errorf("failed to open db: %w", err)
	}

	conn.SetMaxOpenConns(25)
	conn.SetMaxIdleConns(5)
	conn.SetConnMaxLifetime(5 * time.Minute)

	if err := conn.Ping(); err != nil {
		return nil, fmt.Errorf("failed to ping db: %w", err)
	}

	db := &PostgresDB{conn: conn}

	if err := db.ensureTable(); err != nil {
		return nil, err
	}

	return db, nil
}

func (p *PostgresDB) ensureTable() error {
	_, err := p.conn.Exec(createTableSQL)
	if err != nil {
		return fmt.Errorf("failed to create table: %w", err)
	}
	return nil
}

func (p *PostgresDB) InsertMetric(stationID, pileID string, currentAmps, voltage float64) error {
	query := `
		INSERT INTO charging_metrics (station_id, pile_id, current_amps, voltage)
		VALUES ($1, $2, $3, $4)
	`
	_, err := p.conn.Exec(query, stationID, pileID, currentAmps, voltage)
	if err != nil {
		return fmt.Errorf("failed to insert metric: %w", err)
	}
	return nil
}

func (p *PostgresDB) Close() error {
	if p.conn != nil {
		return p.conn.Close()
	}
	return nil
}
