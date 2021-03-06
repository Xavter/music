package ch.jamiete.hilda.music.tasks;

import java.util.TimerTask;
import ch.jamiete.hilda.music.MusicManager;
import ch.jamiete.hilda.music.MusicServer;
import net.dv8tion.jda.core.entities.Guild;

public class MusicServerChecker extends TimerTask {
    private final MusicManager manager;

    public MusicServerChecker(final MusicManager manager) {
        this.manager = manager;
    }

    @Override
    public void run() {
        for (final MusicServer server : this.manager.getServers()) {
            server.prompt();

            if (server.isStopping()) {
                return;
            }

            if (server.getGuild().getAudioManager().isConnected() && server.getGuild().getAudioManager().getConnectedChannel() != server.getChannel()) {
                MusicManager.getLogger().info("Moved from " + server.getGuild().getAudioManager().getConnectedChannel().getName() + " to expected channel");
                server.getGuild().getAudioManager().openAudioConnection(server.getChannel());
            }

            if (!server.getQueue().isEmpty() && server.getPlayer().getPlayingTrack() == null) {
                server.play(server.getQueue().get(0));
            }
        }

        for (final Guild guild : this.manager.getHilda().getBot().getGuilds()) {
            final MusicServer server = this.manager.getServer(guild);

            if (guild.getAudioManager().isConnected() && server == null) {
                MusicManager.getLogger().info("Disconnecting from voice chat in untracked server " + guild.getName());
                guild.getAudioManager().closeAudioConnection();
            }
        }
    }

}
