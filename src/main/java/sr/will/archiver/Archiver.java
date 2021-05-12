package sr.will.archiver;

import com.github.philippheuer.events4j.simple.SimpleEventHandler;
import com.github.twitch4j.TwitchClient;
import com.github.twitch4j.TwitchClientBuilder;
import com.github.twitch4j.helix.domain.Stream;
import com.github.twitch4j.helix.domain.StreamList;
import com.github.twitch4j.helix.domain.User;
import com.github.twitch4j.helix.domain.UserList;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sr.will.archiver.config.Config;
import sr.will.archiver.ffmpeg.TranscodeManager;
import sr.will.archiver.sql.Database;
import sr.will.archiver.sql.Migrations;
import sr.will.archiver.twitch.ChannelDownloader;
import sr.will.archiver.twitch.EventHandler;
import sr.will.archiver.util.FileUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;

public class Archiver {
    public static Archiver instance;

    public static final Logger LOGGER = LoggerFactory.getLogger("Archiver");
    public static final Gson GSON = new GsonBuilder().create();

    public static ThreadPoolExecutor downloadExecutor;
    public static ThreadPoolExecutor transcodeExecutor;
    public static ThreadPoolExecutor uploadExecutor;
    public static ScheduledThreadPoolExecutor scheduledExecutor;
    public static Database database;
    public static TwitchClient twitchClient;

    public static Config config;

    public final List<ChannelDownloader> channelDownloaders = new ArrayList<>();
    public final TranscodeManager transcodeManager;

    public Archiver() {
        instance = this;
        long startTime = System.currentTimeMillis();

        database = new Database();

        reload();

        Migrations.deploy();

        downloadExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(Archiver.config.download.threads);
        transcodeExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(Archiver.config.transcode.threads);
        uploadExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(Archiver.config.upload.threads);
        scheduledExecutor = (ScheduledThreadPoolExecutor) Executors.newScheduledThreadPool(10);

        initializeTwitchClient();
        transcodeManager = new TranscodeManager();

        LOGGER.info("Done after {}ms!", System.currentTimeMillis() - startTime);
    }

    public void stop() {
        LOGGER.info("Stopping!");

        System.exit(0);
    }

    public void reload() {
        config = FileUtil.getConfig();
        FileUtil.saveConfig(config);

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
            twitchClient.getClientHelper().enableStreamEventListener(user.getId(), user.getLogin());
            LOGGER.info("Subscribed to events for channel {}", user.getLogin());

            Stream stream = streams.getStreams().stream().filter(s -> s.getUserId().equals(user.getId())).findFirst().orElse(null);
            new Thread(() -> new ChannelDownloader(user, stream)).start();
        }
    }

    public ChannelDownloader getChannelDownloader(String channel) {
        for (ChannelDownloader downloader : channelDownloaders) {
            if (downloader.user.getLogin().equals(channel)) return downloader;
        }

        return null;
    }
}
