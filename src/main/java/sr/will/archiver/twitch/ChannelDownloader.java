package sr.will.archiver.twitch;

import com.github.twitch4j.helix.domain.*;
import sr.will.archiver.Archiver;
import sr.will.archiver.config.Config;
import sr.will.archiver.entity.Vod;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ChannelDownloader {
    public User user;
    public Stream stream;
    public Config.ArchiveSet archiveSet;
    public final List<VideoDownloader> videoDownloaders = new ArrayList<>();
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
            if (videoDownloaders.stream().anyMatch(downloader -> downloader.video.getId().equals(video.getId()))) {
                // Video downloader already exists, don't create it again
                Archiver.LOGGER.info("Downloader already exists, skipping");
                continue;
            }

            Vod vod = getVod(video.getId(), video.getCreatedAtInstant(), video.getTitle(), video.getDescription());
            if (stream != null && video.getStreamId().equals(stream.getId())) {
                // vod has a current stream associated
                videoDownloaders.add(new VideoDownloader(this, video, vod, stream));
            } else {
                // no current stream
                videoDownloaders.add(new VideoDownloader(this, video, vod, null));
            }
        }
    }

    public void addVideoFromStream(Stream stream) {
        this.stream = stream;
        // There's no easy api call to get a video from a stream id
        // so we just use the call from the original downloader to make things simpler
        run();
    }

    public void streamEnded() {
        // This function is fired after the goOfflineDelay (default 2 minutes)
        // The downloader may not check for an additional time of up to the liveCheckInterval (default 2 minutes)
        //
        // Fastest time is just the goOfflineDelay, slowest time is goOfflineDelay + liveCheckInterval

        // Occasionally the twitch checker marks a stream as online, immediately offline, and immediately online again
        // To account for this we check if there's a current livestream going with the same id
        StreamList streams = Archiver.twitchClient.getHelix().getStreams(null, null, null, null, null, null, null, Collections.singletonList(archiveSet.twitchUser)).execute();
        if (!streams.getStreams().isEmpty() && streams.getStreams().get(0).getId().equals(stream.getId())) return;

        this.stream = null;
        for (VideoDownloader downloader : videoDownloaders) {
            if (downloader.stream != null) downloader.stream = null;
        }
    }

    public Vod getVod(String vodId, Instant createdAt, String title, String description) {
        for (Vod vod : vods) {
            if (vod.id.equals(vodId)) return vod;
        }
        return new Vod(vodId, user.getId(), createdAt, title, description, false, false, false, 0).create();
    }
}
