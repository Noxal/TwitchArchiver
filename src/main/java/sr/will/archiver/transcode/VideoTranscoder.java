package sr.will.archiver.transcode;

import net.bramp.ffmpeg.builder.FFmpegBuilder;
import net.bramp.ffmpeg.probe.FFmpegFormat;
import net.bramp.ffmpeg.probe.FFmpegProbeResult;
import sr.will.archiver.Archiver;
import sr.will.archiver.entity.Vod;
import sr.will.archiver.notification.NotificationEvent;
import sr.will.archiver.transcode.chatrender.ChatRenderer;

import java.io.*;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

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
        Archiver.instance.webhookManager.execute(NotificationEvent.TRANSCODE_START, vod);

        BufferedReader playlistReader = null;
        BufferedWriter playlistWriter = null;
        try {
            // Process playlist file, replaces any muted segments in the latest playlist with any unmuted segments we may have from the stream
            File originalPlaylist = new File(vod.getDownloadDir(), "index-original.m3u8");
            File playlist = new File(vod.getDownloadDir(), "index.m3u8");
            playlistReader = new BufferedReader(new FileReader(originalPlaylist));
            playlistWriter = new BufferedWriter(new FileWriter(playlist));

            List<String> existingFiles = Arrays.stream(new File(vod.getDownloadDir()).listFiles()).map(File::getName).collect(Collectors.toList());
            String line;
            while ((line = playlistReader.readLine()) != null) {
                if (!line.startsWith("#") && line.contains("-muted")) {
                    String newLine = line.replace("-muted", "");
                    if (existingFiles.contains(newLine)) {
                        Archiver.LOGGER.info("Found unmuted file {}, replacing {}", newLine, line);
                        line = newLine;
                    }
                }

                playlistWriter.write(line);
                playlistWriter.newLine();
            }

            playlistReader.close();
            playlistWriter.close();

            // Attempt to transcode with ffmpeg
            new File(vod.getTranscodeDir()).mkdirs();
            probe = TranscodeManager.ffprobe.probe(playlist.getPath());

            FFmpegFormat format = probe.getFormat();
            Archiver.LOGGER.info("vod length: {}s ({} minutes)", format.duration, (int) Math.ceil(format.duration / 60));

            if (Archiver.getArchiveSet(vod.channelId).renderChat) {
                transcodeWithChat();
            } else {
                transcodeWithoutChat();
            }

            vod.setTranscoded();

            Archiver.LOGGER.info("Completed transcode for vod {} on channel {}", vod.id, vod.channelId);
            Archiver.instance.webhookManager.execute(NotificationEvent.TRANSCODE_FINISH, vod);

            synchronized (manager.transcoders) {
                manager.transcoders.remove(this);
            }

            Archiver.instance.youTubeManager.upload(vod);
        } catch (Exception e) {
            Archiver.LOGGER.error("Failed to transcode vod {} on channel {}", vod.id, vod.channelId);
            Archiver.instance.webhookManager.execute(NotificationEvent.TRANSCODE_FAIL, vod);
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

    public void transcodeWithChat() throws Exception {
        long chatStartTime = System.currentTimeMillis();

        ChatRenderer chatRenderer = new ChatRenderer(this);
        chatRenderer.renderThirdParty();

        Archiver.LOGGER.info("Finished rendering chat in {} seconds", (System.currentTimeMillis() - chatStartTime) / 100);

        long transcodeTime = System.currentTimeMillis();

        FFmpegBuilder builder = new FFmpegBuilder()
                .addInput(vod.getTranscodeDir() + "chat.mkv")
                .addInput(vod.getTranscodeDir() + "chat_mask.mkv")
                .addInput(probe)
                .setComplexFilter(Archiver.getArchiveSet(vod.channelId).chatRender.filter)
                .addOutput(vod.getTranscodeDir() + vod.id + "." + Archiver.config.transcode.outputFileType)
                .setVideoCodec(Archiver.config.chatRender.encoder)
                .setAudioCodec("copy")
                .addExtraArgs("-copyts", "-start_at_zero")
                .done();

        TranscodeManager.executor.createJob(builder).run();

        Archiver.LOGGER.info("Finished transcoding with chat in {} seconds", (System.currentTimeMillis() - transcodeTime) / 100);
    }

    public void transcodeWithoutChat() {
        FFmpegBuilder builder = new FFmpegBuilder()
                .addInput(probe)
                .addOutput(vod.getTranscodeDir() + vod.id + "." + Archiver.config.transcode.outputFileType)
                .setVideoCodec("copy")
                .setAudioCodec("copy")
                .addExtraArgs("-copyts", "-start_at_zero")
                .done();

        TranscodeManager.executor.createJob(builder).run();
    }
}
