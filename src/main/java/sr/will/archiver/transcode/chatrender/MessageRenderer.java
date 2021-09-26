package sr.will.archiver.transcode.chatrender;

import com.google.gson.GsonBuilder;
import sr.will.archiver.Archiver;
import sr.will.archiver.entity.SimpleComment;

public class MessageRenderer {
    public SimpleComment comment;

    public MessageRenderer(SimpleComment comment) {
        this.comment = comment;

        Archiver.transcodeExecutor.submit(this::run);
    }

    public void run() {
        Archiver.LOGGER.info("Rendering chat message {}", comment.id);
        Archiver.LOGGER.info(new GsonBuilder().setPrettyPrinting().create().toJson(comment));

        long startTime = System.currentTimeMillis();

        //BufferedImage image = new BufferedImage()
    }
}
