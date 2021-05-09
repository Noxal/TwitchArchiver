package sr.will.archiver.twitch;

import com.github.twitch4j.helix.domain.Stream;
import com.github.twitch4j.helix.domain.User;
import com.github.twitch4j.helix.domain.Video;
import com.github.twitch4j.helix.domain.VideoList;
import sr.will.archiver.Archiver;
import sr.will.archiver.sql.model.Vod;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ChannelDownloader {
    public User user;
    public Stream stream;
    public final List<VideoDownloader> videoDownloaders = new ArrayList<>();
    public List<Vod> vods = new ArrayList<>();

    public ChannelDownloader(User user, Stream stream) {
        this.user = user;
        this.stream = stream;

        synchronized (Archiver.instance.channelDownloaders) {
            Archiver.instance.channelDownloaders.add(this);
        }

        downloadVideos();
    }

    public void downloadVideos() {
        ResultSet resultSet = Archiver.database.query("SELECT * FROM vods WHERE channel_id = ? ORDER BY id DESC LIMIT ?;", user.getId(), Archiver.config.download.numVideos);
        try {
            while (resultSet.next()) {
                vods.add(new Vod(
                        resultSet.getString("id"),
                        resultSet.getString("channel_id"),
                        resultSet.getBoolean("downloaded"),
                        resultSet.getBoolean("transcoded"),
                        resultSet.getBoolean("uploaded")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        VideoList videos = Archiver.twitchClient.getHelix().getVideos(null, null, user.getId(), null, null, null, null, "archive", null, null, Archiver.config.download.numVideos).execute();

        Archiver.LOGGER.info("Got {} videos from channel {}", videos.getVideos().size(), user.getLogin());
        for (Video video : videos.getVideos()) {
            if (videoDownloaders.stream().anyMatch(downloader -> downloader.video.getId().equals(video.getId()))) {
                // Video downloader already exists, don't create it again
                continue;
            }

            Vod vod = getVod(video.getId());
            if (stream != null && video.getStreamId().equals(stream.getId())) {
                // vod has a current stream associated
                new Thread(() -> new VideoDownloader(this, video, vod, stream)).start();
            } else {
                // no current stream
                new Thread(() -> new VideoDownloader(this, video, vod, null)).start();
            }
        }
    }

    public void addVideoFromStream(Stream stream) {
        this.stream = stream;
        // There's no easy api call to get a video from a stream id
        // so we just use the call from the original downloader to make things simpler
        downloadVideos();
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

    public Vod getVod(String vodId) {
        for (Vod vod : vods) {
            if (vod.id.equals(vodId)) return vod;
        }
        return new Vod(vodId, user.getId(), false, false, false).create();
    }
}
