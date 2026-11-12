import { shallowRef } from 'vue'
import { useIntervalFn } from '@vueuse/core'

/**
 * 每秒（或指定间隔）刷新的当前时间。
 * 不直接用 vueuse 的 useNow：其参数在不同大版本间变动过，这里只依赖稳定的 useIntervalFn。
 */
export function useClock(interval = 1000) {
  const now = shallowRef(new Date())

  const { pause, resume, isActive } = useIntervalFn(
    () => {
      now.value = new Date()
    },
    interval,
    { immediate: true, immediateCallback: true },
  )

  return { now, pause, resume, isActive }
}
