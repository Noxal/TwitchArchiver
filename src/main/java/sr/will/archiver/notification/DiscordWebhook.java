package sr.will.archiver.notification;

import club.minnced.discord.webhook.WebhookClient;
import com.github.twitch4j.helix.domain.Stream;
import sr.will.archiver.config.Config;
import sr.will.archiver.entity.Vod;

public class DiscordWebhook extends Webhook {
    private final WebhookClient client;

    public DiscordWebhook(Config.WebHook config) {
        super(config);
        client = WebhookClient.withUrl(config.webhook);
    }

    @Override
    public void execute(NotificationEvent event, String message, Vod vod, Stream stream) {
        if (message == null) client.send(replace(event, vod, stream));
        else client.send(message);
    }

    protected void run(NotificationEvent event, String message, Vod vod, Stream stream) {
        // Not used for discord
    }
}
