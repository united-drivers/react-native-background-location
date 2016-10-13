import React, { Component } from 'react'
import {
  StyleSheet,
  Text,
  TouchableWithoutFeedback,
  PermissionsAndroid,
  View,
  Alert,
} from 'react-native'

import Geolocation from './module'

import NativeEventEmitter from 'NativeEventEmitter'
import { BackgroundLocation } from 'NativeModules'
const BackgroundLocationEventEmitter = new NativeEventEmitter(BackgroundLocation)

async function checkPermissions() {
  const {requestPermission, PERMISSIONS} = PermissionsAndroid

  try {
    return await requestPermission(PERMISSIONS.ACCESS_FINE_LOCATION)
  } catch (e) {
    Promise.reject(e.message)
  }
}

class BackgroundGeolocation extends Component {
  componentDidMount() {
    BackgroundLocationEventEmitter.addListener('location', location => {
      console.log(location);
    })

    BackgroundLocationEventEmitter.addListener('error', error => {
      console.warn(error);
    })

    BackgroundLocation
                .startObserving({
                  accuracy: BackgroundLocation.PriorityLevels.HIGH_ACCURACY
                })
                .then(l => console.log(l))
                .catch(e => console.warn(e))

    /*checkPermissions()
      .then(granted => {
        if (granted) {
          BackgroundLocation
            .startObserving({
              accuracy: BackgroundLocation.PriorityLevels.HIGH_ACCURACY
            })
            .then(l => console.log(l))
            .catch(e => console.warn(e))
        }
      })*/
  }

  render() {
    return (
      <View style={styles.container}>
        <TouchableWithoutFeedback onPress={() => Geolocation.openLocationSettings()}>
          <View>
            <Text style={styles.welcome}>Welcome to React Native!</Text>
          </View>
        </TouchableWithoutFeedback>
      </View>
    )
  }
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#F5FCFF',
  },
  welcome: {
    fontSize: 20,
    textAlign: 'center',
    margin: 10,
  },
  instructions: {
    textAlign: 'center',
    color: '#333333',
    marginBottom: 5,
  },
})

export default BackgroundGeolocation
