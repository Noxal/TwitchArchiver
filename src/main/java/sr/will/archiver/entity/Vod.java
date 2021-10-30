package sr.will.archiver.entity;

import sr.will.archiver.Archiver;
import sr.will.archiver.config.ArchiveSet;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class Vod {
    public final String id;
    public final String channelId;
    public final Instant createdAt;
    public final String title;
    public final String description;
    public String youtubeId;
    public boolean downloaded;
    public boolean transcoded;
    public boolean uploaded;

    public Vod(String id, String channelId, Instant createdAt, String title, String description, String youtubeId, boolean downloaded, boolean transcoded, boolean uploaded) {
        this.id = id;
        this.channelId = channelId;
        this.createdAt = createdAt;
        this.title = title;
        this.description = description;
        this.youtubeId = youtubeId;
        this.downloaded = downloaded;
        this.transcoded = transcoded;
        this.uploaded = uploaded;
    }

    public String getDownloadDir() {
        return Archiver.config.download.directory + "/" + channelId + "/" + id + "/";
    }

    public String getTranscodeDir() {
        return Archiver.config.transcode.directory + "/" + channelId + "/" + id + "/";
    }

    public Vod create() {
        Archiver.database.execute("INSERT IGNORE INTO vods (id, channel_id, created_at, title, description) VALUES (?, ?, ?, ?, ?);", id, channelId, createdAt.toEpochMilli(), title, description);
        return this;
    }

    public void setDownloaded() {
        this.downloaded = true;
        Archiver.database.execute("UPDATE vods SET downloaded = 1 WHERE id = ?;", id);
    }

    public void setTranscoded() {
        this.transcoded = true;
        Archiver.database.execute("UPDATE vods SET transcoded = 1 WHERE id = ?;", id);
    }

    public void setUploaded(String youtubeId) {
        this.uploaded = true;
        this.youtubeId = youtubeId;
        Archiver.database.execute("UPDATE vods SET uploaded = 1, youtube_id = ? WHERE id = ?;", youtubeId, id);
    }

    public String getReplacedString(String original) {
        return original
                .replace("{title}", title)
                .replace("{user}", Archiver.instance.usernames.get(channelId))
                .replace("{description}", description)
                .replace("{date}", getTimeString(Archiver.config.upload.dateFormat))
                .replace("{time}", getTimeString(Archiver.config.upload.timeFormat));
    }

    public String getTimeString(String format) {
        return DateTimeFormatter.ofPattern(format).withZone(ZoneId.systemDefault()).format(createdAt);
    }
}
