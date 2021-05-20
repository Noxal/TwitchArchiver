package sr.will.archiver.twitch.vod;

import com.github.twitch4j.helix.domain.Stream;
import com.github.twitch4j.helix.domain.Video;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.io.FileUtils;
import sr.will.archiver.Archiver;
import sr.will.archiver.entity.Vod;
import sr.will.archiver.notification.NotificationEvent;
import sr.will.archiver.twitch.ChannelDownloader;
import sr.will.archiver.twitch.DownloadPriority;
import sr.will.archiver.twitch.chat.ChatDownloader;
import sr.will.archiver.twitch.model.PlaybackAccessToken;
import sr.will.archiver.twitch.model.PlaybackAccessTokenRequestTemplate;

import javax.net.ssl.HttpsURLConnection;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class VodDownloader {
    public ChannelDownloader channelDownloader;
    public Video video;
    public Vod vod;
    public Stream stream;
    public ChatDownloader chatDownloader;
    public final List<PartDownloader> parts = new ArrayList<>();

    public static final String durationPattern = "#EXT-X-TWITCH-TOTAL-SECS:([0-9.]+)";

    public VodDownloader(ChannelDownloader channelDownloader, Video video, Vod vod, Stream stream) {
        this.channelDownloader = channelDownloader;
        this.video = video;
        this.vod = vod;
        this.stream = stream;

        // Don't download if it's already downloaded
        if (vod.downloaded) return;

        chatDownloader = new ChatDownloader(this);

        // TODO don't forget to uncomment this, it's just commented for chat testing
        Archiver.downloadExecutor.submit(this::run, null, DownloadPriority.VOD.priority);
    }

    public void run() {
        Archiver.LOGGER.info("Starting download for vod {} on channel {} {}", vod.id, vod.channelId, stream == null ? "" : "Currently streaming");

        PlaybackAccessToken vodToken = getVodToken();
        String baseURL = getM3u8(vodToken);
        downloadParts(baseURL);
        chatDownloader.run();
    }

    public void downloadParts(String baseURL) {
        try {
            List<String> files = Files.lines(new File(vod.getDownloadDir(), "index-original.m3u8").toPath())
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .collect(Collectors.toList());

            // If this is a current livestream and this isn't the first time checking
            if (stream != null && parts.size() != 0) {
                Archiver.LOGGER.info("Queuing {} files for vod {} on channel {}", files.size() - parts.size(), vod.id, vod.channelId);
                if (files.size() == parts.size()) {
                    checkCompleted();
                    return;
                }
            } else {
                Archiver.LOGGER.info("Queuing {} files for vod {} on channel {}", files.size(), vod.id, vod.channelId);
                Archiver.instance.webhookManager.execute(NotificationEvent.DOWNLOAD_START, vod, stream);
            }

            for (int x = parts.size(); x < files.size(); x++) {
                parts.add(new PartDownloader(this, baseURL, files.get(x)));
            }

            checkCompleted();
        } catch (IOException e) {
            Archiver.LOGGER.error("Failed to download parts for vod {} on channel {}", vod.id, vod.channelId);
            Archiver.instance.webhookManager.execute(NotificationEvent.DOWNLOAD_FAIL, vod);
            e.printStackTrace();
        }
    }

    public String getM3u8(PlaybackAccessToken token) {
        try {
            URL url = new URL("https://usher.ttvnw.net/vod/" + video.getId() + ".m3u8?" +
                    "allow_source=true&player=twitchweb&playlist_include_framerate=true&allow_spectre=true" +
                    "&token=" + URLEncoder.encode(token.token, "UTF-8") +
                    "&sig=" + token.signature
            );
            Scanner qualityPlaylistScanner = new Scanner(url.openStream());
            while (qualityPlaylistScanner.hasNext()) {
                String line = qualityPlaylistScanner.nextLine();
                if (line.startsWith("#")) continue;
                File file = new File(vod.getDownloadDir(), "index-original.m3u8");
                FileUtils.copyURLToFile(new URL(line), file);
                return line.substring(0, line.lastIndexOf('/') + 1);
            }
        } catch (IOException e) {
            Archiver.LOGGER.error("Failed to get M3u8 playlist for vod {} on channel {}", vod.id, vod.channelId);
            Archiver.instance.webhookManager.execute(NotificationEvent.DOWNLOAD_FAIL, vod);
            e.printStackTrace();
        }
        return null;
    }

    public PlaybackAccessToken getVodToken() {
        try {
            URL url = new URL("https://gql.twitch.tv/gql");
            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Accept", "*/*");
            connection.setRequestProperty("Client-ID", Archiver.TWITCH_CLIENT_ID); // Twitch's client id
            connection.setRequestProperty("Content-Type", "text/plain;charset=UTF-8");
            connection.setDoOutput(true);
            OutputStream outputStream = connection.getOutputStream();
            outputStream.write(Archiver.GSON.toJson(new PlaybackAccessTokenRequestTemplate(vod.id)).getBytes(StandardCharsets.UTF_8));
            outputStream.close();
            connection.connect();

            JsonObject data = JsonParser.parseReader(new InputStreamReader(connection.getInputStream())).getAsJsonObject()
                    .get("data").getAsJsonObject()
                    .get("videoPlaybackAccessToken").getAsJsonObject();
            return new PlaybackAccessToken(data.get("value").getAsString(), data.get("signature").getAsString());
        } catch (IOException e) {
            Archiver.LOGGER.error("Unable to get Vod token for vod {} on channel {}", vod.id, vod.channelId);
            Archiver.instance.webhookManager.execute(NotificationEvent.DOWNLOAD_FAIL, vod);
            e.printStackTrace();
        }
        return null;
    }

    public float getDuration() {
        try {
            File file = new File(vod.getDownloadDir(), "index-original.m3u8");
            float duration = 0;
            Scanner scanner = new Scanner(file);
            while (scanner.hasNext()) {
                String line = scanner.nextLine();
                if (line.matches(durationPattern)) {
                    Matcher matcher = Pattern.compile(durationPattern).matcher(line);
                    if (matcher.find()) {
                        duration = Float.parseFloat(matcher.group(1));
                        break;
                    }
                }
            }

            scanner.close();
            return duration;
        } catch (IOException e) {
            Archiver.LOGGER.error("Failed to get duration of vod {} on channel {}", vod.id, vod.channelId);
            Archiver.instance.webhookManager.execute(NotificationEvent.DOWNLOAD_FAIL, vod);
            e.printStackTrace();
        }

        return 0;
    }

    public void checkCompleted() {
        int partsCompleted = getPartsCompleted();
        //Archiver.LOGGER.info("Video {} is {}% complete ({}/{})", video.getId(), (int) Math.round(((double) partsCompleted / (double) parts.size()) * 100), partsCompleted, parts.size());
        if (partsCompleted < parts.size()) return;
        if (!chatDownloader.done) return;

        Archiver.LOGGER.info("Completed downloading vod {} on channel {}", vod.id, vod.channelId);
        if (stream != null) {
            Archiver.LOGGER.info("Vod {} on channel {} is currently live, will recheck in {} minutes", vod.id, vod.channelId, Archiver.config.times.liveCheckInterval);
            Archiver.scheduledExecutor.schedule(this::run, Archiver.config.times.liveCheckInterval, TimeUnit.MINUTES);
            return;
        }

        // No current livestream, we can mark the download as completed
        Archiver.LOGGER.info("Marking stream as complete, queuing transcode");
        Archiver.instance.webhookManager.execute(NotificationEvent.DOWNLOAD_FINISH, vod);
        vod.setDownloaded();

        synchronized (channelDownloader.vodDownloaders) {
            channelDownloader.vodDownloaders.remove(this);
        }

        Archiver.instance.transcodeManager.transcode(vod);
    }

    public int getPartsCompleted() {
        synchronized (parts) {
            return Math.toIntExact(parts.stream().filter(part -> part.done).count());
        }
    }
}
