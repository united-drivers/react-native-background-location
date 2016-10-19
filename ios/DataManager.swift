import CoreData
import UIKit
import CoreLocation

class DataManager {

    // MaxNumber of record allowed
    var MaxNoOfRecord:Int  = 10

    // Date Format
    let DATEFORMAT = "dd-MM-yyyy, HH:mm:ss"

    // Class keys
    let LocationRecord_Key = "LOCATION_RECORD"
    let LocationData_Key = "LOCATION_DATA"

    var locationRecord : LocationData? = nil

    // User Defaults
    let UserDefaults: NSUserDefaults = NSUserDefaults.standardUserDefaults()

    init () {}

    // Saves an array of LocationData as a NSArray of NSData objects in the NSUserDefaults by deleting old
    // instance and saving array from argument.
    func saveLocationRecord () {
        // Update old array from UserDefaults
        self.UserDefaults.removeObjectForKey(LocationRecord_Key)
        self.UserDefaults.setObject(self.locationRecord, forKey: LocationRecord_Key)
        self.UserDefaults.synchronize()
    }

    func getLocationRecord() -> LocationData? {
        return self.locationRecord
    }

    // Converts CLLocation object to LocationData object, adds new record to [LocationData] from the
    // getUpdatedRecord method and saves modified array to memory.
    func updateLocationRecord (newRecord: CLLocation) {

        // Get CLLocation properties
        let latitude:NSNumber = newRecord.coordinate.latitude
        let longitude:NSNumber = newRecord.coordinate.longitude
        let accuracy:NSNumber = newRecord.horizontalAccuracy
        let altitude:NSNumber = newRecord.altitude
        let time:NSDate = newRecord.timestamp

        let newLocationDataRecord = LocationData()

        newLocationDataRecord.latitude  = latitude
        newLocationDataRecord.longitude = longitude
        newLocationDataRecord.altitude = altitude
        newLocationDataRecord.accuracy = accuracy
        newLocationDataRecord.timestamp = time

        print("location updated")

        self.locationRecord = newLocationDataRecord

        print("--------------------")

        // Save new record
        // self.saveLocationRecord()
    }

    // Fixes date format for debuggin and parsing, adds timezone and returns as NSString
    func fixDateFormat(date: NSDate) -> NSString {

        let dateFormatter = NSDateFormatter()
        // Format parameters
        dateFormatter.timeStyle = NSDateFormatterStyle.MediumStyle
        dateFormatter.dateStyle = NSDateFormatterStyle.ShortStyle

        // Force date format to garantee consistency throught devices
        dateFormatter.dateFormat = DATEFORMAT
        dateFormatter.timeZone = NSTimeZone()

        return  dateFormatter.stringFromDate(date)
    }
}
