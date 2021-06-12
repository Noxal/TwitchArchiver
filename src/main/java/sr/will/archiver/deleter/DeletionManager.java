package sr.will.archiver.deleter;

import org.apache.commons.io.FileUtils;
import sr.will.archiver.Archiver;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class DeletionManager {
    public File downloadDir;
    public File transcodeDir;

    public final List<VodDeleter> vodDeleters = new ArrayList<>();

    private final static long recheckDelay = 60 * 1000; // 1 minute

    public DeletionManager() {
        downloadDir = new File(Archiver.config.download.directory);
        transcodeDir = new File(Archiver.config.transcode.directory);

        Archiver.scheduledExecutor.submit(this::run);
    }

    public void run() {
        try {
            getDownloadedVods(VodType.DOWNLOADED);
            getDownloadedVods(VodType.TRANSCODED);

            vodDeleters.stream()
                    .filter(deleter -> deleter.lastRun + recheckDelay < System.currentTimeMillis())
                    .forEach(deleter -> Archiver.scheduledExecutor.submit(deleter::run));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void getDownloadedVods(VodType type) {
        File parentFile = new File(type.getPath());
        String[] children = getChildren(parentFile);

        for (String child : children) {
            File childFile = new File(parentFile, child);
            getDownloadedVods(type, child, childFile);
        }
    }

    public void getDownloadedVods(VodType type, String channelId, File channelFile) {
        String[] children = getChildren(channelFile);
        if (children == null || children.length == 0) {
            try {
                Archiver.LOGGER.info("Channel {} has no vods, deleting", channelId);
                FileUtils.deleteDirectory(channelFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return;
        }

        for (String child : children) {
            File childFile = new File(channelFile, child);

            if (!childFile.isDirectory()) {
                FileUtils.deleteQuietly(childFile);
                continue;
            }

            if (vodDeleters.stream().anyMatch(deleter -> deleter.type == type && deleter.channelId.equals(channelId) && deleter.vodId.equals(child)))
                continue;

            synchronized (vodDeleters) {
                vodDeleters.add(new VodDeleter(this, childFile, type, channelId, child));
            }
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