package sr.will.archiver.twitch.chat;

import sr.will.archiver.Archiver;
import sr.will.archiver.notification.NotificationEvent;
import sr.will.archiver.twitch.model.Comments;

import javax.net.ssl.HttpsURLConnection;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
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

        Archiver.chatDownloadExecutor.submit(this::run);
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

            if ((direction == Direction.FORWARD && comments._next == null) || // going forward and no next
                    (direction == Direction.BACKWARD && comments._prev == null) || // going backward and no prev
                    (direction == Direction.BOTH && (comments._next == null || comments._prev == null))) { // going both and either is missing
                // We've reached an end of the video
                Archiver.LOGGER.info("Reached the end for direction {}", direction);
                insertMessages(comments);
                return;
            }

            // Collect all the comment UUIDs
            List<UUID> commentIds = comments.comments.stream()
                    .map(comment -> UUID.fromString(comment._id))
                    .collect(Collectors.toList());

            // Add all the comment UUIDs to the master list. If any exist then we've overlapped with another section downloader
            // and should not download the next segment
            synchronized (chatDownloader.comments) {
                for (UUID id : commentIds) {
                    if (chatDownloader.comments.contains(id)) {
                        Archiver.LOGGER.info("Chat overlapped at offset {} going {}, marking thread as complete",
                                comments.comments.stream()
                                        .filter(comment -> comment._id.equals(id.toString()))
                                        .map(comment -> comment.content_offset_seconds)
                                        .findFirst().orElse(-1d),
                                direction
                        );
                        insertMessages(comments);
                        return;
                    }

                    chatDownloader.comments.add(id);
                }
            }

            // Queue the next section
            synchronized (chatDownloader.sections) {
                // keep going in whatever direction it's going
                // for both directions we start two separate downloaders in each direction
                if (direction == Direction.FORWARD || direction == Direction.BOTH)
                    chatDownloader.sections.add(new ChatSectionDownloader(chatDownloader, Direction.FORWARD, comments._next, 0));
                if (direction == Direction.BACKWARD || direction == Direction.BOTH)
                    chatDownloader.sections.add(new ChatSectionDownloader(chatDownloader, Direction.BACKWARD, comments._prev, 0));
            }

            insertMessages(comments);
        } catch (Exception e) {
            Archiver.LOGGER.error("Unable to get chat for vod {} on channel {}", chatDownloader.vodDownloader.vod.id, chatDownloader.vodDownloader.vod.channelId);
            Archiver.instance.webhookManager.execute(NotificationEvent.DOWNLOAD_FAIL, chatDownloader.vodDownloader.vod);
            e.printStackTrace();
        }
    }

    private void insertMessages(Comments comments) {
        // Insert messages into the db
        // This is not a very pretty or performant way of doing things and should probably be looked at in the future
        StringBuilder queryBuilder = new StringBuilder("REPLACE INTO chat (id, channel, vod, offset, author, message) VALUES ");
        List<Object> queryObjects = new ArrayList<>();

        for (Comments.Comment comment : comments.comments) {
            queryBuilder.append("(?, ?, ?, ?, ?, ?), ");
            queryObjects.addAll(Arrays.asList(
                    comment._id,
                    comment.channel_id,
                    comment.content_id,
                    comment.content_offset_seconds,
                    comment.commenter.display_name,
                    Archiver.GSON.toJson(comment.message)
            ));
        }
        queryBuilder.delete(queryBuilder.length() - 2, queryBuilder.length());
        queryBuilder.append(";");
        Archiver.database.execute(queryBuilder.toString(), queryObjects.toArray());

        // Mark completed
        done = true;
        chatDownloader.checkCompleted();
    }
}
