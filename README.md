Cordova Cloud Settings plugin [![Latest Stable Version](https://img.shields.io/npm/v/cordova-plugin-cloud-settings.svg)](https://www.npmjs.com/package/cordova-plugin-cloud-settings)
=====================================================================================================================

A Cordova plugin for Android & iOS to persist user settings in cloud storage across devices and installs.

<!-- doctoc README.md --maxlevel=2 -->
<!-- START doctoc generated TOC please keep comment here to allow auto update -->
<!-- DON'T EDIT THIS SECTION, INSTEAD RE-RUN doctoc TO UPDATE -->
**Table of Contents**

- [Summary](#summary)
  - [Android](#android)
  - [iOS](#ios)
- [Installation](#installation)
  - [Install the plugin](#install-the-plugin)
- [Usage lifecycle](#usage-lifecycle)
- [API](#api)
  - [`exists()`](#exists)
  - [`save()`](#save)
  - [`load()`](#load)
  - [`onRestore()`](#onrestore)
  - [`enableDebug()`](#enabledebug)
- [Testing](#testing)
  - [Testing Android](#testing-android)
  - [Testing iOS](#testing-ios)
- [Example project](#example-project)
- [Use in GDPR-compliant analytics](#use-in-gdpr-compliant-analytics)
  - [GDPR background](#gdpr-background)
  - [Impact on user tracking in analytics](#impact-on-user-tracking-in-analytics)
  - [Benefits of using this plugin](#benefits-of-using-this-plugin)
- [Authors](#authors)
- [Licence](#licence)

<!-- END doctoc generated TOC please keep comment here to allow auto update -->

# Summary
This plugin provides a mechanism to store key/value app settings in the form of a JSON structure which will persist in cloud storage so if the user re-installs the app or installs it on a different device, the settings will be restored and available in the new installation.

Key features:
- Settings are stored using the free native cloud storage solution provided by the platform.
    - Out-of-the-box cloud storage with no additional SDKs required.
- No user authentication is required so you can read and store settings immediately without asking user to log in.
    - This makes the plugin useful for [GDPR-compliant cross-installation user tracking](#use-in-gdpr-compliant-analytics).
- So stored settings are immediately available to new installs on app startup (no user log in required).


Note:
- Settings **cannot** be shared between Android and iOS installations.

## Android
The plugin uses [Android's Data Backup service](http://developer.android.com/guide/topics/data/backup.html) to store settings in a file.
- Supports Android 6.0+ (API level 23) and above

## iOS

The plugin uses [iCloud](https://support.apple.com/en-gb/HT207428) to store the settings, specifically the [NSUbiquitousKeyValueStore class](https://developer.apple.com/documentation/foundation/nsubiquitouskeyvaluestore) to store settings in a native K/V store

Note:
 - Supports iOS v5.0 and above
 - The amount of storage space available is 1MB per user per app.
 - You need to enable iCloud for your App Identifier in the [Apple Member Centre](https://developer.apple.com/membercenter/index.action).


# Installation

The plugin is published to npm as [cordova-plugin-cloud-settings](https://www.npmjs.org/package/cordova-plugin-cloud-settings).

## Install the plugin

```sh
cordova plugin add cordova-plugin-cloud-settings
```

# Usage lifecycle

A typical lifecycle is as follows:
 - User installs your app for the first time
 - App starts, calls `exists()`, sees it has no existing settings
 - Users uses your app, generates settings: app calls `save()` to backup settings to cloud
 - Further use, further backups...
 - User downloads your app onto a new device
 - App starts, calls `exists()`, sees it has existing settings
 - App calls `load()` to access existing settings
 - User continues where they left off

# API

The plugin's JS API is under the global namespace `cordova.plugin.cloudsettings`.

## `exists()`

`cordova.plugin.cloudsettings.exists(successCallback);`

Indicates if any stored cloud settings currently exist for the current user.

### Parameters
- {function} successCallback - callback function to invoke with the result.
Will be passed a boolean flag which indicates whether an store settings exist for the user.

```javascript
cordova.plugin.cloudsettings.exists(function(exists){
    if(exists){
        console.log("Saved settings exist for the current user");
    }else{
        console.log("Saved settings do not exist for the current user");
    }
});
```

## `save()`

`cordova.plugin.cloudsettings.save(settings, [successCallback], [errorCallback], [overwrite]);`

Saves the settings to cloud backup.

Notes:
- you can pass a deep JSON structure and the plugin will store/merge it with an existing one
    - but make sure the structure will [stringify](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/JSON/stringify) (i.e. no functions, circular references, etc.)
- the cloud backup may not happen immediately on Android or iOS but will occur when the next scheduled backup takes place.

### Parameters
- {object} settings - a JSON structure representing the user settings to save to cloud backup.
- {function} successCallback - (optional) callback function to invoke on successfuly saving settings and scheduling for backup.
Will be passed a single object argument which contains the saved settings as a JSON object.
- {function} errorCallback - (optional) callback function to invoke on failure to save settings or schedule for backup.
Will be passed a single string argument which contains a description of the error.
- {boolean} overwrite - (optional) if true, existing settings will be replaced rather than updated. Defaults to false.
    - If false, existing settings will be merged with the new settings passed to this function.

```javascript
var settings = {
  user: {
    id: 1678,
    name: 'Fred',
    preferences: {
      mute: true,
      locale: 'en_GB'
    }
  }
}

cordova.plugin.cloudsettings.save(settings, function(savedSettings){
    console.log("Settings successfully saved at " + (new Date(savedSettings.timestamp)).toISOString());
}, function(error){
    console.error("Failed to save settings: " + error);
}, false);
```

## `load()`

`cordova.plugin.cloudsettings.load(successCallback, [errorCallback]);`

Loads the current settings.

Note: the settings are loaded locally off disk rather than directly from cloud backup so are not guaranteed to be the latest settings in cloud backup.
If you require conflict resolution of local vs cloud settings, you should save a timestamp when loading/saving your settings and use this to resolve any conflicts.

### Parameters
- {function} successCallback - (optional) callback function to invoke on successfuly loading settings.
Will be passed a single object argument which contains the current settings as a JSON object.
- {function} errorCallback - (optional) callback function to invoke on failure to load settings.
Will be passed a single string argument which contains a description of the error.

```javascript
cordova.plugin.cloudsettings.load(function(settings){
    console.log("Successfully loaded settings: " + console.log(JSON.stringify(settings)));
}, function(error){
    console.error("Failed to load settings: " + error);
});
```

## `onRestore()`

`cordova.plugin.cloudsettings.onRestore(successCallback);`

Registers a function which will be called if/when settings on the device have been updated from the cloud.

The purpose of this is to notify your app if current settings have changed due to being updated from the cloud while your app is running.
This may occur, for example, if the user has two devices with your app installed; changing settings on one device will cause them to be synched to the other device.

When you call `save()`, a `timestamp` key is added to the stored settings object which indicates when the settings were saved. 
If necessary, this can be used for conflict resolution.

### Parameters
- {function} successCallback - callback function to invoke when device settings have been updated from the cloud.

```javascript
cordova.plugin.cloudsettings.onRestore(function(){
    console.log("Settings have been updated from the cloud");
});
```

## `enableDebug()`

`cordova.plugin.cloudsettings.enableDebug(successCallback);`

Outputs verbose log messages from the native plugin components to the JS console.

### Parameters
- {function} successCallback - callback function to invoke when debug mode has been enabled

```javascript
cordova.plugin.cloudsettings.enableDebug(function(){
    console.log("Debug mode enabled");
});
```

# Testing

## Testing Android

### Test backup
To test backup of settings you need to manually invoke the backup manager (as instructed in [the Android documentation](https://developer.android.com/guide/topics/data/testingbackup)) to force backing up of the updated values:

First make sure the backup manager is enabled and setup for verbose logging:

```bash
    $ adb shell bmgr enabled
    $ adb shell setprop log.tag.GmsBackupTransport VERBOSE
    $ adb shell setprop log.tag.BackupXmlParserLogging VERBOSE
```

The method of testing the backup then depends on the version of Android running of the target device:

#### Android 7 and above

Run the following command to perform a backup:

```bash
    $ adb shell bmgr backupnow <APP_PACKAGE_ID>
```

#### Android 6

* Run the following command:
```bash
    $ adb shell bmgr backup @pm@ && adb shell bmgr run
```

* Wait until the command in the previous step finishes by monitoring `adb logcat` for the following output:
```
    I/BackupManagerService: Backup pass finished.
```

* Run the following command to perform a backup:
```bash
    $ adb shell bmgr fullbackup <APP_PACKAGE_ID>
```

### Test restore

To manually initiate a restore, run the following command:
```bash
    $ adb shell bmgr restore <TOKEN> <APP_PACKAGE_ID>
```

* To look up backup tokens run `adb shell dumpsys backup`.
* The token is the hexidecimal string following the labels `Ancestral:` and `Current:`
    * The ancestral token refers to the backup dataset that was used to restore the device when it was initially setup (with the device-setup wizard).
    * The current token refers to the device's current backup dataset (the dataset that the device is currently sending its backup data to).
* You can use a regex to filter the output for just your app ID, for example if your app package ID is `io.cordova.plugin.cloudsettings.test`:
```bash
    $ adb shell dumpsys backup | grep -P '^\S+\: | \: io\.cordova\.plugin\.cloudsettings\.test'
```

You also can test automatic restore for your app by uninstalling and reinstalling your app either with adb or through the Google Play Store app.

### Wipe backup data
To wipe the backup data for your app:

```bash
    $ adb shell bmgr list transports
    # note the one with an * next to it, it is the transport protocol for your backup
    $ adb shell bmgr wipe [* transport-protocol] <APP_PACKAGE_ID>
```

## Testing iOS

### Test backup

iCloud backups happen periodically, hence saving data via the plugin will write it to disk, but it may not be synced to iCloud immediately.

To force an iCloud backup immediately (on iOS 11):

- Open the Settings app
- Select: Accounts & Passwords > iCloud > iCloud Backup > Back Up Now

### Test restore

To test restoring, uninstall then re-install the app to sync the data back from iCloud.

You can also install the app on 2 iOS devices. Saving data on one should trigger an call to the `onRestore()` callback on the other.

# Example project

An example project illustrating/validating use of this plugin can be found here: [https://github.com/dpa99c/cordova-plugin-cloud-settings-test](https://github.com/dpa99c/cordova-plugin-cloud-settings-test)

# Use in GDPR-compliant analytics

## GDPR background
- The EU's [General Data Protection Regulation (GDPR)](https://www.eugdpr.org/) is effective from 25 May 2018.
- It introduces strict controls regarding the use of personal data by apps and websites.
- GDPR distinguishes 3 types of data and associated obligations ([reference](https://iapp.org/media/pdf/resource_center/PA_WP2-Anonymous-pseudonymous-comparison.pdf)):
    - Personally Identifiable Information (PII)
        - directly identifies an individual
        - e.g. unencrypted device ID
        - fully obligated under GDPR
    - Pseudonmyized data
        - indirectly identifies an individual
        - e.g. an encypted [one-way hash](https://support.google.com/analytics/answer/6366371?hl=en&ref_topic=2919631) of customer ID
        - partially obligated under GDPR
    - Anonymous data
        - no direct connection to an individual
        - e.g. an randomly-generated GUID
        - no obligation under GDPR


## Impact on user tracking in analytics
- It can be [useful to track a user ID across app installs](https://support.google.com/analytics/answer/3123663)
- However, GDPR applies to any personal information sent to analytics platforms such as Google Analytics or Firebase.
- If you use PII or pseudonmyized data (such as for the user ID), you are obligated by GDPR to provide the user a mechanism to:
    - opt-in to analytics before sending any data (implicit consent is no longer acceptable under GDPR)
    - opt-out at a later date if they opt-in (e.g. via an app setting)
    - request retrieval or removal their analytics data

## Benefits of using this plugin
- This plugin can be used to stored a randomly-generated user ID which persists across app installs and devices.
- Passing this to an analytics service means that user can be (anonymously) tracked across app installs and devices.
- Because the GUID is random and has no association with the user's personal identity, it is classed as **anonymous data** under GDPR and therefore is not obligated.
- This means you don't have to offer opt-in/opt-out and are not obliged to provide retrieval or removal of their analytics data.
    - This of course only applies if you aren't sending any other PII or pseudonymous data to analytics.

# Authors

- [Dave Alden](https://github.com/dpa99c)

Major code contributors:
- [Daniel Jackson](https://github.com/cloakedninjas)
- [Alex Drel](https://github.com/alexdrel)

Based on the plugins:
- https://github.com/cloakedninjas/cordova-plugin-backup
- https://github.com/alexdrel/cordova-plugin-icloudkv
- https://github.com/jcesarmobile/FilePicker-Phonegap-iOS-Plugin

# Licence

The MIT License

Copyright (c) 2018-21, Dave Alden (Working Edge Ltd.)

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
