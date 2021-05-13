package sr.will.archiver.ffmpeg;

import net.bramp.ffmpeg.builder.FFmpegBuilder;
import net.bramp.ffmpeg.probe.FFmpegFormat;
import net.bramp.ffmpeg.probe.FFmpegProbeResult;
import sr.will.archiver.Archiver;
import sr.will.archiver.entity.Vod;

import java.io.*;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class VideoTranscoder {
    private final TranscodeManager manager;
    public final Vod vod;
    public FFmpegProbeResult probe;
    public final List<PartTranscoder> parts = new ArrayList<>();

    public VideoTranscoder(TranscodeManager manager, Vod vod) {
        this.manager = manager;
        this.vod = vod;

        Archiver.transcodeExecutor.submit(this::run);
    }

    public void run() {
        Archiver.LOGGER.info("Starting transcode for vod {} on channel {}", vod.id, vod.channelId);

        BufferedReader playlistReader = null;
        BufferedWriter playlistWriter = null;
        try {
            // Process playlist file, replaces any muted segments in the latest playlist with any unmuted segments we may have from the stream
            File originalPlaylist = new File(vod.getDownloadDir(), "index-original.m3u8");
            File playlist = new File(vod.getDownloadDir(), "index.m3u8");
            playlistReader = new BufferedReader(new FileReader(originalPlaylist));
            playlistWriter = new BufferedWriter(new FileWriter(playlist));

            // TODO replace the files.exists calls with a single directory file list
            String line;
            while ((line = playlistReader.readLine()) != null) {
                if (!line.startsWith("#") && line.contains("-muted")) {
                    String newLine = line.replace("-muted", "");
                    if (Files.exists(new File(vod.getDownloadDir(), newLine).toPath())) {
                        Archiver.LOGGER.info("Found unmuted file {}, replacing {}", newLine, line);
                        line = newLine;
                    }
                }

                playlistWriter.write(line);
                playlistWriter.newLine();
            }

            playlistReader.close();
            playlistWriter.close();

            // Attempt to transcode into segments with ffmpeg
            new File(vod.getTranscodeDir()).mkdirs();
            probe = TranscodeManager.ffprobe.probe(playlist.getPath());

            FFmpegFormat format = probe.getFormat();
            Archiver.LOGGER.info("vod length: {}s ({} minutes), parts: {}", format.duration, (int) Math.ceil(format.duration / 60), (int) Math.ceil(format.duration / (Archiver.config.transcode.maxVideoLength * 60)));

            for (int i = 0; i < Math.ceil(format.duration / (Archiver.config.transcode.maxVideoLength * 60)); i++) {
                long startOffset = (long) Archiver.config.transcode.maxVideoLength * i * 60 * 1000;
                long duration = Math.min((long) (format.duration * 1000) - startOffset, (long) Archiver.config.transcode.maxVideoLength * 60 * 1000);

                FFmpegBuilder builder = new FFmpegBuilder()
                                                .setInput(probe)
                                                .addOutput(vod.getTranscodeDir() + vod.id + "-" + i + ".mp4")
                                                .setStartOffset(startOffset, TimeUnit.MILLISECONDS)
                                                .setDuration(duration, TimeUnit.MILLISECONDS)
                                                .setVideoCodec("copy")
                                                .setAudioCodec("copy")
                                                .addExtraArgs("-copyts", "-start_at_zero")
                                                //"-bsf:a aac_adtstoasc"
                                                .done();

                parts.add(new PartTranscoder(this, vod, TranscodeManager.executor.createJob(builder), i));
            }

            Archiver.LOGGER.info("Queued {} transcode parts for vod {} on channel {}", parts.size(), vod.id, vod.channelId);
        } catch (IOException e) {
            Archiver.LOGGER.error("Failed to transcode vod {} on channel {}", vod.id, vod.channelId);
            e.printStackTrace();
        } finally {
            try {
                if (playlistReader != null) playlistReader.close();
                if (playlistWriter != null) playlistWriter.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void checkCompleted() {
        if (getPartsCompleted() < parts.size()) return;

        Archiver.LOGGER.info("Completed transcode for vod {} on channel {}, queuing for upload", vod.id, vod.channelId);
        vod.setTranscoded(parts.size());

        synchronized (manager.transcoders) {
            manager.transcoders.remove(this);
        }

        Archiver.instance.youTubeManager.upload(vod);
    }

    public int getPartsCompleted() {
        synchronized (parts) {
            return Math.toIntExact(parts.stream().filter(part -> part.done).count());
        }
    }
}
