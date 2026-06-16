<template>
  <div class="app-container">
    <router-view />
  </div>
</template>

<script setup>
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html, body, #app {
  width: 100%;
  height: 100%;
  overflow: hidden;
}

body {
  font-family: 'Microsoft YaHei', 'PingFang SC', sans-serif;
}

.app-container {
  width: 100%;
  height: 100%;
}
</style>
