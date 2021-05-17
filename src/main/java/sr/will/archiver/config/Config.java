package sr.will.archiver.config;

import com.google.gson.annotations.SerializedName;
import sr.will.archiver.notification.NotificationEvent;

import java.io.File;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

public class Config {
    public Twitch twitch = new Twitch();
    public Database database = new Database();
    public List<ArchiveSet> archiveSets = Arrays.asList(
            new ArchiveSet("Willsr71"),
            new ArchiveSet("Apron__")
    );
    public Download download = new Download();
    public Transcode transcode = new Transcode();
    public Upload upload = new Upload();
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
        public boolean upload = false;
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

        public ArchiveSet(String twitchUser) {
            this.twitchUser = twitchUser;
        }

        public void validate() {
            if (google == null) google = new Google();
        }
    }

    public static class Download {
        public int threads = 5;
        public String directory = "downloads";

        public void validate() {
            if (threads <= 0) throw new RuntimeException("Cannot have less than 1 download thread");
        }
    }

    public static class Transcode {
        public int maxVideoLength = 120;
        public int threads = 1;
        public String directory = "transcodes";
        public String ffmpegLocation = "E:\\Downloads\\ffmpeg-4.4-essentials_build\\bin\\ffmpeg";
        public String ffprobeLocation = "E:\\Downloads\\ffmpeg-4.4-essentials_build\\bin\\ffprobe";

        public void validate() {
            if (threads <= 0) throw new RuntimeException("Cannot have less than 1 transcode thread");
            if (new File(ffmpegLocation).exists()) throw new RuntimeException("ffmpeg not found");
            if (new File(ffprobeLocation).exists()) throw new RuntimeException("ffprobe not found");
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
        public int goLiveDelay = 5;
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
