package sr.will.archiver.twitch.vod;

import com.github.twitch4j.helix.domain.Stream;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.apache.commons.io.FileUtils;
import sr.will.archiver.Archiver;
import sr.will.archiver.entity.Vod;
import sr.will.archiver.notification.NotificationEvent;
import sr.will.archiver.twitch.ChannelDownloader;
import sr.will.archiver.twitch.VodDeletedException;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

public class VodDownloader {
    public ChannelDownloader channelDownloader;
    public Vod vod;
    public PlaylistInfo playlistInfo;
    public Stream stream;
    public ChatDownloader chatDownloader;
    public final List<PartDownloader> parts = new ArrayList<>();

    public VodDownloader(ChannelDownloader channelDownloader, Vod vod, Stream stream) {
        this.channelDownloader = channelDownloader;
        this.vod = vod;
        this.stream = stream;

        // Don't download if it's already downloaded
        if (vod.downloaded) return;

        chatDownloader = new ChatDownloader(this);

        Archiver.downloadExecutor.submit(this::run);
    }

    public void run() {
        Archiver.LOGGER.info("Starting download for vod {} on channel {} {}", vod.id, vod.channelId, stream == null ? "" : "Currently streaming");

        try {
            PlaybackAccessToken vodToken = getVodToken();
            playlistInfo = getM3u8(vodToken);
            downloadParts();
            if (channelDownloader.archiveSet.downloadChat) chatDownloader.run();
        } catch (VodDeletedException e) {
            Archiver.LOGGER.error("Failed to download vod {} on channel {}: vod was deleted", vod.id, vod.channelId);
            Archiver.instance.webhookManager.execute(NotificationEvent.DOWNLOAD_DELETED, vod);
            // If there are no sections, the chat downloader never started (or it errored, which doesn't matter if the download failed)
            if (chatDownloader.sections.size() == 0) chatDownloader.done = true;
            checkCompleted();
        } catch (Exception e) {
            Archiver.LOGGER.error("Failed to download vod {} on channel {}", vod.id, vod.channelId);
            Archiver.instance.webhookManager.execute(NotificationEvent.DOWNLOAD_FAIL, vod);
            e.printStackTrace();
        }
    }

    public void downloadParts() {
        // If this is a current livestream and this isn't the first time checking
        if (stream != null && parts.size() != 0) {
            Archiver.LOGGER.info("Queuing {} files for vod {} on channel {}", playlistInfo.parts.size() - parts.size(), vod.id, vod.channelId);
            if (playlistInfo.parts.size() == parts.size()) {
                checkCompleted();
                return;
            }
        } else {
            Archiver.LOGGER.info("Queuing {} files for vod {} on channel {}", playlistInfo.parts.size(), vod.id, vod.channelId);
            Archiver.instance.webhookManager.execute(NotificationEvent.DOWNLOAD_START, vod, stream);
        }

        for (int x = parts.size(); x < playlistInfo.parts.size(); x++) {
            parts.add(new PartDownloader(this, playlistInfo.baseURL, playlistInfo.parts.get(x)));
        }

        if (!channelDownloader.archiveSet.downloadChat) checkCompleted();
    }

    public PlaylistInfo getM3u8(PlaybackAccessToken token) throws IOException {
        URL url = new URL("https://usher.ttvnw.net/vod/" + vod.id + ".m3u8?" +
                                  "allow_source=true&player=twitchweb&playlist_include_framerate=true&allow_spectre=true" +
                                  "&token=" + URLEncoder.encode(token.token, "UTF-8") +
                                  "&sig=" + token.signature
        );
        Scanner qualityPlaylistScanner = new Scanner(url.openStream());
        while (qualityPlaylistScanner.hasNext()) {
            String line = qualityPlaylistScanner.nextLine();
            if (line.startsWith("#")) continue;
            File file = new File(vod.getDownloadDir(), "index-original.m3u8");
            try {
                FileUtils.copyURLToFile(new URL(line), file);
            } catch (IOException e) {
                if (e.getMessage().contains("Server returned HTTP response code: 403")) throw new VodDeletedException();
            }
            return new PlaylistInfo(this, file, line.substring(0, line.lastIndexOf('/') + 1));
        }
        return null;
    }

    public PlaybackAccessToken getVodToken() throws IOException {
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

        JsonElement tokenJson = JsonParser.parseReader(new InputStreamReader(connection.getInputStream())).getAsJsonObject()
                                        .get("data").getAsJsonObject()
                                        .get("videoPlaybackAccessToken");

        if (tokenJson.isJsonNull()) throw new VodDeletedException();
        return new PlaybackAccessToken(tokenJson.getAsJsonObject().get("value").getAsString(), tokenJson.getAsJsonObject().get("signature").getAsString());
    }

    public void checkCompleted() {
        int partsCompleted = getPartsCompleted();
        //Archiver.LOGGER.info("Video {} is {}% complete ({}/{})", video.getId(), (int) Math.round(((double) partsCompleted / (double) parts.size()) * 100), partsCompleted, parts.size());
        if (partsCompleted < parts.size()) return;
        if (!chatDownloader.done && channelDownloader.archiveSet.downloadChat) return;

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
