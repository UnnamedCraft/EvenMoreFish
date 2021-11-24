package com.oheers.fish.competition;

import com.oheers.fish.EvenMoreFish;
import com.oheers.fish.database.Database;
import com.oheers.fish.database.FishReport;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;

public class JoinChecker implements Listener {

    // Gives the player the active fishing bar if there's a fishing event cracking off
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (Competition.isActive()) {
            EvenMoreFish.active.getStatusBar().addPlayer(event.getPlayer());
            Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(EvenMoreFish.getPlugin(EvenMoreFish.class),
                    () -> event.getPlayer().sendMessage(EvenMoreFish.active.getStartMessage().setMSG(EvenMoreFish.msgs.getCompetitionJoin()).toString()), 20*3);
        }

        new BukkitRunnable() {

            @Override
            public void run() {
                List<FishReport> reports;

                if (Database.hasUser(event.getPlayer().getUniqueId().toString())) {
                    reports = Database.readUserData(event.getPlayer().getUniqueId().toString());
                } else {
                    reports = new ArrayList<>();
                    Database.addUser(event.getPlayer().getUniqueId().toString());
                }

                EvenMoreFish.fishReports.put(event.getPlayer().getUniqueId(), reports);
            }
        }.runTaskAsynchronously(EvenMoreFish.getProvidingPlugin(EvenMoreFish.class));
    }

    // Removes the player from the bar list if they leave the server
    @EventHandler
    public void onLeave(PlayerQuitEvent event) {

        if (Competition.isActive()) {
            EvenMoreFish.active.getStatusBar().removePlayer(event.getPlayer());
        }

        if (EvenMoreFish.fishReports.containsKey(event.getPlayer().getUniqueId())) {

            new BukkitRunnable() {

                @Override
                public void run() {
                    if (Database.hasUser(event.getPlayer().getUniqueId().toString())) {
                        Database.writeUserData(event.getPlayer().getUniqueId().toString(), EvenMoreFish.fishReports.get(event.getPlayer().getUniqueId()));
                    } else {
                        Database.addUser(event.getPlayer().getUniqueId().toString());
                        Database.writeUserData(event.getPlayer().getUniqueId().toString(), new ArrayList<>()); // Write user fish reports into their data file
                    }

                    EvenMoreFish.fishReports.remove(event.getPlayer().getUniqueId());
                }
            }.runTaskAsynchronously(EvenMoreFish.getProvidingPlugin(EvenMoreFish.class));

        }
    }
}
