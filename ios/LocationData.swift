import Foundation
import CoreData

class LocationData : NSObject, NSCoding {

    var latitude : NSNumber = NSDecimalNumber.notANumber
    var longitude: NSNumber = NSDecimalNumber.notANumber
    var altitude: NSNumber = NSDecimalNumber.notANumber
    var accuracy: NSNumber = NSDecimalNumber.notANumber
    var timestamp: NSDate? = nil

    required convenience init?(coder aDecoder: NSCoder) {

        self.init()

        self.latitude  = aDecoder.decodeObject(forKey: "latitude") as! NSNumber
        self.longitude = aDecoder.decodeObject(forKey: "longitude") as! NSNumber
        self.accuracy = aDecoder.decodeObject(forKey: "accuracy") as! NSNumber
        self.altitude = aDecoder.decodeObject(forKey: "altitude") as! NSNumber
        self.timestamp = aDecoder.decodeObject(forKey: "timestamp") as? NSDate
    }

    func encode(with aCoder: NSCoder) {

        aCoder.encode(self.latitude, forKey: "latitude")
        aCoder.encode(self.longitude, forKey:"longitude")
        aCoder.encode(self.accuracy, forKey:"accuracy")
        aCoder.encode(self.altitude, forKey:"altitude")
        aCoder.encode(self.timestamp, forKey:"timestamp")
    }

    func toDictionary() -> NSDictionary {
        let location: NSDictionary = [
          "longitude": self.longitude,
          "latitude": self.latitude,
          "altitude": self.altitude,
          "accuracy": self.accuracy,
          "timestamp": self.timestamp!.timeIntervalSince1970 * 1000 as NSNumber // * 1000 in milliseconds
        ]

        return location
    }
}
