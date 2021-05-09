# TwitchArchiver

This program is designed to download twitch vods, transcode them, and optionally upload them to youtube.

## Configuration

|Item|Description|
|---|---|
|Twitch client id & secret|Client id & secret for your twitch oauth integration|
|YouTube upload|Whether you want to upload the videos to youtube or not|
|Database section|section for connecting to a MariaDB database|
|Archive Set|twitch usernames that you'd like to monitor|
|Download threads|How many threads to use when downloading. Default is 5, if you are not reaching desired download speeds, you can try increasing this|
|Download num videos|How many recent videos we should try downloading from twitch. This number can be 1-100. Values above 100 will have no effect|
|Go Live Delay|How long to wait before checking for a VOD after we detect a livestream. Default is 5 minutes.|
|Go Offline Delay|How long we should wait after a livestream stops before marking a VOD as completed. Default is 6 minutes. Values too low may cut downloads short.<br>Note that depending on when the VOD was last checked, it can take up to the goOfflineDelay + liveCheckInterval for a VOD to be marked as complete (default 12 minutes)|
|Live Interval Check|How often we should check the VOD if there is a current livestream. Default is 6 minutes. Values lower than 6 minutes will not have much if any effect, as twitch only updates VODs every approximately 5 minutes|