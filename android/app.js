import React, { Component } from 'react'
import {
  StyleSheet,
  Text,
  TouchableWithoutFeedback,
  View,
  Alert,
} from 'react-native'

import Geolocation from './module'

import NativeEventEmitter from 'NativeEventEmitter'
import { BackgroundLocation } from 'NativeModules'
const BackgroundLocationEventEmitter = new NativeEventEmitter(BackgroundLocation)

class BackgroundGeolocation extends Component {
  componentDidMount() {
    /*Geolocation
      .start(options = {})
      .then(success => {})
      .catch(error => {})

    Geolocation.stop();*/

    BackgroundLocationEventEmitter.addListener('location', location => {
      console.log(location);
    })

    BackgroundLocationEventEmitter.addListener('error', error => {
      console.warn(error);
    })

    BackgroundLocation
      .startObserving({})
      .then(e => console.log(e))
      .catch(e => console.warn(e))

    Geolocation.watchPosition(
      position => {
        console.log(position)
      },
      error => {
        console.warn(error)
      },
    )
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
