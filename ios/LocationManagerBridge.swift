import Foundation
import CoreLocation

@objc(LocationManagerBridge)
class LocationManagerBridge : RCTEventEmitter {

    let locationManager : LocationManager = LocationManager()

    // App gets a new location every 5 minutes to keep timers alive
    var updateInterval : Double = 2

    var topController: UIViewController? = nil

    var timer = Timer()

    // Event const
    let LOCATION_EVENT = "backgroundLocationDidChange"

    override init() {

        super.init()

        self.topController = UIApplication.shared.keyWindow?.rootViewController

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

    @objc override func constantsToExport() -> [String : Any]! {
        return [
          "accuracy": [
            "HIGH": kCLLocationAccuracyBestForNavigation,
            "MEDIUM": kCLLocationAccuracyHundredMeters,
            "LOW": kCLLocationAccuracyThreeKilometers
          ]
        ]
    }

    func areLocationUpdatesEnabled () -> Bool {
        return self.locationManager.areUpdatesEnabled()
    }

    func getKeepAliveTimeInterval () -> Double {
      return self.locationManager.getKeepAlive();
    }

    func getUpdateIntervals () -> Double {
        return self.locationManager.getIntervals()
    }

    func setKeepAliveTimeInterval (interval: Double) {
        self.locationManager.setKeepAlive(keepAlive: interval)
    }

    func setUpdatesIntervals (interval: Double) {
        self.locationManager.setIntervals(updatesInterval: interval)
    }

    // This method starts the location update services through the SurveyLocationManager object of the class
    // (locationManager). Location services will only start if they are NOT already working and if the user has provided
    // permisions for the use of location services (CLAuthorizationStatus.Authorized). Any updates in the user's location
    // are handled by the locationManager property.
    @objc func startLocationServices(_ options: NSDictionary, resolve: @escaping RCTPromiseResolveBlock, reject: RCTPromiseRejectBlock) -> Void {
        var error: NSError? = nil
        var errorMessage: String? = nil
        var errorCode: Int = 0

        do {
            try self.locationManager.startLocationServices()
        } catch LocationServiceError.Unauthorized {
            errorCode = 1
            errorMessage = "Application is not authorized to use location services"
            self.requestWhenInUseAuthorization()
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
            DispatchQueue.global().async {
              DispatchQueue.main.async {
                self.timer = Timer.scheduledTimer(timeInterval: 1,
                                                  target: self,
                                                  selector: #selector(self.updateLocationEvent),
                                                  userInfo: nil,
                                                  repeats: true)
                }
                resolve("success")
              }
          }
    }

  @objc func requestWhenInUseAuthorization() {
        let alertController = UIAlertController(title: "Enable location first",
                                                message: "Location permission was not authorized. Please enable it in Settings to continue.",
                                                preferredStyle: .alert)

        let settingsAction = UIAlertAction(title: "Settings", style: .default) { (alertAction) in

            // THIS IS WHERE THE MAGIC HAPPENS!!!!
            if let appSettings = NSURL(string: UIApplicationOpenSettingsURLString) {
                UIApplication.shared.openURL(appSettings as URL)
            }
        }
        alertController.addAction(settingsAction)

        let cancelAction = UIAlertAction(title: "Cancel", style: .cancel, handler: nil)
        alertController.addAction(cancelAction)

        if (self.topController != nil) {
            self.topController!.present(alertController, animated: true, completion: nil)
        }
    }

  func changeAccuracy(accuracy: CLLocationAccuracy) {
    self.locationManager.changeLocationAccuracy(accuracy: accuracy)
  }

    // This method stops the location update services through the SurveyLocationManager object of the class
    // (locationManager). Location services will only stop if they are already working.
    @objc func stopLocationServices () -> Void {
        // Reset scheduler
        self.timer.invalidate()
        self.timer = Timer()

        // stop location services
        _ = self.locationManager.stopLocationServices()
    }

    // If succesful this method return a dictionary information of the last location
    @objc func getLocationRecord () -> NSDictionary {
        let newLocation = self.locationManager.dataManager.getLocationRecord()
        return newLocation.toDictionary()
    }

    // Fixes date format for debuggin and parsing, adds timezone and returns as NSString
    func fixDateFormat(date: Date) -> String {

        // Date Format
        let DATEFORMAT = "dd-MM-yyyy, HH:mm:ss"

        let dateFormatter = DateFormatter()

        // Format parameters
        dateFormatter.timeStyle = DateFormatter.Style.medium // Set time style
        dateFormatter.dateStyle = DateFormatter.Style.short // Set date style

        // Force date format to garantee consistency throught devices
        dateFormatter.dateFormat = DATEFORMAT
        dateFormatter.timeZone = NSTimeZone() as TimeZone!

      return  dateFormatter.string(from: date)
    }

    // EVENT
    // Update location event
    func updateLocationEvent () {
        self.sendEvent(withName: LOCATION_EVENT, body: self.getLocationRecord())
    }
}
