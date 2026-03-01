//
//  CloudSettingsPlugin.m
//  Cordova Cloud Settings
//  Copyright (c) by Dave Alden 2018

#import "CloudSettingsPlugin.h"

@interface CloudSettingsPlugin (private)
- (void) cloudNotification:(NSNotification *)receivedNotification;
- (void) sendPluginResult: (CDVPluginResult*)result :(CDVInvokedUrlCommand*)command;
- (void) sendPluginResultBool: (BOOL)result :(CDVInvokedUrlCommand*)command;
- (void) sendPluginResultString: (NSString*)result :(CDVInvokedUrlCommand*)command;
- (void) sendPluginSuccess: (CDVInvokedUrlCommand*)command;
- (void) sendPluginError: (NSString*) errorMessage :(CDVInvokedUrlCommand*)command;
- (void) handlePluginException: (NSException*) exception :(CDVInvokedUrlCommand*)command;
- (void)executeGlobalJavascript: (NSString*)jsString;
- (NSString*) arrayToJsonString:(NSArray*)inputArray;
- (NSString*) objectToJsonString:(NSDictionary*)inputObject;
- (NSArray*) jsonStringToArray:(NSString*)jsonStr;
- (NSDictionary*) jsonStringToDictionary:(NSString*)jsonStr;
- (bool)isNull: (NSString*)str;
- (void)jsCallback: (NSString*)name;
- (void)jsCallbackWithArguments: (NSString*)name : (NSString*)arguments;
- (void)d: (NSString*)msg;
- (void)i: (NSString*)msg;
- (void)w: (NSString*)msg;
- (void)e: (NSString*)msg;
- (NSString*)escapeDoubleQuotes: (NSString*)str;
@end

@implementation CloudSettingsPlugin

static NSString*const LOG_TAG = @"CloudSettingsPlugin[native]";
static NSString*const KEY = @"settings";

static NSString*const javascriptNamespace = @"cordova.plugin.cloudsettings";

/********************************/
#pragma mark - Plugin API
/********************************/

-(void)enableDebug:(CDVInvokedUrlCommand*)command{
    self.debugEnabled = true;
    [self d:@"Debug enabled"];
    [self sendPluginSuccess:command];
}

-(void)save:(CDVInvokedUrlCommand *)command
{
    [self.commandDelegate runInBackground:^{
        @try {
            NSString* sNewData = [command.arguments objectAtIndex:0];

            // Check iCloud availability before attempting to write
            id ubiquityToken = [[NSFileManager defaultManager] ubiquityIdentityToken];
            if (ubiquityToken == nil) {
                [self sendPluginError:@"synchronize failed: iCloud not available (user may not be signed in)" :command];
                return;
            }

            // Check data size against the 1MB per-key iCloud KV Store limit
            NSUInteger dataSize = [sNewData lengthOfBytesUsingEncoding:NSUTF8StringEncoding];
            if (dataSize > 1024 * 1024) {
                [self sendPluginError:[NSString stringWithFormat:@"synchronize failed: data too large (%lu bytes, max 1048576)", (unsigned long)dataSize] :command];
                return;
            }

            // Store new settings values
            [[NSUbiquitousKeyValueStore defaultStore] setString:sNewData forKey:KEY];

            // sync memory values to disk (in preparation for next iCloud sync)
            BOOL success = [[NSUbiquitousKeyValueStore defaultStore] synchronize];
            if (success){
                [self sendPluginSuccess:command];
            }else{
                [self sendPluginError:[NSString stringWithFormat:@"synchronize failed: NSUbiquitousKeyValueStore returned NO (data size: %lu bytes)", (unsigned long)dataSize] :command];
            }
        }@catch (NSException *exception) {
            [self handlePluginException:exception :command];
        }
    }];
}


-(void)load:(CDVInvokedUrlCommand *)command  
{
    [self.commandDelegate runInBackground:^{
        @try {
            NSString* sStoredData = [[NSUbiquitousKeyValueStore defaultStore] stringForKey:KEY];
            [self sendPluginResultString:sStoredData :command];
        }@catch (NSException *exception) {
            [self handlePluginException:exception :command];
        }
    }];
}

-(void)exists:(CDVInvokedUrlCommand *)command
{
    [self.commandDelegate runInBackground:^{
         @try {
            NSString* sStoredData = [[NSUbiquitousKeyValueStore defaultStore] stringForKey:KEY];
             if(sStoredData != nil){
                [self sendPluginResultBool:TRUE :command];
             }else{
                [self sendPluginResultBool:FALSE :command];
             }
        }@catch (NSException *exception) {
            [self handlePluginException:exception :command];
        }
    }];
}

- (void)cloudNotification:(NSNotification *)receivedNotification
{
    @try {
        int cause=[[[receivedNotification userInfo] valueForKey:NSUbiquitousKeyValueStoreChangeReasonKey] intValue];
        NSString* msg = @"unknown notification";
        switch(cause) {
            case NSUbiquitousKeyValueStoreQuotaViolationChange:
                msg = @"storage quota exceeded";
                [self e:msg];
                break;
            case NSUbiquitousKeyValueStoreInitialSyncChange:
                msg = @"initial sync notification";
                [self d:msg];
                break;
            case NSUbiquitousKeyValueStoreServerChange:
                msg = @"change sync notification";
                [self d:msg];
                break;
        }
        [self d:[NSString stringWithFormat:@"iCloud notification received: %@", msg]];
        [self jsCallbackWithArguments:@"'_onRestore'" :[NSString stringWithFormat:@"'%@'", msg]];
    }@catch (NSException *exception) {
        [self e:exception.reason];
    }
}

/********************************/
#pragma mark - Internal functions
/********************************/

- (void)pluginInitialize {
    @try {
        [super pluginInitialize];
        self.debugEnabled = false;
        [[NSNotificationCenter defaultCenter] addObserver:self 
            selector:@selector(cloudNotification:)
            name:NSUbiquitousKeyValueStoreDidChangeExternallyNotification 
            object:[NSUbiquitousKeyValueStore defaultStore]];
    }@catch (NSException *exception) {
        [self e:exception.reason];
    }
}

/********************************/
#pragma mark - Send results
/********************************/

- (void) sendPluginResult: (CDVPluginResult*)result :(CDVInvokedUrlCommand*)command
{
    [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
}

- (void) sendPluginResultBool: (BOOL)result :(CDVInvokedUrlCommand*)command
{
    CDVPluginResult* pluginResult;
    if(result) {
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsInt:1];
    } else {
        pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsInt:0];
    }
    [self sendPluginResult:pluginResult :command];
}

- (void) sendPluginResultString: (NSString*)result :(CDVInvokedUrlCommand*)command
{
    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:result];
    [self sendPluginResult:pluginResult :command];
}

- (void) sendPluginSuccess: (CDVInvokedUrlCommand*)command
{
    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
    [self sendPluginResult:pluginResult :command];
}

- (void) sendPluginError: (NSString*) errorMessage :(CDVInvokedUrlCommand*)command
{
    [self e:errorMessage];
    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:errorMessage];
    [self sendPluginResult:pluginResult :command];
}

- (void) handlePluginException: (NSException*) exception :(CDVInvokedUrlCommand*)command
{
    [self e:[NSString stringWithFormat:@"EXCEPTION: %@", exception.reason]];
    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:exception.reason];
    [self sendPluginResult:pluginResult :command];
}

- (void)executeGlobalJavascript: (NSString*)jsString
{
    [self.commandDelegate evalJs:jsString];
}

- (NSString*) arrayToJsonString:(NSArray*)inputArray
{
    NSError* error;
    NSData* jsonData = [NSJSONSerialization dataWithJSONObject:inputArray options:NSJSONWritingPrettyPrinted error:&error];
    NSString* jsonString = [[NSString alloc] initWithData:jsonData encoding:NSUTF8StringEncoding];
    return jsonString;
}

- (NSString*) objectToJsonString:(NSDictionary*)inputObject
{
    NSError* error;
    NSData* jsonData = [NSJSONSerialization dataWithJSONObject:inputObject options:NSJSONWritingPrettyPrinted error:&error];
    NSString* jsonString = [[NSString alloc] initWithData:jsonData encoding:NSUTF8StringEncoding];
    return jsonString;
}

- (NSArray*) jsonStringToArray:(NSString*)jsonStr
{
    NSError* error = nil;
    NSArray* array = [NSJSONSerialization JSONObjectWithData:[jsonStr dataUsingEncoding:NSUTF8StringEncoding] options:kNilOptions error:&error];
    if (error != nil){
        array = nil;
    }
    return array;
}

- (NSDictionary*) jsonStringToDictionary:(NSString*)jsonStr
{
    return (NSDictionary*) [self jsonStringToArray:jsonStr];
}

- (bool)isNull: (NSString*)str
{
    return str == nil || str == (id)[NSNull null] || str.length == 0 || [str isEqual: @"<null>"];
}

- (void)jsCallback: (NSString*)name
{
    NSString* jsString = [NSString stringWithFormat:@"%@[%@]", javascriptNamespace, name];
    [self executeGlobalJavascript:jsString];
}

- (void)jsCallbackWithArguments: (NSString*)name : (NSString*)arguments
{
    NSString* jsString = [NSString stringWithFormat:@"%@[%@](%@)", javascriptNamespace, name, [self escapeDoubleQuotes:arguments]];
    [self executeGlobalJavascript:jsString];
}

/********************************/
#pragma mark - utility functions
/********************************/

- (void)d: (NSString*)msg
{
    if(self.debugEnabled){
        NSLog(@"%@ DEBUG: %@", LOG_TAG, msg);
        NSString* jsString = [NSString stringWithFormat:@"console.log(\"%@: %@\")", LOG_TAG, [self escapeDoubleQuotes:msg]];
        [self executeGlobalJavascript:jsString];
    }
}

- (void)i: (NSString*)msg
{
    if(self.debugEnabled){
        NSLog(@"%@ INFO: %@", LOG_TAG, msg);
        NSString* jsString = [NSString stringWithFormat:@"console.info(\"%@: %@\")", LOG_TAG, [self escapeDoubleQuotes:msg]];
        [self executeGlobalJavascript:jsString];
    }
}

- (void)w: (NSString*)msg
{
    if(self.debugEnabled){
        NSLog(@"%@ WARN: %@", LOG_TAG, msg);
        NSString* jsString = [NSString stringWithFormat:@"console.warn(\"%@: %@\")", LOG_TAG, [self escapeDoubleQuotes:msg]];
        [self executeGlobalJavascript:jsString];
    }
}

- (void)e: (NSString*)msg
{
    NSLog(@"%@ ERROR: %@", LOG_TAG, msg);
    if(self.debugEnabled){
        NSString* jsString = [NSString stringWithFormat:@"console.error(\"%@: %@\")", LOG_TAG, [self escapeDoubleQuotes:msg]];
        [self executeGlobalJavascript:jsString];
    }
}

- (NSString*)escapeDoubleQuotes: (NSString*)str
{
    NSString *result =[str stringByReplacingOccurrencesOfString: @"\"" withString: @"\\\""];
    return result;
}

@end
