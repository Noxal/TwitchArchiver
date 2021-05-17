package sr.will.archiver.youtube;

import sr.will.archiver.Archiver;
import sr.will.archiver.entity.Vod;
import sr.will.archiver.notification.NotificationEvent;

import java.util.ArrayList;
import java.util.List;

public class VideoUploader {
    public final YouTubeClient manager;
    public final Vod vod;
    public final List<PartUploader> parts = new ArrayList<>();

    public VideoUploader(YouTubeClient manager, Vod vod) {
        this.manager = manager;
        this.vod = vod;

        Archiver.uploadExecutor.submit(this::run);
    }

    public void run() {
        Archiver.LOGGER.info("Starting upload for vod {} on channel {}", vod.id, vod.channelId);
        Archiver.instance.webhookManager.execute(NotificationEvent.UPLOAD_START, vod);

        for (int i = 0; i < vod.parts; i++) {
            parts.add(new PartUploader(this, vod, i));
        }

        Archiver.LOGGER.info("Queued {} upload parts for vod {} on channel {}", parts.size(), vod.id, vod.channelId);
    }

    public void checkCompleted() {
        if (getPartsCompleted() < parts.size()) return;

        Archiver.LOGGER.info("Completed upload for vod {} on channel {}", vod.id, vod.channelId);
        Archiver.instance.webhookManager.execute(NotificationEvent.UPLOAD_FINISH, vod);
        vod.setUploaded();

        synchronized (manager.uploaders) {
            manager.uploaders.remove(this);
        }
    }

    public int getPartsCompleted() {
        synchronized (parts) {
            return Math.toIntExact(parts.stream().filter(part -> part.done).count());
        }
    }
}
