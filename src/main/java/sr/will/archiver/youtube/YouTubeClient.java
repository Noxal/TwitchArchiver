package sr.will.archiver.youtube;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.youtube.YouTube;
import sr.will.archiver.Archiver;
import sr.will.archiver.config.Config;
import sr.will.archiver.entity.Vod;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;

public class YouTubeClient {
    private final YouTubeManager manager;
    public final Config.ArchiveSet.Google google;
    public final List<VideoUploader> uploaders = new ArrayList<>();
    public YouTube youTube;

    public YouTubeClient(YouTubeManager manager, Config.ArchiveSet.Google google) {
        this.manager = manager;
        this.google = google;

        Archiver.uploadExecutor.submit(this::run);
    }

    public void run() {
        try {
            NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();

            GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                    httpTransport,
                    YouTubeManager.jsonFactory,
                    google.clientId,
                    google.clientSecret,
                    YouTubeManager.scopes
            )
                    .setDataStoreFactory(new FileDataStoreFactory(new File("google/" + google.clientId)))
                    .setAccessType("offline")
                    .build();
            Credential credential = new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver.Builder().setPort(80).build()).authorize("user");

            youTube = new YouTube.Builder(httpTransport, YouTubeManager.jsonFactory, credential)
                    .setApplicationName("Archiver")
                    .build();
        } catch (IOException | GeneralSecurityException e) {
            Archiver.LOGGER.error("Failed to initialize YouTube manager, not uploading");
            e.printStackTrace();
            return;
        }
    }

    public void upload(Vod vod) {
        uploaders.add(new VideoUploader(this, vod));
    }
}
