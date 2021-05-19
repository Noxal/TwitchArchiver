package sr.will.archiver.notification;

import com.github.twitch4j.helix.domain.Stream;
import sr.will.archiver.Archiver;
import sr.will.archiver.config.Config;
import sr.will.archiver.entity.Vod;

import java.util.ArrayList;
import java.util.List;

public class WebhookManager {
    public final List<Webhook> webhooks = new ArrayList<>();

    public WebhookManager() {
        for (Config.WebHook webHookConfig : Archiver.config.notifications) {
            switch (webHookConfig.type) {
                case DISCORD:
                    webhooks.add(new DiscordWebhook(webHookConfig));
                    break;
                default:
                    break;
            }
        }
    }

    public void execute(NotificationEvent event, String message, Vod vod, Stream stream) {
        for (Webhook webhook : webhooks) {
            if (!webhook.config.events.contains(event)) continue;
            webhook.execute(event, message, vod, stream);
        }
    }

    public void execute(NotificationEvent event, Vod vod, Stream stream) {
        execute(event, null, vod, stream);
    }

    public void execute(NotificationEvent event, Vod vod) {
        execute(event, null, vod, null);
    }

    public void execute(NotificationEvent event, String message) {
        execute(event, message, null, null);
    }
}
