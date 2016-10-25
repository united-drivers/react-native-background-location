import React, { Component } from 'react'
import {
  StyleSheet,
  Text,
  PermissionsAndroid,
  View,
  Alert,
} from 'react-native'

import NativeEventEmitter from 'NativeEventEmitter'
import { BackgroundLocation } from 'NativeModules'

const BackgroundLocationEventEmitter = new NativeEventEmitter(BackgroundLocation)

class BackgroundGeolocation extends Component {

  componentWillMount() {
    BackgroundLocationEventEmitter.addListener('location', location => {
      console.log(location);
    })
  }

  componentDidMount() {
    BackgroundLocation.startLocationServices()
                         .then(l => console.log(l))
                         .catch(e => {
                           console.log(e)
                           BackgroundLocation.requestAlwaysAuthorization()
                         })
  }

  render() {
    return (
      <View style={styles.container}>
        <View>
          <Text style={styles.welcome}>Welcome to React Native!</Text>
        </View>
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
