package sr.will.archiver.twitch.model;

public class PlaybackAccessToken {
    public String token;
    public String signature;

    public PlaybackAccessToken(String token, String signature) {
        this.token = token;
        this.signature = signature;
    }
}
