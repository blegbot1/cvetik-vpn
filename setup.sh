#!/bin/bash
set -e
npm install @capacitor/android --save-dev
npx cap add android
mkdir -p android/app/src/main/java/com/cvetik/vpn
cp ~/plugin_backup/* android/app/src/main/java/com/cvetik/vpn/ 2>/dev/null || true
cat > android/app/src/main/AndroidManifest.xml << 'MANIFEST'
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.cvetik.vpn">
<uses-permission android:name="android.permission.INTERNET"/>
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE"/>
<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
<application android:allowBackup="true" android:icon="@mipmap/ic_launcher" android:label="@string/app_name" android:roundIcon="@mipmap/ic_launcher_round" android:supportsRtl="true" android:theme="@style/AppTheme">
<activity android:configChanges="orientation|keyboardHidden|keyboard|screenSize|locale|smallestScreenSize|screenLayout|uiMode" android:name="com.cvetik.vpn.MainActivity" android:label="@string/title_activity_main" android:theme="@style/AppTheme.NoActionBarLaunch" android:launchMode="singleTask" android:exported="true">
<intent-filter><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LAUNCHER"/></intent-filter>
</activity>
<provider android:name="androidx.core.content.FileProvider" android:authorities="${applicationId}.fileprovider" android:exported="false" android:grantUriPermissions="true">
<meta-data android:name="android.support.FILE_PROVIDER_PATHS" android:resource="@xml/file_paths"/>
</provider>
<service android:name="com.cvetik.vpn.CvetikVpnService" android:permission="android.permission.BIND_VPN_SERVICE" android:exported="true" android:foregroundServiceType="specialUse">
<intent-filter><action android:name="android.net.VpnService"/></intent-filter>
</service>
</application>
</manifest>
MANIFEST
sed -i 's/dependencies {/dependencies {\n    implementation "androidx.core:core-ktx:1.12.0"/' android/app/build.gradle
cat > android/app/src/main/java/com/cvetik/vpn/MainActivity.java << 'MAINACT'
package com.cvetik.vpn;
import android.os.Bundle;
import com.getcapacitor.BridgeActivity;
public class MainActivity extends BridgeActivity {
@Override public void onCreate(Bundle savedInstanceState){registerPlugin(CvetikVpnPlugin.class);super.onCreate(savedInstanceState);}
}
MAINACT
npx cap sync android
git add .
git commit -m "add android platform + real vpn plugin"
git push origin main
echo "✅ ГОТОВО! Иди на github.com/blegbot1/cvetik-vpn/actions"
