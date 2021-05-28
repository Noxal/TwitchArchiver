package sr.will.archiver.entity;

import sr.will.archiver.Archiver;
import sr.will.archiver.config.Config;

public class LocalVod {
    public Type type;
    public String channelId;
    public String vodId;

    public LocalVod(Type type, String channelId, String vodId) {
        this.type = type;
        this.channelId = channelId;
        this.vodId = vodId;
    }

    public enum Type {
        DOWNLOADED,
        TRANSCODED;

        public String getPath() {
            switch (this) {
                case DOWNLOADED:
                    return Archiver.config.download.directory;
                case TRANSCODED:
                    return Archiver.config.transcode.directory;
                default:
                    return null;
            }
        }
    }

    public void delete() {

    }

    public boolean shouldDelete() {
        Vod vod = Archiver.instance.getVod(vodId);
        Config.ArchiveSet.DeletionPolicy policy = getDeletionPolicy();

        return false;
    }

    private Config.ArchiveSet.DeletionPolicy getDeletionPolicy() {
        switch (this.type) {
            case DOWNLOADED:
                return Archiver.getArchiveSet(channelId).downloadDeletionPolicy;
            case TRANSCODED:
                return Archiver.getArchiveSet(channelId).transcodeDeletionPolicy;
            default:
                return null;
        }
    }
}
