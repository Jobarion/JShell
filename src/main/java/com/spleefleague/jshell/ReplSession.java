package com.spleefleague.jshell;

import org.bukkit.ChatColor;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.conversations.Conversable;
import org.bukkit.conversations.Conversation;
import org.bukkit.conversations.ConversationContext;
import org.bukkit.conversations.ConversationFactory;
import org.bukkit.conversations.Prompt;
import org.bukkit.conversations.StringPrompt;
import org.bukkit.permissions.Permissible;

/**
 *
 * @author balsfull
 */
public class ReplSession {
    
    private final JShellEvaluator shell;
    private final Conversation conversation;
    private final JShellInterpreter plugin;
    private final Conversable user;
    
    private ReplSession(JShellInterpreter plugin, Conversable user) {
        this.user = user;
        this.plugin = plugin;
        this.shell = new JShellEvaluator();
        String text = (user instanceof ConsoleCommandSender) ? "\n" : "";
        text += ChatColor.GRAY + "| You will not see chat messages while using the REPL.\n";
        text += "|  Type #exit to quit the REPL at any time.";
        ReplPrompt prompt = new ReplPrompt(text);
        ConversationFactory conversationFactory = new ConversationFactory(plugin)
                .withFirstPrompt(prompt)
                .withLocalEcho(false);
        if(user instanceof Permissible) {
            conversationFactory.withModality(((Permissible) user).hasPermission("jshell.modal"));
        }
        
        conversation = conversationFactory.buildConversation(user);
        conversation.begin();
    }
    
    public void endSession() {
        conversation.abandon();
        shell.close();
        user.sendRawMessage(ChatColor.GRAY + "|  Goodbye!");
    }
    
    public static ReplSession startSession(JShellInterpreter plugin, Conversable user) {
        return new ReplSession(plugin, user);
    }
    
    private class ReplPrompt extends StringPrompt {

        private String text;
        
        private ReplPrompt(String text) {
            this.text = text;
        }
        
        @Override
        public Prompt acceptInput(ConversationContext context, String input) {
            if("#exit".equals(input)) {
                plugin.endRepl(user);
                return Prompt.END_OF_CONVERSATION;
            }
        
            String result = shell.eval(input);

            StringBuilder nextPromptText = new StringBuilder();

            if (user instanceof ConsoleCommandSender) {
                nextPromptText.append("\n");
            }

            if (shell.isHoldingIncompleteScript()) {
                nextPromptText
                        .append(ChatColor.AQUA)
                        .append("...> ")
                        .append(ChatColor.RESET)
                        .append(input);
            } 
            else {                
                nextPromptText
                        .append(ChatColor.AQUA)
                        .append("jshell> ")
                        .append(ChatColor.RESET)
                        .append(input);
            }

            if (result != null) {
                nextPromptText
                        .append("\n")
                        .append(ChatColor.GRAY)
                        .append(result);
            }

            return new ReplPrompt(nextPromptText.toString());
        }

        @Override
        public String getPromptText(ConversationContext cc) {
            return text;
        }
    }
}
