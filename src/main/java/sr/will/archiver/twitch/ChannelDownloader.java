package sr.will.archiver.twitch;

import com.github.twitch4j.helix.domain.Stream;
import com.github.twitch4j.helix.domain.User;
import com.github.twitch4j.helix.domain.Video;
import com.github.twitch4j.helix.domain.VideoList;
import sr.will.archiver.Archiver;
import sr.will.archiver.config.Config;
import sr.will.archiver.entity.Vod;
import sr.will.archiver.twitch.vod.VodDownloader;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class ChannelDownloader {
    public User user;
    public Stream stream;
    public Config.ArchiveSet archiveSet;
    public final List<VodDownloader> vodDownloaders = new ArrayList<>();
    public List<Vod> vods;

    public VodDownloader liveDownloader;

    public ChannelDownloader(User user, Stream stream) {
        this.user = user;
        this.stream = stream;

        archiveSet = Archiver.getArchiveSet(user.getId());
        if (archiveSet == null) {
            Archiver.LOGGER.error("No archive set found for user {}, cancelling downloads", user.getLogin());
            return;
        }

        Archiver.downloadExecutor.submit(this::run, null, DownloadPriority.CHANNEL.priority);
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
                liveDownloader = new VodDownloader(this, video, vod, stream);
                vodDownloaders.add(liveDownloader);
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

    public void addVideoFromStream(Stream stream) {
        this.stream = stream;
        // There's no easy api call to get a video from a stream id
        // so we just use the call from the original downloader to make things simpler
        run();
    }

    public void streamEnded() {
        // This function is fired after the goOfflineDelay
        // The downloader may not check for an additional time of up to the liveCheckInterval
        //
        // Fastest time is just the goOfflineDelay, slowest time is goOfflineDelay + liveCheckInterval

        stream = null;
        liveDownloader.stream = null;
        liveDownloader = null;
    }

    public Vod getVod(String vodId, Instant createdAt, String title, String description) {
        for (Vod vod : vods) {
            if (vod.id.equals(vodId)) return vod;
        }
        return new Vod(vodId, user.getId(), createdAt, title, description, false, false, false, 0).create();
    }
}
