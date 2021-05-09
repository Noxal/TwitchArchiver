package sr.will.archiver.twitch.model;

public class PlaybackAccessTokenRequestTemplate {
    public String operationName = "PlaybackAccessToken_Template";
    public Variables variables = new Variables();
    public String query = "query PlaybackAccessToken_Template($login: String!, $isLive: Boolean!, $vodID: ID!, $isVod: Boolean!, $playerType: String!) {  streamPlaybackAccessToken(channelName: $login, params: {platform: \"web\", playerBackend: \"mediaplayer\", playerType: $playerType}) @include(if: $isLive) {    value    signature    __typename  }  videoPlaybackAccessToken(id: $vodID, params: {platform: \"web\", playerBackend: \"mediaplayer\", playerType: $playerType}) @include(if: $isVod) {    value    signature    __typename  }}";

    public PlaybackAccessTokenRequestTemplate(String videoId) {
        this.variables.vodID = videoId;
    }

    public static class Variables {
        public boolean isLive = false;
        public String login = "";
        public boolean isVod = true;
        public String vodID;
        public String playerType = "site";
    }
}
