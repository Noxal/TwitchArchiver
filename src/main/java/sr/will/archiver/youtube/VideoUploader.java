package sr.will.archiver.youtube;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
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

public class VideoUploader {
    public final YouTubeClient manager;
    public final Vod vod;

    public VideoUploader(YouTubeClient manager, Vod vod) {
        this.manager = manager;
        this.vod = vod;

        Archiver.uploadExecutor.submit(this::run);
    }

    public void run() {
        Archiver.LOGGER.info("Starting upload for vod {} on channel {}", vod.id, vod.channelId);
        Archiver.instance.webhookManager.execute(NotificationEvent.UPLOAD_START, vod);

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

            File mediaFile = new File(vod.getTranscodeDir(), vod.id + ".mp4");
            InputStreamContent mediaContent = new InputStreamContent("application/octet-stream",
                    new BufferedInputStream(new FileInputStream(mediaFile)));
            mediaContent.setLength(mediaContent.getLength());

            YouTube.Videos.Insert insert = manager.youTube.videos()
                    .insert(Arrays.asList("snippet", "status"), video, mediaContent);
            MediaHttpUploader uploader = insert.getMediaHttpUploader();
            uploader.setDirectUploadEnabled(true);

            Video response = insert.execute();

            Archiver.database.execute("REPLACE INTO youtube_videos (video_id, vod) VALUES (?, ?)", response.getId(), vod.id);

            Archiver.LOGGER.info("Completed upload for vod {} on channel {}", vod.id, vod.channelId);
            Archiver.instance.webhookManager.execute(NotificationEvent.UPLOAD_FINISH, vod);
            vod.setUploaded(response.getId());

            synchronized (manager.uploaders) {
                manager.uploaders.remove(this);
            }

            Archiver.instance.deletionManager.run();
        } catch (Exception e) {
            Archiver.LOGGER.error("Failed to upload vod {} on channel {}", vod.id, vod.channelId);
            Archiver.instance.webhookManager.execute(NotificationEvent.UPLOAD_FAIL, vod);
            if (e instanceof GoogleJsonResponseException) {
                Archiver.LOGGER.info(((GoogleJsonResponseException) e).getDetails().getMessage());
            } else {
                e.printStackTrace();
            }
        }
    }

    public String getReplacedString(String original) {
        return vod.getReplacedString(original)
                .replace("<", "")
                .replace(">", "");
    }
}
