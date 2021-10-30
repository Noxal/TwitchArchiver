package sr.will.archiver.config;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

public class ArchiveSet implements Serializable {
    public String twitchUser;
    public String youTubeUser;
    public int numVideos = 2;
    public boolean downloadChat = true;
    public boolean renderChat = true;
    public boolean upload = false;
    public long chatDownloadThreadIncrement = 2 * 60 * 60;
    public ChatRenderSet chatRender = new ChatRenderSet();
    public YouTube youTube = new YouTube();
    public DeletionPolicy downloadDeletionPolicy = new DeletionPolicy();
    public DeletionPolicy transcodeDeletionPolicy = new DeletionPolicy();

    public static class ChatRenderSet {
        public Window window = new Window();

        // https://github.com/lay295/TwitchDownloader/blob/master/TwitchDownloaderCLI/README.md#arguments-for-mode-chatrender
        public List<String> args = Arrays.asList(
                "--background-color", "#00000000", // https://docs.microsoft.com/en-us/dotnet/api/skiasharp.skcolor.parse#remarks #AARRGGBB
                "--outline",
                "--generate-mask"//,
                //"--output-args='-c:v h264_nvenc -crf 18 -b:v 2M -pix_fmt yuv420p \\\"{save_path}\\\"'"
        );

        public String filter = "[0][1]alphamerge[ia];[2][ia]overlay=0:H/2-h/2";

        public static class Window {
            public int width = 350;
            public int height = 350;
        }

        public void validate() {
            if (args == null) args = new ChatRenderSet().args;
        }
    }

    public static class YouTube {
        public String title = "{date}: {title} Part {part}/{parts}";
        public String description = "Twitch vod streamed by {user} on {date}, {time}.\nVOD description: {description}";
        public String category = "20";
        public List<String> tags = Arrays.asList("Gaming", "Twitch");
        public boolean madeForKids = false;
        public boolean embeddable = true;
        @SerializedName("public")
        public boolean publicVideo = false;
    }

    public static class DeletionPolicy {
        public Mode mode = Mode.BOTH_MAX;
        public int maxNum = 2;
        public int maxAge = 7;

        public enum Mode {
            NUMBER,
            AGE,
            BOTH_MIN,
            BOTH_MAX
        }
    }

    public ArchiveSet(String twitchUser) {
        this.twitchUser = twitchUser;
        this.youTubeUser = twitchUser;
    }

    public void validate() {
        if (chatRender == null) chatRender = new ChatRenderSet();
        if (youTube == null) youTube = new YouTube();
        if (downloadDeletionPolicy == null) downloadDeletionPolicy = new DeletionPolicy();
        if (transcodeDeletionPolicy == null) transcodeDeletionPolicy = new DeletionPolicy();

        chatRender.validate();
    }
}
