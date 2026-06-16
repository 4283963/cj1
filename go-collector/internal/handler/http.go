package handler

import (
	"bytes"
	"encoding/json"
	"io"
	"log"
	"net/http"
	"sync"

	"github.com/gin-gonic/gin"
)

type PileLimit struct {
	PileID         string  `json:"pile_id" binding:"required"`
	MaxCurrentAmps float64 `json:"max_current_amps" binding:"required,gte=0"`
}

type LimitConfig struct {
	MaxTotalPowerKW float64     `json:"max_total_power_kw" binding:"required,gte=0"`
	PileLimits      []PileLimit `json:"pile_limits" binding:"required,min=0"`
	Reason          string      `json:"reason"`
	Timestamp       string      `json:"timestamp"`
}

type LimitStore struct {
	mu     sync.RWMutex
	config *LimitConfig
}

func NewLimitStore() *LimitStore {
	return &LimitStore{
		config: &LimitConfig{
			MaxTotalPowerKW: 0,
			PileLimits:      []PileLimit{},
		},
	}
}

func (s *LimitStore) SetConfig(cfg *LimitConfig) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.config = cfg
}

func (s *LimitStore) GetConfig() *LimitConfig {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return s.config
}

type Handler struct {
	store *LimitStore
}

func NewHandler() *Handler {
	return &Handler{
		store: NewLimitStore(),
	}
}

func logRequestBody(c *gin.Context) []byte {
	body, err := io.ReadAll(c.Request.Body)
	if err != nil {
		log.Printf("[HTTP] Failed to read request body: %v", err)
		return nil
	}
	c.Request.Body = io.NopCloser(bytes.NewBuffer(body))

	var prettyJSON bytes.Buffer
	if err := json.Indent(&prettyJSON, body, "", "  "); err == nil {
		log.Printf("[HTTP] Received request to %s:\n%s", c.Request.URL.Path, prettyJSON.String())
	} else {
		log.Printf("[HTTP] Received request to %s: %s", c.Request.URL.Path, string(body))
	}

	return body
}

func (h *Handler) SetLimit(c *gin.Context) {
	logRequestBody(c)

	var req LimitConfig
	if err := c.ShouldBindJSON(&req); err != nil {
		log.Printf("[HTTP] Invalid limit request: %v", err)
		c.JSON(http.StatusBadRequest, gin.H{
			"error":   "invalid request body",
			"details": err.Error(),
		})
		return
	}

	if req.PileLimits == nil {
		req.PileLimits = []PileLimit{}
	}

	for i, limit := range req.PileLimits {
		if limit.PileID == "" {
			log.Printf("[HTTP] Warning: pile limit at index %d has empty pile_id, skipping", i)
			continue
		}
		if limit.MaxCurrentAmps < 0 {
			log.Printf("[HTTP] Warning: pile %s has negative max_current_amps %.2f, clamping to 0",
				limit.PileID, limit.MaxCurrentAmps)
			req.PileLimits[i].MaxCurrentAmps = 0
		}
	}

	h.store.SetConfig(&req)

	log.Printf("[HTTP] Limit config updated successfully: maxTotalPower=%.2fkW, %d pile limits",
		req.MaxTotalPowerKW, len(req.PileLimits))

	c.JSON(http.StatusOK, gin.H{
		"message": "limit config updated successfully",
		"data":    req,
	})
}

func (h *Handler) GetConfig(c *gin.Context) {
	cfg := h.store.GetConfig()
	c.JSON(http.StatusOK, gin.H{
		"data": cfg,
	})
}

func SetupRouter(h *Handler) *gin.Engine {
	r := gin.Default()

	r.Use(gin.Recovery())

	r.Use(func(c *gin.Context) {
		log.Printf("[HTTP] %s %s from %s", c.Request.Method, c.Request.URL.Path, c.ClientIP())
		c.Next()
	})

	api := r.Group("/api/v1")
	{
		api.POST("/limit", h.SetLimit)
		api.GET("/config", h.GetConfig)
	}

	r.GET("/health", func(c *gin.Context) {
		c.JSON(http.StatusOK, gin.H{
			"status": "ok",
		})
	})

	return r
}
