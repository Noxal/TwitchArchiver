package sr.will.archiver.twitch.vod;

import org.apache.commons.io.FileUtils;
import sr.will.archiver.Archiver;
import sr.will.archiver.twitch.DownloadPriority;

import java.io.File;
import java.io.IOException;
import java.net.URL;

public class PartDownloader {
    public final VodDownloader videoDownloader;
    public final String baseURL;
    public final String name;
    boolean done = false;
    int retries = 0;

    public PartDownloader(VodDownloader videoDownloader, String baseURL, String name) {
        this.videoDownloader = videoDownloader;
        this.baseURL = baseURL;
        this.name = name;

        File file = new File(videoDownloader.vod.getDownloadDir(), name);
        if (file.exists()) {
            done = true;
            return;
        }

        Archiver.downloadExecutor.submit(this::run, null, DownloadPriority.VOD_PART.priority);
    }

    public void run() {
        try {
            File file = new File(videoDownloader.vod.getDownloadDir(), name);
            if (file.exists()) {
                markComplete();
                return;
            }
            FileUtils.copyURLToFile(new URL(baseURL + name), file);
            markComplete();
        } catch (IOException e) {
            if (retries >= 3) {
                Archiver.LOGGER.warn("Failed to download part {} for video {} on channel {} (Attempted {} times)", name, videoDownloader.video.getId(), videoDownloader.video.getUserLogin(), retries + 1);
                return;
            }

            retries++;
            Archiver.LOGGER.warn("Failed to download part {} for video {} on channel {}, adding back to queue (attempt {})", name, videoDownloader.video.getId(), videoDownloader.video.getUserLogin(), retries + 1);
            Archiver.downloadExecutor.submit(this::run, null, 10);
        }
    }

    public void markComplete() {
        done = true;
        videoDownloader.checkCompleted();
    }
}
