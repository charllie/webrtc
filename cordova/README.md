WebRTC Conferencing Project
=========================

Android
-----------

1. Be sure to have Apache Cordova with Android SDK installed on your computer.

2. Then, create a new project

```bash
$ cordova create webrtc cz.cvut.fel.webrtc WebRTC
```

3. Go to the project folder

```bash
$ cd webrtc
```

4. Copy *angular-webpage* project files in *www* folder

5. Add Android platform

```bash
$ cordova platform add android
```

6. Edit *www/index.html* and add these lines under section balise

```html
<script src="cordova.js"></script>
<script>window.chrome = true;</script>
```

7. Add these permissions platforms/android/AndroidManifest.xml

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.VIBRATE" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.BROADCAST_STICKY" />
<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />
<uses-permission android:name="android.permission.RECORD_VIDEO" />
<uses-permission android:name="android.permission.CAMERA" />
```

8. Add Crosswalk plugin

```bash
$ cordova plugin add cordova-plugin-crosswalk-webview
```

9. Finally, build the application

```bash
$ cordova build android
```