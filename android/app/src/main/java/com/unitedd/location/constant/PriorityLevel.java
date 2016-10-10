package com.unitedd.location.constant;

import com.google.android.gms.location.LocationRequest;

public class PriorityLevel {
  public static final int HIGH_ACCURACY = LocationRequest.PRIORITY_HIGH_ACCURACY;
  public static final int BALANCED = LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY;
  public static final int LOW_POWER = LocationRequest.PRIORITY_LOW_POWER;
  public static final int NO_POWER = LocationRequest.PRIORITY_NO_POWER;
}
