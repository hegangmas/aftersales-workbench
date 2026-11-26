<template>
  <section class="panel">
    <div class="fig2-card-head">
      <h2>客户与项目档案</h2>
      <button class="btn secondary" type="button" @click="$emit('refresh')">刷新</button>
    </div>
    <p class="muted">本地模拟客户与项目主档（外部安装系统接入后自动同步）。</p>
    <table v-if="customers.length" class="tickets" style="margin-top:12px;">
      <thead>
        <tr><th>客户ID</th><th>客户名称</th><th>联系人</th><th>电话</th><th>项目编号</th><th>项目名称</th><th>类型/SLA</th></tr>
      </thead>
      <tbody>
        <tr v-for="c in customers" :key="c.id">
          <td class="mono">{{ c.id }}</td>
          <td>{{ c.name }}</td>
          <td>{{ c.contactName }}</td>
          <td class="mono">{{ c.phone }}</td>
          <td class="mono">{{ c.projectCode }}</td>
          <td>{{ c.projectName }}</td>
          <td>{{ c.customerType }} · {{ c.slaMinutes }}分钟</td>
        </tr>
      </tbody>
    </table>
    <p v-else class="empty-row">暂无客户与项目主档记录</p>
  </section>
</template>

<script setup>
defineProps({
  customers: { type: Array, default: () => [] }
})
defineEmits(['refresh'])
</script>
