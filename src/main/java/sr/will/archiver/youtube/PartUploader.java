package sr.will.archiver.youtube;

import com.google.api.client.googleapis.media.MediaHttpUploader;
import com.google.api.client.http.InputStreamContent;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.Video;
import com.google.api.services.youtube.model.VideoSnippet;
import com.google.api.services.youtube.model.VideoStatus;
import sr.will.archiver.Archiver;
import sr.will.archiver.config.Config;
import sr.will.archiver.entity.Vod;
import sr.will.archiver.notification.NotificationEvent;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.Arrays;

public class PartUploader {
    private final VideoUploader uploader;
    public final Vod vod;
    public int part;
    public boolean done = false;
    private long size;

    public PartUploader(VideoUploader uploader, Vod vod, int part) {
        this.uploader = uploader;
        this.vod = vod;
        this.part = part;

        Archiver.uploadExecutor.submit(this::run);
    }

    public void run() {
        Archiver.LOGGER.info("Starting upload for vod {} part {} on channel {}", vod.id, part, vod.channelId);

        try {
            Config.ArchiveSet archiveSet = Archiver.getArchiveSet(vod.channelId);
            if (archiveSet == null) {
                Archiver.LOGGER.error("Archive configuration for channel {} is missing, cancelling upload", vod.channelId);
                return;
            }

            Video video = new Video();

            VideoSnippet snippet = new VideoSnippet();
            snippet.setTitle(getReplacedString(archiveSet.youTube.title));
            snippet.setDescription(getReplacedString(archiveSet.youTube.description));
            snippet.setCategoryId(archiveSet.youTube.category);
            snippet.setTags(archiveSet.youTube.tags);
            video.setSnippet(snippet);

            VideoStatus status = new VideoStatus();
            status.setPrivacyStatus(archiveSet.youTube.publicVideo ? "public" : "unlisted");
            status.setEmbeddable(archiveSet.youTube.embeddable);
            status.setMadeForKids(archiveSet.youTube.madeForKids);
            video.setStatus(status);

            File mediaFile = new File(vod.getTranscodeDir(), vod.id + "-" + part + ".mp4");
            InputStreamContent mediaContent = new InputStreamContent("application/octet-stream",
                    new BufferedInputStream(new FileInputStream(mediaFile)));
            mediaContent.setLength(mediaContent.getLength());
            size = mediaContent.getLength();

            YouTube.Videos.Insert insert = uploader.manager.youTube.videos()
                    .insert(Arrays.asList("snippet", "status"), video, mediaContent);
            MediaHttpUploader uploader = insert.getMediaHttpUploader();
            uploader.setDirectUploadEnabled(true);

            Video response = insert.execute();
        } catch (Exception e) {
            Archiver.LOGGER.error("Failed to upload vod {} part {} on channel {}", vod.id, part, vod.channelId);
            Archiver.instance.webhookManager.execute(NotificationEvent.UPLOAD_FAIL, vod);
            e.printStackTrace();
            return;
        }

        Archiver.LOGGER.info("Completed upload for vod {} part {} on channel {}", vod.id, part, vod.channelId);
        markComplete();
    }

    public String getReplacedString(String original) {
        return vod.getReplacedString(original)
                .replace("{part}", part + "");
    }

    public void markComplete() {
        done = true;
        uploader.checkCompleted();
    }
}
