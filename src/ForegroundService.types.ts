export interface NotificationConfig {
  ServiceType?: string;
  button?: boolean;
  button2?: boolean;
  button2OnPress?: string;
  button2Text?: string;
  buttonOnPress?: string;
  buttonText?: string;
  color?: string;
  icon?: string;
  id?: number | string;
  importance?: 'min' | 'low' | 'default' | 'high' | 'max';
  largeIcon?: string;
  mainOnPress?: string;
  message?: string;
  number?: string;
  ongoing?: boolean;
  progress?: { curr: number; max: number };
  progressBar?: boolean;
  progressBarCurr?: number;
  progressBarMax?: number;
  setOnlyAlertOnce?: boolean;
  title?: string;
  vibration?: boolean;
  visibility?: 'public' | 'private' | 'secret';
}

export interface TaskConfig {
  delay?: number;
  loopDelay?: number;
  onLoop?: boolean;
  taskName: string;
}

export type ForegroundServiceModuleEvents = {
  notificationClickHandle(params: { action: string }): void;
  onServiceError(params: { message: string }): void;
};
