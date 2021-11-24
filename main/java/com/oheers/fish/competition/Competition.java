package com.oheers.fish.competition;

import com.oheers.fish.EvenMoreFish;
import com.oheers.fish.FishUtils;
import com.oheers.fish.competition.reward.Reward;
import com.oheers.fish.config.messages.Message;
import com.oheers.fish.fishing.items.Fish;
import com.oheers.fish.fishing.items.Rarity;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.logging.Level;

public class Competition {

    Integer maxDuration, timeLeft;
    CompetitionType competitionType;
    Bar statusBar;

    Fish selectedFish;
    int numberNeeded;

    List<Integer> alertTimes;
    Map<Integer, List<Reward>> rewards;

    int playersNeeded;

    BukkitTask timingSystem;

    Message startMessage;

    static boolean active;

    static boolean originallyRandom;

    public String competitionName;
    public boolean adminStarted;

    // In a SPECIFIC_FISH competition, there won't be a leaderboard
    public boolean leaderboardApplicable;
    public static Leaderboard leaderboard;

    public Competition(final Integer duration, final CompetitionType type) {
        this.maxDuration = duration;
        this.alertTimes = new ArrayList<>();
        this.rewards = new HashMap<>();
        this.competitionType = type;
    }

    public void begin(boolean adminStart) {
        if (competitionType == CompetitionType.RANDOM) {
            competitionType = getRandomType();
            originallyRandom = true;
        }

        if (competitionType == CompetitionType.SPECIFIC_FISH) {
            if (!chooseFish(competitionName, adminStart)) return;
        } else {
            leaderboardApplicable = true;
        }

        this.timeLeft = this.maxDuration;
        if (Bukkit.getOnlinePlayers().size() >= playersNeeded || adminStart) {
            active = true;
            if (leaderboardApplicable) initLeaderboard();
            statusBar.show();
            initTimer();
            announceBegin();
        } else {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendMessage(FishUtils.translateHexColorCodes(EvenMoreFish.msgs.getNotEnoughPlayers()));
            }
        }
    }

    public void end() {
        // print leaderboard
        this.timingSystem.cancel();
        statusBar.hide();
        if (leaderboardApplicable) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendMessage(FishUtils.translateHexColorCodes(EvenMoreFish.msgs.getCompetitionEnd()));
                sendLeaderboard(player);
            }
            handleRewards();
            leaderboard.clear();
        }
        active = false;
        if (originallyRandom) competitionType = CompetitionType.RANDOM;
    }

    // Starts an async task to decrease the time left by 1s each second
    private void initTimer() {
        this.timingSystem = new BukkitRunnable() {
            @Override
            public void run() {
                if (alertTimes.contains(timeLeft)) {
                    Message m = new Message()
                            .setMSG(EvenMoreFish.msgs.getTimeAlertMessage())
                            .setTimeFormatted(FishUtils.timeFormat(timeLeft))
                            .setTimeRaw(FishUtils.timeRaw(timeLeft))
                            .setType(competitionType);
                    if (competitionType == CompetitionType.SPECIFIC_FISH) {
                        m.setAmount(Integer.toString(numberNeeded)).setRarityColour(selectedFish.getRarity().getColour());

                        if (selectedFish.getRarity().getDisplayName() != null) m.setRarity(selectedFish.getRarity().getDisplayName());
                        else m.setRarity(selectedFish.getRarity().getValue());

                        if (selectedFish.getDisplayName() != null) m.setFishCaught(selectedFish.getDisplayName());
                        else m.setFishCaught(selectedFish.getName());
                    }

                    for (Player player : Bukkit.getOnlinePlayers()) {
                        player.sendMessage(m.toString());
                    }

                } else if (timeLeft == 0) {
                    end();
                    return;
                }
                statusBar.timerUpdate(timeLeft, maxDuration);
                timeLeft--;

            }
        }.runTaskTimer(JavaPlugin.getProvidingPlugin(getClass()), 0, 20);
    }

    public void applyToLeaderboard(Fish fish, Player fisher) {

        if (competitionType == CompetitionType.SPECIFIC_FISH || competitionType == CompetitionType.MOST_FISH) {
            // is the fish the specific fish?
            if (competitionType == CompetitionType.SPECIFIC_FISH) {
                if (!(fish.getName().equalsIgnoreCase(selectedFish.getName()) && fish.getRarity() == selectedFish.getRarity())) {
                    return;
                }
            }

            if (leaderboardApplicable) {
                CompetitionEntry entry = leaderboard.getEntry(fisher.getUniqueId());

                if (entry != null) {
                    if (entry.getValue()+1 >= leaderboard.getTopEntry().getValue() && leaderboard.getTopEntry().getPlayer() != fisher.getUniqueId()) {
                        if (EvenMoreFish.msgs.doFirstPlaceNotification()) {
                            if (EvenMoreFish.msgs.getFirstPlaceNotification() != null) {
                                Message message = new Message()
                                        .setMSG(EvenMoreFish.msgs.getFirstPlaceNotification())
                                        .setLength(Float.toString(fish.getLength()))
                                        .setRarityColour(fish.getRarity().getColour())
                                        .setFishCaught(fish.getName())
                                        .setRarity(fish.getRarity().getValue())
                                        .setPlayer(fisher.getName());
                                FishUtils.broadcastFishMessage(message, EvenMoreFish.msgs.doFirstPlaceActionbar());
                            }
                        }
                    }

                    try {
                        // re-adding the entry so it's sorted
                        leaderboard.removeEntry(entry);
                        entry.incrementValue();
                        leaderboard.addEntry(entry);
                    } catch (Exception exception) {
                        EvenMoreFish.logger.log(Level.SEVERE, "无法删除: " + entry);
                    }

                    if (entry.getValue() == numberNeeded && competitionType == CompetitionType.SPECIFIC_FISH) {
                        end();
                    }

                } else {
                    CompetitionEntry newEntry = new CompetitionEntry(fisher.getUniqueId(), fish, competitionType);
                    leaderboard.addEntry(newEntry);
                }
            } else {
                singleReward(fisher);
                end();
            }
        } else {

            // If a fish has no size it shouldn't be able to join the competition
            if (fish.getLength() <= 0) return;

            CompetitionEntry entry = leaderboard.getEntry(fisher.getUniqueId());

            if (entry != null) {

                if (entry.getFish().getLength() < fish.getLength()) {

                    if (fish.getLength() > leaderboard.getTopEntry().getFish().getLength() && leaderboard.getTopEntry().getPlayer() != fisher.getUniqueId()) {
                        if (EvenMoreFish.msgs.doFirstPlaceNotification()) {
                            Message message = new Message()
                                    .setMSG(EvenMoreFish.msgs.getFirstPlaceNotification())
                                    .setRarity(fish.getRarity().getValue())
                                    .setRarityColour(fish.getRarity().getColour())
                                    .setFishCaught(fish.getName())
                                    .setLength(Float.toString(fish.getLength()))
                                    .setPlayer(fisher.getName());
                            FishUtils.broadcastFishMessage(message, EvenMoreFish.msgs.doFirstPlaceActionbar());
                        }
                    }

                    leaderboard.removeEntry(entry);
                    leaderboard.addEntry(new CompetitionEntry(fisher.getUniqueId(), fish, competitionType));
                }

            } else {
                CompetitionEntry newEntry = new CompetitionEntry(fisher.getUniqueId(), fish, competitionType);

                if (leaderboard.getSize() != 0) {
                    if (fish.getLength() > leaderboard.getTopEntry().getFish().getLength() && leaderboard.getTopEntry().getPlayer() != fisher.getUniqueId()) {
                        if (EvenMoreFish.msgs.doFirstPlaceNotification()) {
                            Message message = new Message()
                                    .setMSG(EvenMoreFish.msgs.getFirstPlaceNotification())
                                    .setFishCaught(fish.getName())
                                    .setRarityColour(fish.getRarity().getColour())
                                    .setLength(Float.toString(fish.getLength()))
                                    .setRarity(fish.getRarity().getValue())
                                    .setPlayer(fisher.getName());
                            FishUtils.broadcastFishMessage(message, EvenMoreFish.msgs.doFirstPlaceActionbar());
                        }
                    }
                }

                leaderboard.addEntry(newEntry);
            }
        }
    }

    public void announceBegin() {
        Message msg;
        if (this.competitionType != CompetitionType.SPECIFIC_FISH) {
            msg = new Message()
                    .setMSG(EvenMoreFish.msgs.getCompetitionStart())
                    .setType(this.competitionType);
        } else {
            msg = new Message()
                    .setMSG(EvenMoreFish.msgs.getCompetitionStart())
                    .setAmount(Integer.toString(this.numberNeeded))
                    .setType(this.competitionType)
                    .setRarityColour(selectedFish.getRarity().getColour());

            if (selectedFish.getRarity().getDisplayName() != null) msg.setRarity(selectedFish.getRarity().getDisplayName());
            else msg.setRarity(selectedFish.getRarity().getValue());

            if (selectedFish.getDisplayName() != null) msg.setFishCaught(selectedFish.getDisplayName());
            else msg.setFishCaught(selectedFish.getName());
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(msg.toString());
        }

        this.startMessage = msg;
    }

    public void sendLeaderboard(Player player) {
        boolean reachingCount = true;
        if (active) {
            if (leaderboard.getSize() != 0) {

                List<UUID> leaderboardMembers = new ArrayList<>();

                List<String> competitionColours = EvenMoreFish.competitionConfig.getPositionColours();
                StringBuilder builder = new StringBuilder();
                int pos = 0;

                for (CompetitionEntry entry : leaderboard.getEntries()) {
                    pos++;
                    if (reachingCount) {
                        leaderboardMembers.add(entry.getPlayer());
                        Message message = new Message()
                                .setPosition(Integer.toString(pos))
                                .setPlayer(Bukkit.getOfflinePlayer(entry.getPlayer()).getName());

                        if (pos > competitionColours.size()) {
                            Random r = new Random();
                            int s = r.nextInt(3);
                            switch (s) {
                                case 0:
                                    message.setPositionColour("&c\u00bb &r");
                                    break;
                                case 1:
                                    message.setPositionColour("&c_ &r");
                                    break;
                                case 2:
                                    message.setPositionColour("&c&ko &r");
                                    break;
                            }

                        } else message.setPositionColour(competitionColours.get(pos - 1));

                        if (competitionType == CompetitionType.LARGEST_FISH) {
                            Fish fish = entry.getFish();
                            message.setRarityColour(fish.getRarity().getColour())
                                    .setLength(Float.toString(entry.getValue()))
                                    .setMSG(EvenMoreFish.msgs.getLargestFishLeaderboard());

                            if (fish.getRarity().getDisplayName() != null) message.setRarity(fish.getRarity().getDisplayName());
                            else message.setRarity(fish.getRarity().getValue());

                            if (fish.getDisplayName() != null) message.setFishCaught(fish.getDisplayName());
                            else message.setFishCaught(fish.getName());
                        } else {
                            message.setAmount(Integer.toString((int) entry.getValue()))
                                    .setMSG(EvenMoreFish.msgs.getMostFishLeaderboard());
                        }
                        builder.append(message);

                        if (pos == EvenMoreFish.msgs.getLeaderboardCount()) {
                            if (EvenMoreFish.msgs.shouldAlwaysShowPos()) {
                                if (leaderboardMembers.contains(player.getUniqueId())) break;
                                else reachingCount = false;
                            } else {
                                break;
                            }
                        } else {
                            builder.append("\n");
                        }
                    } else {
                        if (entry.getPlayer() == player.getUniqueId()) {
                            Message message = new Message()
                                    .setPosition(Integer.toString(pos))
                                    .setPlayer(Bukkit.getOfflinePlayer(entry.getPlayer()).getName())
                                    .setPositionColour("&f");

                            if (competitionType == CompetitionType.LARGEST_FISH) {
                                Fish fish = entry.getFish();
                                message.setRarityColour(fish.getRarity().getColour())
                                        .setLength(Float.toString(entry.getValue()))
                                        .setMSG(EvenMoreFish.msgs.getLargestFishLeaderboard());

                                if (fish.getRarity().getDisplayName() != null) message.setRarity(fish.getRarity().getDisplayName());
                                else message.setRarity(fish.getRarity().getValue());

                                if (fish.getDisplayName() != null) message.setFishCaught(fish.getDisplayName());
                                else message.setFishCaught(fish.getName());
                            } else {
                                message.setAmount(Integer.toString((int) entry.getValue()))
                                        .setMSG(EvenMoreFish.msgs.getMostFishLeaderboard());
                            }

                            builder.append("\n").append(message);
                        }
                    }
                }
                player.sendMessage(builder.toString());
            } else {
                player.sendMessage(FishUtils.translateHexColorCodes(EvenMoreFish.msgs.noFish()));
            }
        } else {
            player.sendMessage(FishUtils.translateHexColorCodes(EvenMoreFish.msgs.competitionNotRunning()));
        }
    }

    public Bar getStatusBar() {
        return this.statusBar;
    }

    public Message getStartMessage() {
        return startMessage;
    }

    public static boolean isActive() {
        return active;
    }

    public CompetitionType getCompetitionType() {
        return competitionType;
    }

    public void setNumberNeeded(int numberNeeded) {
        this.numberNeeded = numberNeeded;
    }

    public int getLeaderboardSize() {
        return leaderboard.getSize();
    }

    public Leaderboard getLeaderboard() {
        return leaderboard;
    }

    public void setCompetitionName(String competitionName) {
        this.competitionName = competitionName;
    }

    public void setAdminStarted(boolean adminStarted) {
        this.adminStarted = adminStarted;
    }

    public static void setOriginallyRandom(boolean originallyRandom) {
        Competition.originallyRandom = originallyRandom;
    }

    public void initLeaderboard() {
        leaderboardApplicable = true;
        leaderboard = new Leaderboard(competitionType);
    }

    public boolean chooseFish(String competitionName, boolean adminStart) {
        List<String> allowedRarities = EvenMoreFish.competitionConfig.allowedRarities(competitionName, adminStart);
        List<Fish> fish = new ArrayList<>();
        for (Rarity r : EvenMoreFish.fishCollection.keySet()) {
            if (allowedRarities.contains(r.getValue())) {
                fish.addAll(EvenMoreFish.fishCollection.get(r));
            }
        }

        int y = EvenMoreFish.competitionConfig.getNumberFishNeeded(competitionName, adminStart);
        if (y > 1) this.leaderboardApplicable = true;
        setNumberNeeded(y);

        try {
            Random r = new Random();
            this.selectedFish = fish.get(r.nextInt(fish.size()));
            return true;
        } catch (IllegalArgumentException exception) {
            EvenMoreFish.logger.log(Level.SEVERE, "无法加载: " + competitionName + "，因为无法随机选择一种鱼。\n如果你需要帮助，请提供以下信息:");
            EvenMoreFish.logger.log(Level.SEVERE, "fish.size(): " + fish.size());
            EvenMoreFish.logger.log(Level.SEVERE, "allowedRarities.size(): " + allowedRarities.size());
            return false;
        }
    }

    public void initAlerts(String competitionName) {
        for (String s : EvenMoreFish.competitionConfig.getAlertTimes(competitionName)) {

            String[] split = s.split(":");
            if (split.length == 2) {
                try {
                    alertTimes.add(Integer.parseInt(split[0])*60 + Integer.parseInt(split[1]));
                } catch (NumberFormatException nfe) {
                    EvenMoreFish.logger.log(Level.SEVERE, "无法将 " + s + " 转换为提醒时间。如果你需要帮助，欢迎加入 Discord 服务器: https://discord.gg/Hb9cj3tNbb");
                }
            } else {
                EvenMoreFish.logger.log(Level.SEVERE, s + " 的格式不正确。请使用 MM:SS 的格式");
            }
        }
    }

    public void initRewards(String competitionName, boolean adminStart) {
        Set<String> chosen;
        String path;

        // If the competition is an admin start or doesn't have its own rewards, we use the non-specific rewards, else we use the compeitions
        if (adminStart) {
            chosen = EvenMoreFish.competitionConfig.getRewardPositions();
            path = "rewards.";
        } else {
            if (EvenMoreFish.competitionConfig.getRewardPositions(competitionName).size() == 0) {
                chosen = EvenMoreFish.competitionConfig.getRewardPositions();
                path = "rewards.";
            } else {
                chosen = EvenMoreFish.competitionConfig.getRewardPositions(competitionName);
                path = "competitions." + competitionName + ".rewards.";
            }
        }

        if (chosen != null) {
            for (String i : chosen) {
                List<Reward> addingRewards = new ArrayList<>();
                for (String j : EvenMoreFish.competitionConfig.getStringList(path + i)) {
                    Reward reward = new Reward(j);
                    addingRewards.add(reward);
                }
                this.rewards.put(Integer.parseInt(i), addingRewards);
            }
        }
    }

    private void handleRewards() {
        if (leaderboard.getSize() != 0) {
            Iterator<CompetitionEntry> iterator = leaderboard.getIterator();
            int i = 1;
            while (iterator.hasNext() && i <= rewards.size()) {
                CompetitionEntry entry = iterator.next();
                for (Reward reward : rewards.get(i)) {
                    reward.run(Bukkit.getOfflinePlayer(entry.getPlayer()));
                }
                i++;
            }
        } else {
            if (leaderboardApplicable) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.sendMessage(FishUtils.translateHexColorCodes(EvenMoreFish.msgs.noWinners()));
                }
            }
        }
    }

    public void singleReward(Player player) {
        Message m = new Message()
                .setMSG(EvenMoreFish.msgs.singleWinner())
                .setPlayer(player.getName())
                .setType(competitionType);
        String broadcast;
        if (competitionType == CompetitionType.SPECIFIC_FISH) {
            m.setAmount(Integer.toString(numberNeeded))
                    .setRarityColour(selectedFish.getRarity().getColour());

            if (selectedFish.getRarity().getDisplayName() != null) m.setRarity(selectedFish.getRarity().getDisplayName());
            else m.setRarity(selectedFish.getRarity().getValue());

            if (selectedFish.getDisplayName() != null) m.setFishCaught(selectedFish.getDisplayName());
            else m.setFishCaught(selectedFish.getName());

            broadcast = m.toString();
        } else broadcast = m.toString();
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(broadcast);
        }
        if (rewards.size() > 0) {
            for (Reward reward : rewards.get(1)) {
                reward.run(player);
            }
        }
    }

    public void initBar(String competionName) {
        this.statusBar = new Bar();

        try {
            this.statusBar.setColour(BarColor.valueOf(EvenMoreFish.competitionConfig.getBarColour(competionName)));
        } catch (IllegalArgumentException iae) {
            EvenMoreFish.logger.log(Level.SEVERE, EvenMoreFish.competitionConfig.getBarColour(competionName) + " 不是有效的 BossBar 颜色，请检查 ");
        }

        this.statusBar.setPrefix(FishUtils.translateHexColorCodes(EvenMoreFish.competitionConfig.getBarPrefix(competionName)));
    }

    public void initGetNumbersNeeded(String competitionName) {
        this.playersNeeded = EvenMoreFish.competitionConfig.getPlayersNeeded(competitionName);
    }

    private CompetitionType getRandomType() {
        // -1 from the length so that the RANDOM isn't chosen as the random value.
        int type = new Random().nextInt(CompetitionType.values().length-1);
        return CompetitionType.values()[type];
    }
}
