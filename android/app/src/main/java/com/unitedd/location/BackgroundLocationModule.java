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

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Arguments;
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
import com.unitedd.location.constant.DefaultOption;
import com.unitedd.location.constant.MessageType;
import com.unitedd.location.constant.PriorityLevel;
import com.unitedd.location.constant.RequestCode;

import java.util.Map;

@ReactModule(name = "BackgroundLocation")
public class BackgroundLocationModule extends ReactContextBaseJavaModule implements
  GoogleApiClient.ConnectionCallbacks,
  GoogleApiClient.OnConnectionFailedListener,
  ResultCallback<LocationSettingsResult>,
  ActivityEventListener {

  private static final float RCT_DEFAULT_LOCATION_ACCURACY = 100;

  private @Nullable LocationOptions mLocationOptions;
  private @Nullable GoogleApiClient mGoogleApiClient;
  private @Nullable ErrorReceiver mErrorReceiver;
  private @Nullable LocationReceiver mLocationReceiver;
  private @Nullable Promise mStartPromise;
  private @Nullable Intent mOptionsIntent;
  private @Nullable Intent mServiceIntent;

  public BackgroundLocationModule(ReactApplicationContext reactContext) {
    super(reactContext);
    reactContext.addActivityEventListener(this);
    // TODO: Move this is a promise
    mOptionsIntent = new Intent(MessageType.SETTINGS);
  }

  @Override
  public String getName() {
    return "BackgroundLocation";
  }

  @Override
  public Map<String, Object> getConstants() {
    final Map<String, Object> constants = MapBuilder.newHashMap();

    WritableMap priority = Arguments.createMap();
    priority.putInt("HIGH_ACCURACY", PriorityLevel.HIGH_ACCURACY);
    priority.putInt("BALANCED", PriorityLevel.BALANCED);
    priority.putInt("LOW_POWER", PriorityLevel.LOW_POWER);
    priority.putInt("NO_POWER", PriorityLevel.NO_POWER);

    constants.put("priority", priority);

    return constants;
  }

  @ReactMethod
  public void startObserving(ReadableMap options, final Promise promise) {
    if (mGoogleApiClient != null) return;
    Activity activity = getCurrentActivity();

    if (activity == null) {
      promise.reject("code", "Activity doesn't exist");
      return;
    }

    mStartPromise = promise;
    mLocationOptions = LocationOptions.fromReactMap(options);
    createGoogleApiClient(activity);
  }

  @ReactMethod
  public void stopObserving() {
    stopBackgroundService();
  }

  @Override
  public void onConnected(@Nullable Bundle bundle) {
    if (mLocationOptions == null) return;

    LocationRequest locationRequest = new LocationRequest()
      .setPriority(mLocationOptions.accuracy);

    LocationSettingsRequest settingsRequest = new LocationSettingsRequest.Builder()
      .setAlwaysShow(true)
      .addLocationRequest(locationRequest)
      .build();

    LocationServices.SettingsApi
      .checkLocationSettings(mGoogleApiClient, settingsRequest)
      .setResultCallback(this);
  }

  @Override
  public void onConnectionSuspended(int i) {
    if (mGoogleApiClient != null)
      mGoogleApiClient.connect();
  }

  @Override
  public void onConnectionFailed(@NonNull ConnectionResult result) {
    if (mStartPromise == null) return;
    mStartPromise.reject("code", "Connection to Google Play Services failed");
  }

  @Override
  public void onResult(@NonNull LocationSettingsResult result) {
    final Status status = result.getStatus();

    switch (status.getStatusCode()) {
      case LocationSettingsStatusCodes.SUCCESS:
        startBackgroundService();

        if (mStartPromise == null) return;
        mStartPromise.resolve(true);
        mStartPromise = null;

        break;
      case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
        try {
          status.startResolutionForResult(
            getCurrentActivity(),
            RequestCode.SETTINGS_API
          );
        }
        catch (SendIntentException e) {
          if (mStartPromise == null) return;
          mStartPromise.reject("code", e);
          mStartPromise = null;
        }

        break;
    }
  }

  @Override
  public void onNewIntent(Intent intent) { }

  @Override
  public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
    switch (requestCode) {
      case RequestCode.SETTINGS_API:
        if (resultCode == Activity.RESULT_OK) {
          startBackgroundService();

          if (mStartPromise == null) return;
          mStartPromise.resolve(true);
          mStartPromise = null;
        } else {
          destroyGoogleApiClient();

          if (mStartPromise == null) return;
          mStartPromise.reject("code", "User rejected GPS");
          mStartPromise = null;
        }
    }
  }

  private void createGoogleApiClient(Activity currentActivity) {
    mGoogleApiClient = new GoogleApiClient
      .Builder(currentActivity)
      .addApi(LocationServices.API)
      .addConnectionCallbacks(this)
      .addOnConnectionFailedListener(this)
      .build();

    mGoogleApiClient.connect();
  }

  private void destroyGoogleApiClient() {
    if (mGoogleApiClient == null) return;

    mGoogleApiClient.unregisterConnectionCallbacks(this);
    mGoogleApiClient.unregisterConnectionFailedListener(this);
    mGoogleApiClient.disconnect();
    mGoogleApiClient = null;
  }

  private void startBackgroundService() {
    ReactApplicationContext context = getReactApplicationContext();

    mLocationReceiver = new LocationReceiver();
    mErrorReceiver = new ErrorReceiver();
    mServiceIntent = new Intent(context, BackgroundLocationService.class);

    if (mLocationOptions != null) {
      // Pass here all the options used for the service starting
      Bundle options = new Bundle();
      options.putInt("accuracy", mLocationOptions.accuracy);
      mServiceIntent.putExtras(options);
    }

    context.registerReceiver(mLocationReceiver, new IntentFilter(MessageType.LOCATION));
    context.registerReceiver(mErrorReceiver, new IntentFilter(MessageType.ERROR));
    context.startService(mServiceIntent);
  }

  private void stopBackgroundService() {
    ReactApplicationContext context = getReactApplicationContext();

    context.unregisterReceiver(mLocationReceiver);
    context.unregisterReceiver(mErrorReceiver);
    context.stopService(mServiceIntent);

    mLocationReceiver = null;
    mErrorReceiver = null;
    mServiceIntent = null;
  }

  private class ErrorReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
      Bundle error = intent.getExtras();
      emitError(error.getInt("code"), error.getString("message"));
    }
  }

  private void emitError(int code, @Nullable String message) {
    WritableMap map = Arguments.createMap();
    map.putInt("code", code);
    if (message != null) map.putString("message", message);

    getReactApplicationContext()
      .getJSModule(RCTDeviceEventEmitter.class)
      .emit(MessageType.ERROR, map);
  }

  private class LocationReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
      long now = System.currentTimeMillis();
      Bundle point = intent.getExtras();

      WritableMap coords = Arguments.createMap();
      WritableMap map = Arguments.createMap();

      coords.putDouble("latitude", point.getDouble("latitude", 0));
      coords.putDouble("longitude", point.getDouble("longitude", 0));
      coords.putDouble("altitude", point.getDouble("altitude", 0));
      coords.putDouble("accuracy", point.getDouble("accuracy", 0));
      coords.putDouble("speed", point.getDouble("speed", 0));
      coords.putDouble("heading", point.getDouble("heading", 0));

      map.putMap("coords", coords);
      map.putBoolean("mocked", point.getBoolean("mocked", false));
      map.putDouble("timestamp", point.getDouble("timestamp", now));

      getReactApplicationContext()
        .getJSModule(RCTDeviceEventEmitter.class)
        .emit(MessageType.LOCATION, map);
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
        : DefaultOption.accuracy;

      float distanceFilter = map.hasKey("distanceFilter")
        ? (float) map.getDouble("distanceFilter")
        : RCT_DEFAULT_LOCATION_ACCURACY;

      return new LocationOptions(timeout, maximumAge, accuracy, distanceFilter);
    }
  }

}
