package sr.will.archiver.notification;

import java.awt.*;

public enum NotificationEvent {
    STREAM_START(Color.BLUE, "{user} started streaming!"),
    STREAM_END(Color.BLUE, "{user} stopped streaming!"),
    DOWNLOAD_START(Color.GREEN, "Started downloading {user}'s vod \"{title}\" from {date}!"),
    DOWNLOAD_FINISH(Color.GREEN, "Finished downloading {user}'s vod \"{title}\" from {date}!"),
    DOWNLOAD_FAIL(Color.RED, "Failed to download {user}'s vod \"{title}\" from {date}!"),
    TRANSCODE_START(Color.GREEN, "Started transcoding {user}'s vod \"{title}\" from {date}!"),
    TRANSCODE_FINISH(Color.GREEN, "Finished transcoding {user}'s vod \"{title}\" from {date}!"),
    TRANSCODE_FAIL(Color.RED, "Failed to transcode {user}'s vod \"{title}\" from {date}!"),
    UPLOAD_START(Color.GREEN, "Started uploading {user}'s vod \"{title}\" from {date}!"),
    UPLOAD_FINISH(Color.GREEN, "Finished uploading {user}'s vod \"{title}\" from {date}!"),
    UPLOAD_FAIL(Color.RED, "Failed to upload {user}'s vod \"{title}\" from {date}!"),
    DELETE_START(Color.GREEN, "Started deleting {user}'s vod \"{title}\" from {date}!"),
    DELETE_FINISH(Color.GREEN, "Finished deleting {user}'s vod \"{title}\" from {date}!"),
    DELETE_FAIL(Color.RED, "Failed to delete {user}'s vod \"{title}\" from {date}!");

    public Color color;
    public String message;

    NotificationEvent(Color color, String message) {
        this.color = color;
        this.message = message;
    }
}
