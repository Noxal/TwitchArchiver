# TwitchArchiver

This program is designed to download twitch vods, transcode them, and optionally upload them to youtube.

## Configuration

The default configuration will be generated automatically on initial startup. The following can be used as a reference
for configuration options that are not readily obvious.

```json
{
  "twitch": {
    "clientId": "TWITCH_CLIENT_ID",
    "clientSecret": "TWITCH_CLIENT_SECRET"
  },
  "database": {
    "host": "DB_HOST", // MariaDB
    "database": "DB_NAME",
    "username": "DB_USER",
    "password": "DB_PASSWORD"
  },
  "archiveSets": [
    {
      "twitchUser": "kiva",
      "numVideos": 6,
      "upload": false,
      "youTube": {
        "google": {
          "clientId": "GOOGLE_CLIENT_ID",
          "clientSecret": "GOOGLE_CLIENT_SECRET"
        },
        "title": "{date}: {title} Part {part}/{parts}",
        "description": "Twitch vod streamed by {user} on {date}, {time}.\nVOD description: {description}",
        "category": "20",
        "tags": [
          "Gaming",
          "Twitch"
        ],
        "madeForKids": false,
        "embeddable": true,
        "public": false
      },
      "deletion": {
        "mode": "BOTH_MAX",
        "maxNum": 2,
        "maxAge": 7
      }
    }
  ],
  "download": {
    "threads": 5,
    "chatThreads": 10,
    "directory": "downloads"
  },
  "transcode": {
    "maxVideoLength": 120,
    "threads": 5,
    "directory": "transcodes",
    "ffmpegLocation": "/usr/bin/ffmpeg",
    "ffprobeLocation": "/usr/bin/ffprobe"
  },
  "upload": {
    "threads": 1,
    "dateFormat": "yyyy-MM-dd",
    "timeFormat": "hh:mm"
  },
  "notifications": [
    {
      "webhook": "DISCORD_WEBHOOK_URL",
      "type": "DISCORD",
      "events": [
        "STREAM_START",
        "STREAM_END",
        "DOWNLOAD_START",
        "DOWNLOAD_FINISH",
        "DOWNLOAD_FAIL",
        "TRANSCODE_START",
        "TRANSCODE_FINISH",
        "TRANSCODE_FAIL",
        "UPLOAD_START",
        "UPLOAD_FINISH",
        "UPLOAD_FAIL",
        "DELETE_START",
        "DELETE_FINISH",
        "DELETE_FAIL"
      ]
    }
  ],
  "times": {
    "goLiveDelay": 6,
    "goOfflineDelay": 6,
    "liveCheckInterval": 6
  }
}
```