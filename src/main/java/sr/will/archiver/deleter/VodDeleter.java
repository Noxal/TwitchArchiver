package sr.will.archiver.deleter;

import sr.will.archiver.Archiver;
import sr.will.archiver.entity.Vod;

public class VodDeleter {
    public Vod vod;

    public VodDeleter(Vod vod) {
        Archiver.scheduledExecutor.submit(this::run);
    }

    public void run() {

    }
}
