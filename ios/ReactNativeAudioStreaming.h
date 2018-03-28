// AudioManager.h
// From https://github.com/jhabdas/lumpen-radio/blob/master/iOS/Classes/AudioManager.h

#if __has_include(<React/RCTBridgeModule.h>)
#import <React/RCTBridgeModule.h>
#else
#import "RCTBridgeModule.h"
#endif

//TODO: use new events https://facebook.github.io/react-native/docs/native-modules-ios.html#sending-events-to-javascript

@import AVFoundation;

@interface ReactNativeAudioStreaming : NSObject <RCTBridgeModule>

@property (nonatomic, strong) AVPlayer* audioPlayer;
@property (nonatomic, strong) AVPlayerItem* playerItem;
@property (nonatomic, readwrite) BOOL isPlayingWithOthers;
@property (nonatomic, readwrite) BOOL showNowPlayingInfo;
@property (nonatomic, readwrite) NSString *lastUrlString;
@property (nonatomic, readwrite) NSDictionary *lastOptions;
@property (nonatomic, retain) NSString *currentSong;

@end
