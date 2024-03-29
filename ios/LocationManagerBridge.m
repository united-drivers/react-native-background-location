//
//  LocationManagerBridge.m
//  BackgroundGeolocation
//
//  Created by Guilhem Fanton on 18/10/2016.
//  Copyright © 2016 Facebook. All rights reserved.
//

#import <Foundation/Foundation.h>
#import "RCTBridgeModule.h"
#import "RCTEventEmitter.h"

@interface RCT_EXTERN_REMAP_MODULE(BackgroundLocation, LocationManagerBridge, NSObject)

RCT_EXTERN_METHOD(startLocationServices:(RCTPromiseResolveBlock) resolve reject:(RCTPromiseRejectBlock) reject);
RCT_EXTERN_METHOD(requestAlwaysAuthorization);
RCT_EXTERN_METHOD(stopLocationServices)

@end
