/* @flow */

'use strict';

import NativeEventEmitter from 'NativeEventEmitter'
import { BackgroundLocation } from 'NativeModules'
const BackgroundLocationEventEmitter = new NativeEventEmitter(BackgroundLocation)

var updatesEnabled = false;

type GeoOptions = {
  timeout: number,
  maximumAge: number,
  accuracy: number,
  distanceFilter: number,
}

var Geolocation = {
  watchPosition: function(success: Function, error?: Function, options?: GeoOptions): void {
   if (updatesEnabled) return

   BackgroundLocation.startWatching(options || {});
   //BackgroundLocationEventEmitter.addListener('backgroundLocationDidChange', success)
   //if (error) BackgroundLocationEventEmitter.addListener('backgroundLocationError', error)
   updatesEnabled = true;
  },

  stopWatching: function() {
   if (!updatesEnabled) return

   BackgroundLocation.stopWatching();
   //BackgroundLocationEventEmitter.removeListener('backgroundLocationDidChange')
   //BackgroundLocationEventEmitter.removeListener('backgroundLocationError')
   updatesEnabled = false;
 },
 AccuracyLevels: BackgroundLocation.AccuracyLevels
};

export default Geolocation;
