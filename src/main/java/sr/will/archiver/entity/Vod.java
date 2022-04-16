package sr.will.archiver.entity;

import com.google.api.client.util.ArrayMap;
import sr.will.archiver.Archiver;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

public class Vod {
    public final String id;
    public final String channelId;
    public final Instant createdAt;
    public final String title;
    public final String description;
    public Map<Integer, String> youtubeIds;
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

        ResultSet resultSet = Archiver.database.query("SELECT video_id, part_number FROM youtube_videos WHERE vod = ?;", id);
        this.youtubeIds = new HashMap<>();
        try {
            while (resultSet.next()) {
                this.youtubeIds.put(resultSet.getInt("part_number"), resultSet.getString("video_id"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
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

    public void setTranscoded(int parts) {
        this.transcoded = true;
        this.parts = parts;
        Archiver.database.execute("UPDATE vods SET transcoded = 1, parts = ? WHERE id = ?;", parts, id);
    }

    public void setUploaded(int part, String youtubeId) {
        if (youtubeIds.containsKey(part) && youtubeIds.get(part).equals(youtubeId)) return;
        youtubeIds.put(part, youtubeId);

        Archiver.database.execute("INSERT IGNORE INTO youtube_videos (video_id, vod, part_number) VALUES (?, ?, ?);", youtubeId, id, part);
        if (youtubeIds.size() < parts) return;

        this.uploaded = true;
        Archiver.database.execute("UPDATE vods SET uploaded = 1 WHERE id = ?;", id);
    }

    public String getReplacedString(String original) {
        return original
                .replace("{id}", id)
                .replace("{title}", title)
                .replace("{user}", Archiver.instance.usernames.get(channelId))
                .replace("{description}", description)
                .replace("{parts}", parts + "")
                .replace("{date}", getTimeString(Archiver.config.upload.dateFormat))
                .replace("{time}", getTimeString(Archiver.config.upload.timeFormat));
    }

    public String getTimeString(String format) {
        return DateTimeFormatter.ofPattern(format).withZone(ZoneId.systemDefault()).format(createdAt);
    }
}
