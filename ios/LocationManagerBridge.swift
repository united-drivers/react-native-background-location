import Foundation
import CoreLocation

@objc(LocationManagerBridge)
class LocationManagerBridge : RCTEventEmitter {

    let locationManager : LocationManager = LocationManager()

    var updateInterval : Double = 2 // App gets a new location every 5 minutes to keep timers alive

    var timer = NSTimer()

    // Event const
    let LOCATION_EVENT = "location"

    override func supportedEvents() -> [String]! {
        return [
          LOCATION_EVENT
        ]
     }

    override func startObserving() {
        print("Start Observing")
    }
    override func stopObserving() {
        print("Stop Observing")
    }

    func areLocationUpdatesEnabled () -> Bool {
        return self.locationManager.areUpdatesEnabled()
    }

    func getKeepAliveTimeInterval () -> NSNumber {
        return self.locationManager.getKeepAlive();
    }

    func getUpdateIntervals () -> NSNumber{
        return self.locationManager.getIntervals()
    }

    func setKeepAliveTimeInterval (interval: Double) {
        self.locationManager.setKeepAlive(interval)
    }

    func setUpdatesIntervals (interval: Double) {
        self.locationManager.setIntervals(interval)
    }

    // This method starts the location update services through the SurveyLocationManager object of the class
    // (locationManager). Location services will only start if they are NOT already working and if the user has provided
    // permisions for the use of location services (CLAuthorizationStatus.Authorized).
    @objc func startLocationServices () -> Void {
        self.locationManager.startLocationServices()
        dispatch_async(dispatch_get_main_queue()) {
            self.timer = NSTimer.scheduledTimerWithTimeInterval(1,
                                                                target: self,
                                                                selector: #selector(self.updateLocationEvent),
                                                                userInfo: nil,
                                                                repeats: true)
        }
        print("startlocationservices")
    }

    // This method stops the location update services through the SurveyLocationManager object of the class
    // (locationManager). Location services will only stop if they are already working.
    @objc func stopLocationServices () -> Void {
        self.timer.invalidate()
        self.locationManager.stopLocationServices()
    }

    // If succesful this method return a dictionary information of the last location
    @objc func getLocationRecord () -> NSDictionary {
      if let newLocation = self.locationManager.dataManager.getLocationRecord() {

        // Houuray! A location exist
        let location: NSDictionary = [
          "longitude": newLocation.longitude,
          "latitude": newLocation.latitude,
          "altitude": newLocation.altitude,
          "accuracy": newLocation.accuracy
        ]

        return location
      }

      // No location
      return [
        "longitude": Double.NaN,
        "latitude": Double.NaN,
        "altitude": Double.NaN,
        "accuracy": Double.NaN
      ]
    }

    // Fixes date format for debuggin and parsing, adds timezone and returns as NSString
    func fixDateFormat(date: NSDate) -> NSString {

        // Date Format
        let DATEFORMAT = "dd-MM-yyyy, HH:mm:ss"

        let dateFormatter = NSDateFormatter()
        // Format parameters
        dateFormatter.timeStyle = NSDateFormatterStyle.MediumStyle // Set time style
        dateFormatter.dateStyle = NSDateFormatterStyle.ShortStyle // Set date style

        // Force date format to garantee consistency throught devices
        dateFormatter.dateFormat = DATEFORMAT
        dateFormatter.timeZone = NSTimeZone()

        return  dateFormatter.stringFromDate(date)
    }

    // EVENT
    // Update location event
    func updateLocationEvent () {
        self.sendEventWithName(LOCATION_EVENT, body: self.getLocationRecord())
    }
}
