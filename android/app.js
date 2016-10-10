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
    /*Geolocation
      .start(options = {})
      .then(success => {})
      .catch(error => {})

    Geolocation.stop();

    Geolocation.on('location', location => {

    })

    Geolocation.on('error', error => {

    })*/

    Geolocation.watchPosition(
      position => {
        console.log(position)

        fetch('http://10.18.203.191:3000', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'Accept': 'application/json',
          },
          body: JSON.stringify(position),
        }).catch(e => console.warn(e))
      },
      error => {
        console.warn(error)

        Alert.alert(
          'Le GPS est pas activé coco',
          'Ici, des détails super cools',
          [
            {text: 'Emmène moi grand fou', onPress: () => Geolocation.openLocationSettings()},
          ]
        )
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
