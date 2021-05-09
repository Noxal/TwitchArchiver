package sr.will.archiver.twitch;

import com.github.twitch4j.helix.domain.Stream;
import com.github.twitch4j.helix.domain.Video;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.io.FileUtils;
import sr.will.archiver.Archiver;
import sr.will.archiver.sql.model.Vod;
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
import java.util.stream.Collectors;

public class VideoDownloader {
    public ChannelDownloader channelDownloader;
    public Video video;
    public Vod vod;
    public Stream stream;
    public List<PartDownloader> parts = new ArrayList<>();

    public VideoDownloader(ChannelDownloader channelDownloader, Video video, Vod vod, Stream stream) {
        this.channelDownloader = channelDownloader;
        this.video = video;
        this.vod = vod;
        this.stream = stream;

        synchronized (channelDownloader.videoDownloaders) {
            channelDownloader.videoDownloaders.add(this);
        }

        Archiver.LOGGER.info("Beginning download for video {} ({}) on channel {} ({})", video.getId(), video.getTitle(), video.getUserLogin(), video.getUserId());

        if (stream != null) {
            Archiver.LOGGER.info("Video {} ({}) is live!", video.getId(), video.getTitle());
            Archiver.LOGGER.info("Created at: {}", video.getCreatedAtInstant().toString());
            Archiver.LOGGER.info("Duration: {}", video.getDuration());
        }

        // Don't download if it's already downloaded
        if (vod.downloaded) return;

        download();
    }

    public void download() {
        PlaybackAccessToken vodToken = getVodToken();
        String baseURL = getM3u8(vodToken);
        downloadParts(baseURL);
    }

    public void downloadParts(String baseURL) {
        try {
            String basePath = "downloads/" + video.getUserId() + "/" + video.getId();
            List<String> files = Files.lines(new File(basePath, "index.m3u8").toPath())
                                         .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                                         .collect(Collectors.toList());

            Archiver.LOGGER.info("getting {} files...", files.size());

            // If this is a current livestream and this isn't the first time checking
            if (stream != null && parts.size() != 0) {
                Archiver.LOGGER.info("Downloading an additional {} files", files.size() - parts.size());
                if (files.size() == parts.size()) {
                    checkCompleted();
                    return;
                }
            }

            for (int x = parts.size(); x < files.size(); x++) {
                parts.add(new PartDownloader(this, baseURL, basePath, files.get(x)));
            }
        } catch (IOException e) {
            Archiver.LOGGER.error("Failed to download parts");
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
                File file = new File("downloads/" + video.getUserId() + "/" + video.getId() + "/index.m3u8");
                FileUtils.copyURLToFile(new URL(line), file);
                return line.substring(0, line.lastIndexOf('/') + 1);
            }
        } catch (IOException e) {
            Archiver.LOGGER.error("Failed to get M3u8 playlist");
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
            connection.setRequestProperty("Client-ID", "kimne78kx3ncx6brgo4mv6wki5h1ko"); // Twitch's client id
            connection.setRequestProperty("Content-Type", "text/plain;charset=UTF-8");
            connection.setDoOutput(true);
            OutputStream outputStream = connection.getOutputStream();
            outputStream.write(Archiver.GSON.toJson(new PlaybackAccessTokenRequestTemplate(video.getId())).getBytes(StandardCharsets.UTF_8));
            outputStream.close();
            connection.connect();

            JsonObject data = JsonParser.parseReader(new InputStreamReader(connection.getInputStream())).getAsJsonObject()
                                      .get("data").getAsJsonObject()
                                      .get("videoPlaybackAccessToken").getAsJsonObject();
            return new PlaybackAccessToken(data.get("value").getAsString(), data.get("signature").getAsString());
        } catch (IOException e) {
            Archiver.LOGGER.error("Unable to get Vod token for vod {} ({})", video.getId(), video.getTitle());
            e.printStackTrace();
        }
        return null;
    }

    public void checkCompleted() {
        int partsCompleted = getPartsCompleted();
        Archiver.LOGGER.info("Video {} is {}% complete ({}/{})", video.getId(), (int) Math.round(((double) partsCompleted / (double) parts.size()) * 100), partsCompleted, parts.size());
        if (partsCompleted < parts.size()) return;

        Archiver.LOGGER.info("Video {} ({}) completed downloading", video.getId(), video.getTitle());
        if (stream != null) {
            Archiver.LOGGER.info("Video {} on channel {} is currently live, will recheck in {} minutes", video.getId(), video.getUserLogin(), Archiver.config.times.liveCheckInterval);
            Archiver.scheduledExecutor.schedule(this::download, Archiver.config.times.liveCheckInterval, TimeUnit.MINUTES);
            return;
        }

        // No current livestream, we can mark the download as completed
        Archiver.LOGGER.info("Marking stream as complete, beginning transcode");
        vod.setDownloaded();
    }

    public int getPartsCompleted() {
        return Math.toIntExact(parts.stream().filter(part -> part.done).count());
    }
}
