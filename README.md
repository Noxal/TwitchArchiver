# TwitchArchiver

This program is designed to download twitch vods, transcode them, and optionally upload them to youtube.

## Configuration
The default configuration will be generated automatically on initial startup. The following can be used as a reference for configuration options that are not readily obvious.
```json
{
  "twitch": {
    "clientId": "TWITCH_CLIENT_ID",
    "clientSecret": "TWITCH_CLIENT_SECRET"
  },
  "google": {
    "clientId": "GOOGLE_CLIENT_ID",
    "clientSecret": "GOOGLE_CLIENT_SECRET"
  },
  "database": {
    "host": "DB_HOST", // MariaDB
    "database": "DB_NAME",
    "username": "DB_USER",
    "password": "DB_PASSWORD"
  },
  "archiveSets": [
    {
      "twitchUser": "Willsr71", // Username of a twitch user
      "upload": true // Whether to upload the users's vods to youtube
    },
    {
      "twitchUser": "Apron__",
      "upload": false
    }
  ],
  "download": {
    "numVideos": 2, // How many recent videos we should download. Values over 100 have no effect
    "threads": 5, // How many threads to download with. If you are not saturating your connection enough just increase this
    "directory": "downloads" // Where to download the video files to
  },
  "transcode": {
    "maxVideoLength": 120, // Max video length in minutes, videos will be split into segments of the max length if they exceed it
    "threads": 4, // How many simultaneous transcodes to run. Recommended to only use 1 thread on a HDD, SSDs can typically handle 4+
    "directory": "transcodes", // Where to put the transcoded files
    "ffmpegLocation": "/path/to/ffmpeg", // Path to the ffmpeg executable
    "ffprobeLocation": "/path/to/ffprobe" // Path to the ffprobe executable (comes with ffmpeg)
  },
  "upload": {
    "threads": 1 // How many videos to upload at once
  },
  "times": {
    "goLiveDelay": 5, // How many minutes to wait after someone goes live to check for a vod
    "goOfflineDelay": 6, // How many minutes to wait after someone stops streaming to stop checking the vod
    "liveCheckInterval": 6 // How often in minutes we chould check the vod for updates. Twitch updates vods about every 5-6 minutes so lower numbers will not have much, if any, effect
  }
}
```