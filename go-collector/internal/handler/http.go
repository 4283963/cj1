package handler

import (
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
	PileLimits      []PileLimit `json:"pile_limits" binding:"required"`
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

func (h *Handler) SetLimit(c *gin.Context) {
	var req LimitConfig
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{
			"error":   "invalid request body",
			"details": err.Error(),
		})
		return
	}

	h.store.SetConfig(&req)

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
