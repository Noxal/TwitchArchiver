package sr.will.archiver.youtube;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.services.youtube.YouTube;
import sr.will.archiver.Archiver;
import sr.will.archiver.entity.Vod;

import java.io.IOException;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

public class ChannelUploader {
    public final YouTubeManager manager;
    public final String channelName;

    final List<VideoUploader> videoUploaders = new ArrayList<>();

    private final GoogleAuthorizationCodeFlow authFlow;
    private Credential credential;
    YouTube youTube;

    public ChannelUploader(YouTubeManager manager, String channelName, GoogleAuthorizationCodeFlow authFlow) {
        this.manager = manager;
        this.channelName = channelName;
        this.authFlow = authFlow;

        try {
            // If there is an existing credential, use that
            Credential credential = authFlow.loadCredential(channelName);
            if (credential != null) {
                this.credential = credential;
                Archiver.uploadExecutor.submit(this::run);
                return;
            }
        } catch (IOException e) {
            Archiver.LOGGER.error("Failed to initialize channel uploader for channel {}", channelName);
            e.printStackTrace();
            return;
        }

        // Generate the auth url along with a random state string so we know which user is authenticating with the callback
        String state = null;
        while (state == null || manager.pendingCredentials.containsKey(state)) {
            state = new BigInteger(130, new SecureRandom()).toString(32);
        }
        Archiver.LOGGER.info(state);
        manager.pendingCredentials.put(state, this);
        String url = authFlow.newAuthorizationUrl()
                .setRedirectUri(Archiver.config.upload.google.baseUrl + "/callback")
                .setState(state)
                .build();

        Archiver.LOGGER.error("You must authenticate with YouTube for account {}. Visit the following URL to complete authorization: {}", channelName, url);
    }

    public void doCallback(String state, String code) {
        try {
            GoogleTokenResponse tokenResponse = authFlow.newTokenRequest(code)
                    .setRedirectUri(Archiver.config.upload.google.baseUrl + "/callback")
                    .execute();
            credential = authFlow.createAndStoreCredential(tokenResponse, channelName);
            manager.pendingCredentials.remove(state);

            Archiver.uploadExecutor.submit(this::run);
        } catch (IOException e) {
            manager.pendingCredentials.remove(state);
            e.printStackTrace();
        }
    }

    public void run() {
        if (youTube == null)
            youTube = new YouTube.Builder(authFlow.getTransport(), YouTubeManager.jsonFactory, credential)
                    .setApplicationName("Archiver")
                    .build();

        for (VideoUploader uploader : videoUploaders) {
            if (!uploader.pending) continue;
            Archiver.uploadExecutor.submit(uploader::run);
        }
    }

    public void upload(Vod vod) {
        videoUploaders.add(new VideoUploader(this, vod, youTube == null));
    }
}
