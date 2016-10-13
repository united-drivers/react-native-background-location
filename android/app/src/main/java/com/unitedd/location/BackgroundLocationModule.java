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
import com.unitedd.location.constant.Application;
import com.unitedd.location.constant.EventType;
import com.unitedd.location.constant.PriorityLevel;

import java.util.Map;

@ReactModule(name = "BackgroundLocation")
public class BackgroundLocationModule extends ReactContextBaseJavaModule implements
  ActivityEventListener,
  LifecycleEventListener,
  LocationAssistant.Listener {

  private @Nullable Promise mPromise;
  private @Nullable LocationAssistant mAssistant;
  private final int REQUEST_CHECK_SETTINGS = 0;

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
    WritableMap priorityLevels = Arguments.createMap();

    priorityLevels.putInt("HIGH_ACCURACY", PriorityLevel.HIGH_ACCURACY);
    priorityLevels.putInt("BALANCED", PriorityLevel.BALANCED);
    priorityLevels.putInt("LOW_POWER", PriorityLevel.LOW_POWER);
    priorityLevels.putInt("NO_POWER", PriorityLevel.NO_POWER);

    constants.put("PriorityLevels", priorityLevels);
    return constants;
  }

  @ReactMethod
  public void startObserving(ReadableMap options, final Promise promise) {
    mPromise = promise;
    // accuracy & interval

    // If assitant already exist, reject promise
    LocationAssistant.Accuracy accuracy = LocationAssistant.Accuracy.HIGH;
    mAssistant = new LocationAssistant(getCurrentActivity(), this, accuracy, 1000, false);
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
          Log.e(Application.TAG, "Position declined");
        }
    }
  }

  @Override
  public void onNewIntent(Intent intent) {}

  @Override
  public void onHostResume() {}

  @Override
  public void onHostPause() {}

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
    Log.e(Application.TAG, "onExplainLocationChange");
  }

  @Override
  public void onNeedLocationSettingsChange() {
    if (mAssistant != null)
      mAssistant.changeLocationSettings();
  }

  @Override
  public void onFallBackToSystemSettings(View.OnClickListener fromView, DialogInterface.OnClickListener fromDialog) {
    Log.e(Application.TAG, "onFallBackToSystemSettings");
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
    Log.e(Application.TAG, "onMockLocationsDetected");
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
