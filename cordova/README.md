WebRTC Conferencing Project
=========================

Android
-----------

1. Be sure to have Apache Cordova with Android SDK installed on your computer.

2. Then, create a new project

  ```bash
  $ cordova create webrtc cz.cvut.fel.webrtc WebRTC
  ```

3. Empty *www* folder and copy *angular-webpage* project files in *www* folder

4. Add Android platform

  ```bash
  $ cordova platform add android
  ```

5. Edit *www/index.html* and add these lines under section balise

  ```html
  <script src="cordova.js"></script>
  <script>window.chrome = true;</script>
  ```

6. Install *bower* dependencies from *www* folder
  
  ```bash
  $ bower install
  ```

7. Be sure to have *config.json* in *www* folder

  ```json
  {
      "ws_uri": "KMS_WEBSOCKET_URI",
      "wss_uri": "KMS_SECURE_WEBSOCKET_URI",
      "upload_speed_tester_uri": "UPLOAD_SPEED_TESTER_URI"
  }
  ```

8. Add these permissions platforms/android/AndroidManifest.xml

  ```xml
  <uses-permission android:name="android.permission.INTERNET" />
  <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
  <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
  <uses-permission android:name="android.permission.RECORD_AUDIO" />
  <uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />
  <uses-permission android:name="android.permission.RECORD_VIDEO" />
  <uses-permission android:name="android.permission.CAMERA" />
  ```

9. Add Crosswalk plugin

  ```bash
  $ cordova plugin add cordova-plugin-crosswalk-webview
  ```

10. Finally, build the application

  ```bash
  $ cordova build android
  ```
