package com.oheers.fish;

import com.oheers.fish.competition.AutoRunner;
import com.oheers.fish.competition.Competition;
import com.oheers.fish.competition.CompetitionQueue;
import com.oheers.fish.competition.JoinChecker;
import com.oheers.fish.config.*;
import com.oheers.fish.config.messages.LocaleGen;
import com.oheers.fish.config.messages.MessageFile;
import com.oheers.fish.config.messages.Messages;
import com.oheers.fish.database.Database;
import com.oheers.fish.events.FishEatEvent;
import com.oheers.fish.events.FishInteractEvent;
import com.oheers.fish.events.McMMOTreasureEvent;
import com.oheers.fish.fishing.FishingProcessor;
import com.oheers.fish.fishing.items.Fish;
import com.oheers.fish.fishing.items.Names;
import com.oheers.fish.fishing.items.Rarity;
import com.oheers.fish.selling.GUICache;
import com.oheers.fish.selling.InteractHandler;
import com.oheers.fish.selling.SellGUI;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.permission.Permission;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class EvenMoreFish extends JavaPlugin {

    public static FishFile fishFile;
    public static RaritiesFile raritiesFile;
    public static MessageFile messageFile;
    public static CompetitionFile competitionFile;

    public static Messages msgs;
    public static MainConfig mainConfig;
    public static CompetitionConfig competitionConfig;

    public static Permission permission = null;
    public static Economy econ = null;

    public static Map<Integer, Set<String>> fish = new HashMap<>();

    public static Map<Rarity, List<Fish>> fishCollection = new HashMap<>();

    public static boolean checkingEatEvent;
    public static boolean checkingIntEvent;

    public static Competition active;
    public static CompetitionQueue competitionQueue;

    public static Logger logger;

    public static ArrayList<SellGUI> guis;

    public static boolean isUpdateAvailable;

    public static WorldGuardPlugin wgPlugin;
    public static String guardPL;
    public static boolean papi;

    public static final int METRIC_ID = 11054;

    public static final int MSG_CONFIG_VERSION = 8;
    public static final int MAIN_CONFIG_VERSION = 8;
    public static final int COMP_CONFIG_VERSION = 1;

    public void onEnable() {

        guis = new ArrayList<>();
        logger = getLogger();

        fishFile = new FishFile(this);
        raritiesFile = new RaritiesFile(this);
        messageFile = new MessageFile(this);
        competitionFile = new CompetitionFile(this);

        msgs = new Messages();
        mainConfig = new MainConfig();
        competitionConfig = new CompetitionConfig();

        if (mainConfig.isEconomyEnabled()) {
            // could not setup economy.
            if (!setupEconomy()) {
                EvenMoreFish.logger.log(Level.WARNING, "EvenMoreFish 将不会连接到经济插件。如果你没有在 config.yml 中选择此项，请安装经济处理插件。");
            }
        }

        if (!setupPermissions()) {
            Bukkit.getServer().getLogger().log(Level.SEVERE, "EvenMoreFish 无法连接到财富及财富权限插件。关闭此项以防止出现更严重的问题。");
            getServer().getPluginManager().disablePlugin(this);
        }

        Names names = new Names();
        names.loadRarities();

        competitionQueue = new CompetitionQueue();
        competitionQueue.load();

        LocaleGen lG = new LocaleGen();
        lG.createLocaleFiles(this);

        // async check for updates on the spigot page
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            isUpdateAvailable = checkUpdate();
            try {
                checkConfigVers();
            } catch (IOException exception) {
                logger.log(Level.WARNING, "Could not update messages.yml");
            }
        });

        // checks against both support region plugins and sets an active plugin (worldguard is priority)
        if (checkWG()) {
            guardPL = "worldguard";
        } else if (checkRP()) {
            guardPL = "redprotect";
        }

        listeners();
        commands();

        getConfig().options().copyDefaults();
        saveDefaultConfig();

        AutoRunner.init();

        wgPlugin = getWorldGuard();
        checkPapi();

        Metrics metrics = new Metrics(this, METRIC_ID);

        if (EvenMoreFish.mainConfig.isDatabaseOnline()) {

            // Attempts to connect to the database if enabled
            try {
                if (!Database.dbExists()) {
                    Database.createDatabase();
                }
            } catch (SQLException throwables) {
                throwables.printStackTrace();
            }

        }

        getServer().getLogger().log(Level.INFO, "EvenMoreFish by Oheers : 已开启");

    }

    public void onDisable() {

        terminateSellGUIS();

        // Ends the current competition in case the plugin is being disabled when the server will continue running
        if (Competition.isActive()) {
            active.end();
        }

        if (EvenMoreFish.mainConfig.isDatabaseOnline()) {
            try {
                Database.closeConnections();
            } catch (SQLException throwables) {
                throwables.printStackTrace();
            }
        }

        getServer().getLogger().log(Level.INFO, "EvenMoreFish by Oheers : 已关闭");

    }

    private void listeners() {

        getServer().getPluginManager().registerEvents(new JoinChecker(), this);
        getServer().getPluginManager().registerEvents(new FishingProcessor(), this);
        getServer().getPluginManager().registerEvents(new InteractHandler(this), this);
        getServer().getPluginManager().registerEvents(new UpdateNotify(), this);
        getServer().getPluginManager().registerEvents(new SkullSaver(), this);

        optionalListeners();
    }

    private void optionalListeners() {
        if (checkingEatEvent) {
            getServer().getPluginManager().registerEvents(FishEatEvent.getInstance(), this);
        }

        if (checkingIntEvent) {
            getServer().getPluginManager().registerEvents(FishInteractEvent.getInstance(), this);
        }

        if (Bukkit.getPluginManager().getPlugin("mcMMO") != null) {
            if (mainConfig.disableMcMMOTreasure()) {
                getServer().getPluginManager().registerEvents(McMMOTreasureEvent.getInstance(), this);
            }
        }
    }

    private void commands() {
        getCommand("evenmorefish").setExecutor(new CommandCentre(this));
        CommandCentre.loadTabCompletes();
    }

    private boolean setupPermissions() {
        RegisteredServiceProvider<Permission> rsp = getServer().getServicesManager().getRegistration(Permission.class);
        permission = rsp.getProvider();
        return permission != null;
    }

    private boolean setupEconomy() {
        if (mainConfig.isEconomyEnabled()) {
            RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp == null) {
                return false;
            }
            econ = rsp.getProvider();
            return econ != null;
        } else return false;

    }

    // gets called on server shutdown to simulate all player's closing their /emf shop GUIs
    private void terminateSellGUIS() {
        for (SellGUI gui : guis) {
            GUICache.attemptPop(gui.getPlayer(), true);
        }
        guis.clear();
    }

    public void reload() {

        terminateSellGUIS();

        setupEconomy();

        fish = new HashMap<>();
        fishCollection = new HashMap<>();

        reloadConfig();
        saveDefaultConfig();

        Names names = new Names();
        names.loadRarities();

        HandlerList.unregisterAll(FishEatEvent.getInstance());
        HandlerList.unregisterAll(FishInteractEvent.getInstance());
        HandlerList.unregisterAll(McMMOTreasureEvent.getInstance());
        optionalListeners();

        msgs = new Messages();
        mainConfig = new MainConfig();
        competitionConfig = new CompetitionConfig();

        competitionQueue.load();

        guis = new ArrayList<>();
    }

    // Checks for updates, surprisingly
    private boolean checkUpdate() {


        String[] spigotVersion = new UpdateChecker(this, 91310).getVersion().split("\\.");
        String[] serverVersion = getDescription().getVersion().split("\\.");

        for (int i=0; i<serverVersion.length; i++) {
            if (spigotVersion[i] != null) {
                if (Integer.parseInt(spigotVersion[i]) > Integer.parseInt(serverVersion[i])) {
                    return true;
                }
            } else {
                return false;
            }
        }
        return false;
    }

    private void checkConfigVers() throws IOException {

        if (msgs.configVersion() < MSG_CONFIG_VERSION) {
            ConfigUpdater.updateMessages(msgs.configVersion());
            getLogger().log(Level.WARNING, "你的 messages.yml 不是最新的。此插件自动增加了新的配置功能，但是你也许需要编辑它们" +
                    "才能配合你的服务器使用。");

            EvenMoreFish.messageFile.reload();
        }

        if (mainConfig.configVersion() < MAIN_CONFIG_VERSION) {
            ConfigUpdater.updateConfig(mainConfig.configVersion());
            getLogger().log(Level.WARNING, "你的 config.yml 不是最新的。此插件自动增加了新的配置功能，但是你也许需要编辑它们" +
                    "才能配合你的服务器使用。");

            reloadConfig();
        }

        if (competitionConfig.configVersion() < COMP_CONFIG_VERSION) {
            getLogger().log(Level.WARNING, "你的 competitions.yml 不是最新的。此插件可能已经添加一些新的配置功能，并且如果你没有" +
                    "最新的配置文件的话，你无法自定义它们。要更新的话，要么删除你的 competitions.yml 文件并重启服务器来创建一个全新的，" +
                    "要么浏览最近的更新，自行添加丢失的部分。https://www.spigotmc.org/resources/evenmorefish.91310/updates/");

            EvenMoreFish.competitionFile.reload();
        }
    }

    /* Gets the worldguard plugin, returns null and assumes the player has this functionality disabled if it
       can't find the plugin. */
    private WorldGuardPlugin getWorldGuard() {
        return (WorldGuardPlugin) this.getServer().getPluginManager().getPlugin("WorldGuard");
    }

    private void checkPapi() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            papi = true;
            new PlaceholderReceiver(this).register();
        }

    }

    private boolean checkRP(){
        Plugin pRP = Bukkit.getPluginManager().getPlugin("RedProtect");
        return (pRP != null && mainConfig.regionWhitelist());
    }

    private boolean checkWG(){
        Plugin pWG = Bukkit.getPluginManager().getPlugin("WorldGuard");
        return (pWG != null && mainConfig.regionWhitelist());
    }
}
