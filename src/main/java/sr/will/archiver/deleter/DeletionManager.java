package sr.will.archiver.deleter;

import sr.will.archiver.Archiver;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DeletionManager {
    public File downloadDir;
    public File transcodeDir;

    public List<ChannelDeleter> downloadDeleters = new ArrayList<>();
    public List<ChannelDeleter> transcodeDeleters = new ArrayList<>();

    public DeletionManager() {

        downloadDir = new File(Archiver.config.download.directory);
        transcodeDir = new File(Archiver.config.transcode.directory);

        for (String channelId : Archiver.instance.usernames.keySet()) {
            downloadDeleters.add(new ChannelDeleter(this, DeleterType.DOWNLOADS, channelId));
            transcodeDeleters.add(new ChannelDeleter(this, DeleterType.TRANSCODES, channelId));
        }

        deleteDownloads();
    }

    public void deleteDownloads() {
        // no files found
        String[] children = getChildren(downloadDir);
        if (children == null) return;

        Archiver.LOGGER.info("Downloaded channels:");
        for (String child : children) {
            Archiver.LOGGER.info(child);
        }
    }

    public static String[] getChildren(File file) {
        if (!file.exists() || !file.isDirectory()) {
            Archiver.LOGGER.warn("directory {} not found or invalid", file.getPath());
            return null;
        }

        return file.list();
    }
}