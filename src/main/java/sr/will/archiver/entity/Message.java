package sr.will.archiver.entity;

import com.google.gson.JsonObject;

import java.io.Serializable;
import java.util.List;

// This class is based on the Twitch API message object
public class Message implements Serializable {
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
