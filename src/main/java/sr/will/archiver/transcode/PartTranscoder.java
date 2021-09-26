package sr.will.archiver.transcode;

import net.bramp.ffmpeg.job.FFmpegJob;
import sr.will.archiver.Archiver;
import sr.will.archiver.entity.Vod;
import sr.will.archiver.notification.NotificationEvent;

public class PartTranscoder {
    private final VideoTranscoder videoTranscoder;
    public final Vod vod;
    public final FFmpegJob job;
    public int part;
    public boolean done = false;

    public PartTranscoder(VideoTranscoder videoTranscoder, Vod vod, FFmpegJob job, int part) {
        this.videoTranscoder = videoTranscoder;
        this.vod = vod;
        this.job = job;
        this.part = part;

        //Archiver.transcodeExecutor.submit(this::run);
    }

    public void run() {
        Archiver.LOGGER.info("Starting transcode for vod {} part {} on channel {}", vod.id, part, vod.channelId);

        try {
            job.run();
        } catch (Exception e) {
            Archiver.LOGGER.error("Error transcoding vod {} part {} on channel {}", vod.id, part, vod.channelId);
            Archiver.instance.webhookManager.execute(NotificationEvent.TRANSCODE_FAIL, vod);
            e.printStackTrace();
            return;
        }

        Archiver.LOGGER.info("Completed transcode for vod {} part {} on channel {}", vod.id, part, vod.channelId);
        markComplete();
    }

    public void markComplete() {
        done = true;
        videoTranscoder.checkCompleted();
    }
}
