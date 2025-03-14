package su.plo.lib.velocity;

import com.google.common.collect.Maps;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import su.plo.lib.api.proxy.MinecraftProxyLib;
import su.plo.lib.api.proxy.player.MinecraftProxyPlayer;
import su.plo.lib.api.proxy.server.MinecraftProxyServerInfo;
import su.plo.lib.api.server.permission.PermissionsManager;
import su.plo.lib.velocity.chat.ComponentTextConverter;
import su.plo.lib.velocity.command.VelocityCommandManager;
import su.plo.lib.velocity.player.VelocityProxyPlayer;
import su.plo.lib.velocity.server.VelocityProxyServerInfo;
import su.plo.voice.api.event.EventBus;
import su.plo.voice.api.event.EventSubscribe;
import su.plo.voice.api.server.config.ServerLanguages;
import su.plo.voice.api.server.event.player.PlayerJoinEvent;
import su.plo.voice.api.server.event.player.PlayerQuitEvent;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public final class VelocityProxyLib implements MinecraftProxyLib {

    private final Map<UUID, VelocityProxyPlayer> playerById = Maps.newConcurrentMap();
    private final Map<String, VelocityProxyServerInfo> serverByName = Maps.newConcurrentMap();

    private final ProxyServer proxyServer;
    @Getter
    private final EventBus eventBus;
    private final ComponentTextConverter textConverter;
    @Getter
    private final VelocityCommandManager commandManager;
    @Getter
    private final PermissionsManager permissionsManager = new PermissionsManager();

    public VelocityProxyLib(@NotNull ProxyServer proxyServer,
                            @NotNull EventBus eventBus,
                            @NotNull Supplier<ServerLanguages> languagesSupplier) {
        this.proxyServer = proxyServer;
        this.eventBus = eventBus;
        this.textConverter = new ComponentTextConverter(languagesSupplier);
        this.commandManager = new VelocityCommandManager(this, textConverter);

        loadServers();
    }

    @Override
    public Optional<MinecraftProxyPlayer> getPlayerById(@NotNull UUID playerId) {
        MinecraftProxyPlayer serverPlayer = playerById.get(playerId);
        if (serverPlayer != null) return Optional.of(serverPlayer);

        return proxyServer.getPlayer(playerId)
                .map(this::getPlayerByInstance);
    }

    @Override
    public Optional<MinecraftProxyPlayer> getPlayerByName(@NotNull String name) {
        return proxyServer.getPlayer(name)
                .map(this::getPlayerByInstance);
    }

    @Override
    public @NotNull MinecraftProxyPlayer getPlayerByInstance(@NotNull Object instance) {
        if (!(instance instanceof Player))
            throw new IllegalArgumentException("instance is not " + Player.class);

        Player proxyPlayer = (Player) instance;

        return playerById.computeIfAbsent(
                proxyPlayer.getUniqueId(),
                (playerId) -> new VelocityProxyPlayer(this, textConverter, proxyPlayer)
        );
    }

    @Override
    public Collection<MinecraftProxyPlayer> getPlayers() {
        return proxyServer.getAllPlayers()
                .stream()
                .map(this::getPlayerByInstance)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<MinecraftProxyServerInfo> getServerByName(@NotNull String name) {
        VelocityProxyServerInfo serverInfo = serverByName.get(name);
        if (serverInfo != null) {
            Optional<RegisteredServer> server = proxyServer.getServer(name);

            if (!server.isPresent()) {
                serverByName.remove(name);
                return Optional.empty();
            } else if (!serverInfo.getInstance().equals(server.get().getServerInfo())) {
                serverInfo = new VelocityProxyServerInfo(server.get().getServerInfo());
                serverByName.put(name, serverInfo);
            }

            return Optional.of(serverInfo);
        }

        return proxyServer.getServer(name)
                .map(this::getServerInfoByServerInstance);
    }

    @Override
    public @NotNull MinecraftProxyServerInfo getServerInfoByServerInstance(@NotNull Object instance) {
        if (!(instance instanceof RegisteredServer))
            throw new IllegalArgumentException("instance is not " + RegisteredServer.class);

        RegisteredServer server = (RegisteredServer) instance;

        VelocityProxyServerInfo serverInfo = serverByName.get(server.getServerInfo().getName());
        if (serverInfo == null) {
            serverInfo = new VelocityProxyServerInfo(server.getServerInfo());
            serverByName.put(server.getServerInfo().getName(), serverInfo);
        } else if (!serverInfo.getInstance().equals(server.getServerInfo())) {
            serverInfo = new VelocityProxyServerInfo(server.getServerInfo());
            serverByName.put(server.getServerInfo().getName(), serverInfo);
        }

        return serverInfo;
    }

    @Override
    public Collection<MinecraftProxyServerInfo> getServers() {
        return proxyServer.getAllServers().stream()
                .map(this::getServerInfoByServerInstance)
                .collect(Collectors.toList());
    }

    @Override
    public int getPort() {
        return proxyServer.getBoundAddress().getPort();
    }

    private void loadServers() {
        proxyServer.getAllServers().forEach((server) -> {
            ServerInfo serverInfo = server.getServerInfo();
            serverByName.put(serverInfo.getName(), new VelocityProxyServerInfo(serverInfo));
        });
    }

    @EventSubscribe
    public void onPlayerJoin(@NotNull PlayerJoinEvent event) {
        getPlayerByInstance(event.getPlayer());
    }

    @EventSubscribe
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        playerById.remove(event.getPlayerId());
    }
}
