package sr.will.archiver.deleter;

import sr.will.archiver.Archiver;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ChannelDeleter {
    private DeletionManager manager;
    public DeleterType type;
    public String channelId;

    public ChannelDeleter(DeletionManager manager, DeleterType type, String channelId) {
        this.manager = manager;
        this.type = type;
        this.channelId = channelId;

        run();
    }

    public void run() {
        String[] vods = DeletionManager.getChildren(new File(manager.downloadDir, channelId));
        if (vods == null) return;

        StringBuilder queryBuilder = new StringBuilder("SELECT * FROM vods WHERE (");
        List<String> queryObjects = new ArrayList<>();

        Archiver.LOGGER.info("Checking {} vods for deletion on channel {}", vods.length, channelId);
        for (String file : vods) {

        }
    }
}
