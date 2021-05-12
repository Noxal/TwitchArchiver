package sr.will.archiver.entity;

import sr.will.archiver.Archiver;

public class Vod {
    public final String id;
    public final String channelId;
    public String title;
    public boolean downloaded;
    public boolean transcoded;
    public boolean uploaded;
    public int parts;

    public Vod(String id, String channelId, String title, boolean downloaded, boolean transcoded, boolean uploaded, int parts) {
        this.id = id;
        this.channelId = channelId;
        this.title = title;
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
        Archiver.database.execute("INSERT INTO vods (id, channel_id, title) VALUES (?, ?, ?);", id, channelId, title);
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
