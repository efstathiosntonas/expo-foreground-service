package expo.modules.foregroundservice

/*
 * Production Configuration Guide:
 *
 * TIMEOUT CONSTANTS (adjust based on production requirements):
 * - SERVICE_CLEANUP_TIMEOUT_MS: Maximum time for service cleanup (default: 3000ms)
 * - WAKE_LOCK_RELEASE_TIMEOUT_MS: Maximum time for wake lock release (default: 250ms)
 * - WAKE_LOCK_ACQUIRE_TIMEOUT_MS: Maximum wake lock hold time (default: 10 minutes)
 *
 * LOGGING FLAGS (set to false in production for performance):
 * - ENABLE_DETAILED_LOGGING: Controls verbose debug logs (default: true)
 * - ENABLE_PERFORMANCE_METRICS: Controls timing measurements (default: true)
 *
 * To adjust for production:
 * 1. Set logging flags to false for better performance
 * 2. Increase timeout values if needed based on device performance metrics
 * 3. Monitor logs for timeout warnings to tune values appropriately
 */

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.facebook.react.HeadlessJsTaskService
import com.facebook.react.bridge.Arguments
import com.facebook.react.jstasks.HeadlessJsTaskConfig

class ForegroundTaskService : Service() {
  companion object {
    const val CHANNEL_ID = "ForegroundServiceChannel"
    const val ACTION_START_SERVICE = "ACTION_START_SERVICE"
    const val ACTION_UPDATE_NOTIFICATION = "ACTION_UPDATE_NOTIFICATION"
    const val ACTION_RUN_TASK = "ACTION_RUN_TASK"
    const val ACTION_BUTTON_CLICK = "ACTION_BUTTON_CLICK"
    const val ACTION_MAIN_CLICK = "ACTION_MAIN_CLICK"

    private const val SERVICE_CLEANUP_TIMEOUT_MS = 3000L
    private const val WAKE_LOCK_RELEASE_TIMEOUT_MS = 250L
    private const val WAKE_LOCK_ACQUIRE_TIMEOUT_MS = 10 * 60 * 1000L

    private const val ENABLE_DETAILED_LOGGING = false
    private const val ENABLE_PERFORMANCE_METRICS = false
  }

  private var notificationBuilder: NotificationCompat.Builder? = null
  private var currentNotificationId: Int = 1
  private var isServiceRunning = false
  private var wakeLock: PowerManager.WakeLock? = null

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    // For Android 12+, immediately show a notification within 5 seconds to prevent ANR
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        intent?.action == ACTION_START_SERVICE &&
        !isServiceRunning) {
      // Create a temporary notification immediately to satisfy the 5-second requirement
      val tempNotification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Starting service...")
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setOngoing(true)
        .build()

      try {
        startForeground(9999, tempNotification)
      } catch (e: Exception) {
        e.printStackTrace()
      }
    }

    when (intent?.action) {
      ACTION_START_SERVICE -> {
        if (!isServiceRunning) {
          acquireWakeLock()
          startForegroundServiceWithNotification(intent)
          isServiceRunning = true
        }
      }
      ACTION_UPDATE_NOTIFICATION -> {
        updateNotification(intent)
      }
      ACTION_RUN_TASK -> {
        runHeadlessTask(intent)
      }
      ACTION_BUTTON_CLICK -> {
        handleButtonClick(intent)
      }
      ACTION_MAIN_CLICK -> {
        handleMainClick(intent)
      }
    }
    return START_STICKY
  }

  private fun startForegroundServiceWithNotification(intent: Intent) {
    currentNotificationId = intent.getIntExtra("notificationId", 1)
    val notification = buildNotification(intent)

    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        // Android 14 (API 34) and above
        val serviceType = when (intent.getStringExtra("ServiceType")) {
          "dataSync" -> ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
          "mediaPlayback" -> ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
          "location" -> ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
          "connectedDevice" -> ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
          "camera" -> ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
          "mediaProjection" -> ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
          else -> ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        }
        startForeground(currentNotificationId, notification, serviceType)
      } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        // Android 10 (API 29) to Android 13
        val serviceType = when (intent.getStringExtra("ServiceType")) {
          "dataSync" -> ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
          "mediaPlayback" -> ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
          "location" -> ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
          "connectedDevice" -> ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
          "camera" -> ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
          "mediaProjection" -> ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
          else -> ServiceInfo.FOREGROUND_SERVICE_TYPE_NONE
        }
        startForeground(currentNotificationId, notification, serviceType)
      } else {
        // Below Android 10
        startForeground(currentNotificationId, notification)
      }
    } catch (e: Exception) {
      e.printStackTrace()
      // Fallback to basic startForeground if there's an issue
      startForeground(currentNotificationId, notification)
    }
  }

  private fun buildNotification(intent: Intent): Notification {
    val title = intent.getStringExtra("title") ?: "Foreground Service"
    val message = intent.getStringExtra("message") ?: "Service is running..."
    val icon = getResourceId(intent.getStringExtra("icon") ?: "ic_notification", "drawable")
    val largeIcon = intent.getStringExtra("largeIcon")?.let {
      getResourceId(it, "drawable")
    }
    val importance = intent.getStringExtra("importance") ?: "default"
    val visibility = intent.getStringExtra("visibility") ?: "public"
    val vibration = intent.getBooleanExtra("vibration", false)
    val ongoing = intent.getBooleanExtra("ongoing", true)
    val progressBar = intent.getBooleanExtra("progressBar", false)
    val progressBarMax = intent.getIntExtra("progressBarMax", 100)
    val progressBarCurr = intent.getIntExtra("progressBarCurr", 0)
    val color = intent.getStringExtra("color")
    val setOnlyAlertOnce = intent.getBooleanExtra("setOnlyAlertOnce", false)
    val number = intent.getStringExtra("number")?.toIntOrNull()

    val builder = NotificationCompat.Builder(this, CHANNEL_ID).apply {
      setContentTitle(title)
      setContentText(message)
      setSmallIcon(icon)
      setOngoing(ongoing)
      setOnlyAlertOnce(setOnlyAlertOnce)
      setPriority(getNotificationPriority(importance))
      setVisibility(getNotificationVisibility(visibility))

      // Use BigTextStyle for better readability (from original NotificationHelper)
      setStyle(NotificationCompat.BigTextStyle().bigText(message))

      if (vibration) {
        setVibrate(longArrayOf(0, 250, 250, 250))
      }

      largeIcon?.let { iconRes ->
        try {
          // Decode bitmap with size constraints to prevent OOM
          val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
          }
          BitmapFactory.decodeResource(resources, iconRes, options)

          // Calculate sample size to limit icon to reasonable dimensions (max 512x512)
          val maxSize = 512
          var sampleSize = 1
          while (options.outWidth / sampleSize > maxSize || options.outHeight / sampleSize > maxSize) {
            sampleSize *= 2
          }

          // Decode with calculated sample size
          val finalOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
          }
          val bitmap = BitmapFactory.decodeResource(resources, iconRes, finalOptions)
          if (bitmap != null) {
            setLargeIcon(bitmap)
          }
        } catch (e: OutOfMemoryError) {
          // Skip large icon if OOM
          e.printStackTrace()
        }
      }

      // Use color from parameter or fallback to metadata color
      val notificationColor = color?.let {
        try {
          Color.parseColor(it)
        } catch (e: IllegalArgumentException) {
          null
        }
      } ?: getMetadataResourceColor()

      notificationColor?.let {
        setColor(it)
      }

      number?.let {
        setNumber(it)
      }

      if (progressBar) {
        setProgress(progressBarMax, progressBarCurr, false)
      }

      // Define PendingIntent flags for all intents
      val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        PendingIntent.FLAG_UPDATE_CURRENT
      } else {
        0
      }

      // Main notification click
      val mainOnPress = intent.getStringExtra("mainOnPress")
      if (!mainOnPress.isNullOrEmpty()) {
        val mainIntent = Intent(this@ForegroundTaskService, ForegroundTaskService::class.java).apply {
          action = ACTION_MAIN_CLICK
          putExtra("action", mainOnPress)
        }
        val mainPendingIntent = PendingIntent.getService(
          this@ForegroundTaskService,
          0,
          mainIntent,
          flags
        )
        setContentIntent(mainPendingIntent)
      }

      // Action buttons
      if (intent.getBooleanExtra("button", false)) {
        val buttonText = intent.getStringExtra("buttonText") ?: "Action"
        val buttonOnPress = intent.getStringExtra("buttonOnPress") ?: ""
        val buttonIntent = Intent(this@ForegroundTaskService, ForegroundTaskService::class.java).apply {
          action = ACTION_BUTTON_CLICK
          putExtra("action", buttonOnPress)
        }
        val buttonPendingIntent = PendingIntent.getService(
          this@ForegroundTaskService,
          1,
          buttonIntent,
          flags
        )
        addAction(0, buttonText, buttonPendingIntent)
      }

      if (intent.getBooleanExtra("button2", false)) {
        val button2Text = intent.getStringExtra("button2Text") ?: "Action 2"
        val button2OnPress = intent.getStringExtra("button2OnPress") ?: ""
        val button2Intent = Intent(this@ForegroundTaskService, ForegroundTaskService::class.java).apply {
          action = ACTION_BUTTON_CLICK
          putExtra("action", button2OnPress)
        }
        val button2PendingIntent = PendingIntent.getService(
          this@ForegroundTaskService,
          2,
          button2Intent,
          flags
        )
        addAction(0, button2Text, button2PendingIntent)
      }
    }

    notificationBuilder = builder
    return builder.build()
  }

  private fun updateNotification(intent: Intent) {
    val notification = buildNotification(intent)
    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    notificationManager.notify(currentNotificationId, notification)

    // If service wasn't running, start it
    if (!isServiceRunning) {
      startForegroundServiceWithNotification(intent)
      isServiceRunning = true
    }
  }

  private fun runHeadlessTask(intent: Intent) {
    val taskName = intent.getStringExtra("taskName") ?: return
    val delay = intent.getIntExtra("delay", 0)
    val loopDelay = intent.getIntExtra("loopDelay", 0)
    val onLoop = intent.getBooleanExtra("onLoop", false)

    // Send the task parameters to the HeadlessTaskRunner
    val headlessIntent = Intent(this, HeadlessTaskRunner::class.java).apply {
      putExtra("taskName", taskName)
      putExtra("delay", delay)
      putExtra("loopDelay", loopDelay)
      putExtra("onLoop", onLoop)
    }
    startService(headlessIntent)
  }

  private fun handleButtonClick(intent: Intent) {
    val action = intent.getStringExtra("action") ?: return
    // Send event to React Native
    sendEventToReactNative("notificationClickHandle", action)
  }

  private fun handleMainClick(intent: Intent) {
    val action = intent.getStringExtra("action") ?: return
    // Send event to React Native
    sendEventToReactNative("notificationClickHandle", action)
  }

  private fun sendEventToReactNative(eventName: String, action: String) {
    // Use local broadcast with explicit package to prevent external apps from intercepting
    val broadcastIntent = Intent("expo.modules.foregroundservice.EVENT").apply {
      setPackage(packageName) // Restrict to our app only
      putExtra("eventName", eventName)
      putExtra("action", action)
    }
    // Use explicit permission to further secure the broadcast
    sendBroadcast(broadcastIntent, "${packageName}.permission.FOREGROUND_SERVICE_BROADCAST")
  }

  private fun getResourceId(name: String, type: String): Int {
    return resources.getIdentifier(name, type, packageName).takeIf { it != 0 }
      ?: resources.getIdentifier("ic_launcher", "mipmap", packageName)
  }

  private fun getNotificationPriority(importance: String): Int {
    return when (importance) {
      "max" -> NotificationCompat.PRIORITY_MAX
      "high" -> NotificationCompat.PRIORITY_HIGH
      "default" -> NotificationCompat.PRIORITY_DEFAULT
      "low" -> NotificationCompat.PRIORITY_LOW
      "min" -> NotificationCompat.PRIORITY_MIN
      else -> NotificationCompat.PRIORITY_DEFAULT
    }
  }

  private fun getNotificationVisibility(visibility: String): Int {
    return when (visibility) {
      "public" -> NotificationCompat.VISIBILITY_PUBLIC
      "private" -> NotificationCompat.VISIBILITY_PRIVATE
      "secret" -> NotificationCompat.VISIBILITY_SECRET
      else -> NotificationCompat.VISIBILITY_PUBLIC
    }
  }

  override fun onDestroy() {
    val startTime = if (ENABLE_PERFORMANCE_METRICS) System.currentTimeMillis() else 0L
    if (ENABLE_DETAILED_LOGGING) {
      android.util.Log.i("ForegroundTaskService", "onDestroy() started")
    }

    try {
      val handler = android.os.Handler(android.os.Looper.getMainLooper())
      var timeoutTriggered = false
      val timeoutRunnable = Runnable {
        timeoutTriggered = true
        if (ENABLE_PERFORMANCE_METRICS) {
          val timeoutDuration = System.currentTimeMillis() - startTime
          android.util.Log.w("ForegroundTaskService", "Service cleanup timeout triggered after ${timeoutDuration}ms")
        }
        super.onDestroy()
      }

      handler.postDelayed(timeoutRunnable, SERVICE_CLEANUP_TIMEOUT_MS)

      performQuickCleanup()

      handler.removeCallbacks(timeoutRunnable)

      if (!timeoutTriggered && ENABLE_PERFORMANCE_METRICS) {
        val duration = System.currentTimeMillis() - startTime
        android.util.Log.i("ForegroundTaskService", "Service cleanup completed successfully in ${duration}ms")
      }

    } catch (e: Exception) {
      if (ENABLE_PERFORMANCE_METRICS) {
        val duration = System.currentTimeMillis() - startTime
        android.util.Log.e("ForegroundTaskService", "Service cleanup failed after ${duration}ms", e)
      } else {
        android.util.Log.e("ForegroundTaskService", "Service cleanup failed", e)
      }
    } finally {
      if (ENABLE_PERFORMANCE_METRICS) {
        val totalDuration = System.currentTimeMillis() - startTime
        android.util.Log.i("ForegroundTaskService", "onDestroy() completed in ${totalDuration}ms")
      }
      super.onDestroy()
    }
  }

  private fun performQuickCleanup() {
    val cleanupStart = if (ENABLE_PERFORMANCE_METRICS) System.currentTimeMillis() else 0L
    if (ENABLE_DETAILED_LOGGING) {
      android.util.Log.d("ForegroundTaskService", "Quick cleanup started")
    }

    try {
      if (ENABLE_DETAILED_LOGGING) {
        android.util.Log.d("ForegroundTaskService", "Releasing wake lock...")
      }
      releaseWakeLock()

      if (ENABLE_DETAILED_LOGGING) {
        android.util.Log.d("ForegroundTaskService", "Cleaning notification builder...")
      }
      notificationBuilder = null

      if (ENABLE_DETAILED_LOGGING) {
        android.util.Log.d("ForegroundTaskService", "Marking service as stopped...")
      }
      isServiceRunning = false

      if (ENABLE_DETAILED_LOGGING) {
        android.util.Log.d("ForegroundTaskService", "Stopping foreground mode...")
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        stopForeground(STOP_FOREGROUND_REMOVE)
      } else {
        @Suppress("DEPRECATION")
        stopForeground(true)
      }

      if (ENABLE_PERFORMANCE_METRICS) {
        val duration = System.currentTimeMillis() - cleanupStart
        android.util.Log.d("ForegroundTaskService", "Quick cleanup completed in ${duration}ms")
      }

    } catch (e: Exception) {
      if (ENABLE_PERFORMANCE_METRICS) {
        val duration = System.currentTimeMillis() - cleanupStart
        android.util.Log.e("ForegroundTaskService", "Quick cleanup failed after ${duration}ms", e)
      } else {
        android.util.Log.e("ForegroundTaskService", "Quick cleanup failed", e)
      }
    }
  }

  private fun acquireWakeLock() {
    try {
      if (wakeLock == null) {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
          PowerManager.PARTIAL_WAKE_LOCK,
          "${packageName}:ForegroundService"
        ).apply {
          setReferenceCounted(false)
        }
      }
      wakeLock?.acquire(WAKE_LOCK_ACQUIRE_TIMEOUT_MS)
      if (ENABLE_DETAILED_LOGGING) {
        android.util.Log.d("ForegroundTaskService", "Wake lock acquired for ${WAKE_LOCK_ACQUIRE_TIMEOUT_MS}ms")
      }
    } catch (e: Exception) {
      android.util.Log.e("ForegroundTaskService", "Failed to acquire wake lock", e)
    }
  }

  private fun releaseWakeLock() {
    val startTime = if (ENABLE_PERFORMANCE_METRICS) System.currentTimeMillis() else 0L
    try {
      wakeLock?.let {
        if (it.isHeld) {
          android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            try {
              if (it.isHeld) {
                android.util.Log.w("ForegroundTaskService", "Wake lock timeout triggered after ${WAKE_LOCK_RELEASE_TIMEOUT_MS}ms")
                it.release()
              }
            } catch (e: Exception) {
              android.util.Log.e("ForegroundTaskService", "Wake lock timeout release failed", e)
            }
          }, WAKE_LOCK_RELEASE_TIMEOUT_MS)

          it.release()
          if (ENABLE_PERFORMANCE_METRICS) {
            val duration = System.currentTimeMillis() - startTime
            android.util.Log.d("ForegroundTaskService", "Wake lock released in ${duration}ms")
          }
        }
      }
      wakeLock = null
    } catch (e: Exception) {
      if (ENABLE_PERFORMANCE_METRICS) {
        val duration = System.currentTimeMillis() - startTime
        android.util.Log.e("ForegroundTaskService", "Wake lock release failed after ${duration}ms", e)
      } else {
        android.util.Log.e("ForegroundTaskService", "Wake lock release failed", e)
      }
      wakeLock = null
    }
  }

  private fun getMetadataResourceColor(): Int? {
    return try {
      val appInfo = packageManager.getApplicationInfo(
        packageName,
        android.content.pm.PackageManager.GET_META_DATA
      )
      val resourceId = appInfo.metaData?.getInt("expo.modules.foregroundservice.notification_color", 0) ?: 0
      if (resourceId != 0) {
        androidx.core.content.ContextCompat.getColor(this, resourceId)
      } else {
        null
      }
    } catch (e: Exception) {
      null
    }
  }
}
