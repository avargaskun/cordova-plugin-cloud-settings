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

// iOS has no Drive auth — always authorized (iCloud is device-level)
cloudsettings.checkAuth = function() {
    return Promise.resolve({ authorized: true });
};

cloudsettings.connect = function() {
    return Promise.resolve({});
};

cloudsettings.hasLocalFile = function() {
    return Promise.resolve(false);
};

cloudsettings.deleteLocalFile = function() {
    return Promise.resolve();
};

cloudsettings.revokeAuth = function() {
    return Promise.resolve();
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

cloudsettings.onRestore = function(fn) { onRestoreFn = fn; };
cloudsettings._onRestore = function() { onRestoreFn(); };

module.exports = cloudsettings;
