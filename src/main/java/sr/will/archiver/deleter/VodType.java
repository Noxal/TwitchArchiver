package sr.will.archiver.deleter;

import sr.will.archiver.Archiver;

public enum VodType {
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
