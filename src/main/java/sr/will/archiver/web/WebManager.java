package sr.will.archiver.web;

import io.javalin.Javalin;
import sr.will.archiver.Archiver;
import sr.will.archiver.youtube.ChannelUploader;

public class WebManager {

    public WebManager() {
        Javalin web = Javalin.create().start(Archiver.config.upload.google.oAuthPort);
        web.get("/callback", ctx -> {
            String state = ctx.queryParam("state");
            String code = ctx.queryParam("code");
            if (state == null || code == null) {
                ctx.status(400);
                return;
            }

            ChannelUploader uploader = Archiver.instance.youTubeManager.pendingCredentials.get(state);
            if (uploader == null) {
                ctx.status(400);
                return;
            }

            uploader.doCallback(state, code);
            ctx.status(200);
            ctx.result("Received verification code, you may now close this window.");
        });
    }
}
