package sr.will.archiver.entity;

// This class is based on the Twitch API message object
public class Comment {
    public String _id;
    //public String created_at; // not used
    //public String updated_at; // not used
    public String channel_id; // twitch channel id
    //public String content_type; // always "video" in this context, not used
    public String content_id; // twitch vod id
    public double content_offset_seconds;
    public Comment.Commenter commenter;
    public String source; // always "chat" in this context
    //public String state; // "published"
    public Message message;

    public static class Commenter {
        //public String _id;
        public String display_name;
        //public String name;
        //public String type; // user or bot
        //public String bio;
        //public String created_at;
        //public String updated_at;
        //public String logo; // profile picture
    }
}
