package expo.modules.foregroundservice

import android.content.Intent
import com.facebook.react.HeadlessJsTaskService
import com.facebook.react.bridge.Arguments
import com.facebook.react.jstasks.HeadlessJsTaskConfig

class HeadlessTaskRunner : HeadlessJsTaskService() {
  companion object {
    private const val DEFAULT_TIMEOUT = 30000L // 30 seconds max for safety
    private const val MAX_TIMEOUT = 60000L // 1 minute absolute max
    private const val WARNING_THRESHOLD = 15000L // 15 seconds warning threshold

    private const val ENABLE_TASK_MONITORING = false
    private const val ENABLE_DETAILED_TASK_LOGGING = false
  }

  private val taskStartTimes = mutableMapOf<String, Long>()
  private val warningHandlers = mutableMapOf<String, android.os.Handler>()

  override fun getTaskConfig(intent: Intent?): HeadlessJsTaskConfig? {
    return intent?.let {
      val taskName = it.getStringExtra("taskName") ?: return null
      val delay = it.getIntExtra("delay", 0)
      val loopDelay = it.getIntExtra("loopDelay", 0)
      val onLoop = it.getBooleanExtra("onLoop", false)

      if (ENABLE_TASK_MONITORING) {
        taskStartTimes[taskName] = System.currentTimeMillis()
      }

      val timeout = if (onLoop) {
        DEFAULT_TIMEOUT
      } else {
        MAX_TIMEOUT
      }

      if (ENABLE_DETAILED_TASK_LOGGING) {
        android.util.Log.i("HeadlessTaskRunner", "Starting task '$taskName' with ${timeout}ms timeout (loop: $onLoop)")
      }

      if (ENABLE_TASK_MONITORING) {
        setupTaskWarning(taskName, timeout)
      }

      HeadlessJsTaskConfig(
        taskName,
        Arguments.createMap().apply {
          putInt("delay", delay)
          putInt("loopDelay", loopDelay)
          putBoolean("onLoop", onLoop)
        },
        timeout,
        true
      )
    }
  }

  private fun setupTaskWarning(taskName: String, timeout: Long) {
    val handler = android.os.Handler(android.os.Looper.getMainLooper())
    warningHandlers[taskName] = handler

    handler.postDelayed({
      val startTime = taskStartTimes[taskName] ?: return@postDelayed
      val duration = System.currentTimeMillis() - startTime
      android.util.Log.w("HeadlessTaskRunner", "Task '$taskName' running for ${duration}ms (warning threshold: ${WARNING_THRESHOLD}ms)")
    }, WARNING_THRESHOLD)

    val finalWarningDelay = timeout - 5000L // 5 seconds before timeout
    if (finalWarningDelay > WARNING_THRESHOLD) {
      handler.postDelayed({
        val startTime = taskStartTimes[taskName] ?: return@postDelayed
        val duration = System.currentTimeMillis() - startTime
        android.util.Log.w("HeadlessTaskRunner", "Task '$taskName' approaching timeout after ${duration}ms (timeout: ${timeout}ms)")
      }, finalWarningDelay)
    }
  }

  private fun cleanupTaskTracking(taskName: String) {
    taskStartTimes.remove(taskName)
    warningHandlers[taskName]?.removeCallbacksAndMessages(null)
    warningHandlers.remove(taskName)
  }

  override fun onHeadlessJsTaskStart(taskId: Int) {
    super.onHeadlessJsTaskStart(taskId)
    if (ENABLE_DETAILED_TASK_LOGGING) {
      android.util.Log.d("HeadlessTaskRunner", "Task started with ID: $taskId")
    }
  }

  override fun onHeadlessJsTaskFinish(taskId: Int) {
    super.onHeadlessJsTaskFinish(taskId)
    if (ENABLE_DETAILED_TASK_LOGGING) {
      android.util.Log.d("HeadlessTaskRunner", "Task finished with ID: $taskId")
    }

    if (ENABLE_TASK_MONITORING && taskStartTimes.isNotEmpty()) {
      val oldestTask = taskStartTimes.minByOrNull { it.value }?.key
      oldestTask?.let { cleanupTaskTracking(it) }
    }
  }

  override fun onDestroy() {
    val startTime = if (ENABLE_DETAILED_TASK_LOGGING) System.currentTimeMillis() else 0L
    if (ENABLE_DETAILED_TASK_LOGGING) {
      android.util.Log.i("HeadlessTaskRunner", "HeadlessTaskRunner onDestroy() started")
    }

    try {
      if (ENABLE_TASK_MONITORING) {
        warningHandlers.values.forEach { handler ->
          handler.removeCallbacksAndMessages(null)
        }
        taskStartTimes.clear()
        warningHandlers.clear()
      }

      super.onDestroy()

      if (ENABLE_DETAILED_TASK_LOGGING) {
        val duration = System.currentTimeMillis() - startTime
        android.util.Log.i("HeadlessTaskRunner", "HeadlessTaskRunner destroyed in ${duration}ms")
      }

    } catch (e: Exception) {
      if (ENABLE_DETAILED_TASK_LOGGING) {
        val duration = System.currentTimeMillis() - startTime
        android.util.Log.e("HeadlessTaskRunner", "HeadlessTaskRunner destroy failed after ${duration}ms", e)
      } else {
        android.util.Log.e("HeadlessTaskRunner", "HeadlessTaskRunner destroy failed", e)
      }
    }
  }
}
