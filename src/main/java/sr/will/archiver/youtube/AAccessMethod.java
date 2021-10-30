package sr.will.archiver.youtube;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.http.HttpRequest;
import sr.will.archiver.Archiver;

import java.io.IOException;

public class AAccessMethod implements Credential.AccessMethod {
    @Override
    public void intercept(HttpRequest request, String accessToken) throws IOException {
        Archiver.LOGGER.info("intercepted request with uri: {}, access token {}", request.getUrl().toString(), accessToken);
    }

    @Override
    public String getAccessTokenFromRequest(HttpRequest request) {
        Archiver.LOGGER.info("getting access token from request: {}", request.getUrl().toString());
        return null;
    }
}
