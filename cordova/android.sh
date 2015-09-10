#!/bin/sh

cordova create src cz.cvut.fel.webrtc WebRTC
cd src
rm -rf www
mkdir www
cp -a ../../angular-webpage/ www/
cordova platform add android
sed "s/<\/section>/<\/section><script src=\"cordova.js\"><\/script><script>window.chrome = true;<\/script>/g" www/index.html  > www/index-cordova.html
rm www/index.html
mv www/index-cordova.html www/index.html
cd www
bower install
cd ..
cordova plugin add cordova-plugin-crosswalk-webview
cd platforms/android/
sed "s/ACCESS_WIFI_STATE\" \/>/ACCESS_WIFI_STATE\" \/><uses-permission android:name=\"android.permission.RECORD_AUDIO\" \/><uses-permission android:name=\"android.permission.MODIFY_AUDIO_SETTINGS\" \/><uses-permission android:name=\"android.permission.RECORD_VIDEO\" \/><uses-permission android:name=\"android.permission.CAMERA\" \/>/g" AndroidManifest.xml  > AndroidManifest-cordova.xml
rm AndroidManifest.xml
mv AndroidManifest-cordova.xml AndroidManifest.xml
cd ../../
cordova build android
mv platforms/android/build/outputs/apk/android-x86-debug.apk ../webrtc-x86.apk
mv platforms/android/build/outputs/apk/android-armv7-debug.apk ../webrtc-armv7.apk
cd ..
rm -Rf src
