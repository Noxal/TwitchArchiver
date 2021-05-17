package sr.will.archiver.notification;

import com.github.twitch4j.helix.domain.Stream;
import sr.will.archiver.Archiver;
import sr.will.archiver.config.Config;
import sr.will.archiver.entity.Vod;

public abstract class Webhook {
    protected Config.WebHook config;

    public Webhook(Config.WebHook config) {
        this.config = config;
    }

    public void execute(NotificationEvent event, Vod vod, Stream stream) {
        Archiver.scheduledExecutor.submit(() -> run(event, vod, stream));
    }

    protected abstract void run(NotificationEvent event, Vod vod, Stream stream);

    protected String replace(NotificationEvent event, Vod vod, Stream stream) {
        return event.message
                .replace("{user}", Archiver.instance.usernames.get(vod.channelId))
                .replace("{title}", vod.title);
    }
}
