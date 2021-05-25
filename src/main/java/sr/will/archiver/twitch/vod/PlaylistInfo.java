package sr.will.archiver.twitch.vod;

import sr.will.archiver.Archiver;
import sr.will.archiver.notification.NotificationEvent;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PlaylistInfo {
    public VodDownloader downloader;
    public String baseURL;
    public double duration;
    public List<String> parts;

    public static final Pattern durationPattern = Pattern.compile("#EXT-X-TWITCH-TOTAL-SECS:([0-9.]+)");

    public PlaylistInfo(VodDownloader downloader, File file, String baseURL) {
        this.baseURL = baseURL;

        try {
            Scanner scanner = new Scanner(file);
            while (scanner.hasNext()) {
                String line = scanner.nextLine();
                if (line.isEmpty()) continue;

                if (line.startsWith("#")) {
                    // Lines that start with # contain info about the playlist
                    Matcher durationMatcher = durationPattern.matcher(line);
                    if (durationMatcher.find()) duration = Float.parseFloat(durationMatcher.group(1));
                } else {
                    // Lines that don't start with a # are part file names
                    parts.add(line);
                }
            }

            scanner.close();
        } catch (IOException e) {
            Archiver.LOGGER.error("Failed to process playlist of vod {} on channel {}", downloader.vod.id, downloader.vod.channelId);
            Archiver.instance.webhookManager.execute(NotificationEvent.DOWNLOAD_FAIL, downloader.vod);
            e.printStackTrace();
        }
    }
}
