package sr.will.archiver.twitch.chat;

import sr.will.archiver.Archiver;
import sr.will.archiver.notification.NotificationEvent;
import sr.will.archiver.twitch.DownloadPriority;
import sr.will.archiver.twitch.model.Comments;

import javax.net.ssl.HttpsURLConnection;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class ChatSectionDownloader {
    public final ChatDownloader chatDownloader;
    public final Direction direction;
    public final String cursor;
    public final double offset;
    public boolean done = false;

    public ChatSectionDownloader(ChatDownloader chatDownloader, Direction direction, String cursor, double offset) {
        this.chatDownloader = chatDownloader;
        this.direction = direction;
        this.cursor = cursor;
        this.offset = offset;

        Archiver.downloadExecutor.submit(this::run, null, DownloadPriority.CHAT_PART.priority);
    }

    public void run() {
        try {
            String urlString = "https://api.twitch.tv/v5/videos/" + chatDownloader.vodDownloader.vod.id + "/comments?";
            if (cursor != null) urlString += "cursor=" + cursor;
            else if (offset != -1) urlString += "content_offset_seconds=" + offset;

            URL url = new URL(urlString);
            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
            connection.setRequestProperty("Accept", "*/*");
            connection.setRequestProperty("Client-ID", Archiver.TWITCH_CLIENT_ID);
            connection.connect();

            Comments comments = Archiver.GSON.fromJson(new InputStreamReader(connection.getInputStream()), Comments.class);
            Archiver.LOGGER.info("Got {} chat messages", comments.comments.size());

            String next = null;
            if (direction == Direction.FORWARD) next = comments._next;
            else if (direction == Direction.BACKWARD) next = comments._prev;
            if (next == null) {
                // We've reached an end of the video
                markComplete();
                return;
            }

            // Collect all the commend UUIDs
            List<UUID> commentIds = comments.comments.stream()
                    .map(comment -> UUID.fromString(comment._id))
                    .collect(Collectors.toList());

            // Add all the comment UUIDs to the master list. If any exist then we've overlapped with another section downloader
            // and should not download the next segment
            boolean overlap = false;
            synchronized (chatDownloader.comments) {
                for (UUID id : commentIds) {
                    if (chatDownloader.comments.contains(id)) {
                        overlap = true;
                        continue;
                    }

                    chatDownloader.comments.add(id);
                }
            }

            if (overlap) {
                Archiver.LOGGER.info("Chat overlapped, marking thread as complete");
                markComplete();
                return;
            }

            // Queue the next section
            synchronized (chatDownloader.sections) {
                chatDownloader.sections.add(
                        new ChatSectionDownloader(chatDownloader, direction, next, 0)
                );
            }

            // Put the crap in the database
            // TODO

            markComplete();
        } catch (Exception e) {
            Archiver.LOGGER.error("Unable to get chat for vod {} on channel {}", chatDownloader.vodDownloader.vod.id, chatDownloader.vodDownloader.vod.channelId);
            Archiver.instance.webhookManager.execute(NotificationEvent.DOWNLOAD_FAIL, chatDownloader.vodDownloader.vod);
            e.printStackTrace();
        }

    }

    public void markComplete() {
        done = true;
        chatDownloader.checkCompleted();
    }
}
