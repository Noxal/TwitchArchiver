package sr.will.archiver.sql.model;

import sr.will.archiver.Archiver;

public class Vod {
    public final String id;
    public final String channelId;
    public boolean downloaded;
    public boolean transcoded;
    public boolean uploaded;

    public Vod(String id, String channelId, boolean downloaded, boolean transcoded, boolean uploaded) {
        this.id = id;
        this.channelId = channelId;
        this.downloaded = downloaded;
        this.transcoded = transcoded;
        this.uploaded = uploaded;

        Archiver.LOGGER.info("new vod: {}", id);
    }

    public Vod create() {
        Archiver.database.execute("INSERT INTO vods (id, channel_id) VALUES (?, ?);", id, channelId);
        return this;
    }

    public void setDownloaded() {
        Archiver.database.execute("UPDATE vods SET downloaded = 1 WHERE id = ?;", id);
    }

    public void setTranscoded() {
        Archiver.database.execute("UPDATE vods SET transcoded = 1 WHERE id = ?;", id);
    }

    public void setUploaded() {
        Archiver.database.execute("UPDATE vods SET uploaded = 1 WHERE id = ?;", id);
    }
}
