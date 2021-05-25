package sr.will.archiver.youtube;

import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import sr.will.archiver.Archiver;
import sr.will.archiver.config.Config;
import sr.will.archiver.entity.Vod;

import java.util.*;

public class YouTubeManager {
    public final Map<String, YouTubeClient> clients = new HashMap<>();

    public static final Collection<String> scopes = Arrays.asList(
            "https://www.googleapis.com/auth/youtube.upload",
            "https://www.googleapis.com/auth/youtube.force-ssl"
    );

    public static final JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();

    public YouTubeManager() {
        for (Config.ArchiveSet archiveSet : Archiver.config.archiveSets) {
            // Don't try to create a client if uploading is disabled
            if (!archiveSet.upload) continue;

            // Don't create multiple clients for the same client id
            if (clients.containsKey(archiveSet.youTube.google.clientId)) continue;

            clients.put(archiveSet.youTube.google.clientId, new YouTubeClient(this, archiveSet.youTube.google));
        }

        Archiver.LOGGER.info("Created {} youtube clients", clients.size());

        List<Vod> vods = Archiver.getVods(Archiver.database.query("SELECT * FROM vods WHERE transcoded = true AND uploaded = false;"));
        Archiver.LOGGER.info("Got {} videos to upload", vods.size());

        vods.forEach(this::upload);
        Archiver.LOGGER.info("Queued {} videos for upload", clients.values().stream().mapToInt(youTubeClient -> youTubeClient.uploaders.size()).sum());
    }

    public void upload(Vod vod) {
        Config.ArchiveSet archiveSet = Archiver.getArchiveSet(vod.channelId);
        if (archiveSet == null) {
            Archiver.LOGGER.error("Failed to locate config for channel {}", vod.channelId);
            return;
        }

        if (archiveSet.upload) clients.get(archiveSet.youTube.google.clientId).upload(vod);
    }
}
