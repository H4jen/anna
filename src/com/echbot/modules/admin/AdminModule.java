/*
 * echbot - an open-source IRC bot
 * Copyright (C) 2003  Christopher Pearson
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 * To contact the author, Chris Pearson, email chris@echbot.com
 */
package com.echbot.modules.admin;

import com.echbot.Echbot;
import com.echbot.UserModule;
import com.echbot.UserModuleInterface;
import com.echbot.config.ConfigUtils;
import com.echbot.messages.in.ChatMessageIn;
import com.echbot.messages.out.ChatMessageOut;
import com.echbot.messages.out.JoinOut;
import com.echbot.messages.out.PartOut;
import com.echbot.messages.out.QuitOut;
import com.echbot.modules.auth.AuthModule;
import org.apache.log4j.Logger;

import java.util.*;

/**
 * @author Chris Pearson
 * @version $Id: AdminModule.java,v 1.33 2003/08/29 20:30:14 chris Exp $
 */
public class AdminModule extends UserModule
{
    private static final Logger log = Logger.getLogger(AdminModule.class);
    private int broadcastEventId;

    public AdminModule(UserModuleInterface parent) {
        super(parent);
        broadcastEventId = getUserEventId("admin.broadcast");
        parent.registerForEvent(broadcastEventId, this);
        setDefault("limit,alias", "admins,opped");
        register(ChatMessageIn.class);
    }

    private static final class ChannelRecord
    {
        private String channel, password;

        public ChannelRecord(String channel) {
            this.channel = channel;
        }

        public ChannelRecord(String channel, String password) {
            this.channel = channel;
            this.password = password;
        }

        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ChannelRecord)) return false;
            final ChannelRecord channelRecord = (ChannelRecord)o;
            if (!channel.equals(channelRecord.channel)) return false;
            return true;
        }

        public int hashCode() {
            return channel.hashCode();
        }

        public String toString() {
            if ((password != null) && !"".equals(password))
                return channel + " p:" + password;
            else
                return channel;
        }

        public String toSaveString() {
            if ((password != null) && !"".equals(password))
                return channel + " " + password;
            else
                return channel;
        }
    }

    public void received(ChatMessageIn message) {
        if (!message.isCommand()) return;
        String channel = message.getTo().startsWith("#") ? message.getTo().toLowerCase() : null;
        boolean replyByPrivmsg = (channel == null) && message.isPrivmsg();

        AuthModule auth;
        try {
            auth = (AuthModule)getModule("auth");
        } catch (NoSuchModuleException e) {
            log.warn("Requires auth module", e);
            return;
        }
        /*
         * GLOBAL COMMANDS
         */
        if (message.getCommand().equals("!help")) {
            send(new ChatMessageOut(message.getFrom(), "See \00312\037http://www.echbot.com\037\003 for help!", replyByPrivmsg));
        } else if (message.getCommand().equals("!topic")) {
            send(new ChatMessageOut(message.getFrom(), "In mIRC, press alt+r, go to the Aliases tab, and paste \00312 /f5 /topic $chan \003 then F5 will show topic.", replyByPrivmsg));
            /*
             * MODULE CONTROL COMMANDS (GLOBAL ADMIN ONLY)
             */
        } else if (message.getCommand().equals("!addmodule") &&
                auth.isAdmin(message.getFromWithHost(), null, null)) {
            StringTokenizer args = new StringTokenizer(message.getArguments());
            if (args.hasMoreTokens()) {
                parent.addModule(args.nextToken());
            }
        } else if (message.getCommand().equals("!removemodule") &&
                auth.isAdmin(message.getFromWithHost(), null, null)) {
            StringTokenizer args = new StringTokenizer(message.getArguments());
            if (args.hasMoreTokens()) {
                parent.removeModule(args.nextToken());
            }
        } else if (message.getCommand().equals("!reloadmodules") &&
                auth.isAdmin(message.getFromWithHost(), null, null)) {
            sendUrgent(new ChatMessageOut(message.getFrom(), "Reloading modules...", replyByPrivmsg));
            parent.reloadModules();
            /*
             * CLONE CONTROL COMMANDS (GLOBAL ADMIN ONLY)
             */
        } else if (message.getCommand().equals("!listclones") &&
                auth.isAdmin(message.getFromWithHost(), null, null)) {
            List clones = Echbot.cloneList();
            for (Iterator i = clones.iterator(); i.hasNext();) {
                String clone = (String)i.next();
                send(new ChatMessageOut(message.getFrom(), clone, replyByPrivmsg));
            }
        } else if (message.getCommand().equals("!killclone") &&
                auth.isAdmin(message.getFromWithHost(), null, null)) {
            String name = message.getArguments().trim();
            if (name.length() > 0) {
                getConfig().remove(name + ",network");
                getConfig().remove(name + ",channels");
                getConfig().remove(name + ",nickname");
                Echbot.killClone(name);
            }
        } else if (message.getCommand().equals("!startclone") &&
                auth.isAdmin(message.getFromWithHost(), null, null)) {
            startClone(message.getArguments(), message.getFrom(), replyByPrivmsg);
            /*
             * MORE GLOBAL ADMIN COMMANDS
             */
        } else if (message.getCommand().equals("!setadmins") &&
                auth.isAdmin(message.getFromWithHost(), null, null)) {
            String args = message.getArguments().trim();
            if ("".equals(args)) {
                String currentAdmins = getConfig().get("vars," + parent.getNetwork() + ",admins");
                if (currentAdmins == null) {
                    send(new ChatMessageOut(message.getFrom(), "There are no " + parent.getNetwork() + " admins at the moment", replyByPrivmsg));
                } else {
                    send(new ChatMessageOut(message.getFrom(), "Current network admins: " + currentAdmins, replyByPrivmsg));
                }
            } else {
                getConfig().put("vars," + parent.getNetwork() + ",admins", args);
                String currentAdmins = getConfig().get("vars," + parent.getNetwork() + ",admins");
                send(new ChatMessageOut(message.getFrom(), "Updated network admins: " + currentAdmins, replyByPrivmsg));
            }
        } else if (message.getCommand().equals("!clearadmins") &&
                auth.isAdmin(message.getFromWithHost(), null, null)) {
            getConfig().remove("vars," + parent.getNetwork() + ",admins");
            send(new ChatMessageOut(message.getFrom(), "Admins cleared", replyByPrivmsg));
        } else if (message.getCommand().equals("!network") &&
                auth.isAdmin(message.getFromWithHost(), null, null)) {
            StringTokenizer args = new StringTokenizer(message.getArguments());
            if (args.countTokens() == 2) {
                // we're setting a new network's servers
                getConfig().put("servers," + args.nextToken().toLowerCase(),
                        args.nextToken());
            } else if (args.countTokens() == 1) {
                // We're querying the state of a network's servers
                String network = args.nextToken().toLowerCase();
                String servers = getConfig().get("servers," + network);
                if ((servers == null) || "".equals(servers)) {
                    send(new ChatMessageOut(message.getFrom(), "There are no servers saved for " + network, replyByPrivmsg));
                } else {
                    send(new ChatMessageOut(message.getFrom(), "Servers for " + network + " are: " + servers, replyByPrivmsg));
                }
            } else {
                send(new ChatMessageOut(message.getFrom(), "Usage: !network <network> [serverlist]", replyByPrivmsg));
            }
        } else if (message.getCommand().equals("!removenetwork") &&
                auth.isAdmin(message.getFromWithHost(), null, null)) {
            StringTokenizer args = new StringTokenizer(message.getArguments());
            if (args.countTokens() == 1) {
                // we're removing a network
                String network = args.nextToken().toLowerCase();
                getConfig().remove("servers," + network);
                send(new ChatMessageOut(message.getFrom(), "Removed servers for " + network, replyByPrivmsg));
            } else {
                send(new ChatMessageOut(message.getFrom(), "Usage: !removenetwork <network>", replyByPrivmsg));
            }
            /*
             * CHANNEL CONTROL COMMANDS (NETWORK OR GLOBAL ADMINS)
             */
        } else if (message.getCommand().equals("!reconnect") &&
                auth.isAdmin(message.getFromWithHost(), parent.getNetwork(), null)) {
			send(new QuitOut("Reconnecting..."));
        } else if (message.getCommand().equals("!join") &&
                auth.isAdmin(message.getFromWithHost(), parent.getNetwork(), null)) {
            // Find which channel we're joining
            StringTokenizer tokens = new StringTokenizer(message.getArguments().toLowerCase());
            if (tokens.hasMoreTokens()) {
                String chan = tokens.nextToken().toLowerCase();
                ChannelRecord newChan = tokens.hasMoreTokens() ?
                        new ChannelRecord(chan, tokens.nextToken()) :
                        new ChannelRecord(chan);
                // If it exists in the channel list, replace it
                List channels = getChannels();
                int chanIndex = channels.indexOf(newChan);
                if (chanIndex != -1) {
                    channels.set(chanIndex, newChan);
                    send(new JoinOut(newChan.toSaveString()));
                } else if (channels.size() >= 20) {
                    send(new ChatMessageOut(message.getFrom(), "Cannot join more than 20 channels", replyByPrivmsg));
                    return;
                } else {
                    channels.add(newChan);
                    send(new JoinOut(newChan.toSaveString()));
                    send(new ChatMessageOut(message.getFrom(), "Joined " + newChan.channel, replyByPrivmsg));
                }
                saveChannelList(channels);
            }
        } else if (message.getCommand().equals("!part") &&
                auth.isAdmin(message.getFromWithHost(), parent.getNetwork(), null)) {
            // Find which channel we're leaving
            String leaveChan = message.getArguments().trim().toLowerCase();
            List channels = getChannels();
            // If it exists in the channel list, remove it
            ChannelRecord leavingChan = new ChannelRecord(leaveChan);
            if (channels.remove(leavingChan)) {
                saveChannelList(channels);
                send(new PartOut(leaveChan));
                send(new ChatMessageOut(message.getFrom(), "Left " + leaveChan, replyByPrivmsg));
            } else if (leaveChan.length() > 0) {
                send(new ChatMessageOut(message.getFrom(), "Couldn't leave channel, not on " + leaveChan, replyByPrivmsg));
            }
        } else if (message.getCommand().equals("!announce") &&
                auth.isAdmin(message.getFromWithHost(), parent.getNetwork(), null) &&
                !message.getTo().startsWith("#")) {
            /*
             * The format for this command is !announce <message>, and can only
             * be carried out by network/global admins via a privmsg
             */
            String announcement = message.getArguments();
            if (announcement.length() == 0) {
                send(new ChatMessageOut(message.getFrom(), "Usage: !announce <message>", true));
            } else {
                parent.triggerGlobalEvent(broadcastEventId, announcement);
            }
        } else if (message.getCommand().equals("!channels") &&
                auth.isAdmin(message.getFromWithHost(), parent.getNetwork(), null)) {
            // Get the list of channels, and create a message
            List channels = getChannels();
            StringBuffer sb = new StringBuffer();
            sb.append(channels.size()).append(" channels: ");
            for (Iterator i = channels.iterator(); i.hasNext();) {
                ChannelRecord chan = (ChannelRecord)i.next();
                sb.append(chan.toString());
                if (i.hasNext()) sb.append(", ");
            }
            // Send the channel list
            send(new ChatMessageOut(message.getFrom(), sb.toString(), replyByPrivmsg));
            /*
             * CHANNEL ADMIN COMMANDS
             */
        } else if (message.getCommand().equals("!set") && (channel != null) &&
                auth.isAdmin(message.getFromWithHost(), parent.getNetwork(), channel)) {
            final StringTokenizer args = new StringTokenizer(message.getArguments());
            if (args.hasMoreTokens()) {
                final String userVarName = args.nextToken();
                final int varind = message.getMessage().indexOf(userVarName) + userVarName.length() + 1;
                if (varind >= message.getMessage().length()) {
                    // The message has some content: is a get
                    String varMessage = getVarMessage(userVarName, channel);
                    if (varMessage == null) {
                        send(new ChatMessageOut(message.getFrom(), "Invalid variable name (" + userVarName + ")", replyByPrivmsg));
                    } else {
                        send(new ChatMessageOut(message.getFrom(), varMessage, replyByPrivmsg));
                    }
                } else {
                    // The message has some content: is a set
                    if (getConfig().containsKey("vars," + userVarName)) {
                        ConfigUtils.setChannelVar(getConfig(), userVarName, message.getMessage().substring(varind), parent.getNetwork(), channel);
                        send(new ChatMessageOut(message.getFrom(), userVarName + " set", replyByPrivmsg));
                    }
                }
            }
        } else if (message.getCommand().equals("!unset") && (channel != null) &&
                auth.isAdmin(message.getFromWithHost(), parent.getNetwork(), channel)) {
            final StringTokenizer args = new StringTokenizer(message.getArguments());
            if (args.hasMoreTokens()) {
                final String varName = args.nextToken();
                ConfigUtils.setChannelVar(getConfig(), varName, null, parent.getNetwork(), channel);
                send(new ChatMessageOut(message.getFrom(), "Unset " + varName, replyByPrivmsg));
            }
        } else if (message.getCommand().startsWith("!alias")) {
            cmdAlias(message, replyByPrivmsg);
        } else if (message.getCommand().startsWith("!unalias") &&
                auth.permittedTo(message.getFromWithHost(), "limit,alias", channel)) {
            StringTokenizer args = new StringTokenizer(message.getArguments());
            if (args.hasMoreTokens()) {
                String aliasName = args.nextToken().toLowerCase();
                String result = ConfigUtils.getChannelVar(getConfig(), "alias," + aliasName, parent.getNetwork(), channel, null);
                if (result == null) {
                    send(new ChatMessageOut(message.getFrom(), aliasName + " hasn't been set", replyByPrivmsg));
                } else {
                    ConfigUtils.setChannelVar(getConfig(), "alias," + aliasName, null, parent.getNetwork(), channel);
                    send(new ChatMessageOut(message.getFrom(), aliasName + " unset", replyByPrivmsg));
                }
            }
        }
    }

    private void cmdAlias(ChatMessageIn message, boolean replyByPrivmsg) {
        StringTokenizer args = new StringTokenizer(message.getArguments());
        String channel = (message.getTo().charAt(0) == '#') ? message.getTo() : null;
        // If it's a privmsg, require a channel argument
        if ((channel == null) && args.hasMoreTokens()) channel = args.nextToken();
        // If we've not got a channel yet, give up
        if ((channel == null) || (channel.charAt(0) != '#')) return;
        // If we've not been given any more arguments - we want the full list
        if (!args.hasMoreTokens()) {
            // Get all the aliases from the config object
            List aliases = new ArrayList();
            String varStart = "vars," + parent.getNetwork() + "," + channel.toLowerCase() + ",alias,";
            synchronized (getConfig()) {
                for (Iterator i = getConfig().keySet().iterator(); i.hasNext();) {
                    String key = (String)i.next();
                    if (key.startsWith(varStart)) {
                        aliases.add(key.substring(varStart.length()));
                    }
                }
            }
            // Sort the list, and send it back to the user
            Collections.sort(aliases);
            StringBuffer sb = new StringBuffer("Current aliases: ");
            for (Iterator i = aliases.iterator(); i.hasNext();) {
                String alias = (String)i.next();
                sb.append(alias);
                if (i.hasNext()) sb.append(", ");
            }
            send(new ChatMessageOut(message.getFrom(), sb.toString(), replyByPrivmsg));
        } else {
            // We have parameters, at least the alias
            String alias = args.nextToken();
            AuthModule auth;
            try {
                auth = (AuthModule)getModule("auth");
            } catch (NoSuchModuleException e) {
                log.warn("Requires auth module", e);
                return;
            }
            if (args.hasMoreTokens() && auth.permittedTo(message.getFromWithHost(), "limit,alias", channel)) {
                // We have been given something to set the alias to
                ConfigUtils.setChannelVar(getConfig(), "alias," + alias.toLowerCase(),
                        args.nextToken("").trim(), parent.getNetwork(), channel);
            } else {
                // We want to check the value of a specific alias
                log.debug("Checking value of alias " + alias.toLowerCase());
                send(new ChatMessageOut(message.getFrom(), alias.toLowerCase() + "=\"" +
                        ConfigUtils.getChannelVar(getConfig(), "alias," + alias.toLowerCase(), parent.getNetwork(), channel, "") +
                        "\"", replyByPrivmsg));
            }
        }
    }

    private String getVarMessage(String userVarName, String channel) {
        String varName = "vars," + parent.getNetwork() + "," + channel + "," + userVarName;
        if (getConfig().containsKey(varName)) {
            return "[\002Channel\002] " + userVarName + "=\"" + getConfig().get(varName) + "\"";
        }
        varName = "vars," + parent.getNetwork() + "," + userVarName;
        if (getConfig().containsKey(varName)) {
            return "[\002Network\002] " + userVarName + "=\"" + getConfig().get(varName) + "\"";
        }
        varName = "vars," + userVarName;
        if (getConfig().containsKey(varName)) {
            return "[\002Global\002] " + userVarName + "=\"" + getConfig().get(varName) + "\"";
        }
        return null;
    }

    private List getChannels() {
        List channels = new ArrayList();
        StringTokenizer tokens = new StringTokenizer(getConfig().get(parent.getName() + ",channels"), ",");
        while (tokens.hasMoreTokens()) {
            String chan = tokens.nextToken().trim().toLowerCase();
            int space = chan.indexOf(' ');
            if (space != -1) {
                channels.add(new ChannelRecord(chan.substring(0, space), chan.substring(space).trim()));
            } else {
                channels.add(new ChannelRecord(chan));
            }
        }
        return channels;
    }

    private void saveChannelList(List channels) {
        StringBuffer sb = new StringBuffer();
        for (Iterator i = channels.iterator(); i.hasNext();) {
            ChannelRecord chan = (ChannelRecord)i.next();
            sb.append(chan.toSaveString());
            if (i.hasNext()) sb.append(',');
        }
        getConfig().put(parent.getName() + ",channels", sb.toString());
    }

    private void startClone(String args, String replyTo, boolean replyByPrivmsg) {
        StringTokenizer tokens = new StringTokenizer(args, ", ");
        if (tokens.countTokens() < 4) {
            send(new ChatMessageOut(replyTo, "Usage: !startclone <bindto or \"default\"> <id> <network> <nickname>[,<nickname>,...]", replyByPrivmsg));
            return;
        }
        String bindTo = tokens.nextToken().toLowerCase();
        String botName = "bot" + tokens.nextToken().toLowerCase();
        if (getConfig().containsKey(botName + ",network")) {
            send(new ChatMessageOut(replyTo, "Error: There is already a bot with that id!", replyByPrivmsg));
            return;
        }
        String network = tokens.nextToken().toLowerCase();
        try {
            ConfigUtils.getRandomServer(getConfig(), network);
        } catch (ConfigUtils.NoSuchNetworkException e) {
            send(new ChatMessageOut(replyTo, "Error: " + network + " has no servers!", replyByPrivmsg));
            return;
        }
        Set nicknames = new HashSet();
        StringBuffer nickString = new StringBuffer();
        while (tokens.hasMoreTokens()) {
            String nextNick = tokens.nextToken();
            if (nicknames.add(nextNick)) {
                if (nickString.length() > 0) nickString.append(',');
                nickString.append(nextNick);
            }
        }
        // Now we've got the parameters, go on to start the bot
        getConfig().put(botName + ",network", network);
        getConfig().put(botName + ",channels", "#echbot.control control");
        getConfig().put(botName + ",nickname", nickString.toString());
        if (!"default".equals(bindTo)) getConfig().put(botName + ",bindto", bindTo);
        Echbot.startClones();
    }

    public void userEvent(int id, Object attachment) {
        if (id == broadcastEventId) {
            // Send a message to all channels this clone resides on.
            String announcement = (String)attachment;
            // Find channels to send to
            List channels = getChannels();
            int i = 0;
            StringBuffer channelQueue = new StringBuffer();

            while (i < channels.size()) {
                ChannelRecord chanRec = (ChannelRecord)channels.get(i);

                if (++i % 5 == 0) {
                    // Send 5 messages at a time
                    channelQueue.append(chanRec.channel);

                    // Announce to each channel turn, using a privmsg rather
                    // than a notice to save annoyance.
                    send(new ChatMessageOut(channelQueue.toString(), announcement, true));

                    // Reset the buffer
                    channelQueue = new StringBuffer();
                } else {
                    // Queue
                    channelQueue.append(chanRec.channel + ",");
                }
            }

            if (channelQueue.length() > 0) {
                // Send the remainder
                send(new ChatMessageOut(channelQueue.toString(), announcement, true));
            }
        }
    }
}
