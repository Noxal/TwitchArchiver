package sr.will.archiver.config;

import sr.will.archiver.notification.NotificationEvent;

import java.io.File;
import java.io.Serializable;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Config implements Serializable {
    public Twitch twitch = new Twitch();
    public Database database = new Database();
    public List<ArchiveSet> archiveSets = List.of(
            new ArchiveSet("Willsr71"),
            new ArchiveSet("Apron__")
    );
    public Download download = new Download();
    public Transcode transcode = new Transcode();
    public Upload upload = new Upload();
    public ChatRender chatRender = new ChatRender();
    public List<WebHook> notifications = List.of(
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
        public int threads = 1;
        public String directory = "transcodes";
        public String outputFileType = "mkv";
        public String ffmpegLocation = "E:\\Downloads\\ffmpeg-4.4-essentials_build\\bin\\ffmpeg";
        public String ffprobeLocation = "E:\\Downloads\\ffmpeg-4.4-essentials_build\\bin\\ffprobe";
        public long maxVideoLength = 720;

        public void validate() {
            if (threads <= 0) throw new RuntimeException("Cannot have less than 1 transcode thread");
            if (!new File(directory).exists()) throw new RuntimeException("Transcode directory does not exist");
        }
    }

    public static class Upload {
        public int threads = 1;
        public String dateFormat = "yyyy-MM-dd";
        public String timeFormat = "hh:mm";

        public Google google = new Google();

        public void validate() {
            if (threads <= 0) throw new RuntimeException("Cannot have less than 1 upload thread");
            if (!validatePattern(dateFormat)) throw new RuntimeException("Invalid upload date pattern");
            if (!validatePattern(timeFormat)) throw new RuntimeException("Invalid upload time pattern");
            if (google == null) google = new Google();
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

        public static class Google {
            public String clientId = "GOOGLE_CLIENT_ID";
            public String clientSecret = "GOOGLE_CLIENT_SECRET";
            public String baseUrl = "http://localhost:80";
            public int oAuthPort = 80;
        }
    }

    public static class ChatRender {
        public String twitchDownloaderPath = "TwitchDownloaderCLI.exe";
        public String twitchDownloaderTempPath = "emotes";
        public String encoder = "libx264";
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
        List<String> twitchUsers = new ArrayList<>();
        for (ArchiveSet archiveSet : archiveSets) {
            archiveSet.validate();
            if (twitchUsers.contains(archiveSet.twitchUser)) {
                throw new RuntimeException("You cannot have the same Twitch user in multiple archive sets");
            }
            twitchUsers.add(archiveSet.twitchUser);
        }
        download.validate();
        transcode.validate();
        upload.validate();
        for (WebHook webHook : notifications) {
            webHook.validate();
        }
    }
}
