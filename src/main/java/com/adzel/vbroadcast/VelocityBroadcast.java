package com.adzel.vbroadcast;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;

import javax.inject.Inject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import java.util.Scanner;

@Plugin(id = "velocitybroadcast", name = "VelocityBroadcast", version = "1.0 Pre-Release",
        description = "Broadcast messages across all servers", authors = {"adzel"})
public class VelocityBroadcast {

    protected final ProxyServer server;
    protected final Logger logger;
    private String prefix;
    private boolean updateCheckEnabled;
    private final Path dataDirectory;

    private static final String DEFAULT_PREFIX = "&9&l[&3&lServer&9&l]&r ";
    private static final String PLUGIN_VERSION = "1.0 Pre-Release";

    @Inject
    public VelocityBroadcast(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;

        initializeDataDirectory();
        loadConfig();
        registerCommands();
    }

    public String getPrefix() {
        return prefix + " "; // Always return the prefix with a trailing space
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix + " "; // Store the prefix with a trailing space
        savePrefixToConfig(); // Save the new prefix to config
    }

    public boolean isUpdateCheckEnabled() {
        return updateCheckEnabled;
    }

    private void initializeDataDirectory() {
        try {
            if (Files.notExists(dataDirectory)) {
                Files.createDirectories(dataDirectory);
            }
        } catch (IOException e) {
            logger.error("Could not create data directory: " + dataDirectory.toAbsolutePath(), e);
        }
    }

    private void loadConfig() {
        File configFile = new File(dataDirectory.toFile(), "config.yml");
        if (!configFile.exists()) {
            saveDefaultConfig();
        } else {
            updateConfigVersionIfNeeded(configFile);
        }

        this.prefix = loadPrefix();
        this.updateCheckEnabled = loadUpdateCheckSetting();
    }

    private void registerCommands() {
        server.getCommandManager().register(
                server.getCommandManager().metaBuilder("vbroadcast").aliases("vb").build(),
                new SimpleCommand() {
                    @Override
                    public void execute(Invocation invocation) {
                        CommandSource source = invocation.source();
                        String message = String.join(" ", invocation.arguments());

                        logger.info("BroadcastCommand executed by: " + source.getClass().getSimpleName());
                        logger.info("Arguments: " + message);

                        if (message.isEmpty()) {
                            source.sendMessage(Component.text("Please provide a message to broadcast.").color(NamedTextColor.RED));
                            return;
                        }

                        // Broadcast the message to all players
                        server.getAllPlayers().forEach(player -> {
                            Component broadcastMessage = LegacyComponentSerializer.legacyAmpersand().deserialize(getPrefix() + message);
                            player.sendMessage(broadcastMessage);
                        });

                        source.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(getPrefix() + "Message broadcasted: &f" + message));
                    }

                    @Subscribe
                    public boolean testPermission(CommandSource source) {
                        return source.hasPermission("vb.broadcast");
                    }
                }
        );

        server.getCommandManager().register(
                server.getCommandManager().metaBuilder("vbroadcastprefix").aliases("vbprefix", "vb p").build(),
                new SimpleCommand() {
                    @Override
                    public void execute(Invocation invocation) {
                        CommandSource source = invocation.source();
                        String newPrefix = String.join(" ", invocation.arguments());

                        logger.info("PrefixCommand executed by: " + source.getClass().getSimpleName());
                        logger.info("New prefix: " + newPrefix);

                        if (newPrefix.isEmpty()) {
                            source.sendMessage(Component.text("Please provide a new prefix.").color(NamedTextColor.RED));
                            return;
                        }

                        setPrefix(newPrefix);
                        source.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(getPrefix() + "Prefix set to: &f" + newPrefix));
                    }

                    @Subscribe
                    public boolean testPermission(CommandSource source) {
                        return source.hasPermission("vb.prefix");
                    }
                }
        );
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        logger.info("VelocityBroadcast initialized with prefix: " + prefix);
    }

    private String loadPrefix() {
        File configFile = new File(dataDirectory.toFile(), "config.yml");
        try (InputStream inputStream = new FileInputStream(configFile)) {
            Yaml yaml = new Yaml();
            Map<String, Object> config = yaml.load(new InputStreamReader(inputStream));
            return (String) config.getOrDefault("prefix", DEFAULT_PREFIX);
        } catch (IOException e) {
            logger.error("Failed to load prefix from configuration: " + e.getMessage());
            return DEFAULT_PREFIX;
        }
    }

    private void saveDefaultConfig() {
        File configFile = new File(dataDirectory.toFile(), "config.yml");
        if (!configFile.exists()) {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(configFile))) {
                writer.write("# DO NOT EDIT\n");
                writer.write("Plugin Version: '" + PLUGIN_VERSION + "'\n");
                writer.write("update-check-enabled: true\n");
                writer.write("prefix: '" + DEFAULT_PREFIX + "'\n");
            } catch (IOException e) {
                logger.error("Failed to save default configuration: " + e.getMessage());
            }
        }
    }

    private void updateConfigVersionIfNeeded(File configFile) {
        try (InputStream inputStream = new FileInputStream(configFile)) {
            Yaml yaml = new Yaml();
            Map<String, Object> config = yaml.load(new InputStreamReader(inputStream));

            // Check if the version is not the latest, if so, overwrite
            if (!PLUGIN_VERSION.equals(config.get("Plugin Version"))) {
                saveDefaultConfig(); // Overwrite the config with the new version
            }
        } catch (IOException e) {
            logger.error("Failed to check or update configuration version: " + e.getMessage());
        }
    }

    private boolean loadUpdateCheckSetting() {
        File configFile = new File(dataDirectory.toFile(), "config.yml");
        if (!configFile.exists()) {
            saveDefaultConfig();
        }

        try (InputStream inputStream = new FileInputStream(configFile)) {
            Yaml yaml = new Yaml();
            Map<String, Object> config = yaml.load(new InputStreamReader(inputStream));
            return (boolean) config.getOrDefault("update-check-enabled", true);
        } catch (IOException e) {
            logger.error("Failed to load configuration: " + e.getMessage());
            return true;
        }
    }

    private void savePrefixToConfig() {
        File configFile = new File(dataDirectory.toFile(), "config.yml");
        if (!configFile.exists()) {
            saveDefaultConfig(); // Create config if it doesn't exist
        }

        Yaml yaml = new Yaml();

        try (InputStream inputStream = new FileInputStream(configFile)) {
            Map<String, Object> config = yaml.load(new InputStreamReader(inputStream));

            // Update the prefix in the config
            config.put("prefix", this.prefix);

            // Write changes back to config file
            try (Writer writer = new FileWriter(configFile)) {
                writer.write("# DO NOT EDIT\n");
                writer.write("Plugin Version: '" + config.get("Plugin Version") + "'\n");
                writer.write("update-check-enabled: " + config.get("update-check-enabled") + "\n");
                writer.write("prefix: '" + this.prefix + "'\n");
            }
        } catch (IOException e) {
            logger.error("Failed to save prefix to configuration: " + e.getMessage());
        }
    }

    private void checkForUpdates(CommandSource source) {
        if (!updateCheckEnabled) {
            return;
        }

        try {
            URL url = new URL("https://api.spigotmc.org/legacy/update.php?resource=119858");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            if (connection.getResponseCode() == 200) {
                try (Scanner scanner = new Scanner(connection.getInputStream())) {
                    StringBuilder response = new StringBuilder();
                    while (scanner.hasNextLine()) {
                        response.append(scanner.nextLine());
                    }

                    String latestVersion = response.toString().trim(); // Trim any whitespace
                    logger.info("Latest version retrieved: " + latestVersion); // Log the latest version

                    if (!latestVersion.equals(PLUGIN_VERSION)) {
                        if (source.hasPermission("vb.update")) { // Check if the player has permission to see the update message
                            source.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(
                                    getPrefix() + "A new version of VelocityBroadcast is available: &f" + latestVersion));
                        }
                    }
                }
            }
        } catch (IOException e) {
            logger.error("Error checking for updates: " + e.getMessage());
        }
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        // Optionally, notify the player upon login
        event.getPlayer().sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(getPrefix() + "Welcome to the server!"));
    }
}
