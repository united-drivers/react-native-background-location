package com.unitedd.location.constant;

import com.google.android.gms.location.LocationRequest;

public class AccuracyLevel {
  public static final int HIGH = LocationRequest.PRIORITY_HIGH_ACCURACY;
  public static final int MEDIUM = LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY;
  public static final int LOW = LocationRequest.PRIORITY_LOW_POWER;
  public static final int PASSIVE = LocationRequest.PRIORITY_NO_POWER;
}
