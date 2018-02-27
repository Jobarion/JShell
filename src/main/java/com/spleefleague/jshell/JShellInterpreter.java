package com.spleefleague.jshell;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.conversations.Conversable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 *
 * @author balsfull
 */
public class JShellInterpreter extends JavaPlugin implements Listener {
    
    private final Map<Conversable, ReplSession> activeShells = new ConcurrentHashMap<>();
    private final Logger logger = Logger.getLogger("minecraft");
    
    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
         if (sender instanceof Conversable) {
            if (activeShells.containsKey(sender)) {
                sender.sendMessage("You already have an active REPL running.");
            } else {
                startRepl((Conversable)sender);
            }
        } else {
            sender.sendMessage("Only players and console may start a REPL instance.");
        }
        return true;
    }
    
    public void startRepl(Conversable user) {
        if (activeShells.containsKey(user)) return;

        activeShells.put(user, ReplSession.startSession(this, user));

        logger.info("Started REPL for $user");
    }

    public void endRepl(Conversable user) {
        ReplSession session = activeShells.remove(user);
        if (session != null) {
            session.endSession();
            logger.info("Ended REPL for $user");
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        endRepl(event.getPlayer());
    }
}
