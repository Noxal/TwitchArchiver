package sr.will.archiver.transcode;

import net.bramp.ffmpeg.builder.FFmpegBuilder;
import net.bramp.ffmpeg.job.FFmpegJob;
import sr.will.archiver.Archiver;
import sr.will.archiver.entity.Vod;
import sr.will.archiver.notification.NotificationEvent;
import sr.will.archiver.transcode.chatrender.ChatRenderer;

import java.util.concurrent.TimeUnit;

public class PartTranscoder {
    public final VideoTranscoder videoTranscoder;
    public final Vod vod;
    public long startOffset;
    public long duration;
    public int part;
    public boolean withChat;
    public boolean done = false;

    public PartTranscoder(VideoTranscoder videoTranscoder, Vod vod, long startOffset, long duration, int part, boolean withChat) {
        this.videoTranscoder = videoTranscoder;
        this.vod = vod;
        this.startOffset = startOffset;
        this.duration = duration;
        this.part = part;
        this.withChat = withChat;

        Archiver.transcodeExecutor.submit(this::run);
    }

    public void run() {
        Archiver.LOGGER.info("Starting transcode for vod {} part {} on channel {}", vod.id, part, vod.channelId);

        try {
            if (withChat) transcodeChat();

            FFmpegJob job = createTranscodeJob();
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

    private FFmpegJob createTranscodeJob() {
        FFmpegBuilder builder = new FFmpegBuilder();

        if (withChat) {
            builder.addInput(vod.getTranscodeDir() + "chat.mkv");
            builder.addInput(vod.getTranscodeDir() + "chat_mask.mkv");
        }

        builder.addInput(videoTranscoder.probe);

        if (withChat) builder.setComplexFilter(Archiver.getArchiveSet(vod.channelId).chatRender.filter);

        builder.addOutput(vod.getTranscodeDir() + vod.id + "-" + part + "." + Archiver.config.transcode.outputFileType)
                .setStartOffset(startOffset, TimeUnit.MILLISECONDS)
                .setDuration(duration, TimeUnit.MILLISECONDS)
                .setVideoCodec(withChat ? Archiver.config.chatRender.encoder : "copy")
                .setAudioCodec("copy")
                .addExtraArgs("-copyts", "-start_at_zero")
                //"-bsf:a aac_adtstoasc"
                .done();
        return TranscodeManager.executor.createJob(builder);
    }

    private void transcodeChat() throws Exception {
        long chatStartTime = System.currentTimeMillis();

        ChatRenderer chatRenderer = new ChatRenderer(this);
        chatRenderer.renderThirdParty();

        Archiver.LOGGER.info("Finished rendering chat in {} seconds", (System.currentTimeMillis() - chatStartTime) / 100);
    }
}