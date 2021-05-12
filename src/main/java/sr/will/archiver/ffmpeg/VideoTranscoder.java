package sr.will.archiver.ffmpeg;

import net.bramp.ffmpeg.builder.FFmpegBuilder;
import net.bramp.ffmpeg.probe.FFmpegFormat;
import net.bramp.ffmpeg.probe.FFmpegProbeResult;
import net.bramp.ffmpeg.progress.Progress;
import sr.will.archiver.Archiver;
import sr.will.archiver.sql.model.Vod;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class VideoTranscoder {
    private final TranscodeManager manager;
    public final Vod vod;
    public FFmpegProbeResult probe;

    public VideoTranscoder(TranscodeManager manager, Vod vod) {
        this.manager = manager;
        this.vod = vod;

        Archiver.transcodeExecutor.submit(this::run);
    }

    public void run() {
        Archiver.LOGGER.info("Starting transcode for vod {} on channel {}", vod.id, vod.channelId);

        try {
            new File(vod.getTranscodeDir()).mkdirs();
            probe = TranscodeManager.ffprobe.probe(vod.getDownloadDir() + "index.m3u8");

            FFmpegFormat format = probe.getFormat();
            Archiver.LOGGER.info("vod length: {}s ({} minutes), parts: {}", format.duration, (int) Math.ceil(format.duration / 60), (int) Math.ceil(format.duration / (Archiver.config.transcode.maxVideoLength * 60)));

            for (int i = 0; i < Math.ceil(format.duration / (Archiver.config.transcode.maxVideoLength * 60)); i++) {
                long startOffset = (long) Archiver.config.transcode.maxVideoLength * i * 60 * 1000;

                FFmpegBuilder builder = new FFmpegBuilder()
                                                .setInput(probe)
                                                .setStartOffset(startOffset, TimeUnit.MILLISECONDS)
                                                .addOutput(vod.getTranscodeDir() + vod.id + "-" + i + ".mp4")
                                                .setDuration(Math.min((long) (format.duration * 1000) - startOffset, (long) Archiver.config.transcode.maxVideoLength * 60 * 1000), TimeUnit.MILLISECONDS)
                                                .setVideoCodec("copy")
                                                .setAudioCodec("copy")
                                                .addExtraArgs("-copyts", "-start_at_zero")
                                                //"-bsf:a aac_adtstoasc"
                                                .done();

                TranscodeManager.executor.createJob(builder).run();
            }

            Archiver.LOGGER.info("Completed transcode for vod {} on channel {}", vod.id, vod.channelId);
            vod.setTranscoded();

            synchronized (manager.transcoders) {
                manager.transcoders.remove(this);
            }
        } catch (IOException e) {
            Archiver.LOGGER.error("Failed to transcode vod {} on channel {}", vod.id, vod.channelId);
            e.printStackTrace();
        }
    }

    public void onProgress(Progress progress) {
        double percentage = Math.floor((progress.out_time_ns / (probe.getFormat().duration * TimeUnit.SECONDS.toNanos(1))) * 100);

        Archiver.LOGGER.info("Transcode progress for vod {} on channel {}: {}%", vod.id, vod.channelId, percentage);
    }
}
