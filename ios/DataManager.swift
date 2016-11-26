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

    var locationRecord : LocationData! = LocationData()

    // User Defaults
    let userDefaults: UserDefaults = UserDefaults.standard
    init () {}

    // Saves an array of LocationData as a NSArray of NSData objects in the NSUserDefaults by deleting old
    // instance and saving array from argument.
    func saveLocationRecord () {
        // Update old array from UserDefaults
        self.userDefaults.removeObject(forKey: LocationRecord_Key)
        self.userDefaults.set(self.locationRecord, forKey: LocationRecord_Key)
        self.userDefaults.synchronize()
    }

    func getLocationRecord() -> LocationData {
        return self.locationRecord
    }

    // Converts CLLocation object to LocationData object, adds new record to [LocationData] from the
    // getUpdatedRecord method and saves modified array to memory.
    func updateLocationRecord (newRecord: CLLocation) {

        let newLocationDataRecord = LocationData()

        newLocationDataRecord.latitude  = newRecord.coordinate.latitude as NSNumber!
        newLocationDataRecord.longitude = newRecord.coordinate.longitude as NSNumber!
        newLocationDataRecord.altitude = newRecord.altitude as NSNumber!
        newLocationDataRecord.accuracy = newRecord.horizontalAccuracy as NSNumber!
        newLocationDataRecord.timestamp = newRecord.timestamp as NSDate!

        self.locationRecord = newLocationDataRecord

        // Save new record
        // self.saveLocationRecord()
    }

    // Fixes date format for debuggin and parsing, adds timezone and returns as NSString
    func fixDateFormat(date: Date) -> String {

        let dateFormatter = DateFormatter()
        // Format parameters
        dateFormatter.timeStyle = DateFormatter.Style.medium
        dateFormatter.dateStyle = DateFormatter.Style.short

        // Force date format to garantee consistency throught devices
        dateFormatter.dateFormat = DATEFORMAT
        dateFormatter.timeZone = NSTimeZone() as TimeZone!

        return  dateFormatter.string(from: date)
    }
}
