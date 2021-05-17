package sr.will.archiver.config;

import com.google.gson.annotations.SerializedName;

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
    }

    public static class Download {
        public int threads = 5;
        public String directory = "downloads";
    }

    public static class Transcode {
        public int maxVideoLength = 120;
        public int threads = 1;
        public String directory = "transcodes";
        public String ffmpegLocation = "E:\\Downloads\\ffmpeg-4.4-essentials_build\\bin\\ffmpeg";
        public String ffprobeLocation = "E:\\Downloads\\ffmpeg-4.4-essentials_build\\bin\\ffprobe";
    }

    public static class Upload {
        public int threads = 1;
        public String dateFormat = "yyyy-MM-dd";
        public String timeFormat = "hh:mm";
    }

    public static class Times {
        public int goLiveDelay = 5;
        public int goOfflineDelay = 6;
        public int liveCheckInterval = 6;
    }

    public static void validateConfig(Config config) throws RuntimeException {
        for (ArchiveSet archiveSet : config.archiveSets) {
            if (archiveSet.google == null) archiveSet.google = new ArchiveSet.Google();
        }
    }
}
