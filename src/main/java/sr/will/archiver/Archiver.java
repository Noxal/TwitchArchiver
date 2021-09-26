package sr.will.archiver;

import com.github.philippheuer.events4j.simple.SimpleEventHandler;
import com.github.twitch4j.TwitchClient;
import com.github.twitch4j.TwitchClientBuilder;
import com.github.twitch4j.helix.domain.Stream;
import com.github.twitch4j.helix.domain.StreamList;
import com.github.twitch4j.helix.domain.User;
import com.github.twitch4j.helix.domain.UserList;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sr.will.archiver.config.Config;
import sr.will.archiver.deleter.DeletionManager;
import sr.will.archiver.entity.Vod;
import sr.will.archiver.transcode.TranscodeManager;
import sr.will.archiver.notification.WebhookManager;
import sr.will.archiver.sql.Database;
import sr.will.archiver.sql.Migrations;
import sr.will.archiver.twitch.ChannelDownloader;
import sr.will.archiver.twitch.EventHandler;
import sr.will.archiver.util.FileUtil;
import sr.will.archiver.youtube.YouTubeManager;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;

public class Archiver {
    public static Archiver instance;

    public static final String TWITCH_CLIENT_ID = "kimne78kx3ncx6brgo4mv6wki5h1ko";
    public static final Logger LOGGER = LoggerFactory.getLogger("Archiver");
    public static final Gson GSON = new GsonBuilder().create();

    public static ThreadPoolExecutor downloadExecutor;
    public static ThreadPoolExecutor chatDownloadExecutor;
    public static ThreadPoolExecutor transcodeExecutor;
    public static ThreadPoolExecutor uploadExecutor;
    public static ScheduledThreadPoolExecutor scheduledExecutor;
    public static Database database;
    public static TwitchClient twitchClient;

    public static Config config;

    public final Map<String, Vod> vods = new HashMap<>();
    public final Map<String, String> usernames = new HashMap<>();
    public final List<ChannelDownloader> channelDownloaders = new ArrayList<>();
    public final TranscodeManager transcodeManager;
    public final YouTubeManager youTubeManager;
    public final WebhookManager webhookManager;
    public final DeletionManager deletionManager;

    public Archiver() {
        instance = this;
        long startTime = System.currentTimeMillis();

        database = new Database();

        reload();

        Migrations.deploy();

        downloadExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(Archiver.config.download.threads, new ThreadFactoryBuilder().setNameFormat("download-%d").build());
        chatDownloadExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(Archiver.config.download.chatThreads, new ThreadFactoryBuilder().setNameFormat("chat-%d").build());
        transcodeExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(Archiver.config.transcode.threads, new ThreadFactoryBuilder().setNameFormat("transcode-%d").build());
        uploadExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(Archiver.config.upload.threads, new ThreadFactoryBuilder().setNameFormat("upload-%d").build());
        scheduledExecutor = (ScheduledThreadPoolExecutor) Executors.newScheduledThreadPool(10, new ThreadFactoryBuilder().setNameFormat("scheduled-%d").build());

        webhookManager = new WebhookManager();
        initializeTwitchClient();
        transcodeManager = new TranscodeManager();
        deletionManager = new DeletionManager();
        youTubeManager = new YouTubeManager();

        LOGGER.info("Done after {}ms!", System.currentTimeMillis() - startTime);
        LOGGER.info("The following line is to fool pterodactyl into thinking that the server has started successfully");
        LOGGER.info(")! For help, type ");
    }

    public void stop() {
        LOGGER.info("Stopping!");

        System.exit(0);
    }

    public void reload() {
        config = FileUtil.getConfig();
        try {
            config.validate();
            FileUtil.saveConfig(config);
        } catch (RuntimeException e) {
            Archiver.LOGGER.error("Configuration error: {}", e.getMessage());
            FileUtil.saveConfig(config);
            stop();
        }

        database.setCredentials(config.database.host, config.database.database, config.database.username, config.database.password);
        database.reconnect();
    }

    public void initializeTwitchClient() {
        twitchClient = TwitchClientBuilder.builder()
                               .withClientId(config.twitch.clientId)
                               .withClientSecret(config.twitch.clientSecret)
                               .withEnableHelix(true)
                               .build();
        twitchClient.getEventManager().getEventHandler(SimpleEventHandler.class).registerListener(new EventHandler());

        LOGGER.info("Getting user info...");
        List<String> userLogins = config.archiveSets.stream().map(archiveSet -> archiveSet.twitchUser).collect(Collectors.toList());
        UserList users = twitchClient.getHelix().getUsers(null, null, userLogins).execute();
        StreamList streams = twitchClient.getHelix().getStreams(null, null, null, null, null, null, null, userLogins).execute();

        LOGGER.info("Got {} users and {} streams", users.getUsers().size(), streams.getStreams().size());

        for (User user : users.getUsers()) {
            usernames.put(user.getId(), user.getLogin());
            twitchClient.getClientHelper().enableStreamEventListener(user.getId(), user.getLogin());

            Stream stream = streams.getStreams().stream().filter(s -> s.getUserId().equals(user.getId())).findFirst().orElse(null);

            LOGGER.info("Queuing channel {} ({}) {}", user.getId(), user.getLogin(), stream == null ? "" : "Currently streaming");
            channelDownloaders.add(new ChannelDownloader(user.getId(), stream));
        }

        Archiver.LOGGER.info("Queued {} channel downloads", channelDownloaders.size());
    }

    public ChannelDownloader getChannelDownloader(String channelId) {
        for (ChannelDownloader downloader : channelDownloaders) {
            if (downloader.userId.equals(channelId)) return downloader;
        }

        return null;
    }

    public static List<Vod> getVods(ResultSet resultSet) {
        List<Vod> vodList = new ArrayList<>();
        try {
            while (resultSet.next()) {
                vodList.add(new Vod(
                        resultSet.getString("id"),
                        resultSet.getString("channel_id"),
                        Instant.ofEpochMilli(resultSet.getLong("created_at")),
                        resultSet.getString("title"),
                        resultSet.getString("description"),
                        resultSet.getBoolean("downloaded"),
                        resultSet.getBoolean("transcoded"),
                        resultSet.getBoolean("uploaded"),
                        resultSet.getInt("parts")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return vodList;
    }

    public List<Vod> getVods(List<String> vodIds) {
        List<Vod> vodList = new ArrayList<>();

        StringBuilder queryBuilder = new StringBuilder("SELECT * FROM vods WHERE (");
        List<String> queryObjects = new ArrayList<>();

        for (String vodId : vodIds) {
            if (vods.containsKey(vodId)) {
                // If we already have the vod locally use that
                vodList.add(vods.get(vodId));
            } else {
                // Otherwise add it to the query to get from the db
                queryBuilder.append("id = ? OR");
                queryObjects.add(vodId);
            }
        }

        // If we're not getting any vods from the db we don't need to do anything else
        if (queryObjects.size() == 0) return vodList;

        queryBuilder.delete(queryBuilder.length() - 3, queryBuilder.length());
        queryBuilder.append(");");


        vodList.addAll(getVods(database.query(queryBuilder.toString(), queryObjects.toArray())));

        return vodList;
    }

    public Vod getVod(String vodId) {
        if (vods.containsKey(vodId)) return vods.get(vodId);
        List<Vod> vodList = getVods(List.of(vodId));
        if (vodList.size() == 0) return null;
        return getVods(List.of(vodId)).get(0);
    }

    public static Config.ArchiveSet getArchiveSet(String channelId) {
        String username = instance.usernames.get(channelId);
        for (Config.ArchiveSet set : config.archiveSets) {
            if (set.twitchUser.equalsIgnoreCase(username)) {
                return set;
            }
        }
        return null;
    }
}
