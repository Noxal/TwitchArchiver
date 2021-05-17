package sr.will.archiver.notification;

public enum NotificationEvent {
    STREAM_START("{user} started streaming!"),
    STREAM_END("{user} stopped streaming!"),
    DOWNLOAD_START("Started downloading {user}'s vod \"{title}\"!"),
    DOWNLOAD_FINISH("Finished downloading {user}'s vod \"{title}\"!"),
    DOWNLOAD_FAIL("Failed to download {user}'s vod \"{title}\"!"),
    TRANSCODE_START("Started transcoding {user}'s vod \"{title}\"!"),
    TRANSCODE_FINISH("Finished transcoding {user}'s vod \"{title}\"!"),
    TRANSCODE_FAIL("Failed to transcode {user}'s vod \"{title}\"!"),
    UPLOAD_START("Started uploading {user}'s vod \"{title}\"!"),
    UPLOAD_FINISH("Finished uploading {user}'s vod \"{title}\"!"),
    UPLOAD_FAIL("Failed to upload {user}'s vod \"{title}\"!"),
    DELETE_START("Started deleting {user}'s vod \"{title}\"!"),
    DELETE_FINISH("Finished deleting {user}'s vod \"{title}\"!"),
    DELETE_FAIL("Failed to delete {user}'s vod \"{title}\"!");

    public String message;

    NotificationEvent(String message) {
        this.message = message;
    }
}
