#if __has_include(<React/RCTBridgeModule.h>)
#import <React/RCTBridgeModule.h>
#else
#import "RCTBridgeModule.h"
#endif
#import "RCTEventDispatcher.h"

#import "ReactNativeAudioStreaming.h"

@import AVFoundation;
@import MediaPlayer;

@implementation ReactNativeAudioStreaming

@synthesize bridge = _bridge;

RCT_EXPORT_MODULE()


- (dispatch_queue_t)methodQueue
{
   return dispatch_get_main_queue();
}

- (ReactNativeAudioStreaming *)init
{
   self = [super init];
   if (self) {
      self.lastUrlString = @"";
      self.lastOptions = [NSDictionary dictionary];
      NSLog(@"AudioPlayer initialized");
   }
   
   return self;
}


- (void)dealloc
{
   [self unregisterAudioInterruptionNotifications];
   [self unregisterRemoteControlEvents];
}


#pragma mark - Pubic API

RCT_EXPORT_METHOD(play:(NSString *) streamUrl options:(NSDictionary *)options) {
   
   if(![self activate]) return;
   
   @try {
      if(self.audioPlayer) {
         // remove the observer if the songplayer is allocated - to use pause don't re-instance the player
         [self.audioPlayer removeObserver:self forKeyPath:@"status"];
         [self.audioPlayer removeObserver:self forKeyPath:@"rate"];
      }
      
      if(self.playerItem) {
         [self.playerItem removeObserver:self forKeyPath:@"timedMetadata"];
         [self.playerItem removeObserver:self forKeyPath:@"loadedTimeRanges"];
      }
   } @catch (id Exception){
      NSLog(@"Some observers where already removed");
   }
   
   NSURL *streamingUrl = [NSURL URLWithString:streamUrl];
   AVAsset *asset = [AVAsset assetWithURL:streamingUrl];
   NSArray *assetKeys = @[@"playable", @"hasProtectedContent"];
   
   // Create a new AVPlayerItem with the asset and an
   // array of asset keys to be automatically loaded
   self.playerItem = [AVPlayerItem playerItemWithAsset:asset automaticallyLoadedAssetKeys:assetKeys];
   
   // Associate the player item with the player + emit buffering event
   [self.bridge.eventDispatcher sendDeviceEventWithName:@"AudioBridgeEvent" body:@{@"status": @"BUFFERING"}];
   self.audioPlayer = [AVPlayer playerWithPlayerItem:self.playerItem];
   
   // set observers
   [self.audioPlayer addObserver:self forKeyPath:@"status" options:0 context:nil];
   [self.audioPlayer addObserver:self forKeyPath:@"rate" options:0 context:nil];
   
   [self.playerItem addObserver:self forKeyPath:@"timedMetadata" options:NSKeyValueObservingOptionNew context:nil];
   [self.playerItem addObserver:self forKeyPath:@"loadedTimeRanges" options:NSKeyValueObservingOptionNew context:nil];
   
   
   self.lastUrlString = streamUrl;
   self.lastOptions = options;
   self.showNowPlayingInfo = [options objectForKey:@"showIniOSMediaCenter"] ? [[options objectForKey:@"showIniOSMediaCenter"] boolValue] : false;
   
   if (self.showNowPlayingInfo) {
      //unregister any existing registrations
      [self unregisterAudioInterruptionNotifications];
      [self unregisterRemoteControlEvents];
      //register
      [self registerAudioInterruptionNotifications];
      [self registerRemoteControlEvents];
   }
}


RCT_EXPORT_METHOD(updateTrackInfo:(NSDictionary *)options) {
   // set title, artist, album and artwork url if set
   NSString *trackTitle = @"";
   NSString *trackArtist = @"";
   NSString *trackAlbum = @"";
   NSString *artworkUrl = @"";
   BOOL isPlaying = true;
   if ([options objectForKey:@"isPlaying"]) isPlaying = [[options objectForKey:@"isPlaying"] boolValue];
   if ([options objectForKey:@"trackTitle"]) trackTitle = [options objectForKey:@"trackTitle"];
   if ([options objectForKey:@"trackArtist"]) trackArtist = [options objectForKey:@"trackArtist"];
   if ([options objectForKey:@"trackAlbum"]) trackAlbum = [options objectForKey:@"trackAlbum"];
   if ([options objectForKey:@"artworkUrl"]) artworkUrl = [options objectForKey:@"artworkUrl"];
   
   [self setNowPlayingInfo:isPlaying title:trackTitle artist:trackArtist album:trackAlbum artworkUrl:artworkUrl];
}


RCT_EXPORT_METHOD(stop) {
   if (self.audioPlayer) {
      [self.audioPlayer pause];
      [self deactivate];
   }
}

RCT_EXPORT_METHOD(getStatus: (RCTResponseSenderBlock) callback) {
   callback(@[[NSNull null], @{@"status": [self getCurrentStatus], @"url": self.lastUrlString}]);
}

#pragma mark - Utilities

- (NSString*) getCurrentStatus {
   NSString *status = @"STOPPED";
   if(self.audioPlayer && self.audioPlayer.rate != 0 && self.audioPlayer.error == nil) status = @"PLAYING";
   return status;
}

#pragma mark - AVPlayer Observers

- (void) observeValueForKeyPath:(NSString *)keyPath ofObject:(id)object change:(NSDictionary *)change context:(void *)context {
   
   if (object == self.audioPlayer && [keyPath isEqualToString:@"status"]) {
      if (self.audioPlayer.status == AVPlayerStatusFailed) {
         NSLog(@"AVPlayer Failed");
         [self.bridge.eventDispatcher sendDeviceEventWithName:@"AudioBridgeEvent" body:@{@"status": @"ERROR"}];
      }
      if (self.audioPlayer.status == AVPlayerStatusReadyToPlay) {
         NSLog(@"AVPlayerStatusReadyToPlay");
         [self.audioPlayer play];
      }
      if (self.audioPlayer.status == AVPlayerItemStatusUnknown) {
         NSLog(@"AVPlayer Unknown");
         [self.bridge.eventDispatcher sendDeviceEventWithName:@"AudioBridgeEvent" body:@{@"status": @"UNKNOWN"}];
      }
   }
   
   if (object == self.audioPlayer && [keyPath isEqualToString:@"rate"]) {
      if (![self.audioPlayer rate]) [self.bridge.eventDispatcher sendDeviceEventWithName:@"AudioBridgeEvent" body:@{ @"status": @"STOPPED" }];
   }
   
   if (object == self.playerItem && [keyPath isEqualToString:@"loadedTimeRanges"]) {
      NSLog(@"Ready and playing");
      [self.bridge.eventDispatcher sendDeviceEventWithName:@"AudioBridgeEvent" body:@{ @"status": @"STREAMING" }];
      [self.playerItem removeObserver:self forKeyPath:@"loadedTimeRanges"];
   }
   
   if (object == self.playerItem && [keyPath isEqualToString:@"timedMetadata"]) {
      AVPlayerItem* playerItem = object;
      
      for (AVMetadataItem* metadata in playerItem.timedMetadata) {
         if([metadata.commonKey isEqualToString:@"title"]){
            [self.bridge.eventDispatcher sendDeviceEventWithName:@"AudioBridgeEvent" body:@{
                                                                                            @"status": @"METADATA_UPDATED",
                                                                                            @"key": @"StreamTitle",
                                                                                            @"value": metadata.stringValue
                                                                                            }];
         }
      }
   }
   
}

#pragma mark - Audio Session

- (Boolean) activate {
   NSError *categoryError = nil;
   [[AVAudioSession sharedInstance] setActive:YES error:&categoryError];
   [[AVAudioSession sharedInstance] setCategory:AVAudioSessionCategoryPlayback error:&categoryError];
   
   if (categoryError) return false;
   return true;
}

- (Boolean) deactivate {
   NSError *categoryError = nil;
   [[AVAudioSession sharedInstance] setActive:NO error:&categoryError];
   
   if (categoryError) return false;
   return true;
}

#pragma mark - Audio Interruption notifications

- (void)registerAudioInterruptionNotifications
{
   // Register for audio interrupt notifications
   [[NSNotificationCenter defaultCenter] addObserver:self
                                            selector:@selector(onAudioInterruption:)
                                                name:AVAudioSessionInterruptionNotification
                                              object:nil];
   // Register for route change notifications
   [[NSNotificationCenter defaultCenter] addObserver:self
                                            selector:@selector(onRouteChangeInterruption:)
                                                name:AVAudioSessionRouteChangeNotification
                                              object:nil];
}

- (void)unregisterAudioInterruptionNotifications
{
   [[NSNotificationCenter defaultCenter] removeObserver:self
                                                   name:AVAudioSessionRouteChangeNotification
                                                 object:nil];
   [[NSNotificationCenter defaultCenter] removeObserver:self
                                                   name:AVAudioSessionInterruptionNotification
                                                 object:nil];
}

- (void)onAudioInterruption:(NSNotification *)notification {
   
   // Get the AVAudioSessionInterruptionTypeKey enum
   NSInteger interuptionType = [[notification.userInfo valueForKey:AVAudioSessionInterruptionTypeKey] integerValue];
   self.isPlayingWithOthers = [[AVAudioSession sharedInstance] isOtherAudioPlaying];
   // Decide what to do based on interruption type
   switch (interuptionType)
   {
      case AVAudioSessionInterruptionTypeBegan:
         NSLog(@"Audio Session Interruption case started.");
         [self stop];
         break;
         
      case AVAudioSessionInterruptionTypeEnded:
         NSLog(@"Audio Session Interruption case ended.");
         [self play:self.lastUrlString options:self.lastOptions];
         break;
         
      default:
         NSLog(@"Audio Session Interruption Notification case default.");
         break;
   }
}

- (void)onRouteChangeInterruption:(NSNotification *)notification
{
   
   NSDictionary *interruptionDict = notification.userInfo;
   NSInteger routeChangeReason = [[interruptionDict valueForKey:AVAudioSessionRouteChangeReasonKey] integerValue];
   
   switch (routeChangeReason)
   {
      case AVAudioSessionRouteChangeReasonUnknown:
         NSLog(@"routeChangeReason : AVAudioSessionRouteChangeReasonUnknown");
         break;
         
      case AVAudioSessionRouteChangeReasonNewDeviceAvailable:
         // A user action (such as plugging in a headset) has made a preferred audio route available.
         NSLog(@"routeChangeReason : AVAudioSessionRouteChangeReasonNewDeviceAvailable");
         break;
         
      case AVAudioSessionRouteChangeReasonOldDeviceUnavailable:
         // The previous audio output path is no longer available.
         [self stop];
         break;
         
      case AVAudioSessionRouteChangeReasonCategoryChange:
         // The category of the session object changed. Also used when the session is first activated.
         NSLog(@"routeChangeReason : AVAudioSessionRouteChangeReasonCategoryChange"); //AVAudioSessionRouteChangeReasonCategoryChange
         break;
         
      case AVAudioSessionRouteChangeReasonOverride:
         // The output route was overridden by the app.
         NSLog(@"routeChangeReason : AVAudioSessionRouteChangeReasonOverride");
         break;
         
      case AVAudioSessionRouteChangeReasonWakeFromSleep:
         // The route changed when the device woke up from sleep.
         NSLog(@"routeChangeReason : AVAudioSessionRouteChangeReasonWakeFromSleep");
         break;
         
      case AVAudioSessionRouteChangeReasonNoSuitableRouteForCategory:
         // The route changed because no suitable route is now available for the specified category.
         NSLog(@"routeChangeReason : AVAudioSessionRouteChangeReasonNoSuitableRouteForCategory");
         break;
   }
}

#pragma mark - Remote Control Events

- (void)registerRemoteControlEvents
{
   MPRemoteCommandCenter *commandCenter = [MPRemoteCommandCenter sharedCommandCenter];
   [commandCenter.playCommand addTarget:self action:@selector(didReceivePlayCommand:)];
   [commandCenter.pauseCommand addTarget:self action:@selector(didReceivePauseCommand:)];
   [commandCenter.togglePlayPauseCommand addTarget:self action:@selector(didReceiveToggleCommand:)];
   commandCenter.playCommand.enabled = YES;
   commandCenter.pauseCommand.enabled = YES;
   commandCenter.stopCommand.enabled = NO;
   commandCenter.nextTrackCommand.enabled = NO;
   commandCenter.previousTrackCommand.enabled = NO;
}

- (MPRemoteCommandHandlerStatus)didReceivePlayCommand:(MPRemoteCommand *)event
{
   NSLog(@"didReceivePlayCommand");
   [self play:self.lastUrlString options:self.lastOptions];
   return MPRemoteCommandHandlerStatusSuccess;
}

- (MPRemoteCommandHandlerStatus)didReceivePauseCommand:(MPRemoteCommand *)event
{
   NSLog(@"didReceivePauseCommand");
   [self stop];
   return MPRemoteCommandHandlerStatusSuccess;
}

- (MPRemoteCommandHandlerStatus)didReceiveToggleCommand:(MPRemoteCommand *)event
{
   NSLog(@"didReceiveToggleCommand");
   if ([[self getCurrentStatus] isEqualToString:@"STOPPED"]) [self play:self.lastUrlString options:self.lastOptions];
   else [self stop];
   return MPRemoteCommandHandlerStatusSuccess;
}

- (void)unregisterRemoteControlEvents
{
   MPRemoteCommandCenter *commandCenter = [MPRemoteCommandCenter sharedCommandCenter];
   [commandCenter.playCommand removeTarget:self];
   [commandCenter.pauseCommand removeTarget:self];
}

#pragma mark - Control Center Now playing infos

- (void) setNowPlayingInfo:(bool)isPlaying title:(NSString*)trackTitle artist:(NSString*)trackArtist album:(NSString*)trackAlbum artworkUrl:(NSString*)artworkUrl
{
   
   if (self.showNowPlayingInfo) {
      
      NSString* appName = [[[NSBundle mainBundle] infoDictionary] objectForKey:@"CFBundleDisplayName"];
      appName = appName ? appName : @"";
      
      NSMutableDictionary *nowPlayingInfo = [[NSMutableDictionary alloc] init];
      
      [nowPlayingInfo setObject:trackTitle forKey:MPMediaItemPropertyTitle];
      [nowPlayingInfo setObject:trackArtist forKey:MPMediaItemPropertyArtist];
      
      if([trackAlbum isEqualToString:@""]) [nowPlayingInfo setObject:appName forKey:MPMediaItemPropertyAlbumTitle];
      else [nowPlayingInfo setObject:trackAlbum forKey:MPMediaItemPropertyAlbumTitle];
      
      
      [nowPlayingInfo setObject:[NSNumber numberWithFloat:isPlaying ? 1.0f : 0.0f] forKey:MPNowPlayingInfoPropertyPlaybackRate];
      
      if(isPlaying) {
         
         [self updateControlCenterImage:artworkUrl];
         [MPNowPlayingInfoCenter defaultCenter].nowPlayingInfo = nowPlayingInfo;
      }
      
   } else {
      
      [MPNowPlayingInfoCenter defaultCenter].nowPlayingInfo = nil;
   }
   
}

- (void)updateControlCenterImage:(NSString *)imageStringUrl
{
   NSURL *imageUrl = [NSURL URLWithString:imageStringUrl];
   dispatch_queue_t queue = dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT, 0);
   dispatch_async(queue, ^{
      
      NSMutableDictionary *nowPlayingInfo = [NSMutableDictionary dictionary];
      UIImage *artworkImage = [UIImage imageWithData:[NSData dataWithContentsOfURL:imageUrl]];
      if(artworkImage == nil) return;
      
      MPMediaItemArtwork *albumArt = [[MPMediaItemArtwork alloc] initWithImage: artworkImage];
      [nowPlayingInfo setValue:albumArt forKey:MPMediaItemPropertyArtwork];
      
      MPNowPlayingInfoCenter *infoCenter = [MPNowPlayingInfoCenter defaultCenter];
      if(infoCenter.nowPlayingInfo) {
         NSMutableDictionary *editNowPlayingInfo = [infoCenter.nowPlayingInfo mutableCopy];
         [editNowPlayingInfo setObject:albumArt forKey:MPMediaItemPropertyArtwork];
         infoCenter.nowPlayingInfo = editNowPlayingInfo;
      } else infoCenter.nowPlayingInfo = nowPlayingInfo;
      
   });
}

@end
