import Foundation
import CoreData

class LocationData : NSObject, NSCoding {

    var latitude : NSNumber!
    var longitude: NSNumber!
    var altitude: NSNumber!
    var accuracy: NSNumber!
    var timestamp: NSDate!

    required convenience init?(coder aDecoder: NSCoder) {

        self.init()

        self.latitude  = aDecoder.decodeObjectForKey("latitude") as! NSNumber?
        self.longitude = aDecoder.decodeObjectForKey("longitude") as! NSNumber?
        self.accuracy = aDecoder.decodeObjectForKey("accuracy") as! NSNumber?
        self.altitude = aDecoder.decodeObjectForKey("altitude") as! NSNumber?
        self.timestamp = aDecoder.decodeObjectForKey("timestamp") as! NSDate?
    }

    func encodeWithCoder(aCoder: NSCoder) {

        aCoder.encodeObject(self.latitude, forKey: "latitude")
        aCoder.encodeObject(self.longitude, forKey:"longitude")
        aCoder.encodeObject(self.accuracy, forKey:"accuracy")
        aCoder.encodeObject(self.altitude, forKey:"altitude")
        aCoder.encodeObject(self.timestamp, forKey:"timestamp")
    }
}
