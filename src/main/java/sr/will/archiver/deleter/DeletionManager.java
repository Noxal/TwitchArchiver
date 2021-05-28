package sr.will.archiver.deleter;

import sr.will.archiver.Archiver;
import sr.will.archiver.entity.LocalVod;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DeletionManager {
    public File downloadDir;
    public File transcodeDir;

    public List<LocalVod> localVods = new ArrayList<>();

    public DeletionManager() {
        downloadDir = new File(Archiver.config.download.directory);
        transcodeDir = new File(Archiver.config.transcode.directory);

        Archiver.scheduledExecutor.submit(this::run);
    }

    public void run() {
        getDownloadedVods(LocalVod.Type.DOWNLOADED);
        getDownloadedVods(LocalVod.Type.TRANSCODED);

        for (LocalVod vod : localVods) {
            if (vod.shouldDelete()) vod.delete();
        }
    }

    public void getDownloadedVods(LocalVod.Type type) {
        String[] children = getChildren(new File(type.getPath()));
        if (children == null) return;

        for (String child : children) {
            getDownloadedVods(type, child);
        }
    }

    public void getDownloadedVods(LocalVod.Type type, String channelId) {
        String[] children = getChildren(new File(type.getPath(), channelId));
        if (children == null) return;

        for (String child : children) {
            if (localVods.stream().anyMatch(vod -> vod.type == type && vod.channelId.equals(channelId) && vod.vodId.equals(child)))
                continue;
            localVods.add(new LocalVod(type, channelId, child));
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