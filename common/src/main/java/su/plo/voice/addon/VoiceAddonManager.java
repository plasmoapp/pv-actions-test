package su.plo.voice.addon;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import su.plo.voice.BaseVoice;
import su.plo.voice.api.addon.AddonContainer;
import su.plo.voice.api.addon.AddonManager;
import su.plo.voice.api.addon.AddonScope;
import su.plo.voice.api.addon.annotation.processor.JsonAddon;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;

public final class VoiceAddonManager implements AddonManager {

    private static final Logger LOGGER = LogManager.getLogger(VoiceAddonManager.class);

    private final BaseVoice voice;
    private final AddonScope scope;

    private final Map<Object, AddonContainer> addonByInstance = Maps.newHashMap();
    private final Map<String, AddonContainer> addons = Maps.newHashMap();

    public VoiceAddonManager(BaseVoice voice, AddonScope scope) {
        this.voice = voice;
        this.scope = scope;
    }

    @Override
    public boolean isLoaded(@NotNull String id) {
        return addons.containsKey(id);
    }

    @Override
    public Optional<AddonContainer> getAddon(String id) {
        return Optional.ofNullable(addons.get(id));
    }

    @Override
    public Optional<AddonContainer> getAddon(Object instance) {
        return Optional.ofNullable(addonByInstance.get(instance));
    }

    public void load(List<File> folders) {
        if (!getAddon(voice).isPresent()) {
            // register PlasmoVoice as an addon
            AddonContainer voiceAddon = new PlasmoVoiceAddon(voice, scope);
            this.addons.put("plasmovoice", voiceAddon);
            this.addonByInstance.put(voice, voiceAddon);
        }

        folders.forEach(this::scanForAddons);
    }

    public void clear() {
        addons.values().forEach((addon) -> {
            if (addon.getId().equals("plasmovoice")) return;

            voice.getEventBus().unregister(addon.getInstance().get());
            LOGGER.info(
                    "Addon {} v{} by {} unloaded",
                    addon.getId(),
                    addon.getVersion(),
                    String.join(", ", addon.getAuthors())
            );
        });

        addonByInstance.clear();
        addons.clear();
    }

    private void scanForAddons(File folder) {
        checkNotNull(folder, "folder");
        if (!folder.isDirectory()) return;

        List<VoiceAddon> addons = new ArrayList<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(
                folder.toPath(),
                p -> p.toFile().isFile() && p.toString().endsWith(".jar")
        )) {
            for (Path path : stream) {
                for (JsonAddon jsonAddon : getJsonAddons(path)) {
                    addons.add(createAddon(jsonAddon, path));
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to scan folder ({}) for addons: {}", folder, e.getMessage());
            e.printStackTrace();
        }

        if (addons.isEmpty()) return;

        for (VoiceAddon addon : addons) {
            // todo: addon dependencies
//            for (AddonDependency dependency : addon.getDependencies()) {
//                if (dependency.isOptional()) continue;
//                if (!this.addons.containsKey(dependency.getId())) {
//                    LOGGER.error("Addon {} is missing dependency {}", addon.getId(), dependency.getId());
//                    continue load;
//                }
//            }

            try {
                loadAddon(addon);
                LOGGER.info(
                        "Addon {} v{} by {} loaded",
                        addon.getId(),
                        addon.getVersion(),
                        String.join(", ", addon.getAuthors())
                );
            } catch (Exception e) {
                LOGGER.error("Failed to load the addon {}: {}", addon.getId(), e.getMessage());
                continue;
            }

            Object addonInstance = addon.getInstance().get();
            this.addons.put(addon.getId(), addon);
            this.addonByInstance.put(addonInstance, addon);

            voice.getEventBus().register(addonInstance, addonInstance);
        }
    }

    private VoiceAddon createAddon(JsonAddon jsonAddon, Path addonPath) throws Exception {
        URL pluginJarUrl = addonPath.toUri().toURL();
        AddonClassLoader classLoader = AccessController.doPrivileged(
                (PrivilegedAction<AddonClassLoader>) () -> new AddonClassLoader(new URL[]{pluginJarUrl})
        );
        classLoader.addToClassloaders();

        Class<?> mainClass = classLoader.loadClass(jsonAddon.getMainClass());

        return new VoiceAddon(
                jsonAddon.getId(),
                Strings.emptyToNull(jsonAddon.getName()) == null
                        ? jsonAddon.getId()
                        : jsonAddon.getName(),
                jsonAddon.getScope(),
                jsonAddon.getVersion(),
                jsonAddon.getAuthors(),
                addonPath,
                mainClass
        );
    }

    private List<JsonAddon> getJsonAddons(Path path) throws Exception {
        try (JarInputStream in = new JarInputStream(
                new BufferedInputStream(Files.newInputStream(path))
        )) {
            JarEntry entry;
            while ((entry = in.getNextJarEntry()) != null) {
                if (entry.getName().equals("plasmovoice-addons.json")) {
                    try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                        return JsonAddon.from(reader)
                                .stream()
                                .filter((addon) -> addon.getScope().equals(scope))
                                .collect(Collectors.toList());
                    }
                }
            }
        }

        return ImmutableList.of();
    }

    private void loadAddon(@NotNull AddonContainer addon) throws Exception {
        Object instance = addon.getMainClass().getDeclaredConstructor().newInstance();
        ((VoiceAddon) addon).setInstance(instance);
    }
}
