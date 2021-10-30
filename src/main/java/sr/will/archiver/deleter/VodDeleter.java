package sr.will.archiver.deleter;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.ArrayUtils;
import sr.will.archiver.Archiver;
import sr.will.archiver.config.ArchiveSet;
import sr.will.archiver.entity.Vod;
import sr.will.archiver.notification.NotificationEvent;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class VodDeleter {
    public DeletionManager manager;
    public File file;
    public VodType type;
    public String channelId;
    public String vodId;
    public Vod vod;
    ArchiveSet.DeletionPolicy deletionPolicy;

    public long lastRun;

    public VodDeleter(DeletionManager manager, File file, VodType type, String channelId, String vodId) {
        this.manager = manager;
        this.file = file;
        this.type = type;
        this.channelId = channelId;
        this.vodId = vodId;
        this.deletionPolicy = getDeletionPolicy();
    }

    public void run() {
        lastRun = System.currentTimeMillis();

        vod = Archiver.instance.getVod(vodId);
        if (vod == null) {
            Archiver.LOGGER.warn("Vod {} on channel {} not found in database, deleting", vodId, channelId);
            delete();
        }

        if (deletionPolicy == null) {
            Archiver.LOGGER.warn("No archive set found for vod {} on channel {}", vod.id, vod.channelId);
            synchronized (manager.vodDeleters) {
                manager.vodDeleters.remove(this);
            }
            return;
        }

        if (shouldDelete()) {
            delete();
        }
    }

    public void delete() {
        Archiver.LOGGER.info("Deleting vod {} on channel {}", vodId, channelId);
        try {
            FileUtils.deleteDirectory(file);
            synchronized (manager.vodDeleters) {
                manager.vodDeleters.remove(this);
            }

            Archiver.LOGGER.info("Deleted vod {} on channel {}", vodId, channelId);
            Archiver.instance.webhookManager.execute(NotificationEvent.DELETE_SUCCESS, "Deleted vod " + vodId + " on channel " + channelId);

            File channel = new File(type.getPath(), channelId);
            if (channel.list() == null || channel.list().length == 0) {
                FileUtils.deleteDirectory(channel);
                Archiver.LOGGER.info("No vods left, deleting channel folder {}", channelId);
            }
        } catch (IOException e) {
            Archiver.LOGGER.error("Failed to delete vod {} on channel {}", vodId, channelId);
            Archiver.instance.webhookManager.execute(NotificationEvent.DELETE_FAIL, "Failed to delete vod " + vodId + " on channel " + channelId);
            e.printStackTrace();
        }
    }

    public boolean shouldDelete() {
        switch (deletionPolicy.mode) {
            case BOTH_MIN:
                return outsideAgeLimit() || outsideNumLimit();
            case AGE:
                return outsideAgeLimit();
            case NUMBER:
                return outsideNumLimit();
            case BOTH_MAX:
                return outsideAgeLimit() && outsideNumLimit();
            default:
                return true;
        }
    }

    public boolean outsideAgeLimit() {
        if (deletionPolicy.maxAge == -1) return false;
        return Instant.now().isAfter(vod.createdAt.plus(deletionPolicy.maxAge, ChronoUnit.DAYS));
    }

    public boolean outsideNumLimit() {
        if (deletionPolicy.maxNum == -1) return false;
        String[] vods = DeletionManager.getChildren(new File(type.getPath(), vod.channelId));
        if (vods == null) return false;
        return vods.length - ArrayUtils.indexOf(vods, vod.id) > deletionPolicy.maxNum;
    }

    private ArchiveSet.DeletionPolicy getDeletionPolicy() {
        ArchiveSet archiveSet = Archiver.getArchiveSet(channelId);
        if (archiveSet == null) return null;
        switch (this.type) {
            case DOWNLOADED:
                return archiveSet.downloadDeletionPolicy;
            case TRANSCODED:
                return archiveSet.transcodeDeletionPolicy;
            default:
                return null;
        }
    }
}
