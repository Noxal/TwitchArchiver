package sr.will.archiver.transcode.chatrender.model;

import sr.will.archiver.twitch.model.Comments;

import java.io.Serializable;

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
