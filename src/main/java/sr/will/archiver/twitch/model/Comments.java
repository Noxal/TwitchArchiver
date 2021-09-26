package sr.will.archiver.twitch.model;

import com.google.gson.JsonObject;
import sr.will.archiver.Archiver;

import java.util.ArrayList;
import java.util.List;

public class Comments {
    public List<Comment> comments = new ArrayList<>();
    public String _next; // id of next comment segment
    public String _prev; // id of previous comment segment

    public static class Comment {
        public String _id;
        //public String created_at; // not used
        //public String updated_at; // not used
        public String channel_id; // twitch channel id
        //public String content_type; // always "video" in this context, not used
        public String content_id; // twitch vod id
        public double content_offset_seconds;
        public Commenter commenter;
        public String source; // always "chat" in this context
        //public String state; // "published"
        public Message message;

        public Comment(String id, String channelId, String vodId, double offset, String author, String message) {
            this._id = id;
            this.channel_id = channelId;
            this.content_id = vodId;
            this.content_offset_seconds = offset;

            this.commenter = new Commenter();
            this.commenter.display_name = author;

            this.message = Archiver.GSON.fromJson(message, Message.class);

            this.source = "chat";
        }

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

        public static class Message {
            public String body;
            public List<Emoticon> emoticons;
            public List<Fragment> fragments;
            public boolean is_action; // ?
            public List<UserBadge> user_badges;
            public String user_color;
            public JsonObject user_notice_params;

            public static class Emoticon {
                public String _id;
                public int begin;
                public int end;
            }

            public static class Fragment {
                public String text;
                public Emoticon emoticon;

                public static class Emoticon {
                    public String emoticon_id;
                    public String emoticon_set_id;
                }
            }

            public static class UserBadge {
                public String _id;
                public String version;
            }
        }
    }
}
