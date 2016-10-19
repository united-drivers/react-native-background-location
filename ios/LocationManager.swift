import CoreLocation
import CoreData
import UIKit

class LocationManager : NSObject, CLLocationManagerDelegate {
    // Date Format
    let DATEFORMAT : String = "dd-MM-yyyy, HH:mm:ss"

    // Time intervals for scan
    var UpdatesInterval : NSTimeInterval = 20
    var KeepAliveTimeInterval : Double = 10 // App gets a new location every 5 minutes to keep timers alive

    // NSTimer object for scheduling accuracy changes
    var timer = NSTimer()

    // Controls button calls
    var updatesEnabled = false

    // Location Manager - CoreLocation Framework
    let locationManager = CLLocationManager()

    // DataManager Object - Manages data in memory based on the CoreData framework
    let dataManager = DataManager()

    // UIBackgroundTask
    var bgTask = UIBackgroundTaskInvalid

    // NSNotificationCenter to handle changes in App LifeCycle
    var defaultCentre: NSNotificationCenter = NSNotificationCenter.defaultCenter()

    // NSUserDefaults - LocationServicesControl_KEY to be set to TRUE when user has enabled location services.
    let UserDefaults: NSUserDefaults = NSUserDefaults.standardUserDefaults()
    let LocationServicesControl_KEY: String = "LocationServices"

    override init () {

        // Super Class Constructor
        super.init()

        // Location Manager configuration --------------------------------------------------------------------------
        self.locationManager.delegate = self

        // Authorization for utilization of location services for background process
        if (CLLocationManager.authorizationStatus() != CLAuthorizationStatus.AuthorizedAlways) {
            self.locationManager.requestAlwaysAuthorization()
        }
        // END: Location Manager configuration ---------------------------------------------------------------------

        // NSNotificationCenter configuration for handling transitions in the App's Lifecycle
        let terminateSelector: Selector = #selector(LocationManager.appWillTerminate)
        let relaunchSelector: Selector = #selector(LocationManager.appIsRelaunched)

        self.defaultCentre.addObserver(self, selector: terminateSelector, name: UIApplicationWillTerminateNotification, object: nil)
        self.defaultCentre.addObserver(self, selector: relaunchSelector, name: UIApplicationDidFinishLaunchingNotification, object: nil)

        print("Location Manager Instantiated")
    }
    required init(coder aDecoder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    func setIntervals(updatesInterval: NSTimeInterval){ self.UpdatesInterval = updatesInterval;}
    func setKeepAlive(keepAlive: Double){ self.KeepAliveTimeInterval = keepAlive;}
    func getIntervals() -> NSTimeInterval { return self.UpdatesInterval}
    func getKeepAlive() -> Double { return self.KeepAliveTimeInterval}
    func areUpdatesEnabled() -> Bool { return self.updatesEnabled}

    // Starts location services if not enabled already, checks user permissions
    func startLocationServices () -> Bool {

        print("starting Location Updates")

        if (CLLocationManager.authorizationStatus() == CLAuthorizationStatus.AuthorizedAlways){

            if (!self.updatesEnabled){
                // Location Accuracy, properties & Distance filter
                self.locationManager.desiredAccuracy = kCLLocationAccuracyBest
                self.locationManager.distanceFilter = kCLDistanceFilterNone
                self.locationManager.allowsBackgroundLocationUpdates = true

                // Start receiving location updates
                self.locationManager.startUpdatingLocation()

                self.updatesEnabled = true;

                // Save Location Services ENABLED to NSUserDefaults
                self.UserDefaults.setBool(true, forKey: self.LocationServicesControl_KEY)

                print("Location Updates started")

                return true

            } else {
                print("Location Updates already enabled")
            }

        } else {

            print("Application is not authorized to use location services")
            // TODO: Unauthorized, requests permissions again and makes recursive call
        }
        return false
    }

    // Stops location services if not enabled already, checks user permissions
    func stopLocationServices() -> Bool {

        if(self.updatesEnabled) {

            self.updatesEnabled = false;
            self.locationManager.stopUpdatingLocation()

            // Stops Timer
            self.timer.invalidate()

            // Save Location Services DISABLED to NSUserDefaults
            self.UserDefaults.setBool(false, forKey: self.LocationServicesControl_KEY)

            return true
        } else {

            print("Location updates have not been enabled")
            return false
        }
    }

    // Toggles location manager's accuracy to save battery when waiting for timer to expire
    func changeLocationAccuracy () {

        let CurrentAccuracy = self.locationManager.desiredAccuracy

        switch CurrentAccuracy {

        case kCLLocationAccuracyBest: // Decreses Accuracy

            self.locationManager.desiredAccuracy = kCLLocationAccuracyThreeKilometers
            self.locationManager.distanceFilter = 99999

        case kCLLocationAccuracyThreeKilometers: // Increaces Accuracy

            self.locationManager.desiredAccuracy = kCLLocationAccuracyBest
            self.locationManager.distanceFilter = kCLDistanceFilterNone

        default:

            print("Accuracy not Changed")
        }
    }

    // Returns true newDate input parameter is an NSDate with a time interval difference of 3600 (60 minutes).
    // If zero returns true as no other record are in memory. Else returns false (invalid update)
    func isUpdateValid (newDate: NSDate) -> Bool {

        var interval = NSTimeInterval()

        if let newestRecord = self.dataManager.getLocationRecord() {
            let Date = newestRecord.timestamp
            interval = newDate.timeIntervalSinceDate(Date)
        }

        if ((interval == 0) || (interval >= self.UpdatesInterval)) {

            print("Location is VALID with interval:\(interval)")

            return true
        } else {

            print("got location update")

            return false
        }
    }

    // The AppDelegate triggers this method when the App is about to be terminated (Removed from memory due to
    // a crash or due to the user killing the app from the multitasking feature). This call causes the plugin to stop
    // standard location services if running, and enable significant changes to re-start the app as soon as possible.
    func appWillTerminate (notification: NSNotification) {

        let ServicesEnabled = self.UserDefaults.boolForKey(self.LocationServicesControl_KEY)

        // Stops Standard Location Services if they have been enabled by the user
        if ServicesEnabled {

            // Stop Location Updates
            self.locationManager.stopUpdatingLocation()

            // Stops Timer
            self.timer.invalidate()

            // Enables Significant Location Changes services to restart the app ASAP
            self.locationManager.startMonitoringSignificantLocationChanges()
        }
        NSUserDefaults.standardUserDefaults().synchronize()
    }

    // This method is called by the AppDelegate when the app starts. This method will stop the significant
    // change location updates and restart the standard location services if they where previously running (Checks saved
    // NSUserDefaults)
    func appIsRelaunched (notification: NSNotification) {

        // Stops Significant Location Changes services when app is relaunched
        self.locationManager.stopMonitoringSignificantLocationChanges()

        let ServicesEnabled = self.UserDefaults.boolForKey(self.LocationServicesControl_KEY)

        // Re-Starts Standard Location Services if they have been enabled by the user
        if (ServicesEnabled) {

            // TODO: Remove below after testing.
            let localNotification:UILocalNotification = UILocalNotification()
            localNotification.alertAction = "Application is running"
            localNotification.alertBody = "I'm Alive!"
            localNotification.fireDate = NSDate(timeIntervalSinceNow: 1)
            UIApplication.sharedApplication().scheduleLocalNotification(localNotification)

            // TODO: Remove above after testing.
            self.startLocationServices()
        }
    }

    func locationManager(manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {

        self.bgTask = UIApplication.sharedApplication().beginBackgroundTaskWithExpirationHandler({
                                                                                                     UIApplication.sharedApplication().endBackgroundTask(self.bgTask)
                                                                                                     self.bgTask = UIBackgroundTaskInvalid
                                                                                                 })

        // parse last known location
        let newLocation = locations.last!

        // Filters bad location updates cached by the OS -----------------------------------------------------------
        let Interval: NSTimeInterval = newLocation.timestamp.timeIntervalSinceNow

        let accuracy = self.locationManager.desiredAccuracy

        if ((abs(Interval) < 5) && (accuracy != kCLLocationAccuracyThreeKilometers)) {

            // Updates Persistent record through the DataManager object
            if isUpdateValid(newLocation.timestamp) {
                dataManager.updateLocationRecord(newLocation)
            }

            /* Timer initialized everytime an update is received. When timer expires, reverts accuracy to HIGH, thus
             enabling the delegate to receive new location updates */
            self.timer = NSTimer.scheduledTimerWithTimeInterval(self.KeepAliveTimeInterval,
                                                                target: self,
                                                                selector: #selector(LocationManager.changeLocationAccuracy),
                                                                userInfo: nil,
                                                                repeats: false)

            // Lowers accuracy to avoid battery drainage
            self.changeLocationAccuracy()
        }
        // END: Filters bad location updates cached by the OS ------------------------------------------------------
    }

    func locationManager(manager: CLLocationManager, didFailWithError error: NSError) {
        print("Location update error: \(error.localizedDescription)")
    }
}
