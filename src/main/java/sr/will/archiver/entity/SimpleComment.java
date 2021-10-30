package sr.will.archiver.entity;

import sr.will.archiver.Archiver;

import java.io.Serializable;
import java.util.UUID;

public class SimpleComment implements Serializable {
    public Vod vod;
    public UUID id;
    public double offset;
    public String author;

    public Message message;

    public SimpleComment(Vod vod, String id, double offset, String author, String message) {
        this.vod = vod;
        this.id = UUID.fromString(id);
        this.offset = offset;
        this.author = author;
        this.message = Archiver.GSON.fromJson(message, Message.class);
    }
}
