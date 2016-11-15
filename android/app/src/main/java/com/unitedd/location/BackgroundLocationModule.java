package com.unitedd.location;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.support.annotation.Nullable;
import android.view.View;
import com.facebook.react.bridge.*;
import com.facebook.react.common.MapBuilder;
import com.facebook.react.module.annotations.ReactModule;
import com.facebook.react.modules.core.DeviceEventManagerModule.RCTDeviceEventEmitter;
import com.facebook.react.modules.core.PermissionListener;
import com.google.android.gms.location.LocationRequest;
import com.unitedd.location.constant.ErrorType;
import com.unitedd.location.constant.EventType;

import java.util.Map;

@ReactModule(name = "BackgroundLocation")
public class BackgroundLocationModule extends ReactContextBaseJavaModule implements
  ActivityEventListener,
  LifecycleEventListener,
  PermissionListener,
  LocationAssistant.Listener {

  private @Nullable LocationAssistant mLocationAssistant;
  private @Nullable Promise mPromise;
  private boolean isHostPausedBySettings = false;
  private boolean isObservingLocation = false;
  private static final String TAG = "RCT_BACKGROUND_LOCATION";

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

    accuracyLevels.putInt("HIGH", LocationRequest.PRIORITY_HIGH_ACCURACY);
    accuracyLevels.putInt("MEDIUM", LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
    accuracyLevels.putInt("LOW", LocationRequest.PRIORITY_LOW_POWER);
    accuracyLevels.putInt("PASSIVE", LocationRequest.PRIORITY_NO_POWER);

    constants.put("AccuracyLevels", accuracyLevels);
    return constants;
  }

  @ReactMethod
  public void startObserving(ReadableMap options, final Promise promise) {
    mPromise = promise;
    LocationAssistant.Accuracy accuracy = LocationAssistant.Accuracy.MEDIUM;

    if (options.hasKey("accuracy")) switch (options.getInt("accuracy")) {
      case LocationRequest.PRIORITY_HIGH_ACCURACY:
        accuracy = LocationAssistant.Accuracy.HIGH; break;
      case LocationRequest.PRIORITY_LOW_POWER:
        accuracy = LocationAssistant.Accuracy.LOW; break;
      case LocationRequest.PRIORITY_NO_POWER:
        accuracy = LocationAssistant.Accuracy.PASSIVE; break;
      case LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY:
        accuracy = LocationAssistant.Accuracy.MEDIUM; break;
    }

    long updateInterval = options.hasKey("updateInterval")
      ? options.getInt("updateInterval") : 5000;
    boolean allowMockLocations = options.hasKey("allowMockLocations")
      ? options.getBoolean("allowMockLocations") : false;

    // If assistant already exist, reject promise
    mLocationAssistant = new LocationAssistant(getCurrentActivity(), this, accuracy, updateInterval, allowMockLocations);
    mLocationAssistant.setQuiet(true);
    mLocationAssistant.start();
  }

  @ReactMethod
  public void stopObserving() {
    isObservingLocation = false;
    if (mLocationAssistant != null)
      mLocationAssistant.stop();
  }

  @Override
  public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
    if (mLocationAssistant == null) return;
    mLocationAssistant.onActivityResult(requestCode, resultCode);

    if (requestCode == mLocationAssistant.REQUEST_CHECK_SETTINGS && resultCode != Activity.RESULT_OK) {
      String message = "Settings declined";

      if (mPromise != null) {
        mPromise.reject("SETTINGS_ERROR", message);
        mPromise = null;
      } else
        emitError(ErrorType.SETTINGS_ERROR, message);
    }
  }

  @Override
  public void onNewIntent(Intent intent) {}

  @Override
  public void onHostResume() {
    if (mLocationAssistant == null) return;
    if (isObservingLocation && !isHostPausedBySettings)
      mLocationAssistant.reset();
  }

  @Override
  public void onHostPause() {
    if (mLocationAssistant == null) return;
    if (mLocationAssistant.isChangingSettings())
      isHostPausedBySettings = true;
  }

  @Override
  public void onHostDestroy() {
    stopObserving();
    mLocationAssistant = null;
  }

  @Override
  public boolean onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
    if (mLocationAssistant == null) return false;
    boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;

    if (requestCode == mLocationAssistant.REQUEST_REQUEST_PERMISSION) {
      mLocationAssistant.onPermissionsUpdated(granted);

      if (!granted) {
        String message = "Permissions declined";

        if (mPromise != null) {
          mPromise.reject("PERMISSION_ERROR", message);
          mPromise = null;
        } else
          emitError(ErrorType.PERMISSION_ERROR, message);
      }
    }

    return false;
  }

  @Override
  public void onNeedLocationPermission() {
    if (mLocationAssistant != null)
      mLocationAssistant.requestLocationPermission();
  }

  @Override
  public void onExplainLocationPermission() {}

  @Override
  public void onNeedLocationSettingsChange() {
    if (mLocationAssistant != null)
      mLocationAssistant.changeLocationSettings();
  }

  @Override
  public void onFallBackToSystemSettings(View.OnClickListener fromView, DialogInterface.OnClickListener fromDialog) {}

  @Override
  public void onUpdatesRequested() {
    isObservingLocation = true;

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
