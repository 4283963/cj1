<template>
  <div class="dashboard-container">
    <div class="dashboard-bg"></div>
    
    <header class="dashboard-header">
      <div class="header-title">
        <h1>充电桩运维监控大屏</h1>
        <p class="subtitle">Charging Station Operation Monitoring Dashboard</p>
      </div>
      
      <div class="header-actions">
        <el-button type="danger" size="large" @click="showEmergencyDialog = true" class="emergency-btn">
          <el-icon><Warning /></el-icon>
          紧急限电
        </el-button>
        <el-button type="primary" size="large" @click="handleReset" class="reset-btn">
          <el-icon><Refresh /></el-icon>
          重置调度
        </el-button>
      </div>
    </header>

    <section class="status-bar">
      <div class="status-card">
        <div class="status-icon power-icon">
          <el-icon><Lightning /></el-icon>
        </div>
        <div class="status-info">
          <span class="status-label">站点总功率</span>
          <span class="status-value">{{ formatPower(statusData.currentTotalPowerKw) }} <span class="unit">kW</span></span>
        </div>
      </div>
      
      <div class="status-card">
        <div class="status-icon limit-icon">
          <el-icon><CircleCheck /></el-icon>
        </div>
        <div class="status-info">
          <span class="status-label">总功率上限</span>
          <span class="status-value">{{ formatPower(statusData.maxTotalPowerKw) }} <span class="unit">kW</span></span>
        </div>
      </div>
      
      <div class="status-card alert-card">
        <div class="status-icon alert-icon">
          <el-icon><Bell /></el-icon>
        </div>
        <div class="status-info">
          <span class="status-label">告警信息</span>
          <span class="status-value alert-count">{{ statusData.alerts?.length || 0 }} <span class="unit">条</span></span>
        </div>
        <div class="alert-list" v-if="statusData.alerts && statusData.alerts.length > 0">
          <div v-for="(alert, index) in statusData.alerts.slice(0, 3)" :key="index" class="alert-item">
            <el-icon size="12"><WarningFilled /></el-icon>
            {{ alert }}
          </div>
        </div>
      </div>
      
      <div class="status-card transition-card" v-if="statusData.transition?.inProgress">
        <div class="status-icon transition-icon">
          <el-icon><Refresh /></el-icon>
        </div>
        <div class="status-info">
          <span class="status-label">平滑降载中</span>
          <span class="status-value transition-value">
            {{ formatPower(statusData.transition.currentPowerKw) }} → {{ formatPower(statusData.transition.targetPowerKw) }}
            <span class="unit">kW</span>
          </span>
          <div class="transition-progress-bar">
            <div class="transition-progress-fill" :style="{ width: transitionProgress + '%' }"></div>
          </div>
          <span class="transition-remain">剩余约 {{ remainingMinutes }} 分钟</span>
        </div>
      </div>
      
      <div class="status-card">
        <div class="status-icon time-icon">
          <el-icon><Clock /></el-icon>
        </div>
        <div class="status-info">
          <span class="status-label">更新时间</span>
          <span class="status-value time-value">{{ currentTime }}</span>
        </div>
      </div>
    </section>

    <section class="chart-section">
      <div class="section-header">
        <h2><span class="decorate"></span>总功率趋势预测</h2>
        <span class="section-subtitle">Total Power Forecast</span>
      </div>
      <div ref="trendChartRef" class="trend-chart-container"></div>
    </section>

    <section class="chart-section">
      <div class="section-header">
        <h2><span class="decorate"></span>充电桩功率实时监控</h2>
        <span class="section-subtitle">Real-time Power Monitoring</span>
      </div>
      <div ref="chartRef" class="chart-container"></div>
    </section>

    <section class="piles-section">
      <div class="section-header">
        <h2><span class="decorate"></span>充电桩状态列表</h2>
        <span class="section-subtitle">Charging Pile Status</span>
      </div>
      <div class="piles-grid">
        <div 
          v-for="pile in statusData.pileStatuses" 
          :key="pile.pileId" 
          class="pile-card"
          :class="{ 'limited': pile.isLimited }"
        >
          <div class="pile-header">
            <span class="pile-id">{{ pile.pileId }}</span>
            <el-tag 
              :type="pile.isLimited ? 'warning' : 'success'" 
              size="small"
              effect="dark"
            >
              {{ pile.isLimited ? '已限流' : '正常' }}
            </el-tag>
          </div>
          <div class="pile-power">
            <span class="power-value">{{ formatPower(pile.currentPowerKw) }}</span>
            <span class="power-unit">kW</span>
          </div>
          <div class="pile-detail">
            <div class="detail-item">
              <span class="detail-label">额定电流</span>
              <span class="detail-value">{{ pile.originalMaxCurrentAmps?.toFixed(0) || '-- }} A</span>
            </div>
            <div class="detail-item">
              <span class="detail-label">限制电流</span>
              <span class="detail-value" :class="{ 'limited-text': pile.isLimited }">
                {{ pile.limitedMaxCurrentAmps?.toFixed(0) || '-- }} A
              </span>
            </div>
          </div>
          <div class="pile-status-bar">
            <div 
              class="status-fill" 
              :style="{ width: getPowerPercent(pile) + '%' }"
              :class="{ 'limited-fill': pile.isLimited }"
            ></div>
          </div>
        </div>
      </div>
    </section>

    <el-dialog 
      v-model="showEmergencyDialog" 
      title="紧急限电" 
      width="500px"
      :close-on-click-modal="false"
      class="emergency-dialog"
    >
      <el-form :model="emergencyForm" label-width="100px" class="emergency-form">
        <el-form-item label="功率上限" prop="maxTotalPowerKw">
          <el-input-number 
            v-model="emergencyForm.maxTotalPowerKw" 
            :min="1" 
            :max="10000" 
            :step="10"
            size="large"
            style="width: 100%"
          />
          <span class="form-unit">kW</span>
        </el-form-item>
        <el-form-item label="过渡时间" prop="transitionMinutes">
          <el-select v-model="emergencyForm.transitionMinutes" size="large" style="width: 100%">
            <el-option :value="0" label="立即生效" />
            <el-option :value="1" label="1 分钟内平滑降载" />
            <el-option :value="3" label="3 分钟内平滑降载" />
            <el-option :value="5" label="5 分钟内平滑降载" />
            <el-option :value="10" label="10 分钟内平滑降载" />
            <el-option :value="15" label="15 分钟内平滑降载" />
            <el-option :value="30" label="30 分钟内平滑降载" />
          </el-select>
        </el-form-item>
        <el-form-item label="限电原因" prop="reason">
          <el-input 
            v-model="emergencyForm.reason" 
            type="textarea" 
            :rows="3" 
            placeholder="请输入限电原因"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showEmergencyDialog = false" size="large">取消</el-button>
        <el-button type="danger" @click="handleEmergencyLimit" size="large" :loading="emergencyLoading">
          确认限电
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted, nextTick, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Warning, Refresh, Lightning, CircleCheck, Bell, Clock, WarningFilled } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { getStatus, emergencyLimit, resetSchedule } from '@/api/schedule'

const chartRef = ref(null)
const trendChartRef = ref(null)
let chartInstance = null
let trendChartInstance = null
let pollTimer = null
let timeTimer = null

const MAX_HISTORY_POINTS = 20
const powerHistory = ref([])

const showEmergencyDialog = ref(false)
const emergencyLoading = ref(false)
const emergencyForm = reactive({
  maxTotalPowerKw: 100,
  transitionMinutes: 5,
  reason: ''
})

const statusData = reactive({
  success: true,
  message: '',
  maxTotalPowerKw: 0,
  currentTotalPowerKw: 0,
  pileStatuses: [],
  alerts: [],
  timestamp: '',
  transition: null
})

const currentTime = ref('')

const formatPower = (value) => {
  if (value === null || value === undefined || isNaN(value)) return '0.00'
  return Number(value).toFixed(2)
}

const transitionProgress = computed(() => {
  if (!statusData.transition?.inProgress) return 0
  const current = statusData.transition.currentStep || 0
  const total = statusData.transition.totalSteps || 1
  return Math.min(Math.round((current / total) * 100), 100)
})

const remainingMinutes = computed(() => {
  if (!statusData.transition?.inProgress) return 0
  const remainingSteps = (statusData.transition.totalSteps || 0) - (statusData.transition.currentStep || 0)
  const remainingSeconds = remainingSteps * 10
  return Math.max(1, Math.ceil(remainingSeconds / 60))
})

const getPowerPercent = (pile) => {
  if (!pile || !pile.currentPowerKw || !pile.originalMaxCurrentAmps) return 0
  const maxPowerKw = pile.originalMaxCurrentAmps * 0.66
  if (maxPowerKw <= 0) return 0
  return Math.min((pile.currentPowerKw / maxPowerKw) * 100, 100)
}

const updateCurrentTime = () => {
  const now = new Date()
  const pad = (n) => n.toString().padStart(2, '0')
  currentTime.value = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())} ${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`
}

const initChart = () => {
  if (!chartRef.value) return
  
  chartInstance = echarts.init(chartRef.value, 'dark')
  
  const option = {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      axisPointer: {
        type: 'shadow'
      },
      backgroundColor: 'rgba(0, 30, 60, 0.9)',
      borderColor: '#00d4ff',
      textStyle: {
        color: '#fff'
      }
    },
    legend: {
      data: ['当前功率', '限流上限'],
      textStyle: {
        color: '#a0cfff'
      },
      top: 10,
      right: 20
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '8%',
      top: '15%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: [],
      axisLine: {
        lineStyle: {
          color: '#1e5a8a'
        }
      },
      axisLabel: {
        color: '#a0cfff',
        fontSize: 12,
        rotate: 30,
        interval: 0
      },
      axisTick: {
        show: false
      }
    },
    yAxis: {
      type: 'value',
      name: '功率 (kW)',
      nameTextStyle: {
        color: '#a0cfff',
        fontSize: 12
      },
      axisLine: {
        show: false
      },
      axisLabel: {
        color: '#a0cfff'
      },
      splitLine: {
        lineStyle: {
          color: 'rgba(30, 90, 138, 0.3)',
          type: 'dashed'
        }
      }
    },
    series: [
      {
        name: '当前功率',
        type: 'bar',
        barWidth: '40%',
        data: [],
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#00d4ff' },
            { offset: 1, color: '#0066ff' }
          ]),
          borderRadius: [4, 4, 0, 0]
        },
        emphasis: {
          itemStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: '#00ffff' },
              { offset: 1, color: '#0088ff' }
            ])
          }
        }
      },
      {
        name: '限流上限',
        type: 'line',
        data: [],
        lineStyle: {
          color: '#ff6b6b',
          width: 2,
          type: 'dashed'
        },
        itemStyle: {
          color: '#ff6b6b'
        },
        symbol: 'circle',
        symbolSize: 6
      }
    ]
  }
  
  chartInstance.setOption(option)
}

const updateChart = () => {
  if (!chartInstance || !statusData.pileStatuses.length) return
  
  const pileIds = statusData.pileStatuses.map(p => p.pileId)
  const currentPowers = statusData.pileStatuses.map(p => Number(p.currentPowerKw?.toFixed(2) || 0))
  const limitPowers = statusData.pileStatuses.map(p => {
    const limitAmps = p.limitedMaxCurrentAmps || p.originalMaxCurrentAmps || 0
    return Number((limitAmps * 0.66).toFixed(2))
  })
  
  chartInstance.setOption({
    xAxis: {
      data: pileIds
    },
    series: [
      {
        data: currentPowers
      },
      {
        data: limitPowers
      }
    ]
  })
}

const initTrendChart = () => {
  if (!trendChartRef.value) return
  
  trendChartInstance = echarts.init(trendChartRef.value, 'dark')
  
  const option = {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(0, 30, 60, 0.9)',
      borderColor: '#00d4ff',
      textStyle: {
        color: '#fff'
      },
      formatter: (params) => {
        let result = ''
        params.forEach(p => {
          result += `${p.marker} ${p.seriesName}: ${p.value?.toFixed?.(2) || p.value} kW<br/>`
        })
        return result
      }
    },
    legend: {
      data: ['当前总功率', '预测功率曲线', '目标功率'],
      textStyle: {
        color: '#a0cfff'
      },
      top: 10,
      right: 20
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '8%',
      top: '18%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: [],
      axisLine: {
        lineStyle: {
          color: '#1e5a8a'
        }
      },
      axisLabel: {
        color: '#a0cfff',
        fontSize: 11
      },
      axisTick: {
        show: false
      }
    },
    yAxis: {
      type: 'value',
      name: '功率 (kW)',
      nameTextStyle: {
        color: '#a0cfff',
        fontSize: 12
      },
      axisLine: {
        show: false
      },
      axisLabel: {
        color: '#a0cfff'
      },
      splitLine: {
        lineStyle: {
          color: 'rgba(30, 90, 138, 0.3)',
          type: 'dashed'
        }
      }
    },
    series: [
      {
        name: '当前总功率',
        type: 'line',
        smooth: true,
        data: [],
        lineStyle: {
          color: '#00d4ff',
          width: 3
        },
        itemStyle: {
          color: '#00d4ff'
        },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(0, 212, 255, 0.3)' },
            { offset: 1, color: 'rgba(0, 212, 255, 0.02)' }
          ])
        },
        symbol: 'circle',
        symbolSize: 6
      },
      {
        name: '预测功率曲线',
        type: 'line',
        smooth: true,
        data: [],
        lineStyle: {
          color: '#ff9f43',
          width: 2,
          type: 'dashed'
        },
        itemStyle: {
          color: '#ff9f43'
        },
        symbol: 'none',
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(255, 159, 67, 0.15)' },
            { offset: 1, color: 'rgba(255, 159, 67, 0.02)' }
          ])
        }
      },
      {
        name: '目标功率',
        type: 'line',
        data: [],
        lineStyle: {
          color: '#ff6b6b',
          width: 2,
          type: 'dotted'
        },
        itemStyle: {
          color: '#ff6b6b'
        },
        symbol: 'none'
      }
    ]
  }
  
  trendChartInstance.setOption(option)
}

const updateTrendChart = () => {
  if (!trendChartInstance) return
  
  const now = new Date()
  const timeStr = now.toTimeString().slice(0, 8)
  
  if (statusData.currentTotalPowerKw !== undefined && statusData.currentTotalPowerKw !== null) {
    powerHistory.value.push({
      time: timeStr,
      power: Number(statusData.currentTotalPowerKw.toFixed(2))
    })
    
    if (powerHistory.value.length > MAX_HISTORY_POINTS) {
      powerHistory.value.shift()
    }
  }
  
  const historyTimes = powerHistory.value.map(p => p.time)
  const historyPowers = powerHistory.value.map(p => p.power)
  
  let forecastTimes = []
  let forecastPowers = []
  let targetLine = []
  
  if (statusData.transition && statusData.transition.inProgress && statusData.transition.forecastPowers) {
    const forecastPoints = statusData.transition.forecastPowers
    const currentStep = statusData.transition.currentStep || 0
    const totalSteps = statusData.transition.totalSteps || 1
    const remainingSteps = totalSteps - currentStep
    
    forecastTimes = [...historyTimes]
    forecastPowers = historyPowers.map(() => null)
    
    for (let i = currentStep; i < forecastPoints.length; i++) {
      const offset = i - currentStep
      const futureTime = new Date(now.getTime() + offset * 10 * 1000)
      forecastTimes.push(futureTime.toTimeString().slice(0, 8))
      forecastPowers.push(Number(forecastPoints[i].toFixed(2)))
    }
    
    targetLine = new Array(forecastTimes.length).fill(
      Number(statusData.transition.targetPowerKw.toFixed(2))
    )
  }
  
  trendChartInstance.setOption({
    xAxis: {
      data: forecastTimes.length > historyTimes.length ? forecastTimes : historyTimes
    },
    series: [
      {
        data: historyPowers
      },
      {
        data: forecastPowers
      },
      {
        data: targetLine
      }
    ]
  })
}

const handleResize = () => {
  chartInstance?.resize()
  trendChartInstance?.resize()
}

const fetchStatus = async () => {
  try {
    const data = await getStatus()
    if (data) {
      Object.assign(statusData, data)
      updateChart()
      updateTrendChart()
    }
  } catch (error) {
    console.error('获取状态失败:', error)
  }
}

const handleEmergencyLimit = async () => {
  if (!emergencyForm.maxTotalPowerKw || emergencyForm.maxTotalPowerKw <= 0) {
    ElMessage.warning('请输入有效的功率上限')
    return
  }
  if (!emergencyForm.reason?.trim()) {
    ElMessage.warning('请输入限电原因')
    return
  }
  
  try {
    emergencyLoading.value = true
    await emergencyLimit(emergencyForm.maxTotalPowerKw, emergencyForm.reason, emergencyForm.transitionMinutes)
    ElMessage.success('紧急限电指令已下发')
    showEmergencyDialog.value = false
    emergencyForm.reason = ''
    await fetchStatus()
  } catch (error) {
    ElMessage.error('紧急限电失败，请重试')
  } finally {
    emergencyLoading.value = false
  }
}

const handleReset = async () => {
  try {
    await ElMessageBox.confirm(
      '确定要重置调度吗？重置后所有限流策略将恢复默认值。',
      '重置确认',
      {
        confirmButtonText: '确定重置',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    
    await resetSchedule()
    ElMessage.success('调度已重置')
    await fetchStatus()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('重置失败，请重试')
    }
  }
}

const startPolling = () => {
  pollTimer = setInterval(fetchStatus, 3000)
}

const stopPolling = () => {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

onMounted(async () => {
  updateCurrentTime()
  timeTimer = setInterval(updateCurrentTime, 1000)
  
  await nextTick()
  initTrendChart()
  initChart()
  window.addEventListener('resize', handleResize)
  
  await fetchStatus()
  startPolling()
})

onUnmounted(() => {
  stopPolling()
  
  if (timeTimer) {
    clearInterval(timeTimer)
    timeTimer = null
  }
  
  window.removeEventListener('resize', handleResize)
  
  if (chartInstance) {
    chartInstance.dispose()
    chartInstance = null
  }
  
  if (trendChartInstance) {
    trendChartInstance.dispose()
    trendChartInstance = null
  }
})
</script>

<style scoped>
.dashboard-container {
  position: relative;
  width: 100%;
  height: 100vh;
  padding: 20px;
  overflow-y: auto;
  overflow-x: hidden;
}

.dashboard-bg {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: linear-gradient(135deg, #0a1628 0%, #0d2847 50%, #0a1628 100%);
  z-index: -1;
}

.dashboard-bg::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background-image: 
    radial-gradient(circle at 20% 30%, rgba(0, 150, 255, 0.1) 0%, transparent 50%),
    radial-gradient(circle at 80% 70%, rgba(0, 200, 255, 0.08) 0%, transparent 50%);
}

.dashboard-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 0 20px;
  border-bottom: 1px solid rgba(0, 180, 255, 0.2);
}

.header-title h1 {
  font-size: 28px;
  color: #00d4ff;
  font-weight: 600;
  text-shadow: 0 0 20px rgba(0, 212, 255, 0.5);
  margin: 0;
  letter-spacing: 2px;
}

.header-title .subtitle {
  font-size: 12px;
  color: #4a90c2;
  margin-top: 4px;
  letter-spacing: 1px;
}

.header-actions {
  display: flex;
  gap: 12px;
}

.emergency-btn {
  background: linear-gradient(135deg, #ff4757 0%, #c0392b 100%);
  border: none;
  box-shadow: 0 4px 15px rgba(255, 71, 87, 0.4);
  font-weight: 600;
}

.emergency-btn:hover {
  background: linear-gradient(135deg, #ff6b7a 0%, #e74c3c 100%);
  box-shadow: 0 6px 20px rgba(255, 71, 87, 0.6);
}

.reset-btn {
  background: linear-gradient(135deg, #3498db 0%, #2980b9 100%);
  border: none;
  box-shadow: 0 4px 15px rgba(52, 152, 219, 0.3);
}

.status-bar {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 20px;
  margin: 20px 0;
}

.status-card {
  position: relative;
  background: linear-gradient(135deg, rgba(15, 50, 90, 0.6) 0%, rgba(10, 30, 60, 0.8) 100%);
  border: 1px solid rgba(0, 180, 255, 0.2);
  border-radius: 8px;
  padding: 20px;
  display: flex;
  align-items: center;
  gap: 16px;
  overflow: hidden;
}

.status-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  width: 4px;
  height: 100%;
  background: linear-gradient(180deg, #00d4ff 0%, #0066ff 100%);
}

.status-icon {
  width: 56px;
  height: 56px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
}

.power-icon {
  background: linear-gradient(135deg, rgba(0, 212, 255, 0.2) 0%, rgba(0, 102, 255, 0.2) 100%);
  color: #00d4ff;
}

.limit-icon {
  background: linear-gradient(135deg, rgba(46, 213, 115, 0.2) 0%, rgba(39, 174, 96, 0.2) 100%);
  color: #2ed573;
}

.alert-icon {
  background: linear-gradient(135deg, rgba(255, 159, 67, 0.2) 0%, rgba(255, 107, 107, 0.2) 100%);
  color: #ff9f43;
}

.time-icon {
  background: linear-gradient(135deg, rgba(156, 89, 255, 0.2) 0%, rgba(108, 53, 255, 0.2) 100%);
  color: #9c59ff;
}

.transition-icon {
  background: linear-gradient(135deg, rgba(255, 159, 67, 0.2) 0%, rgba(255, 107, 107, 0.2) 100%);
  color: #ff9f43;
  animation: spin 2s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.transition-card {
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
}

.transition-card .status-icon {
  position: absolute;
  top: 20px;
  right: 20px;
  width: 40px;
  height: 40px;
  font-size: 20px;
}

.transition-value {
  font-size: 20px !important;
  color: #ff9f43 !important;
}

.transition-progress-bar {
  width: 100%;
  height: 6px;
  background: rgba(0, 0, 0, 0.3);
  border-radius: 3px;
  overflow: hidden;
  margin-top: 4px;
}

.transition-progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #ff9f43 0%, #ff6b6b 100%);
  border-radius: 3px;
  transition: width 0.5s ease;
}

.transition-remain {
  font-size: 12px;
  color: #7fb8e0;
}

.status-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.status-label {
  font-size: 14px;
  color: #7fb8e0;
}

.status-value {
  font-size: 28px;
  font-weight: 700;
  color: #fff;
  font-family: 'DIN Alternate', 'Arial Narrow', sans-serif;
}

.status-value .unit {
  font-size: 14px;
  font-weight: 400;
  color: #7fb8e0;
  margin-left: 4px;
}

.alert-count {
  color: #ff9f43;
}

.time-value {
  font-size: 18px !important;
  font-family: 'Courier New', monospace;
}

.alert-list {
  position: absolute;
  bottom: 8px;
  right: 12px;
  font-size: 11px;
  color: #ff9f43;
  max-width: 60%;
  text-align: right;
}

.alert-item {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-top: 2px;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 4px;
}

.chart-section, .piles-section {
  background: linear-gradient(135deg, rgba(15, 50, 90, 0.4) 0%, rgba(10, 30, 60, 0.6) 100%);
  border: 1px solid rgba(0, 180, 255, 0.15);
  border-radius: 8px;
  padding: 20px;
  margin-bottom: 20px;
}

.section-header {
  display: flex;
  align-items: baseline;
  gap: 16px;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid rgba(0, 180, 255, 0.15);
}

.section-header h2 {
  font-size: 18px;
  color: #00d4ff;
  font-weight: 600;
  margin: 0;
  display: flex;
  align-items: center;
  gap: 10px;
}

.decorate {
  display: inline-block;
  width: 4px;
  height: 18px;
  background: linear-gradient(180deg, #00d4ff 0%, #0066ff 100%);
  border-radius: 2px;
}

.section-subtitle {
  font-size: 12px;
  color: #4a90c2;
  letter-spacing: 1px;
}

.trend-chart-container {
  width: 100%;
  height: 260px;
}

.chart-container {
  width: 100%;
  height: 350px;
}

.piles-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 16px;
}

.pile-card {
  background: linear-gradient(135deg, rgba(20, 60, 100, 0.5) 0%, rgba(15, 40, 70, 0.7) 100%);
  border: 1px solid rgba(0, 180, 255, 0.2);
  border-radius: 8px;
  padding: 16px;
  transition: all 0.3s ease;
}

.pile-card:hover {
  border-color: rgba(0, 212, 255, 0.5);
  box-shadow: 0 4px 20px rgba(0, 180, 255, 0.2);
  transform: translateY(-2px);
}

.pile-card.limited {
  border-color: rgba(255, 159, 67, 0.4);
}

.pile-card.limited:hover {
  border-color: rgba(255, 159, 67, 0.7);
  box-shadow: 0 4px 20px rgba(255, 159, 67, 0.2);
}

.pile-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.pile-id {
  font-size: 15px;
  font-weight: 600;
  color: #fff;
}

.pile-power {
  display: flex;
  align-items: baseline;
  gap: 6px;
  margin-bottom: 12px;
}

.power-value {
  font-size: 32px;
  font-weight: 700;
  color: #00d4ff;
  font-family: 'DIN Alternate', 'Arial Narrow', sans-serif;
}

.power-unit {
  font-size: 14px;
  color: #7fb8e0;
}

.pile-detail {
  display: flex;
  justify-content: space-between;
  margin-bottom: 12px;
}

.detail-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.detail-label {
  font-size: 12px;
  color: #5a8cb0;
}

.detail-value {
  font-size: 14px;
  color: #a0cfff;
  font-weight: 500;
}

.detail-value.limited-text {
  color: #ff9f43;
}

.pile-status-bar {
  height: 6px;
  background: rgba(0, 50, 100, 0.5);
  border-radius: 3px;
  overflow: hidden;
}

.status-fill {
  height: 100%;
  background: linear-gradient(90deg, #00d4ff 0%, #00ff88 100%);
  border-radius: 3px;
  transition: width 0.5s ease;
}

.status-fill.limited-fill {
  background: linear-gradient(90deg, #ff9f43 0%, #ff6b6b 100%);
}

.emergency-dialog :deep(.el-dialog) {
  background: linear-gradient(135deg, rgba(15, 50, 90, 0.95) 0%, rgba(10, 30, 60, 0.98) 100%);
  border: 1px solid rgba(255, 107, 107, 0.3);
}

.emergency-dialog :deep(.el-dialog__title) {
  color: #ff6b6b;
}

.emergency-dialog :deep(.el-dialog__header) {
  border-bottom: 1px solid rgba(255, 107, 107, 0.2);
}

.emergency-dialog :deep(.el-dialog__footer) {
  border-top: 1px solid rgba(255, 107, 107, 0.1);
}

.emergency-form {
  padding: 10px 0;
}

.form-unit {
  margin-left: 8px;
  color: #7fb8e0;
  font-size: 14px;
}

@media (max-width: 1200px) {
  .status-bar {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .status-bar {
    grid-template-columns: 1fr;
  }
  
  .header-title h1 {
    font-size: 20px;
  }
  
  .dashboard-header {
    flex-direction: column;
    gap: 16px;
    align-items: flex-start;
  }
}
</style>
