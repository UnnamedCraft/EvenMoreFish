package com.oheers.fish;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.net.URL;
import java.util.Scanner;
import java.util.logging.Level;

public class UpdateChecker {

    private final EvenMoreFish plugin;
    private final int resourceID;


    public UpdateChecker(final EvenMoreFish plugin, final int resourceID) {
        this.plugin = plugin;
        this.resourceID = resourceID;
    }

    public String getVersion() {
        String version;
        try {
            version = ((JSONObject) new JSONParser().parse(new Scanner(new URL("https://api.spigotmc.org/simple/0.1/index.php?action=getResource&id=" + resourceID).openStream()).nextLine())).get("current_version").toString();
        } catch (Exception ignored) {
            version = plugin.getDescription().getVersion();
            EvenMoreFish.logger.log(Level.WARNING, "EvenMoreFish 从 Spigot 网站检查更新失败，你可以到 https://www.spigotmc.org/resources/evenmorefish.91310/updates 手动检查更新");
        }

        return version;
    }
}

class UpdateNotify implements Listener {

    @EventHandler
    // informs admins with emf.admin permission that the plugin needs updating
    public void playerJoin(PlayerJoinEvent event) {
        if (EvenMoreFish.isUpdateAvailable) {
            if (EvenMoreFish.permission.playerHas(event.getPlayer(), "emf.admin")) {
                event.getPlayer().sendMessage(FishUtils.translateHexColorCodes(EvenMoreFish.msgs.getAdminPrefix() + "更新可用: " + "https://www.spigotmc.org/resources/evenmorefish.91310/updates"));
            }
        }
    }
}

