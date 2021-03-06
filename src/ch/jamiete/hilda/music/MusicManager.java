package ch.jamiete.hilda.music;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import ch.jamiete.hilda.Hilda;
import ch.jamiete.hilda.Sanity;
import ch.jamiete.hilda.music.tasks.MusicStartupCheckerTask;
import ch.jamiete.hilda.music.tasks.MusicServerChecker;
import ch.jamiete.hilda.plugins.HildaPlugin;
import net.dv8tion.jda.core.MessageBuilder;
import net.dv8tion.jda.core.MessageBuilder.Formatting;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.Member;
import net.dv8tion.jda.core.entities.Message;
import net.dv8tion.jda.core.entities.Role;

public class MusicManager {
    /**
     * The maximum number of {@link QueueItem}s that can be in a {@link MusicServer} queue.
     */
    public static final int QUEUE_LIMIT = 100;
    /**
     * The maximum milliseconds a song can be.
     */
    public static final long TIME_LIMIT = 3600000; // 1 hour
    /**
     * The maximum milliseconds a song can be for a DJ.
     */
    public static final long DJ_TIME_LIMIT = 10800000; // 3 hours
    private static final Logger LOGGER = Logger.getLogger("Hilda-Music");

    /**
     * Converts an {@link AudioTrack} to a human-readable {@link String}.
     * @param track The track to convert.
     * @return The result of the conversion. Contains spaces and markdown formatting.
     */
    public static String getFriendly(final AudioTrack track) {
        final MessageBuilder mb = new MessageBuilder();

        if (track.getInfo().title != null) {
            mb.append(track.getInfo().title.replaceAll("\\*", "\\\\*"), Formatting.BOLD);

            if (track.getInfo().author != null) {
                mb.append(" by ").append(track.getInfo().author.replaceAll("\\*", "\\\\*"), Formatting.BOLD);
            }
        } else {
            mb.append(track.getIdentifier());
        }

        return mb.build().getContent().trim();
    }

    /**
     * Converts an {@link AudioTrack} to a human-readable {@link String} containing the time of the track.
     * @param track The track to convert.
     * @return The result of the conversion. Contains spaces and markdown formatting.
     */
    public static String getFriendlyTime(final AudioTrack track) {
        final StringBuilder sb = new StringBuilder();

        if (!("" + track.getDuration()).equalsIgnoreCase("null")) { // TODO Not use a hacky workaround
            sb.append(MusicManager.getFriendlyTime(track.getDuration()));
        }

        return sb.toString().trim();
    }

    public static String getFriendlyTime(long duration) {
        final StringBuilder sb = new StringBuilder();

        final long days = TimeUnit.MILLISECONDS.toDays(duration);
        duration -= TimeUnit.DAYS.toMillis(days);

        final long hours = TimeUnit.MILLISECONDS.toHours(duration);
        duration -= TimeUnit.HOURS.toMillis(hours);

        final long minutes = TimeUnit.MILLISECONDS.toMinutes(duration);
        duration -= TimeUnit.MINUTES.toMillis(minutes);

        final long seconds = TimeUnit.MILLISECONDS.toSeconds(duration);

        if (days > 0) {
            sb.append(" ").append(days);
            sb.append(" ").append(days == 1 ? "day" : "days");
        }

        if (hours > 0) {
            sb.append(" ").append(hours);
            sb.append(" ").append(hours == 1 ? "hour" : "hours");
        }

        if (minutes > 0) {
            sb.append(" ").append(minutes);
            sb.append(" ").append(minutes == 1 ? "minute" : "minutes");
        }

        if (seconds > 0) {
            sb.append(" ").append(seconds);
            sb.append(" ").append(seconds == 1 ? "second" : "seconds");
        }

        return sb.toString().trim();
    }

    /**
     * Gets the {@link Logger} to be used by music commands.
     * @return The {@link Logger}.
     */
    public static Logger getLogger() {
        return MusicManager.LOGGER;
    }

    private int played = 0;
    private int queued = 0;

    private final Hilda hilda;
    private final HildaPlugin plugin;
    private final AudioPlayerManager playerManager;
    private final ArrayList<MusicServer> servers = new ArrayList<MusicServer>();

    public MusicManager(final Hilda hilda, final HildaPlugin plugin) {
        this.hilda = hilda;
        this.plugin = plugin;

        this.playerManager = new DefaultAudioPlayerManager();
        AudioSourceManagers.registerRemoteSources(this.playerManager);
        AudioSourceManagers.registerLocalSource(this.playerManager);

        this.hilda.getExecutor().scheduleAtFixedRate(new MusicServerChecker(this), 15, 5, TimeUnit.MINUTES);
    }

    public void addPlayed() {
        this.played++;
    }

    public void addQueued() {
        this.queued++;
    }

    /**
     * Creates a {@link MusicServer} for a {@link Guild}.
     * @param guild The Guild the server should represent.
     * @return A new {@link MusicServer}.
     * @throws IllegalArgumentException If the Guild already has a server associated with it.
     */
    public MusicServer createServer(final Guild guild) {
        Sanity.truthiness(!this.hasServer(guild), "A server with the guild " + guild.getId() + " already exists.");

        final MusicServer server = new MusicServer(this, this.playerManager.createPlayer(), guild);
        this.servers.add(server);

        this.hilda.getExecutor().schedule(new MusicStartupCheckerTask(server), 90, TimeUnit.SECONDS);

        return server;
    }

    /**
     * Gets the AudioPlayerManager instance.
     * @return The AudioPlayerManager instance.
     */
    public AudioPlayerManager getAudioPlayerManager() {
        return this.playerManager;
    }

    public Hilda getHilda() {
        return this.hilda;
    }

    public int getPlayed() {
        return this.played;
    }

    public HildaPlugin getPlugin() {
        return this.plugin;
    }

    public int getQueued() {
        return this.queued;
    }

    /**
     * Searches for the server related to the guild.
     * @param guild The guild to test for.
     * @return The {@link MusicServer} related to the guild or {@code null} if no server exists.
     */
    public MusicServer getServer(final Guild guild) {
        for (final MusicServer server : this.servers) {
            if (server.getGuild() == guild) {
                return server;
            }
        }

        return null;
    }

    /**
     * Lists the servers registered.
     * @return A {@link MusicServer} array containing all registered servers.
     */
    public List<MusicServer> getServers() {
        return Collections.unmodifiableList(this.servers);
    }

    /**
     * Checks whether a server exists for the guild.
     * @param guild The guild to test for.
     * @return Whether a server exists.
     */
    public boolean hasServer(final Guild guild) {
        return this.getServer(guild) != null;
    }

    /**
     * Checks whether the user who sent the message is a DJ in the {@link Guild} the message was sent to.
     * @param message The message to test.
     * @return Whether the user who sent the message is a DJ.
     */
    public boolean isDJ(final Message message) {
        final Member member = message.getGuild().getMember(message.getAuthor());

        for (final Role role : message.getGuild().getRolesByName("dj", true)) {
            if (member.getRoles().contains(role)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Forgets a server.
     * @param server The server to forget.
     */
    public void removeServer(final MusicServer server) {
        this.servers.remove(server);
    }

}
