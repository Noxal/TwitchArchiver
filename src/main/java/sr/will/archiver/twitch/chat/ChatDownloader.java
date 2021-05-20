package sr.will.archiver.twitch.chat;

import sr.will.archiver.Archiver;
import sr.will.archiver.twitch.vod.VodDownloader;

import java.sql.ResultSet;
import java.sql.SQLException;
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
    }

    public void run() {
        float offset = 0;
        if (vodDownloader.stream != null) {
            // Stream is live, check what the the offset of the last comment was
            offset = getChatStartOffset();
        }

        float duration = vodDownloader.getDuration();

        // Start at the beginning of the video going forward
        sections.add(new ChatSectionDownloader(this, Direction.FORWARD, null, offset));

        // Start at the end of the video going backwards
        sections.add(new ChatSectionDownloader(this, Direction.BACKWARD, null, duration));

        // Start at the middle going both directions
        sections.add(new ChatSectionDownloader(this, Direction.BOTH, null, ((duration - offset) / 2) + offset));
    }

    public float getChatStartOffset() {
        ResultSet result = Archiver.database.query("SELECT (offset) FROM chat WHERE vod = ? ORDER BY offset DESC LIMIT 1;", vodDownloader.vod.id);
        try {
            if (!result.first()) return 0; // no chat messages in table, start from the beginning
            return result.getFloat("offset"); // send the offset of the latest message
        } catch (SQLException e) {
            Archiver.LOGGER.error("Failed to get last chat message offset, starting from the beginning");
            return 0;
        }
    }

    public void checkCompleted() {
        int partsCompleted = getPartsCompleted();
        if (partsCompleted < sections.size()) return;

        Archiver.LOGGER.info("Done downloading chat, requests: {}, comments: {}", sections.size(), comments.size());

        comments.clear();

        done = true;
        vodDownloader.checkCompleted();
    }

    public int getPartsCompleted() {
        synchronized (sections) {
            return Math.toIntExact(sections.stream().filter(sections -> sections.done).count());
        }
    }
}
