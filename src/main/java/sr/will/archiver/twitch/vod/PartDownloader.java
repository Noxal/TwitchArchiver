package sr.will.archiver.twitch.vod;

import org.apache.commons.io.FileUtils;
import sr.will.archiver.Archiver;
import sr.will.archiver.notification.NotificationEvent;

import java.io.File;
import java.io.IOException;
import java.net.URL;

public class PartDownloader {
    public final VodDownloader vodDownloader;
    public final String baseURL;
    public final String name;
    boolean optional = false;
    boolean done = false;
    int retries = 0;

    public PartDownloader(VodDownloader vodDownloader, String baseURL, String name, boolean optional) {
        this.vodDownloader = vodDownloader;
        this.baseURL = baseURL;
        this.name = name;
        this.optional = optional;

        // If the file already exists, skip it
        // TODO check if file is intact or only partially downloaded
        File file = new File(vodDownloader.vod.getDownloadDir(), name);
        if (file.exists()) {
            done = true;
            return;
        }

        Archiver.downloadExecutor.submit(this::run);
    }

    public void run() {
        try {
            File file = new File(vodDownloader.vod.getDownloadDir(), name);
            if (file.exists()) {
                markComplete();
                return;
            }
            FileUtils.copyURLToFile(new URL(baseURL + name), file);
            markComplete();
        } catch (IOException e) {
            if (retries >= 3) {
                Archiver.LOGGER.warn("Failed to download part {} for video {} on channel {} (Attempted {} times)", name, vodDownloader.vod.id, vodDownloader.vod.channelId, retries + 1);

                if (optional) {
                    markComplete();
                    return;
                }

                e.printStackTrace();
                Archiver.instance.webhookManager.execute(NotificationEvent.DOWNLOAD_FAIL, vodDownloader.vod);
                return;
            }

            retries++;
            //Archiver.LOGGER.warn("Failed to download part {} for video {} on channel {}, adding back to queue (attempt {})", name, vodDownloader.vod.id, vodDownloader.vod.channelId, retries + 1);
            Archiver.downloadExecutor.submit(this::run);
        }
    }

    public void markComplete() {
        done = true;
        vodDownloader.checkCompleted();
    }
}
