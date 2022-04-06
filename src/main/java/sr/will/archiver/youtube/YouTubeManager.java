package sr.will.archiver.youtube;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import sr.will.archiver.Archiver;
import sr.will.archiver.config.ArchiveSet;
import sr.will.archiver.entity.Vod;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class YouTubeManager {
    public static final GsonFactory jsonFactory = GsonFactory.getDefaultInstance();
    public static final Collection<String> scopes = List.of(
            "https://www.googleapis.com/auth/youtube.readonly",
            "https://www.googleapis.com/auth/youtube.upload",
            "https://www.googleapis.com/auth/youtube.force-ssl"
    );

    public final Map<String, ChannelUploader> channelUploaders = new HashMap<>();
    public final Map<String, ChannelUploader> pendingCredentials = new HashMap<>();

    public YouTubeManager() {
        Archiver.uploadExecutor.submit(this::run);
    }

    public void run() {
        try {
            NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();

            GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                    httpTransport,
                    jsonFactory,
                    Archiver.config.upload.google.clientId,
                    Archiver.config.upload.google.clientSecret,
                    scopes
            )
                    .setCredentialDataStore(new DBDataStore<>("credentials", Archiver.database, null))
                    .setAccessType("offline")
                    .build();

            for (ArchiveSet archiveSet : Archiver.config.archiveSets) {
                // Ignore sets that have uploading disabled
                // Ignore duplicate sets
                if (!archiveSet.upload || channelUploaders.containsKey(archiveSet.youTubeUser)) continue;
                channelUploaders.put(archiveSet.youTubeUser, new ChannelUploader(this, archiveSet.youTubeUser, flow));
            }
        } catch (Exception e) {
            Archiver.LOGGER.error("Failed to initialize YouTube client, not uploading");
            e.printStackTrace();
        }

        List<Vod> vods = Archiver.getVods(Archiver.database.query("SELECT * FROM vods WHERE transcoded = true AND uploaded = false;"));
        Archiver.LOGGER.info("Got {} videos to upload", vods.size());

        vods.forEach(this::upload);
        Archiver.LOGGER.info("Queued {} videos for upload on {} channels", channelUploaders.values().stream().mapToInt(channelUploader -> channelUploader.videoUploaders.size()).sum(), channelUploaders.size());
    }

    public void upload(Vod vod) {
        ArchiveSet archiveSet = Archiver.getArchiveSet(vod.channelId);
        if (archiveSet == null) {
            Archiver.LOGGER.error("Failed to locate config for channel {}", vod.channelId);
            return;
        }

        if (archiveSet.upload) channelUploaders.get(archiveSet.youTubeUser).upload(vod);
        else Archiver.instance.deletionManager.run();
    }
}
