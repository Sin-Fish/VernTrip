import { onBeforeUnmount, ref } from 'vue'

const TERMINAL_STATUSES = ['SUCCEEDED', 'FAILED']

/**
 * AI 视频生成任务轮询。
 * 自递归 setTimeout（模式同 useAppState 的 workbench 轮询），
 * 到达终态或超过最大次数停止，轮询请求出错即停。
 *
 * @param fetchTask 以 taskId 拉取任务状态的函数，返回 { status, videoUrl, ... }
 * @param options   { interval=2500, maxAttempts=240 }
 */
export function useVideoTaskPolling(fetchTask, { interval = 2500, maxAttempts = 240 } = {}) {
  const task = ref(null)
  const polling = ref(false)
  const failed = ref(false)

  let timer = null
  let attempts = 0

  function stop() {
    if (timer !== null) {
      window.clearTimeout(timer)
      timer = null
    }
    polling.value = false
  }

  function schedule() {
    timer = window.setTimeout(async () => {
      try {
        const next = await fetchTask(task.value.id)
        task.value = next
        attempts += 1
        if (TERMINAL_STATUSES.includes(next?.status) || attempts >= maxAttempts) {
          stop()
          return
        }
        schedule()
      } catch (error) {
        console.error('Failed to poll video task', error)
        failed.value = true
        stop()
      }
    }, interval)
  }

  function start(initialTask) {
    stop()
    attempts = 0
    failed.value = false
    task.value = initialTask
    polling.value = true
    schedule()
  }

  onBeforeUnmount(stop)

  return { task, polling, failed, start, stop }
}
