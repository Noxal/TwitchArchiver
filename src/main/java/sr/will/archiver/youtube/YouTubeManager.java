package sr.will.archiver.youtube;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.youtube.YouTube;
import sr.will.archiver.Archiver;
import sr.will.archiver.config.Config;
import sr.will.archiver.entity.Vod;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class YouTubeManager {
    public final List<VideoUploader> uploaders = new ArrayList<>();
    public YouTube youTube;

    public static final Collection<String> scopes = Arrays.asList(
            "https://www.googleapis.com/auth/youtube.upload",
            "https://www.googleapis.com/auth/youtube.force-ssl"
    );

    private static final JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();

    public YouTubeManager() {
        Archiver.uploadExecutor.submit(this::run);
    }

    public void run() {
        try {
            NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();

            GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                    httpTransport,
                    jsonFactory,
                    Archiver.config.google.clientId,
                    Archiver.config.google.clientSecret,
                    scopes
            )
                                                       .setDataStoreFactory(new FileDataStoreFactory(new File("")))
                                                       .setAccessType("offline")
                                                       .build();
            Credential credential = new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver.Builder().setPort(80).build()).authorize("user");

            youTube = new YouTube.Builder(httpTransport, jsonFactory, credential)
                              .setApplicationName("Archiver")
                              .build();
        } catch (IOException | GeneralSecurityException e) {
            Archiver.LOGGER.error("Failed to initialize YouTube manager, not uploading");
            e.printStackTrace();
            return;
        }

        List<Vod> vods = Archiver.getVods(Archiver.database.query("SELECT * FROM vods WHERE transcoded = true AND uploaded = false;"));
        Archiver.LOGGER.info("Got {} videos to upload", vods.size());

        vods.forEach(this::upload);
        Archiver.LOGGER.info("Queued {} videos for upload", uploaders.size());
    }

    public void upload(Vod vod) {
        Config.ArchiveSet archiveSet = Archiver.getArchiveSet(vod.channelId);
        if (archiveSet == null) {
            Archiver.LOGGER.error("Failed to locate config for channel {}", vod.channelId);
            return;
        }

        if (archiveSet.upload) uploaders.add(new VideoUploader(this, vod));
    }
}
