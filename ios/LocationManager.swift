import CoreLocation
import CoreData
import UIKit

enum LocationServiceError: Error {
    case AlreadyEnabled
    case Unauthorized
}

class LocationManager : NSObject, CLLocationManagerDelegate {
    // Date Format
    let DATEFORMAT : String = "dd-MM-yyyy, HH:mm:ss"

    var errorHandler : ((NSError?) -> Void)? = nil
  
    // Time intervals for scan
    var UpdatesInterval : TimeInterval = 20
    var KeepAliveTimeInterval : Double = 10 // App gets a new location every 5 minutes to keep timers alive

    // NSTimer object for scheduling accuracy changes
    var timer = Timer()

    // Controls button calls
    var updatesEnabled = false

    // Location Manager - CoreLocation Framework
    let locationManager = CLLocationManager()

    // DataManager Object - Manages data in memory based on the CoreData framework
    let dataManager = DataManager()

    // UIBackgroundTask
    var bgTask = UIBackgroundTaskInvalid

    // NSNotificationCenter to handle changes in App LifeCycle
    var defaultCentre: NotificationCenter = NotificationCenter.default

    // NSUserDefaults - LocationServicesControl_KEY to be set to TRUE when user has enabled location services.
    let userDefaults: UserDefaults = UserDefaults.standard

    let LocationServicesControl_KEY: String = "LocationServices"

    override init () {

        // Super Class Constructor
        super.init()

        // Location Manager configuration --------------------------------------------------------------------------
        self.locationManager.delegate = self

        // Authorization for utilization of location services for background process
        if (CLLocationManager.authorizationStatus() != CLAuthorizationStatus.authorizedWhenInUse) {
            self.locationManager.requestWhenInUseAuthorization()
        }
        // END: Location Manager configuration ---------------------------------------------------------------------

        // NSNotificationCenter configuration for handling transitions in the App's Lifecycle
        let terminateSelector: Selector = #selector(LocationManager.appWillTerminate)
        let relaunchSelector: Selector = #selector(LocationManager.appIsRelaunched)

        self.defaultCentre.addObserver(self, selector: terminateSelector, name: NSNotification.Name.UIApplicationWillTerminate, object: nil)
        self.defaultCentre.addObserver(self, selector: relaunchSelector, name: NSNotification.Name.UIApplicationDidFinishLaunching, object: nil)

        print("Location Manager Instantiated")
    }

    required init(coder aDecoder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    func setIntervals(updatesInterval: TimeInterval){ self.UpdatesInterval = updatesInterval;}
    func setKeepAlive(keepAlive: Double){ self.KeepAliveTimeInterval = keepAlive;}
    func getIntervals() -> TimeInterval { return self.UpdatesInterval}
    func getKeepAlive() -> Double { return self.KeepAliveTimeInterval}
    func areUpdatesEnabled() -> Bool { return self.updatesEnabled}
    func setErrorHandler(errorHandler: @escaping (NSError?) -> Void) { self.errorHandler = errorHandler }
  
    func startLocationServices() throws {
        print("starting Location Updates: ", self.updatesEnabled)
        guard (CLLocationManager.authorizationStatus() == CLAuthorizationStatus.authorizedWhenInUse) else {
            throw LocationServiceError.Unauthorized
        }

        guard (!self.updatesEnabled) else {
            throw LocationServiceError.AlreadyEnabled
        }

        // Location Accuracy, properties & Distance filter
        self.locationManager.desiredAccuracy = kCLLocationAccuracyBest
        self.locationManager.distanceFilter = kCLDistanceFilterNone
        self.locationManager.allowsBackgroundLocationUpdates = true

        // Start receiving location updates
        self.locationManager.startUpdatingLocation()

        self.updatesEnabled = true;

        // Save Location Services ENABLED to NSUserDefaults
        self.userDefaults.set(true, forKey: self.LocationServicesControl_KEY)

        print("Location Updates started")
    }

    // Stops location services if not enabled already, checks user permissions
    func stopLocationServices() -> Bool {

        if (self.updatesEnabled) {

            self.updatesEnabled = false;
            self.locationManager.stopUpdatingLocation()

            // Stops Timer
            self.timer.invalidate()

            // Save Location Services DISABLED to NSUserDefaults
            self.userDefaults.set(false, forKey: self.LocationServicesControl_KEY)

            return true
        } else {

            print("Location updates have not been enabled")
            return false
        }
    }

    func requestWhenInUseAuthorization() {
        if CLLocationManager.authorizationStatus() != CLAuthorizationStatus.authorizedWhenInUse {
            self.locationManager.requestWhenInUseAuthorization()
        }
    }

    // Toggles location manager's accuracy to save battery when waiting for timer to expire
    func changeLocationAccuracy (accuracy: CLLocationAccuracy) {
         self.locationManager.distanceFilter = accuracy
    }

    // The AppDelegate triggers this method when the App is about to be terminated (Removed from memory due to
    // a crash or due to the user killing the app from the multitasking feature). This call causes the plugin to stop
    // standard location services if running, and enable significant changes to re-start the app as soon as possible.
    func appWillTerminate (notification: NSNotification) {

        let ServicesEnabled = self.userDefaults.bool(forKey: self.LocationServicesControl_KEY)

        // Stops Standard Location Services if they have been enabled by the user
        if ServicesEnabled {

            // Stop Location Updates
            self.locationManager.stopUpdatingLocation()

            // Stops Timer
            self.timer.invalidate()

            // Enables Significant Location Changes services to restart the app ASAP
            self.locationManager.startMonitoringSignificantLocationChanges()
        }

        UserDefaults.standard.synchronize()
    }

    // This method is called by the AppDelegate when the app starts. This method will stop the significant
    // change location updates and restart the standard location services if they where previously running (Checks saved
    // NSUserDefaults)
    func appIsRelaunched (notification: NSNotification) {

        // Stops Significant Location Changes services when app is relaunched
        self.locationManager.stopMonitoringSignificantLocationChanges()

        let ServicesEnabled = self.userDefaults.bool(forKey: self.LocationServicesControl_KEY)

        // Re-Starts Standard Location Services if they have been enabled by the user
        if (ServicesEnabled) {

            // TODO: Remove below after testing.
            let localNotification:UILocalNotification = UILocalNotification()
            localNotification.alertAction = "Application is running"
            localNotification.alertBody = "I'm Alive!"
            localNotification.fireDate = NSDate(timeIntervalSinceNow: 1) as Date
            UIApplication.shared.scheduleLocalNotification(localNotification)

            // TODO: Remove above after testing.
            let _ = try? self.startLocationServices()
        }
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {

        self.bgTask = UIApplication.shared.beginBackgroundTask(expirationHandler: {
                                                                                                     UIApplication.shared.endBackgroundTask(self.bgTask)
                                                                                                     self.bgTask = UIBackgroundTaskInvalid
                                                                                                 })

        // parse last known location
        let newLocation = locations.last!

        // Filters bad location updates cached by the OS -----------------------------------------------------------
        let Interval: TimeInterval = newLocation.timestamp.timeIntervalSinceNow

        let accuracy = self.locationManager.desiredAccuracy

        if ((abs(Interval) < 5) && (accuracy != kCLLocationAccuracyThreeKilometers)) {

            // Updates Persistent record through the DataManager object
            dataManager.updateLocationRecord(newRecord: newLocation)

            /* Timer initialized everytime an update is received. When timer expires, reverts accuracy to HIGH, thus
             enabling the delegate to receive new location updates */
            self.timer = Timer.scheduledTimer(timeInterval: self.KeepAliveTimeInterval,
                                                                target: self,
                                                                selector: #selector(LocationManager.changeLocationAccuracy),
                                                                userInfo: nil,
                                                                repeats: false)
        }
        // END: Filters bad location updates cached by the OS ------------------------------------------------------
    }

    private func locationManager(manager: CLLocationManager, didFailWithError error: NSError) {
        if self.errorHandler != nil {
          self.errorHandler!(error)
        }

        print("Location update error: \(error.localizedDescription)")
    }
}
