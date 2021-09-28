package com.oheers.fish;

import com.oheers.fish.competition.Competition;
import com.oheers.fish.competition.CompetitionType;
import com.oheers.fish.config.messages.Message;
import com.oheers.fish.fishing.items.Fish;
import com.oheers.fish.fishing.items.Rarity;
import com.oheers.fish.selling.SellGUI;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;

public class CommandCentre implements TabCompleter, CommandExecutor {

    private static final List<String> empty = new ArrayList<>();

    public EvenMoreFish plugin;

    public CommandCentre(EvenMoreFish plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        // Aliases are set in the plugin.yml
        if (cmd.getName().equalsIgnoreCase("evenmorefish")) {
            if (args.length == 0) {
                sender.sendMessage(Help.std_help);
            } else {
                control(sender, args);
            }
        }

        return true;
    }

    private void control(CommandSender sender, String[] args) {

        // we've already checked that that args exist
        switch (args[0].toLowerCase()) {
            case "top":
                if (EvenMoreFish.permission.has(sender, "emf.top")) {
                    if (!Competition.isActive()) {
                        sender.sendMessage(FishUtils.translateHexColorCodes(EvenMoreFish.msgs.competitionNotRunning()));
                    } else {
                        EvenMoreFish.active.sendLeaderboard((Player) sender);
                    }
                } else {
                    sender.sendMessage(new Message().setMSG(EvenMoreFish.msgs.getNoPermission()).setReceiver((Player) sender).toString());
                }
                break;
            case "shop":
                if (sender instanceof Player) {
                    if (EvenMoreFish.mainConfig.isEconomyEnabled()) {
                        if (EvenMoreFish.permission.has(sender, "emf.shop")) {
                            SellGUI gui = new SellGUI((Player) sender);
                            EvenMoreFish.guis.add(gui);
                        } else {
                            sender.sendMessage(new Message().setMSG(EvenMoreFish.msgs.getNoPermission()).setReceiver((Player) sender).toString());
                        }
                    } else {
                        sender.sendMessage(new Message().setMSG(EvenMoreFish.msgs.economyDisabled()).setReceiver((Player) sender).toString());
                    }
                } else {
                    EvenMoreFish.msgs.disabledInConsole();
                }
                break;
            case "admin":
                if (EvenMoreFish.permission.has(sender, "emf.admin")) {
                    Controls.adminControl(this.plugin, args, sender);
                } else {
                    Message msg = new Message().setMSG(EvenMoreFish.msgs.getNoPermission());
                    if (sender instanceof Player) msg.setReceiver((Player) sender);
                    sender.sendMessage(msg.toString());
                }
                break;
            default:
                sender.sendMessage(Help.std_help);
        }
    }

    private static List<String> emfTabs, adminTabs, compTabs, compTypes;

    public static void loadTabCompletes() {
        adminTabs = Arrays.asList(
                "competition",
                "fish",
                "reload",
                "version"
        );

        compTabs = Arrays.asList(
                "start",
                "end"
        );

        emfTabs = Arrays.asList(
                "help",
                "shop",
                "top"
        );

        compTypes = Arrays.asList(
                "largest_fish",
                "most_fish",
                "specific_fish"
        );
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (sender instanceof Player) {

            switch (args.length) {
                case 1:
                    if (EvenMoreFish.permission.has(sender, "emf.admin")) {

                        // creates a temp version of tablist where only the qualified completes go through
                        List<String> TEMP_townTabCompletes = l(args, emfTabs);
                        // if the player is writing "admin" it adds it to the temporary tabcomplete list
                        if ("admin".startsWith(args[args.length - 1].toLowerCase())) {
                            TEMP_townTabCompletes.add("admin");
                        }
                        return TEMP_townTabCompletes;
                    } else {
                        return l(args, emfTabs);
                    }
                case 2:
                    // checks player has admin perms and has actually used "/emf admin" prior to the 2nd arg
                    if (args[0].equalsIgnoreCase("admin") && EvenMoreFish.permission.has(sender, "emf.admin")) {
                        return l(args, adminTabs);
                    } else {
                        return empty;
                    }
                case 3:
                    if (args[1].equalsIgnoreCase("competition") && args[0].equalsIgnoreCase("admin") && EvenMoreFish.permission.has(sender, "emf.admin")) {
                        return l(args, compTabs);
                    } else if (args[1].equalsIgnoreCase("fish") && args[0].equalsIgnoreCase("admin") && EvenMoreFish.permission.has(sender, "emf.admin")) {
                        List<String> returning = new ArrayList<>();
                        for (Rarity r : EvenMoreFish.fishCollection.keySet()) {
                            returning.add(r.getValue());
                        }

                        return l(args, returning);
                    } else {
                        return empty;
                    }
                case 4:
                    if (args[1].equalsIgnoreCase("fish") && args[0].equalsIgnoreCase("admin") && EvenMoreFish.permission.has(sender, "emf.admin")) {
                        for (Rarity r : EvenMoreFish.fishCollection.keySet()) {
                            if (r.getValue().equalsIgnoreCase(args[2])) {
                                List<String> fish = new ArrayList<>();
                                for (Fish f : EvenMoreFish.fishCollection.get(r)) {
                                    fish.add(f.getName());
                                }
                                return l(args, fish);
                            }
                        }
                        return empty;
                    }
                    return empty;
                case 5:
                    if (EvenMoreFish.permission.has(sender, "emf.admin") && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("competition") && args[2].equalsIgnoreCase("start")) {
                        return l(args, compTypes);
                    } else {
                        return empty;
                }
            }

            return empty;
        } else {
            // it's a console sending the command
            return empty;
        }
    }

    // works out how far the player is into the tab and reduces the returned list accordingly
    private List<String> l(String[] progress, List<String> total) {
        List<String> prep = new ArrayList<>();
        for (String s : total) {
            if (s.toLowerCase().startsWith(progress[progress.length - 1].toLowerCase())) {
                prep.add(s);
            }
        }

        return prep;
    }
}

class Controls{

    protected static void adminControl(EvenMoreFish plugin, String[] args, CommandSender sender) {

        // will only proceed after this if at least args[1] exists
        if (args.length == 1) {
            sender.sendMessage(Help.admin_help);
            return;
        }

        switch (args[1].toLowerCase()) {

            // bumps the command to another method, if it's a little too complicated it gets bumped to yet another method
            case "competition":
                competitionControl(args, sender);
                break;

            case "fish":
                if (args.length == 3) {
                    for (Rarity r : EvenMoreFish.fishCollection.keySet()) {
                        if (args[2].equalsIgnoreCase(r.getValue())) {
                            BaseComponent baseComponent = new TextComponent("");
                            baseComponent.addExtra(new TextComponent(FishUtils.translateHexColorCodes(r.getColour() + "&l" + r.getValue() + ": ")));

                            for (Fish fish : EvenMoreFish.fishCollection.get(r)) {
                                BaseComponent tC = new TextComponent(FishUtils.translateHexColorCodes(r.getColour() + "[" + fish.getName() + "] "));
                                tC.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_ITEM, TextComponent.fromLegacyText("点击来获得一条鱼"))); // The only element of the hover events basecomponents is the item json
                                tC.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/emf admin fish " + fish.getRarity().getValue() + " " + fish.getName()));
                                baseComponent.addExtra(tC);
                            }

                            sender.spigot().sendMessage(baseComponent);
                            return;
                        }
                    }
                    BaseComponent baseComponent = new TextComponent("");
                    for (Rarity r : EvenMoreFish.fishCollection.keySet()) {
                        BaseComponent tC = new TextComponent(FishUtils.translateHexColorCodes(r.getColour() + "[" + r.getValue() + "] "));
                        tC.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText("Click to view " + r.getValue() + " fish."))); // The only element of the hover events basecomponents is the item json
                        tC.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/emf admin fish " + r.getValue()));
                        baseComponent.addExtra(tC);
                    }
                    sender.spigot().sendMessage(baseComponent);
                } else if (args.length >= 4) {
                    StringBuilder using = new StringBuilder();

                    if (args.length > 4) {
                        for (int section = 3; section < args.length; section++) {
                            if (section == args.length-1) using.append(args[section]);
                            else using.append(args[section]).append(" ");
                        }
                    } else {
                        using = new StringBuilder(args[3]);
                    }

                    if (sender instanceof Player) {
                        for (Rarity r : EvenMoreFish.fishCollection.keySet()) {
                            if (args[2].equalsIgnoreCase(r.getValue())) {
                                for (Fish f : EvenMoreFish.fishCollection.get(r)) {
                                    if (f.getName().equalsIgnoreCase(using.toString())) {
                                        f.setFisherman(((Player) sender).getUniqueId());
                                        f.init();
                                        FishUtils.giveItems(Collections.singletonList(f.give()), (Player) sender);
                                    }
                                }
                            }
                        }
                    } else {
                        EvenMoreFish.msgs.disabledInConsole();
                    }

                } else {
                    BaseComponent baseComponent = new TextComponent("");
                    for (Rarity r : EvenMoreFish.fishCollection.keySet()) {
                        BaseComponent tC = new TextComponent(FishUtils.translateHexColorCodes(r.getColour() + "[" + r.getValue() + "] "));
                        tC.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText("点击来查看 " + r.getValue() + " 鱼。"))); // The only element of the hover events basecomponents is the item json
                        tC.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/emf admin fish " + r.getValue()));
                        baseComponent.addExtra(tC);
                    }
                    sender.spigot().sendMessage(baseComponent);
                }

                break;

            case "reload":

                EvenMoreFish.fishFile.reload();
                EvenMoreFish.raritiesFile.reload();
                EvenMoreFish.messageFile.reload();
                EvenMoreFish.competitionFile.reload();

                plugin.reload();
                plugin.reloadConfig();

                Message message = new Message().setMSG(EvenMoreFish.msgs.getReloaded());
                if (sender instanceof Player) message.setReceiver((Player) sender);
                sender.sendMessage(message.toString());
                break;

            case "version":
                Message msg = new Message().setMSG(
                        EvenMoreFish.msgs.getSTDPrefix() + "EvenMoreFish by Oheers " + plugin.getDescription().getVersion() + "\n" +
                                EvenMoreFish.msgs.getSTDPrefix() + "MCV: " + Bukkit.getServer().getVersion() + "\n" +
                                EvenMoreFish.msgs.getSTDPrefix() + "SSV: " + Bukkit.getServer().getBukkitVersion() + "\n" +
                                EvenMoreFish.msgs.getSTDPrefix() + "Online: " + Bukkit.getServer().getOnlineMode()
                );
                if (sender instanceof Player) msg.setReceiver((Player) sender);
                sender.sendMessage(msg.toString());
                break;
            default:
                sender.sendMessage(Help.admin_help);
        }
    }

    protected static void competitionControl(String[] args, CommandSender player) {
        if (args.length == 2) {
            player.sendMessage(Help.comp_help);
        } else {
            {
                if (args[2].equalsIgnoreCase("start")) {
                    // if the admin has only done /emf admin competition start
                    if (args.length < 4) {
                        startComp(Integer.toString(EvenMoreFish.mainConfig.getCompetitionDuration()*60), player, CompetitionType.LARGEST_FISH);
                    } else {
                        if (args.length < 5) {
                            startComp(args[3], player, CompetitionType.LARGEST_FISH);
                        } else {
                            try {
                                startComp(args[3], player, CompetitionType.valueOf(args[4].toUpperCase()));
                            } catch (IllegalArgumentException iae) {
                                player.sendMessage(FishUtils.translateHexColorCodes(EvenMoreFish.msgs.getInvalidType()));
                            }
                        }
                    }
                }

                else if (args[2].equalsIgnoreCase("end")) {
                    if (Competition.isActive()) {
                        EvenMoreFish.active.end();
                    } else {
                        player.sendMessage(FishUtils.translateHexColorCodes(EvenMoreFish.msgs.competitionNotRunning()));
                    }
                } else {
                    player.sendMessage(Help.comp_help);
                }
            }
        }
    }

    protected static void startComp(String argsDuration, CommandSender player, CompetitionType type) {
        if (Competition.isActive()) {
            player.sendMessage(FishUtils.translateHexColorCodes(EvenMoreFish.msgs.competitionRunning()));
            return;
        }

        try {
            // converts argsDuration to an integer (throwing exceptions) and starts a competition with that
            int duration = Integer.parseInt(argsDuration);
            // I've just discovered /emf admin competition start -1 causes some funky stuff - so this prevents that.
            if (duration > 0) {
                Competition comp = new Competition(duration, type);

                if (type == CompetitionType.SPECIFIC_FISH) comp.chooseFish(null, true);
                else comp.leaderboardApplicable = true;

                comp.initRewards(null, true);
                comp.initBar(null);
                comp.initGetNumbersNeeded(null);
                EvenMoreFish.active = comp;
                comp.begin(true);
            } else {
                player.sendMessage(FishUtils.translateHexColorCodes(EvenMoreFish.msgs.notInteger()));
            }
        } catch (NumberFormatException nfe) {
            player.sendMessage(FishUtils.translateHexColorCodes(EvenMoreFish.msgs.notInteger()));
        }
    }
}

class Help {

    public static Map<String, String> cmdDictionary = new HashMap<>();
    public static Map<String, String> adminDictionary = new HashMap<>();
    public static Map<String, String> compDictionary = new HashMap<>();

    public static String std_help, admin_help, comp_help;

    // puts values into the command dictionaries for later use in /emf help and what not
    public static void loadValues() {

        cmdDictionary.put("emf admin", "管理员指令帮助页面。");
        cmdDictionary.put("emf help", "将这个页面展示给你。");
        cmdDictionary.put("emf shop", "开启一个商店来售卖你的鱼。");
        cmdDictionary.put("emf top", "显示正在进行的比赛的排行榜。");

        adminDictionary.put("emf admin competition <start/end> <time(seconds)>", "开始或结束一场比赛");
        adminDictionary.put("emf admin reload", "重新加载此插件的配置文件");
        adminDictionary.put("emf admin version", "显示插件信息。");

        compDictionary.put("emf admin competition start <time<seconds>", "开始一场特定时间长度的比赛");
        compDictionary.put("emf admin competition end <time<seconds>", "（如果有正在进行的比赛的话）结束当前的比赛");

        std_help = formString(cmdDictionary);
        admin_help = formString(adminDictionary);
        comp_help = formString(compDictionary);

        // gc
        cmdDictionary = null;
        adminDictionary = null;
        compDictionary = null;

    }

    public static String formString(Map<String, String> dictionary) {

        StringBuilder out = new StringBuilder();

        out.append(FishUtils.translateHexColorCodes(EvenMoreFish.msgs.getSTDPrefix() + "----- &a&lEvenMoreFish &r-----\n"));

        for (String s : dictionary.keySet()) {
            // we pass a null into here since there's no need to use placeholders in a help message.
            out.append(new Message().setCMD(s).setDesc(dictionary.get(s)).setMSG(EvenMoreFish.msgs.getEMFHelp()).toString()).append("\n");
        }

        return out.toString();

    }

}
