package com.oheers.fish.competition.reward;

import com.oheers.fish.EvenMoreFish;
import com.oheers.fish.FishUtils;
import com.oheers.fish.api.EMFRewardEvent;
import com.oheers.fish.config.messages.Message;
import org.apache.commons.lang.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Collections;
import java.util.Objects;
import java.util.logging.Level;

public class Reward {

    RewardType type;
    String action;

    public Reward(String value) {
        String[] split = value.split(":");

        if (split.length < 2) {
            EvenMoreFish.logger.log(Level.WARNING, value + " 格式不正确，不会作为奖励给予。");
            this.type = RewardType.BAD_FORMAT;
        } else {
            try {
                // getting the value, imagine split being "COMMAND", "evenmorefish", "emf shop"
                this.type = RewardType.valueOf(split[0].toUpperCase());
                // joins the action by the removed ":" joiner
                this.action = StringUtils.join(split, ":", 1, split.length);

            } catch (IllegalArgumentException e) {
                this.type = RewardType.OTHER;
                // Sends the full reward out to the api if it's a custom reward
                this.action = value;
            }

        }
    }

    public String getAction() {
        return action;
    }

    Plugin plugin = JavaPlugin.getProvidingPlugin(getClass());

    public void run(OfflinePlayer player) {
        Player p = null;

        if (player.isOnline()) p = (Player) player;

        switch (type) {
            case COMMAND:
                // running the command
                Bukkit.getScheduler().callSyncMethod( plugin, () ->
                        Bukkit.dispatchCommand(Bukkit.getServer().getConsoleSender(), this.action.replace("{player}", player.getName())));
                break;
            case EFFECT:
                if (p != null) {
                    String[] parsedEffect = action.split(",");
                    // Adds a potion effect in accordance to the config.yml "EFFECT:" value

                    p.addPotionEffect(new PotionEffect(Objects.requireNonNull(PotionEffectType.getByName(parsedEffect[0])), Integer.parseInt(parsedEffect[2]) * 20, Integer.parseInt(parsedEffect[1])));
                }

                break;
            case HEALTH:
                // checking the player doesn't have a special effect thingy on

                if (p != null) {
                    if (!(p.getHealth() > 20)) {
                        double newhealth = p.getHealth() + Integer.parseInt(action);
                        // checking the new health won't go above 20
                        if (newhealth > 20) {
                            p.setHealth(20);
                        } else {
                            p.setHealth(newhealth);
                        }
                    }
                }

                break;
            case HUNGER:

                if (p != null) {
                    p.setFoodLevel(p.getFoodLevel() + Integer.parseInt(action));
                }

                break;
            case ITEM:
                if (p != null) {
                    String[] parsedItem = action.split(",");
                    FishUtils.giveItems(Collections.singletonList(new ItemStack(Material.getMaterial(parsedItem[0]), Integer.parseInt(parsedItem[1]))), p);
                }

                break;
            case MESSAGE:
                if (p != null) {
                    p.sendMessage(FishUtils.translateHexColorCodes(action));
                }

                break;
            case MONEY:
                EvenMoreFish.econ.depositPlayer(player, Integer.parseInt(action));
                break;
            case OTHER:
                EMFRewardEvent event = new EMFRewardEvent(this, p);
                Bukkit.getPluginManager().callEvent(event);
                break;
            default:
                EvenMoreFish.logger.log(Level.SEVERE, "加载奖励时出错。");
        }
    }

    public String format() {
        switch (type) {
            case COMMAND:
                if (EvenMoreFish.mainConfig.rewardCommand(action) != null) {
                    return new Message()
                            .setMSG(EvenMoreFish.mainConfig.rewardCommand(action))
                            .toString();
                } else {
                    return new Message()
                            .setMSG(action)
                            .toString();
                }
            case EFFECT:
                String[] parsedEffect = action.split(",");
                // Adds a potion effect in accordance to the config.yml "EFFECT:" value
                return new Message()
                        .setMSG(EvenMoreFish.mainConfig.rewardEffect())
                        .setEffect(parsedEffect[0])
                        .setAmplifier(parsedEffect[1])
                        .setTime(FishUtils.timeFormat(Integer.parseInt(parsedEffect[2])))
                        .toString();
            case ITEM:
                String[] parsedItem = action.split(",");
                return new Message()
                        .setMSG(EvenMoreFish.mainConfig.rewardItem())
                        .setItem(parsedItem[0])
                        .setAmount(parsedItem[1])
                        .toString();
            case MONEY:
                return new Message()
                        .setMSG(EvenMoreFish.mainConfig.rewardMoney())
                        .setAmount(action)
                        .toString();
            case HEALTH:
                return new Message()
                        .setMSG(EvenMoreFish.mainConfig.rewardHealth())
                        .setAmount(action)
                        .toString();
            case HUNGER:
                return new Message()
                        .setMSG(EvenMoreFish.mainConfig.rewardHunger())
                        .setAmount(action)
                        .toString();
            default:
                return null;

        }
    }
}
