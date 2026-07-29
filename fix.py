import re

# === Fix AndroidManifest.xml ===
with open('android/app/src/main/AndroidManifest.xml', 'r') as f:
    content = f.read()

# Add permissions
perms = '''    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
'''
if 'FOREGROUND_SERVICE' not in content:
    content = content.replace('<application', perms + '    <application', 1)

# Add VPN service
service = '''        <service
            android:name="com.cvetik.vpn.CvetikVpnService"
            android:permission="android.permission.BIND_VPN_SERVICE"
            android:exported="true"
            android:foregroundServiceType="specialUse">
            <intent-filter>
                <action android:name="android.net.VpnService" />
            </intent-filter>
        </service>
'''
if 'CvetikVpnService' not in content:
    content = content.replace('    </application>', service + '    </application>', 1)

with open('android/app/src/main/AndroidManifest.xml', 'w') as f:
    f.write(content)

# === Fix build.gradle ===
with open('android/app/build.gradle', 'r') as f:
    content = f.read()

if 'core-ktx' not in content:
    content = content.replace('dependencies {', 'dependencies {\n    implementation "androidx.core:core-ktx:1.12.0"')

with open('android/app/build.gradle', 'w') as f:
    f.write(content)

print('✅ AndroidManifest.xml и build.gradle пофикшены!')
