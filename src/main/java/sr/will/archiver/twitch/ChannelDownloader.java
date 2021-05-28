package sr.will.archiver.twitch;

import com.github.twitch4j.helix.domain.Stream;
import com.github.twitch4j.helix.domain.User;
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
    public User user;
    public Stream stream;
    public Config.ArchiveSet archiveSet;
    public final List<VodDownloader> vodDownloaders = new ArrayList<>();
    public List<Vod> vods;

    public ChannelDownloader(User user, Stream stream) {
        this.user = user;
        this.stream = stream;

        archiveSet = Archiver.getArchiveSet(user.getId());
        if (archiveSet == null) {
            Archiver.LOGGER.error("No archive set found for user {}, cancelling downloads", user.getLogin());
            return;
        }

        Archiver.downloadExecutor.submit(this::run);
    }

    public void run() {
        vods = Archiver.getVods(Archiver.database.query("SELECT * FROM vods WHERE channel_id = ? ORDER BY id DESC LIMIT ?;", user.getId(), archiveSet.numVideos));

        VideoList videos = Archiver.twitchClient.getHelix().getVideos(null, null, user.getId(), null, null, null, null, "archive", null, null, archiveSet.numVideos).execute();

        Archiver.LOGGER.info("Got {} videos from channel {}", videos.getVideos().size(), user.getId());
        for (Video video : videos.getVideos()) {
            if (vodDownloaders.stream().anyMatch(downloader -> downloader.video.getId().equals(video.getId()))) {
                // Video downloader already exists, don't create it again
                Archiver.LOGGER.info("Downloader already exists, skipping");
                continue;
            }

            Vod vod = getVod(video.getId(), video.getCreatedAtInstant(), video.getTitle(), video.getDescription());
            vods.remove(vod);
            if (stream != null && video.getStreamId().equals(stream.getId())) {
                // vod has a current stream associated
                vodDownloaders.add(new VodDownloader(this, video, vod, stream));
            } else {
                // no current stream
                vodDownloaders.add(new VodDownloader(this, video, vod, null));
            }
        }

        // Handle vods that have been deleted from twitch but are still in the db
        if (vods.size() > 0)
            Archiver.LOGGER.info("Got an additional {} videos that have been deleted from twitch", vods.size());
        for (Vod vod : vods) {
            Archiver.LOGGER.info("deleted vod: {} ({})", vod.title, vod.id);
            vodDownloaders.add(new VodDownloader(this, null, vod, null));
        }
    }

    public void addVideoFromStream(Stream stream, int retries) {
        this.stream = stream;
        // There's no easy api call to get a video from a stream id
        // so we just use the call from the original downloader to make things simpler
        run();

        // Check if the vod was added
        if (vodDownloaders.stream().anyMatch(downloader -> downloader.stream.getId().equals(stream.getId()))) return;

        // Vod was not added
        if (retries > 3) {
            Archiver.LOGGER.error("Failed to find vod for {}'s livestream {}", stream.getUserLogin(), stream.getId());
            Archiver.instance.webhookManager.execute(NotificationEvent.STREAM_ASSOCIATE_FAIL, NotificationEvent.STREAM_ASSOCIATE_FAIL.message.replace("{user}", stream.getUserLogin()));
            return;
        }

        Archiver.scheduledExecutor.schedule(() -> addVideoFromStream(stream, retries + 1), 1, TimeUnit.MINUTES);
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

        // TODO remove this bit after testing
        Archiver.LOGGER.info("Got {} vods marked as live", liveVods.size());
        for (VodDownloader downloader : liveVods) {
            Archiver.LOGGER.info("vod {} start time: {}", downloader.vod.id, downloader.vod.createdAt);
        }

        stream = null;

        if (liveVods.size() == 0) {
            Archiver.LOGGER.error("Attempted to mark stream as complete, but no vods are marked as streaming");
            return;
        }

        Archiver.LOGGER.info("Marking last vod as complete");
        liveVods.get(liveVods.size() - 1).stream = null;
    }

    public Vod getVod(String vodId, Instant createdAt, String title, String description) {
        for (Vod vod : vods) {
            if (vod.id.equals(vodId)) return vod;
        }
        Vod vod = Archiver.instance.getVod(vodId);
        if (vod != null) return vod;

        return new Vod(vodId, user.getId(), createdAt, title, description, false, false, false, 0).create();
    }
}
