package com.unitedd.location;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.location.Location;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.View;

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
import com.unitedd.location.constant.AccuracyLevel;
import com.unitedd.location.constant.EventType;

import java.util.Map;

@ReactModule(name = "BackgroundLocation")
public class BackgroundLocationModule extends ReactContextBaseJavaModule implements
  ActivityEventListener,
  LifecycleEventListener,
  LocationAssistant.Listener {

  private @Nullable Promise mPromise;
  private @Nullable LocationAssistant mAssistant;
  private boolean hasBeenPaused = false;
  private static final String TAG = "RCT_BACKGROUND_LOCATION";
  private static final int REQUEST_CHECK_SETTINGS = 0;

  public BackgroundLocationModule(ReactApplicationContext reactContext) {
    super(reactContext);
    reactContext.addActivityEventListener(this);
    reactContext.addLifecycleEventListener(this);
  }

  @Override
  public String getName() {
    return "BackgroundLocation";
  }

  @Override
  public Map<String, Object> getConstants() {
    final Map<String, Object> constants = MapBuilder.newHashMap();
    WritableMap accuracyLevels = Arguments.createMap();

    accuracyLevels.putInt("HIGH", AccuracyLevel.HIGH);
    accuracyLevels.putInt("MEDIUM", AccuracyLevel.MEDIUM);
    accuracyLevels.putInt("LOW", AccuracyLevel.LOW);
    accuracyLevels.putInt("PASSIVE", AccuracyLevel.PASSIVE);

    constants.put("AccuracyLevels", accuracyLevels);
    return constants;
  }

  @ReactMethod
  public void startObserving(ReadableMap options, final Promise promise) {
    mPromise = promise;

    // Default Options
    LocationAssistant.Accuracy accuracy = LocationAssistant.Accuracy.MEDIUM;

    if (options.hasKey("accuracy")) {
      Log.e(TAG, "it has accuracy level " + options.getInt("accuracy"));

      switch (options.getInt("accuracy")) {
        case AccuracyLevel.HIGH: accuracy = LocationAssistant.Accuracy.HIGH; break;
        case AccuracyLevel.LOW: accuracy = LocationAssistant.Accuracy.LOW; break;
        case AccuracyLevel.PASSIVE: accuracy = LocationAssistant.Accuracy.PASSIVE; break;
      }
    }

    long updateInterval = options.hasKey("updateInterval")
      ? options.getInt("updateInterval") : 5000;
    boolean allowMockLocations = options.hasKey("allowMockLocations")
      ? options.getBoolean("allowMockLocations") : false;

    // If assitant already exist, reject promise
    mAssistant = new LocationAssistant(getCurrentActivity(), this, accuracy, updateInterval, allowMockLocations);
    mAssistant.start();
  }

  public void checkSettings() {
    if (mAssistant != null)
      mAssistant.reset();
  }

  @ReactMethod
  public void stopObserving() {
    if (mAssistant != null)
      mAssistant.stop();
  }

  @Override
  public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
    if (mAssistant != null) {
      mAssistant.onActivityResult(requestCode, resultCode);
    }

    switch (requestCode) {
      case REQUEST_CHECK_SETTINGS:
        if (resultCode != Activity.RESULT_OK) {
          Log.e(TAG, "Position declined");
        }
    }
  }

  @Override
  public void onNewIntent(Intent intent) {}

  @Override
  public void onHostResume() {
    Log.e(TAG, "onHostResume");

    if (mAssistant != null && !hasBeenPaused)
      mAssistant.reset();
    hasBeenPaused = false;
  }

  @Override
  public void onHostPause() {
    Log.e(TAG, "onHostPause");

    if (mAssistant != null && mAssistant.isSettingsDialogOn())
      hasBeenPaused = true;
  }

  @Override
  public void onHostDestroy() {
    stopObserving();
    mAssistant = null;
  }

  @Override
  public void onNeedLocationPermission() {
    if (mAssistant != null)
      mAssistant.requestLocationPermission();
  }

  @Override
  public void onExplainLocationPermission() {
    Log.e(TAG, "onExplainLocationChange");
  }

  @Override
  public void onNeedLocationSettingsChange() {
    if (mAssistant != null)
      mAssistant.changeLocationSettings();
  }

  @Override
  public void onFallBackToSystemSettings(View.OnClickListener fromView, DialogInterface.OnClickListener fromDialog) {
    Log.e(TAG, "onFallBackToSystemSettings");
  }

  @Override
  public void onNewLocationAvailable(Location location) {
    WritableMap map = Arguments.createMap();
    map.putDouble("latitude", location.getLatitude());
    map.putDouble("longitude", location.getLongitude());
    map.putDouble("altitude", location.getAltitude());
    map.putDouble("accuracy", location.getAccuracy());
    map.putDouble("speed", location.getSpeed());
    map.putDouble("heading", location.getBearing());
    map.putDouble("timestamp", location.getTime());

    getReactApplicationContext()
      .getJSModule(RCTDeviceEventEmitter.class)
      .emit(EventType.LOCATION, map);
  }

  @Override
  public void onMockLocationsDetected(View.OnClickListener fromView, DialogInterface.OnClickListener fromDialog) {
    Log.e(TAG, "onMockLocationsDetected");
  }

  @Override
  public void onError(LocationAssistant.ErrorType type, String message) {
    int code = type == LocationAssistant.ErrorType.RETRIEVAL ? 0 : 1;

    WritableMap map = Arguments.createMap();
    map.putInt("code", code);
    map.putString("message", message);

    getReactApplicationContext()
      .getJSModule(RCTDeviceEventEmitter.class)
      .emit(EventType.ERROR, map);
  }
}
