package com.oheers.fish.config.messages;

import com.oheers.fish.EvenMoreFish;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;

public class Messages {

    private final EvenMoreFish plugin;
    private FileConfiguration config;

    public Messages(EvenMoreFish plugin) {
        this.plugin = plugin;
        reload();
    }

	public void reload() {
        File messageFile = new File(this.plugin.getDataFolder(), "messages.yml");

        if (!messageFile.exists()) {
            File parentFile = messageFile.getAbsoluteFile().getParentFile();
            if (!parentFile.exists()) {
                parentFile.mkdirs();
            }
            try {
                messageFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }

            InputStream stream = this.plugin.getResource("locales/messages_" + EvenMoreFish.mainConfig.getLocale() + ".yml");
            if (stream == null) {
                stream = this.plugin.getResource("locales/messages_en.yml");
            }
            if (stream == null) {
                EvenMoreFish.logger.log(Level.SEVERE, "Could not get resource for EvenMoreFish/messages.yml");
                return;
            }
            try {
                Files.copy(stream, messageFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        this.config = new YamlConfiguration();

        try {
            this.config.load(messageFile);
        } catch (IOException | org.bukkit.configuration.InvalidConfigurationException e) {
            e.printStackTrace();
        }
    }

    public int configVersion() {
        return config.getInt("config-version");
    }

    public String getSellMessage() {
        return getSTDPrefix() + config.getString("fish-sale");
    }

    public String getWorthGUIName() {
        return config.getString("worth-gui-name");
    }

    public String noFish() {
        if (Objects.equals(config.getString("no-record"), "none")) {
            return null;
        } else return getSTDPrefix() + config.getString("no-record");
    }

    public String noWinners() {
        return getSTDPrefix() + config.getString("no-winners");
    }

    public String getCompetitionEnd() {
        return getSTDPrefix() + config.getString("contest-end");
    }

    public String getCompetitionStart() {
        return getSTDPrefix() + config.getString("contest-start");
    }

    public String getCompetitionJoin() {
        String returning = config.getString("contest-join");
        if (returning != null) return getSTDPrefix() + returning;
        else return getSTDPrefix() + "&r一场{type}的钓鱼比赛正在进行。";
    }

    public int getLeaderboardCount() {
        return config.getInt("leaderboard-count");
    }

    public String getLargestFishLeaderboard() {
        if (config.getString("leaderboard-largest-fish") != null) {
            return getSTDPrefix() + config.getString("leaderboard-largest-fish");
        } else {
            if (config.getString("leaderboard") != null) {
                return getSTDPrefix() + config.getString("leaderboard");
            }
        }
        return "&r#{position} | {pos_colour}{player} &r({rarity} {fish}&r, {length}厘米)";
    }

    public String getMostFishLeaderboard() {
        if (config.getString("leaderboard-most-fish") != null) {
            return getSTDPrefix() + config.getString("leaderboard-most-fish");
        } else {
            return "&r#{position} | {pos_colour}{player} &r({pos_colour}{amount} &r鱼)";
        }
    }

    public String getEMFHelp() {
        return getSTDPrefix() + config.getString("help");
    }
    public String getBarSecond() {
        return config.getString("bossbar.second");
    }

    public String getBarMinute() {
        return config.getString("bossbar.minute");
    }

    public String getBarHour() {
        return config.getString("bossbar.hour");
    }

    private String getPrefix() {
        return config.getString("prefix");
    }

    private String getStandardPrefixColour() {
        return config.getString("prefix-regular");
    }

    private String getAdminPrefixColour() {
        return config.getString("prefix-admin");
    }

    private String getErrorPrefixColour() {
        return config.getString("prefix-error");
    }

    public String getSTDPrefix() {
        return getStandardPrefixColour() + getPrefix() + "&r";
    }

    public String getAdminPrefix() {
        return getAdminPrefixColour() + getPrefix() + "&r";
    }

    public String getErrorPrefix() {
        return getErrorPrefixColour() + getPrefix() + "&r";
    }

    public String getReloaded() {
        return getAdminPrefix() + "成功重载插件";
    }

    public String getFishCaught() {
        return config.getString("fish-caught");
    }

    public String getLengthlessFishCaught() {
        String returning = config.getString("lengthless-fish-caught");
        if (returning != null) return returning;

        returning = getFishCaught();
        if (returning != null) {
            EvenMoreFish.getPlugin(EvenMoreFish.class).getLogger().log(Level.WARNING, "Missing config value: \"lengthless-fish-caught\". [messages.yml]");
            return returning;
        }

        EvenMoreFish.getPlugin(EvenMoreFish.class).getLogger().log(Level.WARNING, "Missing config value: \"lengthless-fish-caught\". [messages.yml]");
        return "&l{player} &rhas fished a {rarity_colour}&l{rarity} {rarity_colour}{fish}!";
    }

    public String getNoPermission() {
        return getErrorPrefix() + config.getString("no-permission");
    }

    public String notInteger() {
        return getErrorPrefix() + "请提供一个整数值";
    }

    public String competitionRunning() {
        return getErrorPrefix() + "比赛已经开始了";
    }

    public String competitionNotRunning() {
        return getErrorPrefix() + config.getString("no-competition-running");
    }

    public String getNotEnoughPlayers() {
        return getErrorPrefix() + config.getString("not-enough-players");
    }

    public String getSellName() {
        return config.getString("sell-gui-name");
    }

    public String getConfirmName() {
        return config.getString("confirm-gui-name");
    }

    public String getConfirmSellAllName() {
        String returning = config.getString("confirm-sell-all-gui-name");
        if (returning != null) return returning;
        else return "&6&l确认";
    }

    public String getNoValueName() {
        String s = config.getString("error-gui-name");
        if (s != null) return s;
        else return "&c&l卖不出去";
    }

    public String getNoValueSellAllName() {
        String s = config.getString("error-sell-all-gui-name");
        if (s != null) return s;
        else return "&c&l卖不出去";
    }

    public List<String> sellLore() {
        return config.getStringList("sell-gui-lore");
    }

    public List<String> noValueLore() {
        List<String> l = config.getStringList("error-gui-lore");
        if (!l.isEmpty()) return l;
        else {
            l.add("&c&l价格: &c$0");
            l.add("&c放入你钓到的鱼");
            l.add("&c在GUI中卖掉他们");
            return l;
        }
    }

    public List<String> noValueSellAllLore() {
        List<String> l = config.getStringList("error-sell-all-gui-lore");
        if (!l.isEmpty()) return l;
        else {
            l.add("&c&l价格: &c$0");
            l.add("&c这里没有一条鱼可以卖掉");
            l.add("&c在你的背包中");
            return l;
        }
    }

    public String economyDisabled() {
        return getErrorPrefix() + "插件的经济功能被禁用";
    }

    public String fishCaughtBy() {
        String returning = config.getString("fish-caught-by");
        if (returning != null) return returning;
        else return "&f被{player}钓上来了";
    }

    public String fishLength() {
        String returning = config.getString("fish-length");
        if (returning != null) return returning;
        else return "&f长度 {length}厘米";
    }

    public String getRemainingWord() {
        String returning = config.getString("bossbar.remaining");
        if (returning != null) return returning;
        else return " left";
    }

    public String getRarityPrefix() {
        String returning = config.getString("fish-rarity-prefix");
        if (returning != null) return returning;
        else return "";
    }

    public void disabledInConsole() {
        EvenMoreFish.logger.log(Level.SEVERE, "此命令在后台被禁用，请在游戏中使用");
    }

    public String getNoCompPlaceholder() {
        String returning = config.getString("no-competition-running");
        if (returning != null) return returning;
        else return "现在没有进行中的比赛";
    }

    public String getNoPlayerInposPlaceholder() {
        String returning = config.getString("no-player-in-place");
        if (returning != null) return returning;
        else return "开始在这里钓鱼吧";
    }

    public boolean shouldNullPlayerCompPlaceholder() {
        return config.getBoolean("emf-competition-player-null");
    }

    public boolean shouldNullSizeCompPlaceholder() {
        return config.getBoolean("emf-competition-size-null");
    }

    public boolean shouldNullFishCompPlaceholder() {
        return config.getBoolean("emf-competition-fish-null");
    }

    public String getFishFormat() {
        String returning = config.getString("emf-competition-fish-format");
        if (returning != null) return returning;
        else return "{length}厘米 &l{rarity} {fish}";
    }

    public String getTypeVariable(String sub) {
        return config.getString("competition-types." + sub);
    }

    public String getFirstPlaceNotification() {
        return getSTDPrefix() + config.getString("new-first");
    }

    public boolean doFirstPlaceNotification() {
        return config.getString("new-first") != null;
    }

    public boolean shouldAlwaysShowPos() {
        return config.getBoolean("always-show-pos");
    }

    public boolean doFirstPlaceActionbar() {
        boolean a = config.getBoolean("action-bar-message");
        boolean b = config.getStringList("action-bar-types").size() == 0 || config.getStringList("action-bar-types").contains(EvenMoreFish.active.getCompetitionType().toString());
        return a && b;
    }

    public String getTimeAlertMessage() {
        return getSTDPrefix() + config.getString("time-alert");
    }

    public String getInvalidType() {
        String returning = config.getString("invalid-type");
        if (returning != null) return getErrorPrefix() + returning;
        else return getErrorPrefix() + "&rThat isn't a type of competition type, available types: MOST_FISH, LARGEST_FISH, SPECIFIC_FISH";
    }

    public String singleWinner() {
        String returning = config.getString("single-winner");
        if (returning != null) return getSTDPrefix() + returning;
        else return getSTDPrefix() + "&r{player} 在 {type} 比赛中获得了冠军，恭喜！";
    }

    public String getSellAllName() {
        String returning = config.getString("sell-all-name");
        if (returning != null) return returning;
        else return "&6&l卖掉全部";
    }

    public List<String> getSellAllLore() {
        List<String> returning = config.getStringList("sell-all-lore");
        if (returning.size() != 0) return returning;
        else return Arrays.asList("&e&l价格： &e${sell-price}", "&7左键卖掉背包中所有的鱼");
    }

    public List<String> getGeneralHelp() {
        List<String> returning = config.getStringList("help-general");
        if (returning.size() == 0) EvenMoreFish.logger.log(Level.WARNING, "Missing config value: \"help-general\". [messages.yml]");
        return returning;
    }

    public List<String> getAdminHelp() {
        List<String> returning = config.getStringList("help-admin");
        if (returning.size() == 0) EvenMoreFish.logger.log(Level.WARNING, "Missing config value: \"help-admin\". [messages.yml]");
        return returning;
    }

    public List<String> getCompetitionHelp() {
        List<String> returning = config.getStringList("help-competition");
        if (returning.size() == 0) EvenMoreFish.logger.log(Level.WARNING, "Missing config value: \"help-competition\". [messages.yml]");
        return returning;
    }

    public String getPlaceFishBlocked() {
        String returning = config.getString("place-fish-blocked");
        if (returning != null) return returning;
        else return getErrorPrefix() + "You cannot place this fish.";
    }
}
