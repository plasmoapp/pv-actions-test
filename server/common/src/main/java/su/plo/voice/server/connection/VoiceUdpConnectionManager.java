package su.plo.voice.server.connection;

import com.google.common.collect.Maps;
import lombok.AllArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import su.plo.voice.api.event.EventSubscribe;
import su.plo.voice.api.server.PlasmoVoiceServer;
import su.plo.voice.api.server.connection.UdpServerConnectionManager;
import su.plo.voice.api.server.event.connection.UdpConnectEvent;
import su.plo.voice.api.server.event.connection.UdpConnectedEvent;
import su.plo.voice.api.server.event.connection.UdpDisconnectEvent;
import su.plo.voice.api.server.event.player.PlayerQuitEvent;
import su.plo.voice.api.server.player.VoiceServerPlayer;
import su.plo.voice.api.server.socket.UdpServerConnection;
import su.plo.voice.proto.packets.Packet;
import su.plo.voice.proto.packets.udp.clientbound.ClientPacketUdpHandler;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

@AllArgsConstructor
public final class VoiceUdpConnectionManager implements UdpServerConnectionManager {

    private static final Logger LOGGER = LogManager.getLogger();

    private final PlasmoVoiceServer server;

    private final Map<UUID, UUID> secretByPlayerId = Maps.newConcurrentMap();
    private final Map<UUID, UUID> playerIdBySecret = Maps.newConcurrentMap();

    private final Map<UUID, UdpServerConnection> connectionBySecret = Maps.newConcurrentMap();
    private final Map<UUID, UdpServerConnection> connectionByPlayerId = Maps.newConcurrentMap();

    @Override
    public Optional<UUID> getPlayerIdBySecret(UUID secret) {
        return Optional.ofNullable(playerIdBySecret.get(secret));
    }

    @Override
    public UUID getSecretByPlayerId(UUID playerUUID) {
        if (secretByPlayerId.containsKey(playerUUID)) {
            return secretByPlayerId.get(playerUUID);
        }

        UUID secret = UUID.randomUUID();
        secretByPlayerId.put(playerUUID, secret);
        playerIdBySecret.put(secret, playerUUID);

        return secret;
    }

    @Override
    public void addConnection(UdpServerConnection connection) {
        UdpConnectEvent connectEvent = new UdpConnectEvent(connection);
        server.getEventBus().call(connectEvent);
        if (connectEvent.isCancelled()) return;

        UdpServerConnection bySecret = connectionBySecret.put(connection.getSecret(), connection);
        UdpServerConnection byPlayer = connectionByPlayerId.put(connection.getPlayer().getInstance().getUUID(), connection);

        if (bySecret != null) bySecret.disconnect();
        if (byPlayer != null) byPlayer.disconnect();

        server.getEventBus().call(new UdpConnectedEvent(connection));
    }

    @Override
    public boolean removeConnection(UdpServerConnection connection) {
        UdpServerConnection bySecret = connectionBySecret.remove(connection.getSecret());
        UdpServerConnection byPlayer = connectionByPlayerId.remove(connection.getPlayer().getInstance().getUUID());

        if (bySecret != null) disconnect(bySecret);
        if (byPlayer != null && !byPlayer.equals(bySecret)) disconnect(byPlayer);

        return bySecret != null || byPlayer != null;
    }

    @Override
    public boolean removeConnection(VoiceServerPlayer player) {
        UdpServerConnection connection = connectionByPlayerId.remove(player.getInstance().getUUID());
        if (connection != null) disconnect(connection);

        return connection != null;
    }

    @Override
    public boolean removeConnection(UUID secret) {
        UdpServerConnection connection = connectionBySecret.remove(secret);
        if (connection != null) disconnect(connection);

        return connection != null;
    }

    @Override
    public Optional<UdpServerConnection> getConnectionBySecret(@NotNull UUID secret) {
        return Optional.ofNullable(connectionBySecret.get(secret));
    }

    @Override
    public Optional<UdpServerConnection> getConnectionByPlayerId(@NotNull UUID playerId) {
        return Optional.ofNullable(connectionByPlayerId.get(playerId));
    }

    @Override
    public Collection<UdpServerConnection> getConnections() {
        return connectionByPlayerId.values();
    }

    @Override
    public void clearConnections() {
        getConnections().forEach(this::removeConnection);
    }

    private void disconnect(UdpServerConnection connection) {
        connection.disconnect();

        secretByPlayerId.remove(connection.getPlayer().getInstance().getUUID());
        playerIdBySecret.remove(connection.getSecret());

        LOGGER.info("{} disconnected", connection.getPlayer());
        server.getEventBus().call(new UdpDisconnectEvent(connection));
    }

    @Override
    public void broadcast(@NotNull Packet<ClientPacketUdpHandler> packet, @Nullable Predicate<VoiceServerPlayer> filter) {
        for (UdpServerConnection connection : getConnections()) {
            if (filter == null || filter.test(connection.getPlayer()))
                connection.sendPacket(packet);
        }
    }

    @EventSubscribe
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        getConnectionByPlayerId(event.getPlayerId()).ifPresent(this::removeConnection);
    }
}
