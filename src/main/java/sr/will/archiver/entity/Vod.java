package sr.will.archiver.entity;

import sr.will.archiver.Archiver;

import java.time.Instant;

public class Vod {
    public final String id;
    public final String channelId;
    public final Instant createdAt;
    public final String title;
    public final String description;
    public boolean downloaded;
    public boolean transcoded;
    public boolean uploaded;
    public int parts;

    public Vod(String id, String channelId, Instant createdAt, String title, String description, boolean downloaded, boolean transcoded, boolean uploaded, int parts) {
        this.id = id;
        this.channelId = channelId;
        this.createdAt = createdAt;
        this.title = title;
        this.description = description;
        this.downloaded = downloaded;
        this.transcoded = transcoded;
        this.uploaded = uploaded;
        this.parts = parts;
    }

    public String getDownloadDir() {
        return Archiver.config.download.directory + "/" + channelId + "/" + id + "/";
    }

    public String getTranscodeDir() {
        return Archiver.config.transcode.directory + "/" + channelId + "/" + id + "/";
    }

    public Vod create() {
        Archiver.database.execute("INSERT INTO vods (id, channel_id, created_at, title, description) VALUES (?, ?, ?, ?, ?);", id, channelId, createdAt.getEpochSecond(), title, description);
        return this;
    }

    public void setDownloaded() {
        Archiver.database.execute("UPDATE vods SET downloaded = 1 WHERE id = ?;", id);
    }

    public void setTranscoded(int parts) {
        this.parts = parts;
        Archiver.database.execute("UPDATE vods SET transcoded = 1, parts = ? WHERE id = ?;", parts, id);
    }

    public void setUploaded() {
        Archiver.database.execute("UPDATE vods SET uploaded = 1 WHERE id = ?;", id);
    }
}
