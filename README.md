# TwitchArchiver

Archiver downloads Twitch vods, stores them, and optionally uploads them to YouTube. It can also download and store chat and has a REST API available for chat  and vod information.

This program is designed to be extremely fast, therefore it is very heavily parallelized. You can tune the number of threads the program uses to your needs in the configuration. There are separate thread pools for vod downloading, chat downloading, transcoding, and uploading.

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
    "host": "DB_HOST",
    // A MariaDB database is required. If chat downloading is disabled it will only store VOD information
    "database": "DB_NAME",
    "username": "DB_USER",
    "password": "DB_PASSWORD"
  },
  "archiveSets": [
    {
      "twitchUser": "kiva",
      "numVideos": 6,
      // how many recent videos we should download
      "downloadChat": true,
      // whether we should download chat and store it in the DB
      "renderChat": false,
      // whether to use the TwitchDownloader CLI to render and embed the chat
      "upload": false,
      // whether we should upload them to YouTube
      "chatDownloadThreadIncrement": 7200,
      // How often to start a new chat downloading thread when downloading. The default is 2 hours. Generally speaking, the larger the streamer and the more chat messages that are sent, the shorter this should be. For very small streamers this can be more of a hinderence than a help and can be disabled with -1
      "youTube": {
        "google": {
          "clientId": "GOOGLE_CLIENT_ID",
          "clientSecret": "GOOGLE_CLIENT_SECRET"
        },
        "title": "{date}: {title}",
        // Title of the video to upload to YouTube. Replacements can be used here or in the description.
        "description": "Twitch vod streamed by {user} on {date}, {time}.\nVOD description: {description}",
        // A list of replacements can be found below
        "category": "20",
        // YouTube category. A list can be found at https://gist.github.com/dgp/1b24bf2961521bd75d6c
        "tags": [
          "Gaming", // Video tags
          "Twitch"
        ],
        "madeForKids": false,
        // Whether this content should be marked as made for kids. This is required by YouTube to be specified
        "embeddable": true,
        // Whether this video should be embeddable on sites other than YouTube
        "public": false
      },
      "downloadDeletionPolicy": {
        // Deletion policy for downloads
        "mode": "BOTH_MAX",
        // Deletion mode options are NUMBER, AGE, BOTH_MIN, and BOTH_MAX. BOTH_MIN will delete a video if either critera is met, while BOTH_MAX requires both criteria to be met for deletion.
        "maxNum": 2,
        // Number of videos to keep
        "maxAge": 7
        // How many days we should keep videos
      },
      "transcodeDeletionPolicy": {
        // Deletion policy for transcodes
        "mode": "BOTH_MAX",
        "maxNum": 2,
        "maxAge": 7
      }
    }
  ],
  "download": {
    "threads": 5,
    // How many threads to use for downloading. Increasing this is close to directly proportional to how fast a video will download
    "chatThreads": 10,
    // How many threads to use for chat downloading. Chat downloading is sequential. As such, this currently will have no effect over 4.
    "directory": "downloads"
    // Where to keep the downloaded files
  },
  "chatRender": {
    "window": {
      // What size the rendered chat window should be.
      "width": 350,
      "height": 350
    },
    "args": [
      // These arguments are passed to the TwitchDownloader CLI
      "--background-color",
      "#00000000",
      // Sets the background color. Supports alpha channel (RGBA)
      "--outline",
      "--generate-mask"
    ],
    "filter": "[0][1]alphamerge[ia];[2][ia]overlay=0:H/2-h/2",
    // The ffmpeg filter to use when overlaying chat on top of the VOD. This filter overlays it in the middle left.
    "encoder": "libx264"
    // What encoder to use for overlaying chat
  },
  "transcode": {
    "maxVideoLength": 120,
    // How long in minutes the max length for a video should be. The maximum twitch VOD length is 48 hours, so if you do not want VODs split at all, set this to 2880 or greater
    "threads": 5,
    // How many threads to use for transcoding. This will only be effective if your video has more than 1 part. This operation is very heavily disk I/O dependent. If you don't have an SSD, lower this to 1 or 2
    "directory": "transcodes",
    // Where to keep the output video files
    "ffmpegLocation": "/usr/bin/ffmpeg",
    // ffmpeg and ffprobe location. This is the default on most linux distros.
    "ffprobeLocation": "/usr/bin/ffprobe"
    // ffmpeg and ffprobe are required for transcoding
  },
  "upload": {
    "threads": 1,
    // How many threads to use for uploading. YouTube uploads aren't very bandwidth limited, so unless you have >500Mbps upload, there's no real reason to change this
    "dateFormat": "yyyy-MM-dd",
    // The date format used for title and description replacements
    "timeFormat": "hh:mm"
    // Time format used for replacements
  },
  "twitchDownloader": {
    "path": "TwitchDownloaderCLI.exe",
    // The path to the TwitchDownloader CLI. If placing the CLI in the same path on linux, use ./TwitchDownloaderCLI
    "tempPath": "emotes"
    // The temporary path for TwitchDownloader
  },
  "notifications": [
    {
      "webhook": "DISCORD_WEBHOOK_URL",
      // Your discord webhook URL
      "type": "DISCORD",
      // Current options: DISCORD, DISCORD, and DISCORD. Slack is planned soon(tm)
      "events": [
        // Which events should be sent to this webhook
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
    "goLiveDelay": 6, // How long in minutes we should wait before trying to download a VOD
    "goOfflineDelay": 6, // How long in minutes we should wait before marking a VOD as downloaded. Don't set this to much lower unless you want the end of your VOD downloads cut off
    "liveCheckInterval": 6 // How long in minutes we should wait between downloading VOD segments. Twitch only updates VODs about every 6 minutes for major streamers, so setting this to anything lower is rather pointless
  }
}

```

### Replacements
| replacement | description                               |
|-------------|-------------------------------------------|
| id          | VOD id                                    |
| title       | Original title of the VOD on Twitch       |
| user        | Twitch user that uploaded the VOD         |
| description | Original description of the VOD on Twitch |
| date        | Date the VOD started on Twitch            |
| time        | Time the VOD started on Twitch            |