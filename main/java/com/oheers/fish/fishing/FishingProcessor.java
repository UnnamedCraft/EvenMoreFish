package com.oheers.fish.fishing;

import com.oheers.fish.EvenMoreFish;
import com.oheers.fish.FishUtils;
import com.oheers.fish.api.EMFFishEvent;
import com.oheers.fish.competition.Competition;
import com.oheers.fish.config.messages.Message;
import com.oheers.fish.database.Database;
import com.oheers.fish.fishing.items.Fish;
import com.oheers.fish.fishing.items.Rarity;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
        if (EvenMoreFish.mainConfig.getEnabled()) {

            if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH) {

                if (competitionOnlyCheck()) {

                    if (!FishUtils.checkRegion(event.getHook().getLocation())) {
                        return;
                    }

                    Player player = event.getPlayer();

                    Fish fish = getFish(random(), event.getHook().getLocation().getBlock().getBiome());
                    fish.setFisherman(player.getUniqueId());
                    fish.init();
                    // puts all the fish information into a format that Messages.renderMessage() can print out nicely

                    String length = Float.toString(fish.getLength());
                    String name = FishUtils.translateHexColorCodes(fish.getRarity().getColour() + "&l" + fish.getName());
                    String rarity = FishUtils.translateHexColorCodes(fish.getRarity().getColour() + "&l" + fish.getRarity().getValue());

                    // checks if the fish can have durability, and if it's set in the config it receives random durability
                    if (checkBreakable(fish.getType().getType())) fish.randomBreak();

                    EMFFishEvent cEvent = new EMFFishEvent(fish, event.getPlayer());
                    Bukkit.getPluginManager().callEvent(cEvent);

                    Message msg = new Message()
                            .setMSG(EvenMoreFish.msgs.getFishCaught())
                            .setPlayer(player.getName())
                            .setColour(fish.getRarity().getColour())
                            .setLength(length)
                            .setFishCaught(name)
                            .setRarity(rarity)
                            .setReceiver(player);

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
                        Bukkit.getLogger().log(Level.SEVERE, "创建副本失败: " + fish);
                        e.printStackTrace();
                    }

                    // replaces the fishing item with a custom evenmorefish fish.
                    Item nonCustom = (Item) event.getCaught();
                    nonCustom.setItemStack(fish.give());

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
                                        Database.add(fish.getName(), player, fish.getLength());
                                    }

                                } catch (SQLException throwables) {
                                    throwables.printStackTrace();
                                }
                            }
                        }.runTaskAsynchronously(EvenMoreFish.getProvidingPlugin(EvenMoreFish.class));
                    }
                }
            }
        }
    }

    private static Rarity random() {
        // Loads all the rarities
        List<Rarity> rarities = new ArrayList<>(EvenMoreFish.fishCollection.keySet());

        double totalWeight = 0;

        // Weighted random logic (nabbed from stackoverflow)
        for (Rarity r : rarities) {
            totalWeight += r.getWeight();
        }

        int idx = 0;
        for (double r = Math.random() * totalWeight; idx < rarities.size() - 1; ++idx) {
            r -= rarities.get(idx).getWeight();
            if (r <= 0.0) break;
        }

        return rarities.get(idx);
    }

    private static Fish getFish(Rarity r, Biome b) {
        // the fish that are of (Rarity r)
        List<Fish> rarityFish = EvenMoreFish.fishCollection.get(r);
        // will store all the fish that match the player's biome or don't discriminate biomes
        List<Fish> available = new ArrayList<>();

        for (Fish f : rarityFish) {

            if (f.getBiomes().contains(b) || f.getBiomes().size()==0) {
                available.add(f);
            }
        }

        // if the config doesn't define any fish that can be fished in this biome.
        if (available.size() == 0) {
            Bukkit.getLogger().log(Level.WARNING, "在 " + b.name() + " 生物群系中没有稀有度为 " + r.getValue() + " 的鱼可供垂钓。");
            return defaultFish();
        }

        int ran = (int) (Math.random() * available.size());
        return available.get(ran);
    }

    // if there's no fish available in the current biome, this gets sent out
    private static Fish defaultFish() {
        Rarity r = new Rarity("未找到生物群系", "&4", 1.0d, false, null);
        return new Fish(r, "");
    }

    // Checks if it should be giving the player the fish considering the fish-only-in-competition option in config.yml
    private static boolean competitionOnlyCheck() {
        if (EvenMoreFish.mainConfig.isCompetitionUnique()) {
            return Competition.isActive();
        } else {
            return true;
        }
    }

    private static void competitionCheck(Fish fish, Player fisherman) {
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
