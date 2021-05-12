package sr.will.archiver.youtube;

import com.google.api.client.http.InputStreamContent;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.Video;
import com.google.api.services.youtube.model.VideoSnippet;
import com.google.api.services.youtube.model.VideoStatus;
import sr.will.archiver.Archiver;
import sr.will.archiver.config.Config;
import sr.will.archiver.entity.Vod;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;

public class PartUploader {
    private final VideoUploader uploader;
    public final Vod vod;
    public int part;
    public boolean done = false;

    public PartUploader(VideoUploader uploader, Vod vod, int part) {
        this.uploader = uploader;
        this.vod = vod;
        this.part = part;

        Archiver.uploadExecutor.submit(this::run);
    }

    public void run() {
        Archiver.LOGGER.info("Starting upload for vod {} part {} on channel {}", vod.id, part, vod.channelId);

        try {
            Config.ArchiveSet archiveSet = Archiver.getArchiveSet(vod.id);
            if (archiveSet == null) {
                Archiver.LOGGER.error("Archive configuration for channel {} is missing, cancelling upload", vod.channelId);
                return;
            }

            Video video = new Video();

            VideoSnippet snippet = new VideoSnippet();
            snippet.setTitle(archiveSet.title.replace("{title}", vod.title).replace("{part}", (part + 1) + ""));
            snippet.setDescription(archiveSet.description);
            snippet.setCategoryId(archiveSet.category);
            snippet.setTags(archiveSet.tags);
            video.setSnippet(snippet);

            VideoStatus status = new VideoStatus();
            status.setPrivacyStatus(archiveSet.publicVideo ? "public" : "unlisted");
            status.setEmbeddable(true);
            video.setStatus(status);

            File mediaFile = new File(vod.getTranscodeDir(), vod.id + "-" + part + ".mp4");
            InputStreamContent mediaContent = new InputStreamContent("application/octet-stream",
                    new BufferedInputStream(new FileInputStream(mediaFile)));
            mediaContent.setLength(mediaContent.getLength());

            YouTube.Videos.Insert request = uploader.manager.youTube.videos()
                                                    .insert(Arrays.asList("snippet", "status"), video, mediaContent);
            Video response = request.setUploadType("direct").execute();
            Archiver.LOGGER.info("response: {}", response);
        } catch (IOException e) {
            Archiver.LOGGER.error("Failed to upload vod {} part {} on channel {}", vod.id, part, vod.channelId);
            e.printStackTrace();
            return;
        }

        Archiver.LOGGER.info("Completed upload for vod {} part {} on channel {}", vod.id, part, vod.channelId);
        markComplete();
    }

    public void markComplete() {
        done = true;
        uploader.checkCompleted();
    }
}
