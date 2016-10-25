import Foundation
import CoreLocation

@objc(LocationManagerBridge)
class LocationManagerBridge : RCTEventEmitter {

    let locationManager : LocationManager = LocationManager()

    var updateInterval : Double = 2 // App gets a new location every 5 minutes to keep timers alive

    var topController: UIViewController? = nil

    var timer = NSTimer()

    // Event const
    let LOCATION_EVENT = "location"

    override init() {

      super.init()

      self.topController = UIApplication.sharedApplication().keyWindow?.rootViewController

      if (self.topController != nil) {
        while let presentedViewController = self.topController!.presentedViewController {
          self.topController = presentedViewController
        }
      }
    }

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
    // permisions for the use of location services (CLAuthorizationStatus.Authorized). Any updates in the user's location
    // are handled by the locationManager property.
    @objc func startLocationServices (resolve: RCTPromiseResolveBlock, reject: RCTPromiseRejectBlock) -> Void {
        var error: NSError? = nil
        var errorMessage: String? = nil
        var errorCode: Int = 0

        do {
            try self.locationManager.startLocationServices()
        } catch LocationServiceError.Unauthorized {
            errorCode = 1
            errorMessage = "Application is not authorized to use location services"
        } catch LocationServiceError.AlreadyEnabled {
            errorCode = 2
            errorMessage = "Location Updates already enabled"
        } catch {
            errorCode = 3
            errorMessage = "Unknown location error"
        }

        if errorCode > 0 {
            error = NSError(domain: "LocationServiceError",
                            code: errorCode,
                            userInfo: [NSLocalizedDescriptionKey: errorMessage!])

            reject("LocationServiceError", errorMessage, error)
        } else {

            dispatch_async(dispatch_get_main_queue()) {
                self.timer = NSTimer.scheduledTimerWithTimeInterval(1,
                                                                    target: self,
                                                                    selector: #selector(self.updateLocationEvent),
                                                                    userInfo: nil,
                                                                    repeats: true)
            }

            resolve("success")
        }

    }

  @objc func requestAlwaysAuthorization() {
    let alertController = UIAlertController(title: "Enable location first",
                                            message: "Location permission was not authorized. Please enable it in Settings to continue.",
                                            preferredStyle: .Alert)

    let settingsAction = UIAlertAction(title: "Settings", style: .Default) { (alertAction) in

      // THIS IS WHERE THE MAGIC HAPPENS!!!!
      if let appSettings = NSURL(string: UIApplicationOpenSettingsURLString) {
        UIApplication.sharedApplication().openURL(appSettings)
      }
    }
    alertController.addAction(settingsAction)

    let cancelAction = UIAlertAction(title: "Cancel", style: .Cancel, handler: nil)
    alertController.addAction(cancelAction)

    if (self.topController != nil) {
      self.topController!.presentViewController(alertController, animated: true, completion: nil)
    }
  }

    // This method stops the location update services through the SurveyLocationManager object of the class
    // (locationManager). Location services will only stop if they are already working.
    @objc func stopLocationServices () -> Void {
        // Reset scheduler
        self.timer.invalidate()
        self.timer = NSTimer()

        // stop location services
        self.locationManager.stopLocationServices()
    }

    // If succesful this method return a dictionary information of the last location
    @objc func getLocationRecord () -> NSDictionary {
        let newLocation = self.locationManager.dataManager.getLocationRecord()

        // Houuray! A location exist
        let location: NSDictionary = [
          "longitude": newLocation.longitude,
          "latitude": newLocation.latitude,
          "altitude": newLocation.altitude,
          "accuracy": newLocation.accuracy,
          "timestamp": newLocation.timestamp.timeIntervalSince1970 * 1000 as NSNumber // * 1000 in milliseconds
        ]

        return location
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
