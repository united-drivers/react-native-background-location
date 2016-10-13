package com.unitedd.location;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.unitedd.location.constant.DefaultOption;
import com.unitedd.location.constant.ErrorType;
import com.unitedd.location.constant.MessageType;

public class BackgroundLocationService extends Service implements
  LocationListener,
  GoogleApiClient.ConnectionCallbacks,
  GoogleApiClient.OnConnectionFailedListener,
  ResultCallback<LocationSettingsResult> {

  private @Nullable GoogleApiClient mGoogleApiClient;
  private @Nullable LocationRequest mLocationRequest;
  private @Nullable BroadcastReceiver mMessageReceiver;

  @Nullable
  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    setOptions(intent.getExtras());
    return START_STICKY;
  }

  @Override
  public void onCreate() {
    createGoogleApiClient();
    createMessageReceiver();
  }

  @Override
  public void onDestroy() {
    stopLocationUpdates();
    destroyGoogleApiClient();
    destroyMessageReceiver();
  }

  @Override
  public void onConnected(@Nullable Bundle bundle) {
    if (mLocationRequest == null) return;

    LocationSettingsRequest settingsRequest = new LocationSettingsRequest.Builder()
      .addLocationRequest(mLocationRequest)
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
    sendError(
      ErrorType.PLAY_CONNECTION_FAILED,
      "Service connection to Google Play Services failed"
    );
  }

  @Override
  public void onResult(@NonNull LocationSettingsResult result) {
    final Status status = result.getStatus();

    switch (status.getStatusCode()) {
      case LocationSettingsStatusCodes.SUCCESS:
        startLocationUpdates();
        break;
      case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
        sendError(
          ErrorType.PLAY_RESOLUTION_REQUIRED,
          "Service requires a resolution for Settings API"
        );
        break;
    }
  }

  @Override
  public void onLocationChanged(Location location) {
    Bundle bundle = new Bundle();

    bundle.putDouble("latitude", location.getLatitude());
    bundle.putDouble("longitude", location.getLongitude());
    bundle.putDouble("altitude", location.getAltitude());
    bundle.putDouble("accuracy", location.getAccuracy());
    bundle.putDouble("heading", location.getBearing());
    bundle.putDouble("speed", location.getSpeed());
    bundle.putDouble("timestamp", location.getTime());

    if (android.os.Build.VERSION.SDK_INT >= 18) {
      bundle.putBoolean("mocked", location.isFromMockProvider());
    } else {
      boolean isMocked = isMockSettingsOn(getApplicationContext());
      bundle.putBoolean("mocked", isMocked);
    }

    sendMessage(MessageType.LOCATION, bundle);
  }

  private void setOptions(Bundle options) {
    if (mLocationRequest == null)
      mLocationRequest = new LocationRequest();

    int accuracy = options.getInt("accuracy", DefaultOption.accuracy);
    long fastestInterval = options.getLong("fastestInterval", DefaultOption.fastestInterval);
    long interval = options.getLong("interval", DefaultOption.interval);

    mLocationRequest.setPriority(accuracy);
    mLocationRequest.setFastestInterval(fastestInterval);
    mLocationRequest.setInterval(interval);
  }

  private void createGoogleApiClient() {
    mGoogleApiClient = new GoogleApiClient
      .Builder(getApplicationContext())
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

  private void createMessageReceiver() {
    mMessageReceiver = new BroadcastReceiver() {
      public @Override void onReceive(Context context, Intent intent) {
        Bundle content = intent.getExtras();

        switch (intent.getAction()) {
          case MessageType.SETTINGS:
            setOptions(content); break;
        }
      }
    };

    IntentFilter filter = new IntentFilter();
    filter.addAction(MessageType.SETTINGS);

    LocalBroadcastManager
      .getInstance(getApplicationContext())
      .registerReceiver(mMessageReceiver, filter);
  }

  private void destroyMessageReceiver() {
    LocalBroadcastManager
      .getInstance(getApplicationContext())
      .unregisterReceiver(mMessageReceiver);
  }

  private void startLocationUpdates() {
    if (mGoogleApiClient == null || !mGoogleApiClient.isConnected()) return;

    LocationServices.FusedLocationApi
      .requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
  }

  private void stopLocationUpdates() {
    if (mGoogleApiClient == null || !mGoogleApiClient.isConnected()) return;

    LocationServices.FusedLocationApi
      .removeLocationUpdates(mGoogleApiClient, this);
  }

  public static boolean isMockSettingsOn(Context context) {
    return !Settings.Secure
      .getString(context.getContentResolver(), Settings.Secure.ALLOW_MOCK_LOCATION)
      .equals("0");
  }

  private void sendMessage(String action, Bundle content) {
    LocalBroadcastManager
      .getInstance(getApplicationContext())
      .sendBroadcast(new Intent(action).putExtras(content));
  }

  private void sendError(int code, String message) {
    Bundle bundle = new Bundle();
    bundle.putInt("code", code);
    bundle.putString("message", message);

    sendMessage(MessageType.ERROR, bundle);
  }

}
