package sr.will.archiver.twitch;

public enum DownloadPriority {
    CHANNEL(1),
    VOD(2),
    CHAT_PART(5),
    VOD_PART(10);

    public final int priority;

    DownloadPriority(int priority) {
        this.priority = priority;
    }
}
