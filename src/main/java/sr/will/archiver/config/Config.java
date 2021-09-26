package sr.will.archiver.config;

import com.google.gson.annotations.SerializedName;
import sr.will.archiver.notification.NotificationEvent;

import java.io.File;
import java.io.Serializable;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

public class Config implements Serializable {
    public Twitch twitch = new Twitch();
    public Database database = new Database();
    public List<ArchiveSet> archiveSets = Arrays.asList(
            new ArchiveSet("Willsr71"),
            new ArchiveSet("Apron__")
    );
    public Download download = new Download();
    public Transcode transcode = new Transcode();
    public Upload upload = new Upload();
    public TwitchDownloader twitchDownloader = new TwitchDownloader();
    public List<WebHook> notifications = Arrays.asList(
            new WebHook("DISCORD_WEBHOOK_URL", WebHook.Type.DISCORD)
    );
    public Times times = new Times();

    public static class Twitch {
        public String clientId = "TWITCH_CLIENT_ID";
        public String clientSecret = "TWITCH_CLIENT_SECRET";
    }

    public static class Database {
        public String host = "DB_HOST";
        public String database = "DB_NAME";
        public String username = "DB_USER";
        public String password = "DB_PASSWORD";
    }

    public static class ArchiveSet {
        public String twitchUser;
        public int numVideos = 2;
        public boolean downloadChat = true;
        public boolean renderChat = true;
        public boolean upload = false;
        public ChatRender chatRender = new ChatRender();
        public YouTube youTube = new YouTube();
        public DeletionPolicy downloadDeletionPolicy = new DeletionPolicy();
        public DeletionPolicy transcodeDeletionPolicy = new DeletionPolicy();

        public static class ChatRender {
            // https://github.com/lay295/TwitchDownloader/blob/master/TwitchDownloaderCLI/README.md#arguments-for-mode-chatrender
            public List<String> args = Arrays.asList(
                    "--background-color", "#00000000", // https://docs.microsoft.com/en-us/dotnet/api/skiasharp.skcolor.parse#remarks #AARRGGBB
                    "--outline",
                    "--output-args=\"-c:v h264_nvenc -preset veryfast -crf 18 -pix_fmt yuv420p '{save_path}'\""
            );

            public int pos_x;
            public int pos_y;
        }

        public static class YouTube {
            public Google google = new Google();

            public String title = "{date}: {title} Part {part}/{parts}";
            public String description = "Twitch vod streamed by {user} on {date}, {time}.\nVOD description: {description}";
            public String category = "20";
            public List<String> tags = Arrays.asList("Gaming", "Twitch");
            public boolean madeForKids = false;
            public boolean embeddable = true;
            @SerializedName("public")
            public boolean publicVideo = false;

            public static class Google {
                public String clientId = "GOOGLE_CLIENT_ID";
                public String clientSecret = "GOOGLE_CLIENT_SECRET";
            }

            public void validate() {
                if (google == null) google = new Google();
            }
        }

        public static class DeletionPolicy {
            public Mode mode = Mode.BOTH_MAX;
            public int maxNum = 2;
            public int maxAge = 7;

            public enum Mode {
                NUMBER,
                AGE,
                BOTH_MIN,
                BOTH_MAX
            }
        }

        public ArchiveSet(String twitchUser) {
            this.twitchUser = twitchUser;
        }

        public void validate() {
            if (chatRender == null) chatRender = new ChatRender();
            if (youTube == null) youTube = new YouTube();
            if (downloadDeletionPolicy == null) downloadDeletionPolicy = new DeletionPolicy();
            if (transcodeDeletionPolicy == null) transcodeDeletionPolicy = new DeletionPolicy();

            youTube.validate();
        }
    }

    public static class Download {
        public int threads = 5;
        public int chatThreads = 10;
        public String directory = "downloads";

        public void validate() {
            if (threads <= 0) throw new RuntimeException("Cannot have less than 1 download thread");
            if (chatThreads <= 0) throw new RuntimeException("Cannot have less than 1 chat download thread");
            if (!new File(directory).exists()) throw new RuntimeException("Download directory does not exist");
        }
    }

    public static class Transcode {
        public int maxVideoLength = 2880;
        public int threads = 1;
        public String directory = "transcodes";
        public String ffmpegLocation = "E:\\Downloads\\ffmpeg-4.4-essentials_build\\bin\\ffmpeg";
        public String ffprobeLocation = "E:\\Downloads\\ffmpeg-4.4-essentials_build\\bin\\ffprobe";

        public void validate() {
            if (threads <= 0) throw new RuntimeException("Cannot have less than 1 transcode thread");
            if (!new File(directory).exists()) throw new RuntimeException("Transcode directory does not exist");
        }
    }

    public static class Upload {
        public int threads = 1;
        public String dateFormat = "yyyy-MM-dd";
        public String timeFormat = "hh:mm";

        public void validate() {
            if (threads <= 0) throw new RuntimeException("Cannot have less than 1 upload thread");
            if (!validatePattern(dateFormat)) throw new RuntimeException("Invalid upload date pattern");
            if (!validatePattern(timeFormat)) throw new RuntimeException("Invalid upload time pattern");
        }

        private boolean validatePattern(String pattern) {
            try {
                DateTimeFormatter.ofPattern(pattern);
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
            return true;
        }
    }

    public static class TwitchDownloader {
        public String path = "TwitchDownloaderCLI.exe";
        public String tempPath = "emotes";
    }

    public static class WebHook {
        public String webhook;
        public Type type;
        public List<NotificationEvent> events = Arrays.asList(NotificationEvent.values());

        public WebHook(String webhook, Type type) {
            this.webhook = webhook;
            this.type = type;
        }

        public void validate() {
            if (events == null) events = Arrays.asList(NotificationEvent.values());
        }

        public enum Type {
            DISCORD
        }
    }

    public static class Times {
        public int goLiveDelay = 6;
        public int goOfflineDelay = 6;
        public int liveCheckInterval = 6;
    }

    public void validate() {
        for (ArchiveSet archiveSet : archiveSets) {
            archiveSet.validate();
        }
        download.validate();
        transcode.validate();
        upload.validate();
        for (WebHook webHook : notifications) {
            webHook.validate();
        }
    }
}
