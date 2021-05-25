package sr.will.archiver.twitch;

import com.github.philippheuer.events4j.simple.domain.EventSubscriber;
import com.github.twitch4j.events.ChannelGoLiveEvent;
import com.github.twitch4j.events.ChannelGoOfflineEvent;
import com.github.twitch4j.helix.domain.StreamList;
import sr.will.archiver.Archiver;
import sr.will.archiver.notification.NotificationEvent;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class EventHandler {

    @EventSubscriber
    public void onChannelGoLive(ChannelGoLiveEvent event) {
        Archiver.LOGGER.info("{} just started streaming, will check for vod in {} minutes", event.getChannel().getName(), Archiver.config.times.goLiveDelay);
        Archiver.instance.webhookManager.execute(NotificationEvent.STREAM_START, NotificationEvent.STREAM_START.message.replace("{user}", event.getChannel().getName()));
        Archiver.scheduledExecutor.schedule(
                () -> Archiver.instance.getChannelDownloader(event.getChannel().getId()).addVideoFromStream(event.getStream()),
                Archiver.config.times.goLiveDelay,
                TimeUnit.MINUTES
        );
    }

    @EventSubscriber
    public void onChannelGoOffline(ChannelGoOfflineEvent event) {
        // Occasionally the twitch checker marks a stream as online, immediately offline, and immediately online again
        // To account for this we check if there's a current livestream going with the same id
        StreamList streams = Archiver.twitchClient.getHelix().getStreams(null, null, null, null, null, null, Collections.singletonList(event.getChannel().getId()), null).execute();
        ChannelDownloader channelDownloader = Archiver.instance.getChannelDownloader(event.getChannel().getId());
        if (!streams.getStreams().isEmpty() && streams.getStreams().get(0).getId().equals(channelDownloader.stream.getId())) {
            Archiver.LOGGER.warn("Got channel offline event for {}'s stream that still appears to be live, ignoring", event.getChannel().getName());
            return;
        }

        Archiver.LOGGER.info("{} just stopped streaming, will stop vod checking in {} minutes", event.getChannel().getName(), Archiver.config.times.goOfflineDelay);
        Archiver.scheduledExecutor.schedule(
                channelDownloader::streamEnded,
                Archiver.config.times.goOfflineDelay,
                TimeUnit.MINUTES
        );

        Archiver.instance.webhookManager.execute(NotificationEvent.STREAM_END, NotificationEvent.STREAM_END.message.replace("{user}", event.getChannel().getName()));
    }
}
