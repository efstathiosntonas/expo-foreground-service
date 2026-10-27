package expo.modules.foregroundservice

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import expo.modules.kotlin.Promise
import expo.modules.kotlin.exception.CodedException
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import expo.modules.kotlin.records.Field
import expo.modules.kotlin.records.Record
import java.util.concurrent.atomic.AtomicInteger

class NotificationConfig : Record {
  @Field
  var id: Int = 0

  @Field
  var title: String = "Foreground Service"

  @Field
  var message: String = "Service is running..."

  @Field
  var icon: String? = null

  @Field
  var largeIcon: String? = null

  @Field
  var importance: String = "default"

  @Field
  var visibility: String = "public"

  @Field
  var vibration: Boolean = false

  @Field
  var ongoing: Boolean = true

  @Field
  var button: Boolean = false

  @Field
  var buttonText: String? = null

  @Field
  var buttonOnPress: String? = null

  @Field
  var button2: Boolean = false

  @Field
  var button2Text: String? = null

  @Field
  var button2OnPress: String? = null

  @Field
  var mainOnPress: String? = null

  @Field
  var progressBar: Boolean = false

  @Field
  var progressBarMax: Int? = null

  @Field
  var progressBarCurr: Int? = null

  @Field
  var color: String? = null

  @Field
  var setOnlyAlertOnce: Boolean = false

  @Field
  var number: String? = null

  @Field
  var ServiceType: String = "dataSync"
}

class ForegroundServiceModule : Module() {
  private val context: Context
    get() = appContext.reactContext ?: throw CodedException("React context is null")

  private val notificationManager: NotificationManager
    get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

  private var serviceIntent: Intent? = null
  private val serviceStartCount = AtomicInteger(0) // Thread-safe counter
  private var notificationEventReceiver: BroadcastReceiver? = null
  private var notificationEventContext: Context? = null

  private fun ensureNotificationEventReceiverRegistered() {
    if (notificationEventReceiver != null) {
      return
    }

    val reactContext = appContext.reactContext ?: return
    val applicationContext = reactContext.applicationContext
    val receiver = object : BroadcastReceiver() {
      override fun onReceive(context: Context?, intent: Intent?) {
        if (intent == null) {
          return
        }
        val eventName = intent.getStringExtra("eventName") ?: return
        val action = intent.getStringExtra("action") ?: ""

        sendEvent(eventName, bundleOf("action" to action))
      }
    }

    try {
      ContextCompat.registerReceiver(
        applicationContext,
        receiver,
        IntentFilter("expo.modules.foregroundservice.EVENT"),
        "${reactContext.packageName}.permission.FOREGROUND_SERVICE_BROADCAST",
        null,
        ContextCompat.RECEIVER_NOT_EXPORTED
      )
      notificationEventReceiver = receiver
      notificationEventContext = applicationContext
    } catch (e: Exception) {
      notificationEventReceiver = null
      notificationEventContext = null
    }
  }

  private fun unregisterNotificationEventReceiver() {
    val receiver = notificationEventReceiver ?: return
    val receiverContext = notificationEventContext ?: return
    try {
      receiverContext.unregisterReceiver(receiver)
    } catch (e: Exception) {
      e.printStackTrace()
    } finally {
      notificationEventReceiver = null
      notificationEventContext = null
    }
  }

  override fun definition() = ModuleDefinition {
    Name("ForegroundService")

    Events("notificationClickHandle", "onServiceError")

    OnDestroy {
      unregisterNotificationEventReceiver()
    }

    AsyncFunction("startService") { config: NotificationConfig, promise: Promise ->
      try {
        ensureNotificationEventReceiverRegistered()
        // Note: We don't check POST_NOTIFICATIONS permission - service continues even if notifications are disabled
        // Android will just not show the notification but the service will still run

        startForegroundService(config)
        serviceStartCount.incrementAndGet()
        promise.resolve(null)
      } catch (e: Exception) {
        sendEvent("onServiceError", bundleOf("message" to (e.message ?: "Failed to start service")))
        promise.reject("START_SERVICE_ERROR", e.message ?: "Failed to start service", e)
      }
    }

    AsyncFunction("updateNotification") { config: NotificationConfig, promise: Promise ->
      try {
        ensureNotificationEventReceiverRegistered()
        updateNotification(config)
        promise.resolve(null)
      } catch (e: Exception) {
        promise.reject("UPDATE_NOTIFICATION_ERROR", e.message ?: "Failed to update notification", e)
      }
    }

    AsyncFunction("stopService") { promise: Promise ->
      try {
        val count = serviceStartCount.get()
        if (count > 0) {
          val newCount = serviceStartCount.decrementAndGet()
          if (newCount == 0) {
            stopForegroundService()
          }
        }
        promise.resolve(null)
      } catch (e: Exception) {
        promise.reject("STOP_SERVICE_ERROR", e.message ?: "Failed to stop service", e)
      }
    }

    AsyncFunction("stopServiceAll") { promise: Promise ->
      try {
        serviceStartCount.set(0)
        stopForegroundService()
        promise.resolve(null)
      } catch (e: Exception) {
        promise.reject("STOP_SERVICE_ALL_ERROR", e.message ?: "Failed to stop all services", e)
      }
    }

    AsyncFunction("cancelNotification") { params: Map<String, Any>, promise: Promise ->
      try {
        val id = params["id"] as? Int ?: throw IllegalArgumentException("Notification ID is required")
        notificationManager.cancel(id)
        promise.resolve(null)
      } catch (e: Exception) {
        promise.reject("CANCEL_NOTIFICATION_ERROR", e.message ?: "Failed to cancel notification", e)
      }
    }

    AsyncFunction("isRunning") { promise: Promise ->
      promise.resolve(serviceStartCount.get())
    }

    AsyncFunction("runTask") { taskConfig: Map<String, Any>, promise: Promise ->
      try {
        // Task execution will be handled by the service
        val intent = Intent(context, ForegroundTaskService::class.java).apply {
          action = ForegroundTaskService.ACTION_RUN_TASK
          putExtra("taskName", taskConfig["taskName"] as? String ?: "")
          putExtra("delay", taskConfig["delay"] as? Int ?: 0)
          putExtra("loopDelay", taskConfig["loopDelay"] as? Int ?: 0)
          putExtra("onLoop", taskConfig["onLoop"] as? Boolean ?: false)
        }
        context.startService(intent)
        promise.resolve(null)
      } catch (e: Exception) {
        promise.reject("RUN_TASK_ERROR", e.message ?: "Failed to run task", e)
      }
    }
  }

  private fun startForegroundService(config: NotificationConfig) {
    createNotificationChannel()

    serviceIntent = Intent(context, ForegroundTaskService::class.java).apply {
      action = ForegroundTaskService.ACTION_START_SERVICE
      putExtra("notificationId", config.id)
      putExtra("title", config.title)
      putExtra("message", config.message)
      putExtra("icon", config.icon)
      putExtra("largeIcon", config.largeIcon)
      putExtra("importance", config.importance)
      putExtra("visibility", config.visibility)
      putExtra("vibration", config.vibration)
      putExtra("ongoing", config.ongoing)
      putExtra("button", config.button)
      putExtra("buttonText", config.buttonText)
      putExtra("buttonOnPress", config.buttonOnPress)
      putExtra("button2", config.button2)
      putExtra("button2Text", config.button2Text)
      putExtra("button2OnPress", config.button2OnPress)
      putExtra("mainOnPress", config.mainOnPress)
      putExtra("progressBar", config.progressBar)
      putExtra("progressBarMax", config.progressBarMax)
      putExtra("progressBarCurr", config.progressBarCurr)
      putExtra("color", config.color)
      putExtra("setOnlyAlertOnce", config.setOnlyAlertOnce)
      putExtra("number", config.number)
      putExtra("ServiceType", config.ServiceType)
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      context.startForegroundService(serviceIntent)
    } else {
      context.startService(serviceIntent)
    }
  }

  private fun updateNotification(config: NotificationConfig) {
    val intent = Intent(context, ForegroundTaskService::class.java).apply {
      action = ForegroundTaskService.ACTION_UPDATE_NOTIFICATION
      putExtra("notificationId", config.id)
      putExtra("title", config.title)
      putExtra("message", config.message)
      putExtra("icon", config.icon)
      putExtra("largeIcon", config.largeIcon)
      putExtra("importance", config.importance)
      putExtra("visibility", config.visibility)
      putExtra("vibration", config.vibration)
      putExtra("ongoing", config.ongoing)
      putExtra("button", config.button)
      putExtra("buttonText", config.buttonText)
      putExtra("buttonOnPress", config.buttonOnPress)
      putExtra("button2", config.button2)
      putExtra("button2Text", config.button2Text)
      putExtra("button2OnPress", config.button2OnPress)
      putExtra("mainOnPress", config.mainOnPress)
      putExtra("progressBar", config.progressBar)
      putExtra("progressBarMax", config.progressBarMax)
      putExtra("progressBarCurr", config.progressBarCurr)
      putExtra("color", config.color)
      putExtra("setOnlyAlertOnce", config.setOnlyAlertOnce)
      putExtra("number", config.number)
      putExtra("ServiceType", config.ServiceType)
    }

    context.startService(intent)
  }

  private fun stopForegroundService() {
    val startTime = System.currentTimeMillis()
    android.util.Log.i("ForegroundServiceModule", "Stopping foreground service")

    serviceIntent?.let { intent ->
      try {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        var timeoutTriggered = false
        val timeoutRunnable = Runnable {
          timeoutTriggered = true
          val timeoutDuration = System.currentTimeMillis() - startTime
          android.util.Log.w("ForegroundServiceModule", "Service stop timeout triggered after ${timeoutDuration}ms")
          serviceIntent = null
        }

        handler.postDelayed(timeoutRunnable, 2000)

        android.util.Log.d("ForegroundServiceModule", "Calling context.stopService()")
        context.stopService(intent)
        serviceIntent = null

        handler.removeCallbacks(timeoutRunnable)

        if (!timeoutTriggered) {
          val duration = System.currentTimeMillis() - startTime
          android.util.Log.i("ForegroundServiceModule", "Service stopped successfully in ${duration}ms")
        }

      } catch (e: Exception) {
        val duration = System.currentTimeMillis() - startTime
        android.util.Log.e("ForegroundServiceModule", "Service stop failed after ${duration}ms", e)
        serviceIntent = null
      }
    } ?: android.util.Log.w("ForegroundServiceModule", "No service intent to stop")
  }

  private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channelName = getMetadata("expo.modules.foregroundservice.notification_channel_name")
        ?: "Foreground Service Channel"
      val channelDescription = getMetadata("expo.modules.foregroundservice.notification_channel_description")
        ?: "Channel for foreground service notifications"

      val channel = NotificationChannel(
        ForegroundTaskService.CHANNEL_ID,
        channelName,
        NotificationManager.IMPORTANCE_DEFAULT
      ).apply {
        description = channelDescription
      }
      notificationManager.createNotificationChannel(channel)
    }
  }

  private fun getMetadata(name: String): String? {
    return try {
      val appInfo = context.packageManager.getApplicationInfo(
        context.packageName,
        android.content.pm.PackageManager.GET_META_DATA
      )
      appInfo.metaData?.getString(name)
    } catch (e: Exception) {
      null
    }
  }
}
