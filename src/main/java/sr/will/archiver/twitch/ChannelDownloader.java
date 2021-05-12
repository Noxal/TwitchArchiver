package sr.will.archiver.twitch;

import com.github.twitch4j.helix.domain.Stream;
import com.github.twitch4j.helix.domain.User;
import com.github.twitch4j.helix.domain.Video;
import com.github.twitch4j.helix.domain.VideoList;
import sr.will.archiver.Archiver;
import sr.will.archiver.entity.Vod;

import java.util.ArrayList;
import java.util.List;

public class ChannelDownloader {
    public User user;
    public Stream stream;
    public final List<VideoDownloader> videoDownloaders = new ArrayList<>();
    public List<Vod> vods;

    public ChannelDownloader(User user, Stream stream) {
        this.user = user;
        this.stream = stream;

        Archiver.downloadExecutor.submit(this::run);
    }

    public void run() {
        vods = Archiver.getVods(Archiver.database.query("SELECT * FROM vods WHERE channel_id = ? ORDER BY id DESC LIMIT ?;", user.getId(), Archiver.config.download.numVideos));

        VideoList videos = Archiver.twitchClient.getHelix().getVideos(null, null, user.getId(), null, null, null, null, "archive", null, null, Archiver.config.download.numVideos).execute();

        Archiver.LOGGER.info("Got {} videos from channel {}", videos.getVideos().size(), user.getId());
        for (Video video : videos.getVideos()) {
            if (videoDownloaders.stream().anyMatch(downloader -> downloader.video.getId().equals(video.getId()))) {
                // Video downloader already exists, don't create it again
                Archiver.LOGGER.info("Downloader already exists, skipping");
                continue;
            }

            Vod vod = getVod(video.getId(), video.getTitle());
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
        this.stream = null;
        for (VideoDownloader downloader : videoDownloaders) {
            if (downloader.stream != null) downloader.stream = null;
        }
    }

    public Vod getVod(String vodId, String title) {
        for (Vod vod : vods) {
            if (vod.id.equals(vodId)) return vod;
        }
        return new Vod(vodId, user.getId(), title, false, false, false, 0).create();
    }
}
