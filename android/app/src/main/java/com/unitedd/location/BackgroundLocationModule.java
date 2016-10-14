package com.unitedd.location;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.support.annotation.Nullable;
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
import com.facebook.react.modules.core.PermissionListener;
import com.unitedd.location.constant.AccuracyLevel;
import com.unitedd.location.constant.ErrorType;
import com.unitedd.location.constant.EventType;

import java.util.Map;

@ReactModule(name = "BackgroundLocation")
public class BackgroundLocationModule extends ReactContextBaseJavaModule implements
  ActivityEventListener,
  LifecycleEventListener,
  PermissionListener,
  LocationAssistant.Listener {

  private @Nullable Promise mPromise;
  private @Nullable LocationAssistant mAssistant;
  private boolean hasBeenPaused = false;
  private boolean isObserving = false;
  private static final String TAG = "RCT_BACKGROUND_LOCATION";
  private static final int REQUEST_CHECK_SETTINGS = 0;
  private static final int REQUEST_REQUEST_PERMISSION = 1;

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
    mAssistant.setQuiet(true);
    mAssistant.start();
  }

  @ReactMethod
  public void checkSettings() {
    if (mAssistant != null)
      mAssistant.reset();
  }

  @ReactMethod
  public void stopObserving() {
    isObserving = false;
    if (mAssistant != null)
      mAssistant.stop();
  }

  @Override
  public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
    if (mAssistant != null)
      mAssistant.onActivityResult(requestCode, resultCode);

    if (requestCode == REQUEST_CHECK_SETTINGS && resultCode != Activity.RESULT_OK) {
      String message = "Settings declined";

      if (mPromise != null) {
        mPromise.reject("SETTINGS_ERROR", message);
        mPromise = null;
      } else {
        emitError(ErrorType.SETTINGS_ERROR, message);
      }
    }
  }

  @Override
  public void onNewIntent(Intent intent) {}

  @Override
  public void onHostResume() {
    if (mAssistant != null && !hasBeenPaused && isObserving)
      mAssistant.reset();
    hasBeenPaused = false;
  }

  @Override
  public void onHostPause() {
    if (mAssistant != null && mAssistant.isSettingsDialogOn())
      hasBeenPaused = true;
  }

  @Override
  public void onHostDestroy() {
    stopObserving();
    mAssistant = null;
  }

  @Override
  public boolean onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
    boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;

    if (mAssistant != null && requestCode == REQUEST_REQUEST_PERMISSION) {
      mAssistant.onPermissionsUpdated(granted);

      if (!granted) {
        String message = "Permissions declined";

        if (mPromise != null) {
          mPromise.reject("PERMISSION_ERROR", message);
          mPromise = null;
        } else {
          emitError(ErrorType.PERMISSION_ERROR, message);
        }
      }
    }

    return false;
  }

  @Override
  public void onNeedLocationPermission() {
    if (mAssistant != null)
      mAssistant.requestLocationPermission();
  }

  @Override
  public void onExplainLocationPermission() {}

  @Override
  public void onNeedLocationSettingsChange() {
    if (mAssistant != null)
      mAssistant.changeLocationSettings();
  }

  @Override
  public void onFallBackToSystemSettings(View.OnClickListener fromView, DialogInterface.OnClickListener fromDialog) {}

  @Override
  public void onUpdatesRequested() {
    isObserving = true;

    if (mPromise != null) {
      mPromise.resolve(null);
      mPromise = null;
    }
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
  public void onMockLocationsDetected(View.OnClickListener fromView, DialogInterface.OnClickListener fromDialog) {}

  @Override
  public void onError(LocationAssistant.ErrorType type, String message) {
    if (mPromise != null) {
      mPromise.reject("INIT_ERROR", message);
      mPromise = null;
    } else {
      int code = type == LocationAssistant.ErrorType.RETRIEVAL
        ? ErrorType.SETTINGS_ERROR
        : ErrorType.RETRIEVAL_ERROR;

      emitError(code, message);
    }
  }

  private void emitError(int code, String message) {
    WritableMap map = Arguments.createMap();
    map.putInt("code", code);
    map.putString("message", message);

    getReactApplicationContext()
      .getJSModule(RCTDeviceEventEmitter.class)
      .emit(EventType.ERROR, map);
  }

}
