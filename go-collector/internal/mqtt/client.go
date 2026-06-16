package mqtt

import (
	"encoding/json"
	"fmt"
	"log"
	"strings"
	"time"

	"charging-collector/internal/config"
	"charging-collector/internal/db"

	mqttlib "github.com/eclipse/paho.mqtt.golang"
)

type TelemetryPayload struct {
	Current float64 `json:"current"`
	Voltage float64 `json:"voltage"`
}

type MQTTClient struct {
	client    mqttlib.Client
	db        *db.PostgresDB
	stationID string
}

func InitMQTT(cfg config.MQTTConfig, database *db.PostgresDB, stationID string) (*MQTTClient, error) {
	opts := mqttlib.NewClientOptions()
	opts.AddBroker(cfg.Broker)
	opts.SetClientID(fmt.Sprintf("charging-collector-%s-%d", stationID, time.Now().UnixNano()))
	opts.SetCleanSession(true)
	opts.SetAutoReconnect(true)
	opts.SetConnectRetry(true)
	opts.SetConnectRetryInterval(5 * time.Second)
	opts.SetMaxReconnectInterval(30 * time.Second)
	opts.SetKeepAlive(60 * time.Second)
	opts.SetPingTimeout(10 * time.Second)
	opts.SetWriteTimeout(10 * time.Second)

	if cfg.User != "" {
		opts.SetUsername(cfg.User)
	}
	if cfg.Password != "" {
		opts.SetPassword(cfg.Password)
	}

	opts.OnConnect = func(c mqttlib.Client) {
		log.Printf("[MQTT] Connected to broker: %s", cfg.Broker)
	}

	opts.OnConnectionLost = func(c mqttlib.Client, err error) {
		log.Printf("[MQTT] Connection lost: %v", err)
	}

	opts.OnReconnecting = func(c mqttlib.Client, o *mqttlib.ClientOptions) {
		log.Printf("[MQTT] Reconnecting to broker...")
	}

	client := mqttlib.NewClient(opts)

	mc := &MQTTClient{
		client:    client,
		db:        database,
		stationID: stationID,
	}

	connectToken := client.Connect()
	if ok := connectToken.WaitTimeout(30 * time.Second); !ok {
		return nil, fmt.Errorf("mqtt connect timeout")
	}
	if connectToken.Error() != nil {
		return nil, fmt.Errorf("mqtt connect failed: %w", connectToken.Error())
	}

	if err := mc.subscribe(); err != nil {
		return nil, err
	}

	return mc, nil
}

func (m *MQTTClient) subscribe() error {
	topic := "charging/+/telemetry"
	token := m.client.Subscribe(topic, 1, m.messageHandler)
	if ok := token.WaitTimeout(10 * time.Second); !ok {
		return fmt.Errorf("subscribe timeout for topic: %s", topic)
	}
	if token.Error() != nil {
		return fmt.Errorf("subscribe failed: %w", token.Error())
	}
	log.Printf("[MQTT] Subscribed to topic: %s", topic)
	return nil
}

func (m *MQTTClient) messageHandler(client mqttlib.Client, msg mqttlib.Message) {
	topic := msg.Topic()
	pileID, err := extractPileID(topic)
	if err != nil {
		log.Printf("[MQTT] Failed to extract pile_id from topic %s: %v", topic, err)
		return
	}

	var payload TelemetryPayload
	if err := json.Unmarshal(msg.Payload(), &payload); err != nil {
		log.Printf("[MQTT] Failed to unmarshal payload from %s: %v", topic, err)
		return
	}

	if err := m.db.InsertMetric(m.stationID, pileID, payload.Current, payload.Voltage); err != nil {
		log.Printf("[MQTT] Failed to insert metric for pile %s: %v", pileID, err)
		return
	}

	powerKW := payload.Current * payload.Voltage / 1000
	log.Printf("[MQTT] Received telemetry - pile: %s, current: %.2fA, voltage: %.2fV, power: %.3fkW",
		pileID, payload.Current, payload.Voltage, powerKW)
}

func extractPileID(topic string) (string, error) {
	parts := strings.Split(topic, "/")
	if len(parts) != 3 {
		return "", fmt.Errorf("invalid topic format")
	}
	return parts[1], nil
}

func (m *MQTTClient) Disconnect() {
	if m.client != nil {
		m.client.Disconnect(250)
		log.Printf("[MQTT] Disconnected")
	}
}
