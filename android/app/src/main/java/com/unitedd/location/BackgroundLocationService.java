package com.unitedd.location;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

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
import com.unitedd.location.constant.MessageType;
import com.unitedd.location.constant.PriorityLevel;

public class BackgroundLocationService extends Service implements
  LocationListener,
  GoogleApiClient.ConnectionCallbacks,
  GoogleApiClient.OnConnectionFailedListener,
  ResultCallback<LocationSettingsResult> {

  private @Nullable GoogleApiClient mGoogleApiClient;
  private @Nullable LocationRequest mLocationRequest;
  private @Nullable OptionsReceiver mOptionsReceiver;
  private @Nullable Intent mLocationIntent;
  private @Nullable Intent mErrorIntent;

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
    registerOptionListener();
    createGoogleApiClient();
  }

  @Override
  public void onDestroy() {
    unregisterOptionListener();
    destroyGoogleApiClient();
  }

  @Override
  public void onConnected(@Nullable Bundle bundle) {
    askForSettings();
  }

  @Override
  public void onConnectionSuspended(int i) {
    if (mGoogleApiClient != null)
      mGoogleApiClient.connect();
  }

  @Override
  public void onConnectionFailed(@NonNull ConnectionResult result) {
    sendError(0, "Connection to Google Play Services failed");
  }

  @Override
  public void onResult(@NonNull LocationSettingsResult result) {
    final Status status = result.getStatus();

    switch (status.getStatusCode()) {
      case LocationSettingsStatusCodes.SUCCESS:
        startLocationUpdates();
        break;
      case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
        sendError(0, "Resolution required");
        break;
    }
  }

  @Override
  public void onLocationChanged(Location location) {
    sendLocation(location);
  }

  private void setOptions(Bundle options) {
    if (mLocationRequest == null)
      mLocationRequest = new LocationRequest();

    // TODO: fallback on default params
    mLocationRequest.setPriority(options.getInt("accuracy", PriorityLevel.BALANCED));
    mLocationRequest.setFastestInterval(1000);
    mLocationRequest.setInterval(1000);
  }

  private void askForSettings() {
    if (mLocationRequest == null) return;

    LocationSettingsRequest settingsRequest = new LocationSettingsRequest.Builder()
      .addLocationRequest(mLocationRequest)
      .build();

    LocationServices.SettingsApi
      .checkLocationSettings(mGoogleApiClient, settingsRequest)
      .setResultCallback(this);
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
    stopLocationUpdates();
    mGoogleApiClient.disconnect();
    mGoogleApiClient = null;
  }

  private void startLocationUpdates() {
    if (mGoogleApiClient == null || mLocationRequest == null) return;
    LocationServices.FusedLocationApi
      .requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
  }

  private void stopLocationUpdates() {
    if (mGoogleApiClient == null || !mGoogleApiClient.isConnected()) return;
    LocationServices.FusedLocationApi
      .removeLocationUpdates(mGoogleApiClient, this);
  }

  private void registerOptionListener() {
    if (mOptionsReceiver != null) return;
    mOptionsReceiver = new OptionsReceiver();

    getApplicationContext().registerReceiver(
      mOptionsReceiver,
      new IntentFilter(MessageType.SETTINGS
    ));
  }

  private void unregisterOptionListener() {
    if (mOptionsReceiver == null) return;
    getApplicationContext().unregisterReceiver(mOptionsReceiver);
    mOptionsReceiver = null;
  }

  private void sendLocation(Location location) {
    if (mLocationIntent == null)
      mLocationIntent = new Intent(MessageType.LOCATION);

    mLocationIntent.putExtra("latitude", location.getLatitude());
    mLocationIntent.putExtra("longitude", location.getLongitude());
    mLocationIntent.putExtra("altitude", location.getAltitude());
    mLocationIntent.putExtra("accuracy", location.getAccuracy());
    mLocationIntent.putExtra("heading", location.getBearing());
    mLocationIntent.putExtra("speed", location.getSpeed());
    mLocationIntent.putExtra("timestamp", location.getTime());

    if (android.os.Build.VERSION.SDK_INT >= 18) {
      mLocationIntent.putExtra("mocked", location.isFromMockProvider());
    }

    sendBroadcast(mLocationIntent);
  }

  private void sendError(int code, String message) {
    if (mErrorIntent == null)
      mErrorIntent = new Intent(MessageType.ERROR);

    Bundle error = new Bundle();
    error.putInt("code", code);
    error.putString("message", message);
    mErrorIntent.putExtras(error);
    sendBroadcast(mErrorIntent);
  }

  private class OptionsReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
      stopLocationUpdates();
      setOptions(intent.getExtras());
      startLocationUpdates();
    }
  }
}
