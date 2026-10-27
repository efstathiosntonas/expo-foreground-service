import { AppRegistry, Platform } from 'react-native';

import { NotificationConfig, TaskConfig } from './ForegroundService.types';
import ForegroundServiceModule from './ForegroundServiceModule';
import type { ForegroundServiceModuleType } from './ForegroundServiceModule';

const isAndroid = Platform.OS === 'android';

const FOREGROUND_TASK_RUNNER_NAME = 'foregroundServiceTaskRunner';

interface Task {
  delay: number;
  maxRetries?: number;
  nextExecutionTime: number;
  onError: (error: any) => void;
  onLoop: boolean;
  onSuccess: () => void;
  retryCount?: number;
  task: () => Promise<any> | any;
  taskId: string;
}

class ForegroundServiceManager {
  private static tasks: Record<string, Task> = {};

  private static samplingInterval = 500;

  private static serviceRunning = false;

  static registerForegroundTask(taskName: string, task: () => Promise<any> | any) {
    if (!isAndroid) return;
    AppRegistry.registerHeadlessTask(taskName, () => task);
  }

  static async startService(config: NotificationConfig): Promise<void> {
    if (!isAndroid) return;
    const nativeModule = this.ensureNativeModule();

    if (!this.serviceRunning) {
      await nativeModule.startService(config);
      this.serviceRunning = true;

      await nativeModule.runTask({
        taskName: FOREGROUND_TASK_RUNNER_NAME,
        delay: this.samplingInterval,
        loopDelay: this.samplingInterval,
        onLoop: true
      });
    }
  }

  static async updateNotification(config: NotificationConfig): Promise<void> {
    if (!isAndroid) return;
    const nativeModule = this.ensureNativeModule();

    await nativeModule.updateNotification(config);

    if (!this.serviceRunning) {
      this.serviceRunning = true;
      await nativeModule.runTask({
        taskName: FOREGROUND_TASK_RUNNER_NAME,
        delay: this.samplingInterval,
        loopDelay: this.samplingInterval,
        onLoop: true
      });
    }
  }

  static async stopService(): Promise<void> {
    if (!isAndroid) return;
    const nativeModule = this.ensureNativeModule();
    this.serviceRunning = false;
    await nativeModule.stopService();
  }

  static async stopServiceAll(): Promise<void> {
    if (!isAndroid) return;
    const nativeModule = this.ensureNativeModule();
    this.serviceRunning = false;
    await nativeModule.stopServiceAll();
  }

  static async cancelNotification(id: number): Promise<void> {
    if (!isAndroid) return;
    const nativeModule = this.ensureNativeModule();
    await nativeModule.cancelNotification({ id });
  }

  static async runTask(taskConfig: TaskConfig): Promise<void> {
    if (!isAndroid) return;
    const nativeModule = this.ensureNativeModule();
    await nativeModule.runTask(taskConfig);
  }

  static async isRunning(): Promise<number> {
    if (!isAndroid) return 0;
    const nativeModule = this.ensureNativeModule();
    return nativeModule.isRunning();
  }

  static addTask(
    task: () => Promise<any> | any,
    options: {
      delay?: number;
      onError?: (error: any) => void;
      onLoop?: boolean;
      onSuccess?: () => void;
      taskId?: string;
    } = {}
  ): string {
    const taskId = options.taskId || this.generateTaskId();
    const delay =
      Math.ceil((options.delay || 5000) / this.samplingInterval) * this.samplingInterval;

    if (!this.tasks[taskId]) {
      this.tasks[taskId] = {
        task,
        nextExecutionTime: Date.now(),
        delay,
        onLoop: options.onLoop ?? true,
        taskId,
        onSuccess: options.onSuccess || (() => {}),
        onError: options.onError || (() => {})
      };
    }

    return taskId;
  }

  static updateTask(
    task: () => Promise<any> | any,
    options: {
      delay?: number;
      onError?: (error: any) => void;
      onLoop?: boolean;
      onSuccess?: () => void;
      taskId?: string;
    } = {}
  ): string {
    const taskId = options.taskId || this.generateTaskId();
    const delay =
      Math.ceil((options.delay || 5000) / this.samplingInterval) * this.samplingInterval;

    this.tasks[taskId] = {
      task,
      nextExecutionTime: Date.now(),
      delay,
      onLoop: options.onLoop ?? true,
      taskId,
      onSuccess: options.onSuccess || (() => {}),
      onError: options.onError || (() => {})
    };

    return taskId;
  }

  static removeTask(taskId: string): void {
    delete this.tasks[taskId];
  }

  static removeAllTasks(): void {
    this.tasks = {};
  }

  static isTaskRunning(taskId: string): boolean {
    return !!this.tasks[taskId];
  }

  static getTask(taskId: string): Task | undefined {
    return this.tasks[taskId];
  }

  static getAllTasks(): Record<string, Task> {
    return this.tasks;
  }

  static eventListener(callback: (data: any) => void) {
    if (!isAndroid) return () => {};
    const nativeModule = this.ensureNativeModule();

    const subscription = nativeModule.addListener('notificationClickHandle', callback);

    return () => {
      subscription.remove();
    };
  }

  static setupServiceErrorListener(options: {
    alert?: boolean;
    onServiceFailToStart?: () => void;
  }) {
    if (!isAndroid) return () => {};
    const nativeModule = this.ensureNativeModule();

    const subscription = nativeModule.addListener('onServiceError', (event) => {
      if (options.alert) {
        console.error('Service Error:', event.message);
      }
      if (options.onServiceFailToStart) {
        options.onServiceFailToStart();
      }
      void this.stopService();
    });

    return () => {
      subscription.remove();
    };
  }

  private static generateTaskId(length = 12): string {
    return 'x'.repeat(length).replace(/[xy]/g, () => {
      const r = (Math.random() * 16) | 0;
      return r.toString(16);
    });
  }

  private static async taskRunner(): Promise<void> {
    try {
      if (!this.serviceRunning) return;

      const now = Date.now();
      const promises: Promise<any>[] = [];
      const tasksToDelete: string[] = [];

      Object.entries(this.tasks).forEach(([taskId, task]) => {
        if (now >= task.nextExecutionTime) {
          if (!task.retryCount) task.retryCount = 0;

          promises.push(
            Promise.resolve()
              .then(() => task.task())
              .then(() => {
                task.retryCount = 0;
                return task.onSuccess();
              })
              .catch((error) => {
                task.retryCount = (task.retryCount || 0) + 1;
                const maxRetries = task.maxRetries || 3;

                if (task.retryCount >= maxRetries && !task.onLoop) {
                  tasksToDelete.push(taskId);
                  console.error(
                    `Task ${taskId} failed after ${maxRetries} retries:`,
                    error
                  );
                }

                return task.onError(error);
              })
          );

          if (task.onLoop) {
            task.nextExecutionTime = now + task.delay;
          } else if (!task.retryCount || task.retryCount >= (task.maxRetries || 3)) {
            tasksToDelete.push(taskId);
          }
        }
      });

      tasksToDelete.forEach((taskId) => delete this.tasks[taskId]);

      await Promise.all(promises);
    } catch (error) {
      console.error('Error in FgService taskRunner:', error);
    }
  }

  private static ensureNativeModule(): ForegroundServiceModuleType {
    if (!ForegroundServiceModule) {
      throw new Error('ForegroundService native module is unavailable on this platform.');
    }

    return ForegroundServiceModule;
  }
}

ForegroundServiceManager.registerForegroundTask(FOREGROUND_TASK_RUNNER_NAME, () => {
  return ForegroundServiceManager['taskRunner']();
});

const ReactNativeForegroundService = {
  add_task: ForegroundServiceManager.addTask.bind(ForegroundServiceManager),
  eventListener: ForegroundServiceManager.eventListener.bind(ForegroundServiceManager),
  get_all_tasks: ForegroundServiceManager.getAllTasks.bind(ForegroundServiceManager),
  get_task: ForegroundServiceManager.getTask.bind(ForegroundServiceManager),
  is_running: () => ForegroundServiceManager['serviceRunning'],
  is_task_running: ForegroundServiceManager.isTaskRunning.bind(ForegroundServiceManager),
  register: (config: {
    config: { alert?: boolean; onServiceErrorCallBack?: () => void };
  }) => {
    if (!isAndroid) return;

    const { alert, onServiceErrorCallBack } = config.config;
    ForegroundServiceManager.setupServiceErrorListener({
      alert,
      onServiceFailToStart: onServiceErrorCallBack
    });
  },
  remove_all_tasks: ForegroundServiceManager.removeAllTasks.bind(
    ForegroundServiceManager
  ),
  remove_task: ForegroundServiceManager.removeTask.bind(ForegroundServiceManager),
  start: ForegroundServiceManager.startService.bind(ForegroundServiceManager),
  stop: ForegroundServiceManager.stopService.bind(ForegroundServiceManager),
  stopAll: ForegroundServiceManager.stopServiceAll.bind(ForegroundServiceManager),
  update: ForegroundServiceManager.updateNotification.bind(ForegroundServiceManager),
  update_task: ForegroundServiceManager.updateTask.bind(ForegroundServiceManager)
};

export default ReactNativeForegroundService;
export { ForegroundServiceManager };
export * from './ForegroundService.types';
