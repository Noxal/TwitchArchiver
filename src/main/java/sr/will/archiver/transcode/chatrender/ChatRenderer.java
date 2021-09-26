package sr.will.archiver.transcode.chatrender;

import sr.will.archiver.Archiver;
import sr.will.archiver.entity.Comment;
import sr.will.archiver.entity.Message;
import sr.will.archiver.entity.SimpleComment;
import sr.will.archiver.transcode.VideoTranscoder;
import sr.will.archiver.transcode.chatrender.model.MComments;
import sr.will.archiver.util.FileUtil;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ChatRenderer {
    private final VideoTranscoder transcoder;
    public List<MessageRenderer> messageRenderers = new ArrayList<>();

    public ChatRenderer(VideoTranscoder transcoder) {
        this.transcoder = transcoder;

        Archiver.transcodeExecutor.submit(this::run);
    }

    public void run() {
        Archiver.LOGGER.info("Starting chat render for vod {} on channel {}", transcoder.vod.id, transcoder.vod.channelId);

        try {
            renderMessages();
            //renderThirdParty();
        } catch (Exception e) {
            Archiver.LOGGER.error("Failed to render chat for vod {} on channel {}", transcoder.vod.id, transcoder.vod.channelId);
            e.printStackTrace();
        }
    }

    public void renderMessages() throws SQLException {
        ResultSet result = Archiver.database.query("SELECT id, `offset`, author, message FROM chat WHERE vod = ? ORDER BY `offset` ASC;", transcoder.vod.id);
        while (result.next()) {
            messageRenderers.add(new MessageRenderer(new SimpleComment(
                    transcoder.vod,
                    result.getString("id"),
                    result.getDouble("offset"),
                    result.getString("author"),
                    result.getString("message")
            )));
            break;
        }
    }

    public void renderThirdParty() throws Exception {
        createJsonFile();

        List<String> args = new ArrayList<>(Arrays.asList(
                Archiver.config.twitchDownloader.path,
                "--ffmpeg-path", Archiver.config.transcode.ffmpegLocation,
                "--temp-path", Archiver.config.twitchDownloader.tempPath,
                "-m", "ChatRender",
                "-i", transcoder.vod.getTranscodeDir() + "chat.json",
                "-o", transcoder.vod.getTranscodeDir() + "chat.mov"
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
    }

    public void createJsonFile() throws SQLException {
        MComments comments = new MComments(new MComments.Streamer(Archiver.instance.usernames.get(transcoder.vod.channelId), transcoder.vod.channelId), new MComments.Video(transcoder.probe.getFormat().duration));

        ResultSet result = Archiver.database.query("SELECT id, `offset`, author, message FROM chat WHERE vod = ? ORDER BY `offset` ASC;", transcoder.vod.id);
        while (result.next()) {
            Comment comment = new Comment();

            comment._id = result.getString("id");
            comment.channel_id = transcoder.vod.channelId;
            comment.content_id = transcoder.vod.id;
            comment.content_offset_seconds = result.getDouble("offset");

            comment.commenter = new Comment.Commenter();
            comment.commenter.display_name = result.getString("author");

            comment.message = Archiver.GSON.fromJson(result.getString("message"), Message.class);

            comment.source = "chat";

            comments.comments.add(comment);
        }

        FileUtil.writeGson(comments, transcoder.vod.getTranscodeDir(), "chat", "chat for vod " + transcoder.vod.id);
    }
}
