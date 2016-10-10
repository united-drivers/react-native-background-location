package com.unitedd.location;

import android.app.Service;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

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
import com.unitedd.location.constant.Application;
import com.unitedd.location.constant.MessageType;

public class BackgroundLocationService extends Service implements
  LocationListener,
  GoogleApiClient.ConnectionCallbacks,
  GoogleApiClient.OnConnectionFailedListener,
  ResultCallback<LocationSettingsResult> {

  private @Nullable GoogleApiClient mGoogleApiClient;
  private @Nullable LocationRequest mLocationRequest;
  private Intent mLocationIntent;
  private Intent mErrorIntent;

  public BackgroundLocationService() {
    mLocationIntent = new Intent(MessageType.LOCATION);
    mErrorIntent = new Intent(MessageType.ERROR);
  }

  @Nullable
  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  @Override
  public void onCreate() {
    mGoogleApiClient = new GoogleApiClient
      .Builder(getApplicationContext())
      .addApi(LocationServices.API)
      .addConnectionCallbacks(this)
      .addOnConnectionFailedListener(this)
      .build();

    mGoogleApiClient.connect();
  }

  @Override
  public void onDestroy() {
    if (mGoogleApiClient == null) return;

    mGoogleApiClient.unregisterConnectionFailedListener(this);
    mGoogleApiClient.unregisterConnectionCallbacks(this);

    if (mGoogleApiClient.isConnected()) {
      LocationServices.FusedLocationApi
        .removeLocationUpdates(mGoogleApiClient, this);
    }

    mGoogleApiClient.disconnect();
    mGoogleApiClient = null;
  }

  @Override
  public void onConnected(@Nullable Bundle bundle) {
    Log.e(Application.TAG, "it is connected");

    mLocationRequest = new LocationRequest()
      .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
      .setFastestInterval(1000)
      .setInterval(1000);

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
    sendError(0, "Connection to Google Play Services failed");
  }

  @Override
  public void onResult(@NonNull LocationSettingsResult result) {
    final Status status = result.getStatus();

    switch (status.getStatusCode()) {
      case LocationSettingsStatusCodes.SUCCESS:
        requestLocationUpdate();
        break;
      case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
        sendError(0, "Resolution required");
        break;
    }
  }

  @Override
  public void onLocationChanged(Location location) {
    Log.e(Application.TAG, "Location has changed");

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

  private void requestLocationUpdate() {
    LocationServices.FusedLocationApi
      .requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
  }

  private void sendError(int code, String message) {
    mErrorIntent.putExtra("code", code);
    mErrorIntent.putExtra("message", message);

    sendBroadcast(mErrorIntent);
  }
}
