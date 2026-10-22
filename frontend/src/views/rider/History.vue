<template>
  <div class="page" v-loading="loading">
    <el-card shadow="never">
      <template #header>
        <div class="header">
          <span>历史订单</span>
          <span class="text-muted">累计 {{ page.total }} 单</span>
        </div>
      </template>

      <el-empty v-if="!loading && !orders.length" description="还没有已完成的配送订单" />

      <el-table v-else :data="orders" stripe>
        <el-table-column prop="orderNo" label="订单号" min-width="170" />
        <el-table-column label="收货信息" min-width="200">
          <template #default="{ row }">
            <div>{{ row.receiver }} {{ row.receiverPhone }}</div>
            <div class="text-muted">{{ row.address }}</div>
          </template>
        </el-table-column>
        <el-table-column label="配送费" width="100" align="right">
          <template #default="{ row }">¥{{ money(row.deliveryFee) }}</template>
        </el-table-column>
        <el-table-column label="订单金额" width="110" align="right">
          <template #default="{ row }">¥{{ money(row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column prop="finishTime" label="完成时间" width="170" />
      </el-table>

      <el-pagination
        v-if="page.total > page.pageSize"
        v-model:current-page="page.pageNum"
        :page-size="page.pageSize"
        :total="page.total"
        layout="prev, pager, next"
        class="pager"
        @current-change="load"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'

import type { Order } from '@/api/order'
import { listRiderOrders } from '@/api/rider'
import { money } from '@/utils/dict'

const orders = ref<Order[]>([])
const loading = ref(false)
const page = reactive({ pageNum: 1, pageSize: 10, total: 0 })

async function load() {
  loading.value = true
  try {
    const res = await listRiderOrders({ status: 4, pageNum: page.pageNum, pageSize: page.pageSize })
    orders.value = res.list
    page.total = res.total
  } catch {
    // 拦截器提示
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.pager {
  margin-top: 16px;
  justify-content: center;
}
.text-muted {
  color: #909399;
  font-size: 12px;
}
</style>
