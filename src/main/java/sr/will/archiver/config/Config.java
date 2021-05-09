package sr.will.archiver.config;

import java.util.Arrays;
import java.util.List;

public class Config {
    public Twitch twitch = new Twitch();
    public Google google = new Google();
    public Database database = new Database();
    public List<ArchiveSet> archiveSets = Arrays.asList(
            new ArchiveSet("Willsr71"),
            new ArchiveSet("Apron__")
    );
    public Download download = new Download();
    public Times times = new Times();

    public static class Twitch {
        public String clientId = "TWITCH_CLIENT_ID";
        public String clientSecret = "TWITCH_CLIENT_SECRET";
    }

    public static class Google {
        public String clientId = "GOOGLE_CLIENT_ID";
        public String clientSecret = "GOOGLE_CLIENT_SECRET";
    }

    public static class Database {
        public String host = "DB_HOST";
        public String database = "DB";
        public String username = "DB_USER";
        public String password = "DB_PASSWORD";
    }

    public static class ArchiveSet {
        public String twitchUser;
        public boolean upload = false;

        public ArchiveSet(String twitchUser) {
            this.twitchUser = twitchUser;
        }
    }

    public static class Download {
        public int threads = 5;
        public int numVideos = 2;
    }

    public static class Times {
        public int goLiveDelay = 5;
        public int goOfflineDelay = 6;
        public int liveCheckInterval = 6;
    }
}
