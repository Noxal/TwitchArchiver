package sr.will.archiver.transcode.chatrender;

import sr.will.archiver.Archiver;
import sr.will.archiver.transcode.VideoTranscoder;
import sr.will.archiver.transcode.chatrender.model.MComments;
import sr.will.archiver.twitch.model.Comments;
import sr.will.archiver.util.FileUtil;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ChatRenderer {
    private final VideoTranscoder transcoder;

    public ChatRenderer(VideoTranscoder transcoder) {
        this.transcoder = transcoder;

        Archiver.transcodeExecutor.submit(this::run);
    }

    public void run() {
        Archiver.LOGGER.info("Starting chat render for vod {} on channel {}", transcoder.vod.id, transcoder.vod.channelId);

        try {
            createJsonFile();

            List<String> args = new ArrayList<>(Arrays.asList(
                    Archiver.config.twitchDownloader.path,
                    "--ffmpeg-path", Archiver.config.transcode.ffmpegLocation,
                    "--temp-path", Archiver.config.twitchDownloader.tempPath,
                    "-m", "ChatRender",
                    "-i", transcoder.vod.getTranscodeDir() + "chat.json",
                    "-o", transcoder.vod.getTranscodeDir() + "chat.mp4"
            ));
            args.addAll(Archiver.getArchiveSet(transcoder.vod.channelId).chatRender.args);

            ProcessBuilder builder = new ProcessBuilder(args);
            builder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            builder.redirectError(ProcessBuilder.Redirect.INHERIT);
            Archiver.LOGGER.info("command: {}", builder.command());
            Process process = builder.start();

            int result = process.waitFor();
            Archiver.LOGGER.info("command result: {}", result);

            Archiver.LOGGER.info("Finished chat render for vod {} on channel {}", transcoder.vod.id, transcoder.vod.channelId);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void createJsonFile() throws SQLException {
        MComments comments = new MComments(new MComments.Streamer(Archiver.instance.usernames.get(transcoder.vod.channelId), transcoder.vod.channelId), new MComments.Video(transcoder.probe.getFormat().duration));

        ResultSet result = Archiver.database.query("SELECT id, `offset`, author, message FROM chat WHERE vod = ? ORDER BY `offset` ASC;", transcoder.vod.id);
        while (result.next()) {
            comments.comments.add(new Comments.Comment(
                    result.getString("id"),
                    transcoder.vod.channelId,
                    transcoder.vod.id,
                    result.getDouble("offset"),
                    result.getString("author"),
                    result.getString("message")
            ));
        }

        FileUtil.writeGson(comments, transcoder.vod.getTranscodeDir(), "chat", "chat for vod " + transcoder.vod.id);
    }
}
