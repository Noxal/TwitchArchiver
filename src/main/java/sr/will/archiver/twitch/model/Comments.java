package sr.will.archiver.twitch.model;

import sr.will.archiver.entity.Comment;

import java.util.ArrayList;
import java.util.List;

public class Comments {
    public List<Comment> comments = new ArrayList<>();
    public String _next; // id of next comment segment
    public String _prev; // id of previous comment segment
}
