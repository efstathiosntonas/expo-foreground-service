import { requireOptionalNativeModule } from 'expo-modules-core';

import {
  ForegroundServiceModuleEvents,
  NotificationConfig,
  TaskConfig
} from './ForegroundService.types';

type ForegroundServiceEventName = keyof ForegroundServiceModuleEvents;

export type ForegroundServiceModuleType = {
  addListener<EventName extends ForegroundServiceEventName>(
    eventName: EventName,
    listener: (event: Parameters<ForegroundServiceModuleEvents[EventName]>[0]) => void
  ): { remove(): void };
  cancelNotification(params: { id: number }): Promise<void>;
  isRunning(): Promise<number>;
  removeListeners(count: number): void;
  runTask(taskConfig: TaskConfig): Promise<void>;
  startService(config: NotificationConfig): Promise<void>;
  stopService(): Promise<void>;
  stopServiceAll(): Promise<void>;
  updateNotification(config: NotificationConfig): Promise<void>;
};

const ForegroundServiceModuleInstance =
  requireOptionalNativeModule<ForegroundServiceModuleType>('ForegroundService');

export default ForegroundServiceModuleInstance;
