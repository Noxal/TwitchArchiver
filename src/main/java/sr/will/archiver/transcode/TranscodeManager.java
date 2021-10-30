package sr.will.archiver.transcode;

import net.bramp.ffmpeg.FFmpeg;
import net.bramp.ffmpeg.FFmpegExecutor;
import net.bramp.ffmpeg.FFprobe;
import sr.will.archiver.Archiver;
import sr.will.archiver.entity.Vod;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class TranscodeManager {
    public final List<VideoTranscoder> transcoders = new ArrayList<>();
    public static FFmpeg ffmpeg;
    public static FFprobe ffprobe;
    public static FFmpegExecutor executor;

    public TranscodeManager() {
        try {
            ffmpeg = new FFmpeg(Archiver.config.transcode.ffmpegLocation);
            ffprobe = new FFprobe(Archiver.config.transcode.ffprobeLocation);
            executor = new FFmpegExecutor(ffmpeg, ffprobe);
        } catch (IOException e) {
            Archiver.LOGGER.error("Unable to initialize ffmpeg, not transcoding");
            e.printStackTrace();
            return;
        }

        List<Vod> vods = Archiver.getVods(Archiver.database.query("SELECT * FROM vods WHERE downloaded = true AND transcoded = false;"));
        Archiver.LOGGER.info("Got {} videos to transcode", vods.size());

        vods.forEach(this::transcode);
        Archiver.LOGGER.info("Queued {} videos for transcode", transcoders.size());
    }

    public void transcode(Vod vod) {
        transcoders.add(new VideoTranscoder(this, vod));
    }
}
