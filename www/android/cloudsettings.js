var onRestoreFn = function(){};

var merge = function () {
    var destination = {},
        sources = [].slice.call(arguments, 0);
    sources.forEach(function (source) {
        var prop;
        for (prop in source) {
            if (prop in destination && Array.isArray(destination[prop])) {

                // Concat Arrays
                destination[prop] = destination[prop].concat(source[prop]);

            } else if (prop in destination && typeof destination[prop] === "object") {

                // Merge Objects
                destination[prop] = merge(destination[prop], source[prop]);

            } else {

                // Set new values
                destination[prop] = source[prop];

            }
        }
    });
    return destination;
};

var cloudsettings = {};

cloudsettings.checkAuth = function() {
    return new Promise(function(resolve, reject) {
        cordova.exec(function(resultJson) {
            try { resolve(JSON.parse(resultJson)); }
            catch(e) { reject("Error parsing checkAuth result: " + e.message); }
        }, reject, 'CloudSettingsPlugin', 'checkAuth', []);
    });
};

cloudsettings.connect = function() {
    return new Promise(function(resolve, reject) {
        cordova.exec(function(resultJson) {
            try { resolve(JSON.parse(resultJson)); }
            catch(e) { reject("Error parsing connect result: " + e.message); }
        }, reject, 'CloudSettingsPlugin', 'connect', []);
    });
};

cloudsettings.load = function() {
    return new Promise(function(resolve, reject) {
        cordova.exec(function(sData) {
            try { resolve(JSON.parse(sData)); }
            catch(e) { reject("Error parsing stored settings: " + e.message); }
        }, reject, 'CloudSettingsPlugin', 'load', []);
    });
};

cloudsettings.save = function(settings, overwrite) {
    if(typeof settings !== "object" || typeof settings.length !== "undefined")
        throw "settings must be a key/value object!";

    var doSave = function() {
        settings.timestamp = (new Date()).valueOf();
        var data = JSON.stringify(settings);
        return new Promise(function(resolve, reject) {
            cordova.exec(function() { resolve(settings); }, reject,
                'CloudSettingsPlugin', 'save', [data]);
        });
    };

    if(overwrite) {
        return doSave();
    } else {
        return cloudsettings.exists().then(function(exists) {
            if(exists) {
                return cloudsettings.load().then(function(stored) {
                    settings = merge(stored, settings);
                    return doSave();
                });
            } else {
                return doSave();
            }
        });
    }
};

cloudsettings.exists = function() {
    return new Promise(function(resolve, reject) {
        cordova.exec(function(result) { resolve(!!result); }, reject,
            'CloudSettingsPlugin', 'exists', []);
    });
};

cloudsettings.hasLocalFile = function() {
    return new Promise(function(resolve, reject) {
        cordova.exec(function(result) { resolve(!!result); }, reject,
            'CloudSettingsPlugin', 'hasLocalFile', []);
    });
};

cloudsettings.deleteLocalFile = function() {
    return new Promise(function(resolve, reject) {
        cordova.exec(resolve, reject, 'CloudSettingsPlugin', 'deleteLocalFile', []);
    });
};

// Test-only: revokes Google Drive OAuth grant so consent bottom sheet reappears
cloudsettings.revokeAuth = function() {
    return new Promise(function(resolve, reject) {
        cordova.exec(resolve, reject, 'CloudSettingsPlugin', 'revokeAuth', []);
    });
};

cloudsettings.onRestore = function(fn) { onRestoreFn = fn; };
cloudsettings._onRestore = function() { onRestoreFn(); };

module.exports = cloudsettings;
