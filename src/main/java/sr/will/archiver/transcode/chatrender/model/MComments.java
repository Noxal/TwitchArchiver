package sr.will.archiver.transcode.chatrender.model;

import sr.will.archiver.twitch.model.Comments;

import java.io.Serializable;

// This model is based on the TwitchDownloader json file for chat messages
// This way we can pull the chat from the database instead of having TwitchDownloader download it
// This is simply because the chat downloading algorithm in this program more time efficient
// and the file is significantly smaller since some of the unneeded junk from the API is cut out
public class MComments extends Comments implements Serializable {
    public Streamer streamer;
    public Video video;
    public String emotes = null;

    public MComments(Streamer streamer, Video video) {
        this.streamer = streamer;
        this.video = video;
    }

    public static class Streamer {
        public String name;
        public long id;

        public Streamer(String name, String id) {
            this.name = name;
            this.id = Long.parseLong(id);
        }
    }

    public static class Video {
        public double start = 0.0;
        public double end;

        public Video(double end) {
            this.end = end;
        }
    }
}
