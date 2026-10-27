# @like-magic-dev/expo-foreground-service

Run a resilient Android foreground service with a persistent, configurable notification and a
background task runner, from Expo/React Native.

**Android only.** Battle tested with thousands of devices and users. On iOS and web every call is
a no-op, so it's safe to import and use from shared code.

## Installation

```sh
npx expo install @like-magic-dev/expo-foreground-service
```

Add the config plugin to your `app.json`/`app.config.js`, declaring which foreground service
type(s) your app actually uses — this controls which `FOREGROUND_SERVICE_*` permission(s) get
added to your manifest:

```json
{
  "expo": {
    "plugins": [
      [
        "@like-magic-dev/expo-foreground-service",
        {
          "serviceTypes": ["dataSync"]
        }
      ]
    ]
  }
}
```

`serviceTypes` defaults to `["dataSync"]` if omitted. Valid values: `dataSync`, `mediaPlayback`,
`location`, `connectedDevice`, `camera`, `mediaProjection` — list every type you'll pass as
`ServiceType` in any `start()`/`update()` call anywhere in the app. Each one adds exactly the
matching `android.permission.FOREGROUND_SERVICE_*` permission (plus the always-required
`FOREGROUND_SERVICE` and `WAKE_LOCK`), so only declare the types you actually use.

Rebuild your dev client / native project afterwards (`npx expo prebuild` or a new EAS build) since
this package ships native Android code and the plugin only runs at prebuild time.

### Permissions

This module needs the notification permission to be granted before starting the service. It will
silently fail if the permission isn't granted — the module does **not** ask for permissions itself,
that's up to your app, for example:

```ts
import { PermissionsAndroid, Platform } from 'react-native';

if (Platform.OS === 'android' && Number(Platform.Version) >= 33) {
  await PermissionsAndroid.request(PermissionsAndroid.PERMISSIONS.POST_NOTIFICATIONS).catch((e) =>
    console.log(e)
  );
}
```

### AndroidManifest configuration

This module merges a `<service>` declaration and some default `<meta-data>` into your app's merged
manifest. Override the channel name, description and notification color for your app by adding the
following `meta-data` entries to your own `android/app/src/main/AndroidManifest.xml` (or via a
config plugin):

```xml
<meta-data
    android:name="expo.modules.foregroundservice.notification_channel_name"
    android:value="Put your app's channel name here, e.g. App Channel" />
<meta-data
    android:name="expo.modules.foregroundservice.notification_channel_description"
    android:value="App is running in foreground" />
<meta-data
    android:name="expo.modules.foregroundservice.notification_color"
    android:resource="@color/some_color_from_your_theme" />
```

## Usage

```ts
import ForegroundService from '@like-magic-dev/expo-foreground-service';

await ForegroundService.start({
  ServiceType: 'dataSync',
  icon: 'ic_launcher',
  importance: 'low',
  message: 'App will stay alive during capture',
  setOnlyAlertOnce: true,
  title: 'Camera Active',
  visibility: 'secret'
});

// ... do work that needs to survive the app being backgrounded ...

await ForegroundService.stop();
```

### Real-life scenario

```ts
import { useCallback } from 'react';
import { Platform } from 'react-native';
import ForegroundService from '@like-magic-dev/expo-foreground-service';
import {
  getCameraPermissionsAsync,
  requestCameraPermissionsAsync
} from 'expo-image-picker';

type CameraOptions = {
  errorContext?: string;
  onError?: (e: any) => void;
};

/**
 * A hook for handling camera operations with consistent error handling
 */
export const useCamera = () => {
  const handleCameraCapture = useCallback(
    async <T>(
      onSuccess: (image: { uri: string }) => T | Promise<T>,
      options: CameraOptions = {}
    ): Promise<T | undefined> => {
      const { errorContext = 'CameraCapture', onError } = options;

      try {
        await ForegroundService.start({
          ServiceType: 'dataSync',
          icon: 'ic_launcher',
          importance: 'low',
          message: 'App will stay alive during capture',
          setOnlyAlertOnce: true,
          title: 'Camera Active',
          visibility: 'secret'
        });

        const { status } = await getCameraPermissionsAsync();
        if (status !== 'granted') {
          const perm = await requestCameraPermissionsAsync();
          if (perm.status !== 'granted') {
            throw new Error('Required permission not granted');
          }
        }

        const img = await openCamera();
        return await onSuccess(img);
      } catch (e: any) {
        console.log(`[${errorContext}]`, e);
        onError?.(e);
        return undefined;
      } finally {
        await ForegroundService.stop();
      }
    },
    []
  );

  return { handleCameraCapture };
};
```

## API

See [`src/ForegroundService.ts`](./src/ForegroundService.ts) for the implementation and
[`src/ForegroundService.types.ts`](./src/ForegroundService.types.ts) for the full `NotificationConfig`
and `TaskConfig` shapes. The exported default object:

| Method | Description |
| --- | --- |
| `start(config: NotificationConfig)` | Starts the foreground service with a notification. Reference-counted: safe to call from multiple call sites. |
| `update(config: NotificationConfig)` | Updates the current notification, starting the service if it isn't running yet. |
| `stop()` | Decrements the start reference count; stops the service once it reaches zero. |
| `stopAll()` | Forcibly stops the service regardless of the reference count. |
| `is_running()` | Returns whether the service is currently running. |
| `add_task(task, options)` / `update_task(task, options)` / `remove_task(id)` / `remove_all_tasks()` | Register recurring or one-off work that gets ticked while the service is running. |
| `get_task(id)` / `get_all_tasks()` / `is_task_running(id)` | Inspect registered tasks. |
| `eventListener(callback)` | Subscribe to notification button/click events. |
| `register({ config })` | Registers a listener that stops the service and calls back when the native service fails to start. |

## Development

```sh
npm install   # also builds the package (prepare script)
npm run build # compile TypeScript to build/
npm run format
```

This repository is the module itself — there's no example app bundled. To try it out locally,
reference it from an Expo app with a `file:` or `link:` dependency, or `npm pack` it and install the
tarball, then run `npx expo prebuild` in that app.

More info on Expo modules: <https://docs.expo.dev/modules/overview/> and
<https://docs.expo.dev/modules/native-module-tutorial/>.

## License

MIT
