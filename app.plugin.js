const {
  AndroidConfig,
  withAndroidManifest,
  createRunOncePlugin,
  WarningAggregator
} = require('@expo/config-plugins');

const pkg = require('./package.json');

// Fully qualified so it matches the element the library's own AndroidManifest.xml declares
// (relative ".ForegroundTaskService" there resolves against the library's own namespace).
const SERVICE_CLASS_NAME = 'expo.modules.foregroundservice.ForegroundTaskService';

const SERVICE_TYPE_PERMISSIONS = {
  dataSync: 'android.permission.FOREGROUND_SERVICE_DATA_SYNC',
  mediaPlayback: 'android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK',
  location: 'android.permission.FOREGROUND_SERVICE_LOCATION',
  connectedDevice: 'android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE',
  camera: 'android.permission.FOREGROUND_SERVICE_CAMERA',
  mediaProjection: 'android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION'
};

function withForegroundServiceType(config, serviceTypes) {
  return withAndroidManifest(config, (config) => {
    const app = config.modResults.manifest.application?.[0];
    if (!app) {
      return config;
    }

    app.service = app.service ?? [];
    let service = app.service.find((s) => s.$['android:name'] === SERVICE_CLASS_NAME);
    if (!service) {
      service = { $: { 'android:name': SERVICE_CLASS_NAME } };
      app.service.push(service);
    }
    service.$['android:foregroundServiceType'] = serviceTypes.join('|');

    return config;
  });
}

/**
 * @param {import('@expo/config-plugins').ExpoConfig} config
 * @param {{ serviceTypes?: (keyof typeof SERVICE_TYPE_PERMISSIONS)[] }} [props]
 */
function withForegroundService(config, props = {}) {
  const serviceTypes = props.serviceTypes ?? ['dataSync'];

  const permissions = [];
  for (const type of serviceTypes) {
    const permission = SERVICE_TYPE_PERMISSIONS[type];
    if (!permission) {
      WarningAggregator.addWarningAndroid(
        'expo-foreground-service',
        `Unknown foreground service type "${type}" (ignored). Valid types: ${Object.keys(SERVICE_TYPE_PERMISSIONS).join(', ')}.`
      );
      continue;
    }
    permissions.push(permission);
  }

  config = AndroidConfig.Permissions.withPermissions(config, [
    'android.permission.FOREGROUND_SERVICE',
    'android.permission.WAKE_LOCK',
    ...permissions
  ]);

  config = withForegroundServiceType(config, serviceTypes);

  return config;
}

module.exports = createRunOncePlugin(withForegroundService, pkg.name, pkg.version);
