package com.unitedd.location;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender.SendIntentException;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;

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
import com.facebook.react.modules.core.PermissionAwareActivity;
import com.facebook.react.modules.core.PermissionListener;
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
import com.unitedd.location.constant.ErrorType;
import com.unitedd.location.constant.EventType;
import com.unitedd.location.constant.MessageType;
import com.unitedd.location.constant.PriorityLevel;
import com.unitedd.location.constant.RequestCode;

import java.util.Map;

@ReactModule(name = "BackgroundLocation")
public class BackgroundLocationModule extends ReactContextBaseJavaModule implements
  GoogleApiClient.ConnectionCallbacks,
  GoogleApiClient.OnConnectionFailedListener,
  ResultCallback<LocationSettingsResult>,
  ActivityEventListener,
  LifecycleEventListener,
  PermissionListener {

  private static final float RCT_DEFAULT_LOCATION_ACCURACY = 100;

  private @Nullable GoogleApiClient mGoogleApiClient;
  private @Nullable LocationOptions mLocationOptions;
  private @Nullable BroadcastReceiver mMessageReceiver;
  private @Nullable Promise mPromise;
  private @Nullable Intent mService;

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
    // TODO: if service is already started, just check the settings && pass the options
    mLocationOptions = LocationOptions.fromReactMap(options);
    mPromise = promise;

    if (!checkPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
      requestPermission(Manifest.permission.ACCESS_FINE_LOCATION);
    } else {
      connectGoogleApiClient();
    }
  }

  @ReactMethod
  public void stopObserving() {
    disconnectGoogleApiClient();
    disconnectMessageReceiver();
    stopBackgroundService();
  }

  @Override
  public void onConnected(@Nullable Bundle bundle) {
    if (mLocationOptions == null)
      mLocationOptions = LocationOptions.fromReactMap(Arguments.createMap());
    if (mLocationOptions == null) return; // Useless, to avoid IDE screaming

    LocationSettingsRequest settingsRequest = new LocationSettingsRequest.Builder()
      .setAlwaysShow(true)
      .addLocationRequest(LocationRequest.create().setPriority(mLocationOptions.accuracy))
      .build();

    LocationServices.SettingsApi
      .checkLocationSettings(mGoogleApiClient, settingsRequest)
      .setResultCallback(this);
  }

  @Override
  public void onConnectionSuspended(int i) {
    connectGoogleApiClient();
  }

  @Override
  public void onConnectionFailed(@NonNull ConnectionResult result) {
    if (mPromise != null)
      mPromise.reject("code", "Connection to Google Play Services failed");
  }

  @Override
  public void onResult(@NonNull LocationSettingsResult result) {
    final Status status = result.getStatus();

    switch (status.getStatusCode()) {
      case LocationSettingsStatusCodes.SUCCESS:
        connectMessageReceiver();
        startBackgroundService();

        if (mPromise != null) {
          mPromise.resolve(null);
          mPromise = null;
        }

        break;
      case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
        try {
          status.startResolutionForResult(getCurrentActivity(), RequestCode.SETTINGS_API);
        }
        catch (SendIntentException e) {
          if (mPromise != null) {
            mPromise.reject("code", e);
            mPromise = null;
          }
        }

        break;
    }
  }

  @Override
  public void onNewIntent(Intent intent) { }

  @Override
  public void onHostResume() {
    if (mGoogleApiClient != null) return;

    if (!checkPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
      requestPermission(Manifest.permission.ACCESS_FINE_LOCATION);
    } else {
      connectGoogleApiClient();
    }
  }

  @Override
  public void onHostPause() { }

  @Override
  public void onHostDestroy() {
    stopObserving();
  }

  @Override
  public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
    switch (requestCode) {
      case RequestCode.SETTINGS_API:
        if (resultCode == Activity.RESULT_OK) {
          startBackgroundService();

          if (mPromise != null) {
            mPromise.resolve(true);
            mPromise = null;
          }
        } else {
          disconnectGoogleApiClient();

          if (mPromise != null) {
            mPromise.reject("code", "User rejected GPS");
            mPromise = null;
          }
        }
    }
  }

  private void connectGoogleApiClient() {
    if (mGoogleApiClient == null) {
      mGoogleApiClient = new GoogleApiClient
        .Builder(getReactApplicationContext().getBaseContext())
        .addApi(LocationServices.API)
        .addConnectionCallbacks(this)
        .addOnConnectionFailedListener(this)
        .build();
    }

    if (!mGoogleApiClient.isConnected() && !mGoogleApiClient.isConnecting())
      mGoogleApiClient.connect();
  }

  private void disconnectGoogleApiClient() {
    if (mGoogleApiClient == null) return;

    mGoogleApiClient.unregisterConnectionCallbacks(this);
    mGoogleApiClient.unregisterConnectionFailedListener(this);
    mGoogleApiClient.disconnect();
    mGoogleApiClient = null;
  }

  private void startBackgroundService() {
    Context context = getReactApplicationContext().getBaseContext();
    mService = new Intent(context, BackgroundLocationService.class);

    if (mLocationOptions != null) {
      // Pass here all the options used for the service starting
      Bundle options = new Bundle();
      options.putInt("accuracy", mLocationOptions.accuracy);
      mService.putExtras(options);
    }

    context.startService(mService);
  }

  private void stopBackgroundService() {
    if (mService == null) return;

    getReactApplicationContext()
      .getBaseContext()
      .stopService(mService);

    mService = null;
  }

  private void connectMessageReceiver() {
    if (mMessageReceiver != null) return;

    mMessageReceiver = new BroadcastReceiver() {
      public @Override void onReceive(Context context, Intent intent) {
        Bundle content = intent.getExtras();

        switch (intent.getAction()) {
          case MessageType.LOCATION:
            emitLocation(content); break;
          case MessageType.ERROR:
            emitError(content); break;
        }
      }
    };

    IntentFilter filter = new IntentFilter();
    filter.addAction(MessageType.LOCATION);
    filter.addAction(MessageType.ERROR);

    LocalBroadcastManager
      .getInstance(getReactApplicationContext().getBaseContext())
      .registerReceiver(mMessageReceiver, filter);
  }

  private void disconnectMessageReceiver() {
    if (mMessageReceiver == null) return;

    LocalBroadcastManager
      .getInstance(getReactApplicationContext().getBaseContext())
      .unregisterReceiver(mMessageReceiver);

    mMessageReceiver = null;
  }

  private void sendMessage(String action, Bundle content) {
    LocalBroadcastManager
      .getInstance(getReactApplicationContext().getBaseContext())
      .sendBroadcast(new Intent(action).putExtras(content));
  }

  private void emitLocation(Bundle location) {
    long now = System.currentTimeMillis();
    WritableMap coords = Arguments.createMap();
    WritableMap map = Arguments.createMap();

    coords.putDouble("latitude", location.getDouble("latitude"));
    coords.putDouble("longitude", location.getDouble("longitude"));
    coords.putDouble("altitude", location.getDouble("altitude"));
    coords.putDouble("accuracy", location.getDouble("accuracy"));
    coords.putDouble("speed", location.getDouble("speed"));
    coords.putDouble("heading", location.getDouble("heading"));

    map.putMap("coords", coords);
    map.putBoolean("mocked", location.getBoolean("mocked"));
    map.putDouble("timestamp", location.getDouble("timestamp", now));

    getReactApplicationContext()
      .getJSModule(RCTDeviceEventEmitter.class)
      .emit(EventType.LOCATION, map);
  }

  private void emitError(Bundle error) {
    WritableMap map = Arguments.createMap();

    map.putInt("code", error.getInt("code"));
    String message = error.getString("message");

    if (message != null)
      map.putString("message", message);

    getReactApplicationContext()
      .getJSModule(RCTDeviceEventEmitter.class)
      .emit(EventType.ERROR, map);
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


  private boolean checkPermission(String permission) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;

    return getReactApplicationContext()
      .getBaseContext()
      .checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
  }

  private void requestPermission(final String permission) {
    if (checkPermission(permission)) return;

    PermissionAwareActivity activity = getPermissionAwareActivity();
    activity.requestPermissions(new String[] { permission }, RequestCode.RUNTIME_PERMISSION, this);
  }

  @Override
  public boolean onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
    boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;

    if (requestCode == RequestCode.RUNTIME_PERMISSION && granted) {
      connectGoogleApiClient();
    } else if (mPromise != null) {
      mPromise.reject("ERROR_" + ErrorType.PERMISSION_DENIED, "request code blabla");
      mPromise = null;
    }

    return false;
  }

  private PermissionAwareActivity getPermissionAwareActivity() {
    Activity activity = getCurrentActivity();

    if (activity == null) {
      throw new IllegalStateException("Tried to use permissions API while not attached to an Activity.");
    } else if (!(activity instanceof PermissionAwareActivity)) {
      throw new IllegalStateException("Tried to use permissions API but the host Activity doesn't implement PermissionAwareActivity.");
    }

    return (PermissionAwareActivity) activity;
  }

}
