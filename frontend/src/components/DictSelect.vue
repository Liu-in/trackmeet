<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getDictItems, type DictItem } from '@/api/dict'

/**
 * 字典下拉组件：按 dictType 拉取字典项并双向绑定。
 * 用法：<DictSelect v-model="form.gender" dict-type="gender" />
 */
defineOptions({ name: 'DictSelect' })

interface Props {
  modelValue?: string | number | null
  dictType: string
  placeholder?: string
  clearable?: boolean
  disabled?: boolean
  width?: string
}

const props = withDefaults(defineProps<Props>(), {
  placeholder: '请选择',
  clearable: true,
  disabled: false,
  width: '100%'
})

const emit = defineEmits<{ (e: 'update:modelValue', value: string | number | null): void }>()

const items = ref<DictItem[]>([])
const loading = ref(false)

onMounted(async () => {
  loading.value = true
  try {
    items.value = (await getDictItems(props.dictType)) || []
  } catch {
    items.value = []
  } finally {
    loading.value = false
  }
})

function onChange(value: string | number | null) {
  emit('update:modelValue', value)
}
</script>

<template>
  <el-select
    :model-value="props.modelValue"
    :placeholder="props.placeholder"
    :clearable="props.clearable"
    :disabled="props.disabled"
    :loading="loading"
    :style="{ width: props.width }"
    @update:model-value="onChange"
  >
    <el-option v-for="item in items" :key="item.itemValue" :label="item.itemLabel" :value="item.itemValue" />
  </el-select>
</template>
