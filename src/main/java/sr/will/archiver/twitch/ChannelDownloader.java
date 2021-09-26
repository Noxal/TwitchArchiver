package sr.will.archiver.twitch;

import com.github.twitch4j.helix.domain.Stream;
import com.github.twitch4j.helix.domain.Video;
import com.github.twitch4j.helix.domain.VideoList;
import sr.will.archiver.Archiver;
import sr.will.archiver.config.Config;
import sr.will.archiver.entity.Vod;
import sr.will.archiver.notification.NotificationEvent;
import sr.will.archiver.twitch.vod.VodDownloader;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ChannelDownloader {
    public String userId;
    public List<Stream> unhandledStreams = new ArrayList<>();
    public Config.ArchiveSet archiveSet;
    public final List<VodDownloader> vodDownloaders = new ArrayList<>();
    public List<Vod> vods;

    public ChannelDownloader(String userId, Stream stream) {
        this.userId = userId;
        if (stream != null) unhandledStreams.add(stream);

        archiveSet = Archiver.getArchiveSet(userId);
        if (archiveSet == null) {
            Archiver.LOGGER.error("No archive set found for user {}, cancelling downloads", userId);
            return;
        }

        Archiver.downloadExecutor.submit(this::run);
    }

    public void run() {
        vods = Archiver.getVods(Archiver.database.query("SELECT * FROM vods WHERE channel_id = ? ORDER BY id DESC LIMIT ?;", userId, archiveSet.numVideos));

        VideoList videos = Archiver.twitchClient.getHelix().getVideos(null, null, userId, null, null, null, null, "archive", null, null, archiveSet.numVideos).execute();

        Archiver.LOGGER.info("Got {} videos from channel {}", videos.getVideos().size(), userId);
        synchronized (vodDownloaders) {
            for (Video video : videos.getVideos()) {
                // Video downloader already exists, don't create it again
                if (vodDownloaders.stream().anyMatch(downloader -> downloader.vod.id.equals(video.getId()))) continue;

                Vod vod = getVod(video.getId(), video.getCreatedAtInstant(), video.getTitle(), video.getDescription());
                vods.remove(vod);

                Stream stream = unhandledStreams.stream().filter(s -> video.getStreamId().equals(s.getId())).findFirst().orElse(null);
                vodDownloaders.add(new VodDownloader(this, vod, stream));
                if (stream != null) unhandledStreams.remove(stream);
            }

            // Handle vods that have been deleted from twitch but are still in the db
            if (vods.size() > 0)
                Archiver.LOGGER.info("Got an additional {} videos that have been deleted from twitch", vods.size());
            for (Vod vod : vods) {
                Archiver.LOGGER.info("deleted vod: {} ({})", vod.title, vod.id);
                vodDownloaders.add(new VodDownloader(this, vod, null));
            }
        }
    }

    public void addVideoFromStream(Stream stream, int retries) {
        if (!unhandledStreams.contains(stream)) unhandledStreams.add(stream);
        // There's no easy api call to get a video from a stream id
        // so we just use the call from the original downloader to make things simpler
        run();

        // Check if the vod was added
        if (!unhandledStreams.contains(stream)) return;

        // Vod was not added
        if (retries > 3) {
            Archiver.LOGGER.error("Failed to find vod for {}'s livestream {}", stream.getUserLogin(), stream.getId());
            Archiver.instance.webhookManager.execute(NotificationEvent.STREAM_ASSOCIATE_FAIL, NotificationEvent.STREAM_ASSOCIATE_FAIL.message.replace("{user}", stream.getUserLogin()));
            return;
        }

        Archiver.LOGGER.warn("Failed to find vod for {}'s livestream {}", stream.getUserLogin(), stream.getId());
        Archiver.scheduledExecutor.schedule(() -> addVideoFromStream(stream, retries + 1), Archiver.config.times.liveCheckInterval, TimeUnit.MINUTES);
    }

    public void addVideoFromStream(Stream stream) {
        addVideoFromStream(stream, 0);
    }

    public void streamEnded() {
        // This function is fired after the goOfflineDelay
        // The downloader may not check for an additional time of up to the liveCheckInterval
        //
        // Fastest time is just the goOfflineDelay, slowest time is goOfflineDelay + liveCheckInterval

        List<VodDownloader> liveVods = vodDownloaders.stream()
                .filter(downloader -> downloader.stream != null)
                .sorted(Comparator.comparing(downloader -> downloader.vod.createdAt))
                .collect(Collectors.toList());

        if (liveVods.size() == 0) {
            Archiver.LOGGER.error("Attempted to mark stream as complete, but no vods are marked as streaming");
            return;
        }

        liveVods.get(0).stream = null;
    }

    public Vod getVod(String vodId, Instant createdAt, String title, String description) {
        for (Vod vod : vods) {
            if (vod.id.equals(vodId)) return vod;
        }
        Vod vod = Archiver.instance.getVod(vodId);
        if (vod != null) return vod;

        return new Vod(vodId, userId, createdAt, title, description, null, false, false, false).create();
    }
}
