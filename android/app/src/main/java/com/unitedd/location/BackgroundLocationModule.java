package com.unitedd.location;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender.SendIntentException;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.common.MapBuilder;
import com.facebook.react.module.annotations.ReactModule;
import com.facebook.react.modules.core.DeviceEventManagerModule.RCTDeviceEventEmitter;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.unitedd.location.constant.Application;
import com.unitedd.location.constant.EventType;
import com.unitedd.location.constant.MessageType;
import com.unitedd.location.constant.RequestCode;

import java.util.Map;

@ReactModule(name = "BackgroundLocation")
public class BackgroundLocationModule extends ReactContextBaseJavaModule implements
  ActivityEventListener/*,
  LifecycleEventListener*/ {

  // PUT THAT IN CONSTANTS FILE(S)
  private static final int HIGH_ACCURACY = LocationRequest.PRIORITY_HIGH_ACCURACY;
  private static final int BALANCED = LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY;
  private static final int LOW_POWER = LocationRequest.PRIORITY_LOW_POWER;
  private static final int NO_POWER = LocationRequest.PRIORITY_NO_POWER;

  private static final float RCT_DEFAULT_LOCATION_ACCURACY = 100;

  private @Nullable LocationOptions mLocationOptions;
  private @Nullable GoogleApiClient mGoogleApiClient;
  private @Nullable ErrorReceiver mErrorReceiver;
  private @Nullable LocationReceiver mLocationReceiver;
  private @Nullable Promise mStartPromise;
  private @Nullable Intent mServiceIntent;

  private @Nullable GoogleApiClient.ConnectionCallbacks mConnectionCallbacks;
  private @Nullable GoogleApiClient.OnConnectionFailedListener mFailedListener;
  private @Nullable ResultCallback<LocationSettingsResult> mSettingsResult;

  // FINISHED
  public BackgroundLocationModule(ReactApplicationContext reactContext) {
    super(reactContext);
    reactContext.addActivityEventListener(this);
    //reactContext.addLifecycleEventListener(this);
  }

  @Override
  public String getName() {
    return "BackgroundLocation";
  }

  /*@Override
  public Map<String, Object> getConstants() {
    final Map<String, Object> constants = MapBuilder.newHashMap();

    constants.put("HIGH_ACCURACY", HIGH_ACCURACY);
    constants.put("BALANCED", BALANCED);
    constants.put("LOW_POWER", LOW_POWER);
    constants.put("NO_POWER", NO_POWER);

    return constants;
  }*/

  @Override
  public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
    Log.e(Application.TAG, "" + resultCode);
  }

  @Override
  public void onNewIntent(Intent intent) {
    Log.e(Application.TAG, "onNewIntent");
  }

  /*@Override
  public void onHostResume() {
    Log.e(Application.TAG, "onHostResume");
  }

  @Override
  public void onHostPause() {
    Log.e(Application.TAG, "onHostPause");
  }

  @Override
  public void onHostDestroy() {
    Log.e(Application.TAG, "onHostDestroy");
  }*/

  @ReactMethod
  public void startObserving(final ReadableMap options, final Promise promise) {
    if (mGoogleApiClient != null) return;

    mStartPromise = promise;
    ReactApplicationContext context = getReactApplicationContext();
    Activity currentActivity = getCurrentActivity();

    if (currentActivity == null) {
      promise.reject("code", "Activity doesn't exist");
      return;
    }

    mSettingsResult = new ResultCallback<LocationSettingsResult>() {
      @Override
      public void onResult(@NonNull LocationSettingsResult result) {
        final Status status = result.getStatus();

        switch (status.getStatusCode()) {
          case LocationSettingsStatusCodes.SUCCESS:
            startBackgroundService();
            promise.resolve(null);

            break;
          case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
            try { status.startResolutionForResult(getCurrentActivity(), RequestCode.SETTINGS_API); }
            catch (SendIntentException e) {
              promise.reject("code", "Can't open Google Play Services window");
            }

            break;
        }
      }
    };

    mConnectionCallbacks = new GoogleApiClient.ConnectionCallbacks() {
      @Override
      public void onConnected(@Nullable Bundle bundle) {
        mLocationOptions = LocationOptions.fromReactMap(options);
        LocationRequest locationRequest = LocationRequest.create();

        locationRequest.setPriority(mLocationOptions.accuracy);
        locationRequest.setPriority(mLocationOptions.accuracy);
        locationRequest.setPriority(mLocationOptions.accuracy);

        LocationSettingsRequest settingsRequest = new LocationSettingsRequest
          .Builder()
          .addLocationRequest(locationRequest)
          .setAlwaysShow(true)
          .build();

        LocationServices.SettingsApi
          .checkLocationSettings(mGoogleApiClient, settingsRequest)
          .setResultCallback(mSettingsResult);
      }

      @Override
      public void onConnectionSuspended(int i) {
        if (mGoogleApiClient != null)
          mGoogleApiClient.connect();
      }
    };

    mFailedListener = new GoogleApiClient.OnConnectionFailedListener() {
      @Override
      public void onConnectionFailed(@NonNull ConnectionResult result) {
        promise.reject("code", "Connection to Google Play Services failed");
      }
    };

    createGoogleApiClient(currentActivity);
  }

  @ReactMethod
  public void stopObserving() {
    stopBackgroundService();
  }

  private void createGoogleApiClient(Activity activity) {
    if (mConnectionCallbacks != null || mFailedListener != null)
      return;

    mGoogleApiClient = new GoogleApiClient
      .Builder(activity)
      .addApi(LocationServices.API)
      .addConnectionCallbacks(mConnectionCallbacks)
      .addOnConnectionFailedListener(mFailedListener)
      .build();

    mGoogleApiClient.connect();
  }

  private void destroyGoogleApiClient() {
    if (
      mGoogleApiClient == null ||
      mConnectionCallbacks == null ||
      mFailedListener == null
    ) return;

    mGoogleApiClient.unregisterConnectionCallbacks(mConnectionCallbacks);
    mGoogleApiClient.unregisterConnectionFailedListener(mFailedListener);
    mGoogleApiClient.disconnect();

    mGoogleApiClient = null;
  }

  private void startBackgroundService() {
    ReactApplicationContext context = getReactApplicationContext();

    // USE MLOCATIONOPTIONS HERE

    mLocationReceiver = new LocationReceiver();
    mErrorReceiver = new ErrorReceiver();
    context.registerReceiver(mLocationReceiver, new IntentFilter(MessageType.LOCATION));
    context.registerReceiver(mErrorReceiver, new IntentFilter(MessageType.ERROR));

    mServiceIntent = new Intent(context, BackgroundLocationService.class);
    context.startService(mServiceIntent);
  }

  private void stopBackgroundService() {
    ReactApplicationContext context = getReactApplicationContext();

    if (mLocationReceiver != null) {
      context.unregisterReceiver(mLocationReceiver);
      mLocationReceiver = null;
    }

    if (mErrorReceiver != null) {
      context.unregisterReceiver(mErrorReceiver);
      mErrorReceiver = null;
    }

    if (mServiceIntent != null) {
      context.stopService(mServiceIntent);
      mServiceIntent = null;
    }
  }


  private class ErrorReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
      WritableMap map = Arguments.createMap();

      map.putInt("code", intent.getIntExtra("code", 0));
      String message = intent.getStringExtra("message");
      if (message != null) map.putString("message", message);

      getReactApplicationContext()
        .getJSModule(RCTDeviceEventEmitter.class)
        .emit(EventType.ERROR, map);
    }
  }

  private class LocationReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
      long now = System.currentTimeMillis();
      WritableMap coords = Arguments.createMap();
      WritableMap map = Arguments.createMap();

      coords.putDouble("latitude", intent.getDoubleExtra("latitude", 0));
      coords.putDouble("longitude", intent.getDoubleExtra("longitude", 0));
      coords.putDouble("altitude", intent.getDoubleExtra("altitude", 0));
      coords.putDouble("accuracy", intent.getDoubleExtra("accuracy", 0));
      coords.putDouble("speed", intent.getDoubleExtra("speed", 0));
      coords.putDouble("heading", intent.getDoubleExtra("heading", 0));

      map.putMap("coords", coords);
      map.putBoolean("mocked", intent.getBooleanExtra("mocked", false));
      map.putDouble("timestamp", intent.getDoubleExtra("timestamp", now));

      getReactApplicationContext()
        .getJSModule(RCTDeviceEventEmitter.class)
        .emit(EventType.LOCATION, map);
    }
  }

  private static class LocationOptions {
    private final long timeout;
    private final double maximumAge;
    private final int accuracy;
    private final float distanceFilter;

    private LocationOptions(
      long timeout,
      double maximumAge,
      int accuracy,
      float distanceFilter
    ) {
      this.timeout = timeout;
      this.maximumAge = maximumAge;
      this.accuracy = accuracy;
      this.distanceFilter = distanceFilter;
    }

    private static LocationOptions fromReactMap(ReadableMap map) {
      // precision might be dropped on timeout (double -> int conversion), but that's OK
      long timeout = map.hasKey("timeout")
        ? (long) map.getDouble("timeout")
        : Long.MAX_VALUE;

      double maximumAge = map.hasKey("maximumAge")
        ? map.getDouble("maximumAge")
        : Double.POSITIVE_INFINITY;

      int accuracy = map.hasKey("accuracy")
        ? map.getInt("accuracy")
        : BALANCED;

      float distanceFilter = map.hasKey("distanceFilter")
        ? (float) map.getDouble("distanceFilter")
        : RCT_DEFAULT_LOCATION_ACCURACY;

      return new LocationOptions(timeout, maximumAge, accuracy, distanceFilter);
    }
  }

}
