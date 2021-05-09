package sr.will.archiver.twitch;

import com.github.philippheuer.events4j.simple.domain.EventSubscriber;
import com.github.twitch4j.events.ChannelGoLiveEvent;
import com.github.twitch4j.events.ChannelGoOfflineEvent;
import sr.will.archiver.Archiver;

import java.util.concurrent.TimeUnit;

public class EventHandler {

    @EventSubscriber
    public void onChannelGoLive(ChannelGoLiveEvent event) {
        Archiver.LOGGER.info("{} just started streaming, will check for vod in {} minutes", event.getChannel().getName(), Archiver.config.times.goLiveDelay);
        Archiver.scheduledExecutor.schedule(
                () -> Archiver.instance.getChannelDownloader(event.getChannel().getName()).addVideoFromStream(event.getStream()),
                Archiver.config.times.goLiveDelay,
                TimeUnit.MINUTES
        );
    }

    @EventSubscriber
    public void onChannelGoOffline(ChannelGoOfflineEvent event) {
        Archiver.LOGGER.info("{} just stopped streaming, will stop vod checking in {} minutes", event.getChannel().getName(), Archiver.config.times.goOfflineDelay);
        Archiver.scheduledExecutor.schedule(
                () -> Archiver.instance.getChannelDownloader(event.getChannel().getName()).streamEnded(),
                Archiver.config.times.goOfflineDelay,
                TimeUnit.MINUTES
        );

        Archiver.instance.getChannelDownloader(event.getChannel().getName()).streamEnded();
    }
}
