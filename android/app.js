import React, { Component } from 'react'
import {
  StyleSheet,
  Text,
  TouchableWithoutFeedback,
  View,
  Alert,
} from 'react-native'

import Geolocation from './module'

class BackgroundGeolocation extends Component {
  componentDidMount() {
    Geolocation.watchPosition(
      () => {},
      () => {},
      {
        accuracy: Geolocation.AccuracyLevels.HIGH
      })

    console.log(Geolocation.AccuracyLevels)
  }

  render() {
    return (
      <View style={styles.container}>
        <TouchableWithoutFeedback onPress={() => {}}>
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
