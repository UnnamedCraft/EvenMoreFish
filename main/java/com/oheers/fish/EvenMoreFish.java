package com.oheers.fish;

import com.oheers.fish.competition.AutoRunner;
import com.oheers.fish.competition.Competition;
import com.oheers.fish.competition.CompetitionQueue;
import com.oheers.fish.competition.JoinChecker;
import com.oheers.fish.config.*;
import com.oheers.fish.config.messages.Messages;
import com.oheers.fish.database.Database;
import com.oheers.fish.database.FishReport;
import com.oheers.fish.events.FishEatEvent;
import com.oheers.fish.events.FishInteractEvent;
import com.oheers.fish.events.McMMOTreasureEvent;
import com.oheers.fish.fishing.FishingProcessor;
import com.oheers.fish.fishing.items.Fish;
import com.oheers.fish.fishing.items.Names;
import com.oheers.fish.fishing.items.Rarity;
import com.oheers.fish.selling.InteractHandler;
import com.oheers.fish.selling.SellGUI;
import com.oheers.fish.xmas2021.ConfigReader;
import com.oheers.fish.xmas2021.GUISecurity;
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

    public static Messages msgs;
    public static MainConfig mainConfig;
    public static CompetitionConfig competitionConfig;
    public static ConfigReader xmas2021Config;

    public static Permission permission = null;
    public static Economy econ = null;

    public static Map<Integer, Set<String>> fish = new HashMap<>();

    public static Map<Rarity, List<Fish>> fishCollection = new HashMap<>();

    public static Map<UUID, List<FishReport>> fishReports = new HashMap<>();

    public static boolean checkingEatEvent;
    public static boolean checkingIntEvent;

    public static Competition active;
    public static CompetitionQueue competitionQueue;

    public static Logger logger;
    public static ArrayList<SellGUI> guis;

    // this is for pre-deciding a rarity and running particles if it will be chosen
    // it's a work-in-progress solution and probably won't stick.
    public static Map<UUID, Rarity> decidedRarities;
    public static boolean isUpdateAvailable;

    public static WorldGuardPlugin wgPlugin;
    public static String guardPL;
    public static boolean papi;

    public static final int METRIC_ID = 11054;

    public static final int MSG_CONFIG_VERSION = 8;
    public static final int MAIN_CONFIG_VERSION = 9;
    public static final int COMP_CONFIG_VERSION = 1;

    @Override
    public void onEnable() {

        guis = new ArrayList<>();
        decidedRarities = new HashMap<>();
        logger = getLogger();

        getConfig().options().copyDefaults();
        saveDefaultConfig();

        mainConfig = new MainConfig(this);
        msgs = new Messages(this);

        fishFile = new FishFile(this);
        raritiesFile = new RaritiesFile(this);
        // HSGamer, this class was made before your contributions - I am incredibly grateful for your work and will incorporate your changes
        // into following updates.
        xmas2021Config = new ConfigReader(this);
        competitionConfig = new CompetitionConfig(this);

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

        // checks against both support region plugins and sets an active plugin (worldguard is priority)
        if (checkWG()) {
            guardPL = "worldguard";
        } else if (checkRP()) {
            guardPL = "redprotect";
        }

        Names names = new Names();
        names.loadRarities(fishFile.getConfig(), raritiesFile.getConfig(), false);
        names.loadRarities(xmas2021Config.getConfig(), xmas2021Config.getConfig(), true);

        if (!names.regionCheck && !(mainConfig.getAllowedRegions().size() > 0)) guardPL = null;

        competitionQueue = new CompetitionQueue();
        competitionQueue.load();

        // async check for updates on the spigot page
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            isUpdateAvailable = checkUpdate();
            try {
                checkConfigVers();
            } catch (IOException exception) {
                logger.log(Level.WARNING, "Could not update messages.yml");
            }
        });

        listeners();
        commands();

        AutoRunner.init();

        wgPlugin = getWorldGuard();
        checkPapi();

        Metrics metrics = new Metrics(this, METRIC_ID);

        if (EvenMoreFish.mainConfig.isDatabaseOnline()) {

            Database.getUrl();

            // Attempts to connect to the database if enabled
            try {
                if (!Database.fishTableExists()) {
                    Database.createDatabase();
                }
                if (!Database.userTableExists()) {
                    Database.createUserTable();
                }
            } catch (SQLException throwables) {
                throwables.printStackTrace();
            }

        }

        getServer().getLogger().log(Level.INFO, "EvenMoreFish by Oheers : 已开启");

    }

    @Override
    public void onDisable() {

        terminateSellGUIS();
        saveUserData();

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
        getServer().getPluginManager().registerEvents(new GUISecurity(), this);

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
        getServer().getOnlinePlayers().forEach(player -> {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof SellGUI) {
                player.closeInventory();
            }
        });
    }

    private void saveUserData() {
        for (UUID uuid : fishReports.keySet()) {
            if (Database.hasUser(uuid.toString())) {
                Database.writeUserData(uuid.toString(), fishReports.get(uuid));
            } else {
                Database.addUser(uuid.toString());
                Database.writeUserData(uuid.toString(), fishReports.get(uuid));
            }
        }
    }

    public void reload() {

        terminateSellGUIS();

        setupEconomy();

        fish = new HashMap<>();
        fishCollection = new HashMap<>();

        reloadConfig();
        saveDefaultConfig();

        Names names = new Names();
        names.loadRarities(fishFile.getConfig(), raritiesFile.getConfig(), false);
        names.loadRarities(xmas2021Config.getConfig(), xmas2021Config.getConfig(), true);

        HandlerList.unregisterAll(FishEatEvent.getInstance());
        HandlerList.unregisterAll(FishInteractEvent.getInstance());
        HandlerList.unregisterAll(McMMOTreasureEvent.getInstance());
        optionalListeners();

        mainConfig.reload();
        msgs.reload();
        competitionConfig.reload();

        competitionQueue.load();
    }

    // Checks for updates, surprisingly
    private boolean checkUpdate() {


        String[] spigotVersion = new UpdateChecker(this, 91310).getVersion().split("\\.");
        String[] serverVersion = getDescription().getVersion().split("\\.");

        for (int i=0; i<serverVersion.length; i++) {
            if (i < spigotVersion.length) {
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

            msgs.reload();
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

            competitionConfig.reload();
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
        return (pRP != null);
    }

    private boolean checkWG(){
        Plugin pWG = Bukkit.getPluginManager().getPlugin("WorldGuard");
        return (pWG != null);
    }
}
