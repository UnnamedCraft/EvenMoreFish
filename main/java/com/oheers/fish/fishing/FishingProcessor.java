package com.oheers.fish.fishing;

import com.oheers.fish.EvenMoreFish;
import com.oheers.fish.FishUtils;
import com.oheers.fish.api.EMFFishEvent;
import com.oheers.fish.competition.Competition;
import com.oheers.fish.competition.reward.Reward;
import com.oheers.fish.config.messages.Message;
import com.oheers.fish.database.Database;
import com.oheers.fish.database.FishReport;
import com.oheers.fish.fishing.items.Fish;
import com.oheers.fish.fishing.items.Rarity;
import com.oheers.fish.xmas2021.ParticleEngine;
import com.oheers.fish.xmas2021.Xmas2021;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

public class FishingProcessor implements Listener {

    private static final List<String> breakabletools = Arrays.asList(
            "FISHING_ROD",
            "SHOVEL",
            "TRIDENT",
            "AXE",
            "BOW",
            "HOE",
            "SHEARS",
            "HELMET",
            "CHESTPLATE",
            "LEGGINGS",
            "BOOTS",
            "SHIELD",
            "CROSSBOW",
            "ELYTRA",
            "FLINT_AND_STEEL"
    );

    @EventHandler
    public static void process(PlayerFishEvent event) {
        if (!EvenMoreFish.mainConfig.getEnabled()) {
            return;
        }

        if (!competitionOnlyCheck()) {
            return;
        }

        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH) {

            if (!FishUtils.checkRegion(event.getHook().getLocation(), EvenMoreFish.mainConfig.getAllowedRegions())) {
                return;
            }

            if (!FishUtils.checkWorld(event.getHook().getLocation())) {
                return;
            }

            Player player = event.getPlayer();

            Fish fish = getFish(randomWeightedRarity(player), event.getHook().getLocation(), player);
            if (fish == null) return;
            fish.setFisherman(player.getUniqueId());
            fish.init();
            // puts all the fish information into a format that Messages.renderMessage() can print out nicely

            String length = Float.toString(fish.getLength());
            // Translating the colours because some servers store colour in their fish name
            String name = FishUtils.translateHexColorCodes(fish.getName());
            String rarity = FishUtils.translateHexColorCodes(fish.getRarity().getValue());

            if (fish.hasFishRewards()) {
                for (Reward fishReward : fish.getFishRewards()) {
                    fishReward.run(player);
                }
            }

            // checks if the fish can have durability, and if it's set in the config it receives random durability
            if (checkBreakable(fish.getType().getType())) fish.randomBreak();

            EMFFishEvent cEvent = new EMFFishEvent(fish, event.getPlayer());
            Bukkit.getPluginManager().callEvent(cEvent);

            Message msg = new Message()
                    .setMSG(EvenMoreFish.msgs.getFishCaught())
                    .setPlayer(player.getName())
                    .setRarityColour(fish.getRarity().getColour())
                    .setLength(length)
                    .setRarity(rarity)
                    .setReceiver(player);

            if (fish.getDisplayName() != null) msg.setFishCaught(fish.getDisplayName());
            else msg.setFishCaught(name);

            if (fish.getRarity().getDisplayName() != null) msg.setRarity(fish.getRarity().getDisplayName());
            else msg.setRarity(rarity);

            if (fish.getLength() != -1) {
                msg.setMSG(EvenMoreFish.msgs.getFishCaught());
            } else {
                msg.setMSG(EvenMoreFish.msgs.getLengthlessFishCaught());
            }

            // Gets whether it's a serverwide announce or not
            if (fish.getRarity().getAnnounce()) {
                // should we only broadcast this information to rod holders?
                FishUtils.broadcastFishMessage(msg, false);
            } else {
                // sends it to just the fisher
                player.sendMessage(msg.toString());
            }

            try {
                competitionCheck(fish.clone(), event.getPlayer());
            } catch (CloneNotSupportedException e) {
                EvenMoreFish.logger.log(Level.SEVERE, "Failed to create a clone of: " + fish);
                e.printStackTrace();
            }

            // replaces the fishing item with a custom evenmorefish fish.
            Item nonCustom = (Item) event.getCaught();
            if (nonCustom != null) {
                if (fish.getType().getType() != Material.AIR) nonCustom.setItemStack(fish.give());
                else nonCustom.remove();
            }

            if (EvenMoreFish.mainConfig.isDatabaseOnline()) {
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        try {

                            // increases the fish fished count if the fish is already in the db
                            if (Database.hasFish(fish.getName())) {
                                Database.fishIncrease(fish.getName());

                                // sets the new leader in top fish, if the player has fished a record fish
                                if (Database.getTopLength(fish.getName()) < fish.getLength()) {
                                    Database.newTopSpot(player, fish.getName(), fish.getLength());
                                }
                            } else {
                                // the database doesn't contain the fish yet
                                Database.add(fish, player);
                            }

                            boolean foundReport = false;

                            if (EvenMoreFish.fishReports.containsKey(player.getUniqueId())) {
                                for (FishReport report : EvenMoreFish.fishReports.get(player.getUniqueId())) {
                                    if (report.getName().equals(fish.getName()) && report.getRarity().equals(fish.getRarity().getValue())) {
                                        report.addFish(fish);
                                        foundReport = true;
                                    }
                                }
                            }

                            if (!foundReport) {
                                EvenMoreFish.fishReports.get(player.getUniqueId()).add(new FishReport(fish.getRarity().getValue(), fish.getName(), fish.getLength(), 1));
                            }

                        } catch (SQLException throwables) {
                            throwables.printStackTrace();
                        }
                    }
                }.runTaskAsynchronously(EvenMoreFish.getProvidingPlugin(EvenMoreFish.class));
            }
        } else if (event.getState() == PlayerFishEvent.State.FISHING) {
            if (!EvenMoreFish.decidedRarities.containsKey(event.getPlayer().getUniqueId())) {
                EvenMoreFish.decidedRarities.put(event.getPlayer().getUniqueId(), randomWeightedRarity(event.getPlayer()));
            }

            if (EvenMoreFish.decidedRarities.get(event.getPlayer().getUniqueId()).isXmas2021()) {

                if (!Objects.equals(EvenMoreFish.xmas2021Config.getParticleMessage(), "none")) {
                    event.getPlayer().sendMessage(FishUtils.translateHexColorCodes(EvenMoreFish.xmas2021Config.getParticleMessage()));
                }

                if (EvenMoreFish.xmas2021Config.doXmas2021Particles()) {
                    ParticleEngine.renderParticles(event.getHook());
                }
            }
        } else if (event.getState() == PlayerFishEvent.State.REEL_IN) {
            // For a failed attempt the player needs to have triggered a FISHING which generates a pre-decided rarity.
            if (EvenMoreFish.decidedRarities.get(event.getPlayer().getUniqueId()).isXmas2021()) {
                EvenMoreFish.decidedRarities.remove(event.getPlayer().getUniqueId());
            }
        }
    }

    public static boolean xmas2021Check(Rarity r, Player f) {
        if (r.isXmas2021()) {
            Fish fish = Xmas2021.getFish();
            for (FishReport report : EvenMoreFish.fishReports.get(f.getUniqueId())) {
                if (report.getName().equals(fish.getName()) && report.getRarity().equals(r.getValue())) {
                    // it's not ok to proceed with the selected rarity
                    return false;
                }
            }
        }
        // it's ok to proceed with the selected rarity
        return true;
    }

    public static Rarity randomWeightedRarity(Player fisher) {

        if (EvenMoreFish.decidedRarities.containsKey(fisher.getUniqueId())) {
            Rarity chosenRarity = EvenMoreFish.decidedRarities.get(fisher.getUniqueId());
            EvenMoreFish.decidedRarities.remove(fisher.getUniqueId());
            return chosenRarity;
        }

        // Loads all the rarities
        List<Rarity> allowedRarities = new ArrayList<>();

        if (EvenMoreFish.permission != null) {
            for (Rarity rarity : EvenMoreFish.fishCollection.keySet()) {
                boolean xmas2021Pass = false;

                if (rarity.isXmas2021()) {
                    if (EvenMoreFish.xmas2021Config.isOneFishPerDay()) {
                        if (Xmas2021.hiddenCheck()) {
                            if (xmas2021Check(rarity, fisher)) {
                                xmas2021Pass = true;
                            }
                        }
                    } else xmas2021Pass = true;
                } else {
                    xmas2021Pass = true;
                }

                if (rarity.getPermission() != null) {
                    if (EvenMoreFish.permission.has(fisher, rarity.getPermission()) && xmas2021Pass) {
                        allowedRarities.add(rarity);
                    }
                } else if (xmas2021Pass) {
                    allowedRarities.add(rarity);
                }
            }

        } else {
            for (Rarity r : EvenMoreFish.fishCollection.keySet()) {
                if (r.isXmas2021()) {
                    if (xmas2021Check(r, fisher)) {
                        if (EvenMoreFish.xmas2021Config.isOneFishPerDay()) {
                            if (Xmas2021.hiddenCheck()) allowedRarities.add(r);
                        } else allowedRarities.add(r);
                    }
                } else {
                    allowedRarities.addAll(EvenMoreFish.fishCollection.keySet());
                }
            }

        }

        double totalWeight = 0;

        // Weighted random logic (nabbed from stackoverflow)
        for (Rarity r : allowedRarities) {
            totalWeight += r.getWeight();
        }

        int idx = 0;
        for (double r = Math.random() * totalWeight; idx < allowedRarities.size() - 1; ++idx) {
            r -= allowedRarities.get(idx).getWeight();
            if (r <= 0.0) break;
        }

        if (allowedRarities.size() == 0) {
            EvenMoreFish.logger.log(Level.SEVERE, "用户 " + fisher.getName() + " 没有任何稀有度的鱼可供垂钓。他们将不会收到鱼。");
            return null;
        }

        return allowedRarities.get(idx);
    }

    private static Fish randomWeightedFish(List<Fish> fishList) {
        double totalWeight = 0;

        // Weighted random logic (nabbed from stackoverflow)
        for (Fish fish : fishList) {
            totalWeight += fish.getWeight();
        }

        int idx = 0;
        for (double r = Math.random() * totalWeight; idx < fishList.size() - 1; ++idx) {
            r -= fishList.get(idx).getWeight();
            if (r <= 0.0) break;
        }

        return fishList.get(idx);
    }

    public static Fish getFish(Rarity r, Location l, Player p) {
        if (r == null) return null;
        // will store all the fish that match the player's biome or don't discriminate biomes

        List<Fish> available = new ArrayList<>();

        // Protection against /emf admin reload causing the plugin to be unable to get the rarity
        if (EvenMoreFish.fishCollection.get(r) == null) r = randomWeightedRarity(p);

        if (r.isXmas2021()) {
            if (FishUtils.checkRegion(l, Xmas2021.getFish().getAllowedRegions())) {
                return Xmas2021.getFish();
            }
        }


        for (Fish f : EvenMoreFish.fishCollection.get(r)) {

            if (EvenMoreFish.permission != null && f.getPermissionNode() != null) {
                if (!EvenMoreFish.permission.has(p, f.getPermissionNode())) {
                    continue;
                }
            }

            if (!FishUtils.checkRegion(l, f.getAllowedRegions())) {
                continue;
            }

            if (l.getWorld() != null) {
                if (f.getBiomes().contains(l.getBlock().getBiome()) || f.getBiomes().isEmpty()) {
                    available.add(f);
                }
            } else EvenMoreFish.logger.log(Level.SEVERE, "Could not get world for " + p.getUniqueId());
        }

        // if the config doesn't define any fish that can be fished in this biome.
        if (available.isEmpty()) {
            EvenMoreFish.logger.log(Level.WARNING, "此处没有稀有度为 " + r.getValue() + " 的鱼，它们可以在 (x=" + l.getX() + ", y=" + l.getY() + ", z=" + l.getZ() + ") 处垂钓。");
            return null;
        }

        // checks whether weight calculations need doing for fish
        if (r.isFishWeighted()) {
            return randomWeightedFish(available);
        } else {
            int ran = ThreadLocalRandom.current().nextInt(available.size());
            return available.get(ran);
        }
    }

    // Checks if it should be giving the player the fish considering the fish-only-in-competition option in config.yml
    public static boolean competitionOnlyCheck() {
        if (EvenMoreFish.mainConfig.isCompetitionUnique()) {
            return Competition.isActive();
        } else {
            return true;
        }
    }

    public static void competitionCheck(Fish fish, Player fisherman) {
        if (Competition.isActive()) {
            EvenMoreFish.active.applyToLeaderboard(fish, fisherman);
        }
    }

    private static boolean checkBreakable(Material material) {
        if (EvenMoreFish.mainConfig.doingRandomDurability()) {
            for (String s : breakabletools) {
                if (material.toString().contains(s)) {
                    return true;
                }
            }

            return false;
        }

        return false;
    }
}
