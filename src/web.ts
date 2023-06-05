import { WebPlugin } from '@capacitor/core';
import type { PermissionType } from '@capacitor/core';

import type {
  CallbackID,
  PermissionStatus,
  Position,
  PositionOptions,
  WatchPositionCallback,
} from './definitions';

export class NativeGeolocationWeb extends WebPlugin {
  constructor() {
    super({
      name: 'NativeGeolocation',
      platforms: ['web'],
    });
  }

  async getCurrentPosition(options?: PositionOptions): Promise<Position> {
    return new Promise((resolve, reject) => {
      navigator.geolocation.getCurrentPosition(
        pos => {
          resolve(pos);
        },
        err => {
          reject(err);
        },
        {
          enableHighAccuracy: false,
          timeout: 10000,
          maximumAge: 0,
          ...options,
        },
      );
    });
  }

  async watchPosition(
    options: PositionOptions,
    callback: WatchPositionCallback,
  ): Promise<CallbackID> {
    const id = navigator.geolocation.watchPosition(
      pos => {
        callback(pos);
      },
      err => {
        callback(null, err);
      },
      {
        enableHighAccuracy: false,
        timeout: 10000,
        maximumAge: 0,
        ...options,
      },
    );

    return `${id}`;
  }

  async clearWatch(options: { id: string }): Promise<void> {
    window.navigator.geolocation.clearWatch(parseInt(options.id, 10));
  }

  async checkPermissions(): Promise<PermissionStatus> {
    if (typeof navigator === 'undefined' || !navigator.permissions) {
      throw 'Permissions API not available in this browser';
    }

    const permission = await window.navigator.permissions.query({
      name: 'geolocation',
    });
    return {
      location: permission.state as PermissionType,
      coarseLocation: permission.state as PermissionType,
    };
  }
}

const NativeGeolocation = new NativeGeolocationWeb();

export { NativeGeolocation };

import { registerWebPlugin } from '@capacitor/core';
registerWebPlugin(NativeGeolocation);
