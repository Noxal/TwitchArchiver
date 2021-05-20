package sr.will.archiver.twitch.chat;

import sr.will.archiver.Archiver;
import sr.will.archiver.twitch.vod.VodDownloader;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ChatDownloader {
    public final VodDownloader vodDownloader;
    public final List<ChatSectionDownloader> sections = new ArrayList<>();
    public final List<UUID> comments = new ArrayList<>();
    public boolean done = false;

    public ChatDownloader(VodDownloader vodDownloader) {
        this.vodDownloader = vodDownloader;

        run();
    }

    public void run() {
        int offset = 0;
        if (vodDownloader.stream != null) {
            // Stream is live, check what the the offset of the last comment was

        }

        sections.add(new ChatSectionDownloader(this, Direction.FORWARD, null, offset));
        // This won't work until we have get a video length to pass to this
        //sections.add(new CommentSectionDownloader(this, Direction.BACKWARD, null, vodDownloader.vod.length));
    }

    public void checkCompleted() {
        int partsCompleted = getPartsCompleted();
        if (partsCompleted < sections.size()) return;

        Archiver.LOGGER.info("Done downloading chat, requests: {}, comments: {}", sections.size(), comments.size());

        done = true;
        // TODO uncomment when chat testing is finished
        vodDownloader.checkCompleted();
    }

    public int getPartsCompleted() {
        synchronized (sections) {
            return Math.toIntExact(sections.stream().filter(sections -> sections.done).count());
        }
    }
}
