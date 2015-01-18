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
package com.echbot.modules.pickup;

import com.echbot.UserModule;
import com.echbot.UserModuleInterface;
import com.echbot.config.ConfigUtils;
import com.echbot.config.FormatUtils;
import com.echbot.messages.in.*;
import com.echbot.messages.out.ChatMessageOut;
import com.echbot.messages.out.TopicOut;
import com.echbot.modules.auth.AuthModule;
import com.echbot.modules.db.DbModule;
import org.apache.log4j.Logger;

import java.util.*;

/**
 * @author Chris Pearson
 * @version $Id: PickupModule.java,v 1.34 2003/10/09 19:57:19 alex Exp $
 */
public class PickupModule extends UserModule
{
    private static final Logger log = Logger.getLogger(PickupModule.class);
    private static final int ERR_CHANOPRIVSNEEDED = 482;
    protected final int EVENT_PICKUPSTART;
    private final Map pickups = Collections.synchronizedMap(new HashMap());

    public PickupModule(UserModuleInterface parent) {
        super(parent);
        EVENT_PICKUPSTART = getUserEventId("pickup.start");
        setDefault("pickup,format", "\0034Next Game: $firstgame$${ \0032|| }motd$");
        setDefault("pickup,format,firstgame", "\00306$players$${ \0034@ }ip$${\00310 on }map$");
        setDefault("pickup,format,firstgame,players", "$nick$");
        setDefault("pickup,format,firstgame,players,sep", "/");
        setDefault("pickup,format,games", "\00306$players$${ \0034@ }ip$${\00310 on }map$");
        setDefault("pickup,format,games,sep", "\00312 ][ ");
        setDefault("pickup,format,games,players", "$num$:$nick$");
        setDefault("pickup,format,games,players,sep", "/");
        setDefault("pickup,top10_msg", "\0033Top 10 for last 7 days:\0036 $players$");
        setDefault("pickup,top10_msg,players", "$num$:$nick$(\00314$games$\0036)");
        setDefault("pickup,top10_msg,players,sep", " ");
        setDefault("pickup,ready_msg", "\0035\002Game ready @\0034 $ip$\0035 - Players:\00310 $players$ ${\0035- Captains: \00310}cap1$ $cap2$");
        setDefault("pickup,ready_msg,players,sep", ", ");
        setDefault("pickup,who_msg", "\00310[\002$id$\002]\0036 $players_compact$");
        setDefault("pickup,who_msg,sep", " \0034][ ");
        setDefault("pickup,who_msg,players_compact", "$nick$");
        setDefault("pickup,who_msg,players_compact,sep", "/");
        setDefault("pickup,addpolicy", "0");
        setDefault("pickup,readymessage", "on");
        setDefault("pickup,autodeleteip", "off");
        setDefault("pickup,rotateservers", "on");
        setDefault("pickup,cleartopic", "");
        setDefault("pickup,playercount", "8");
        setDefault("limit,ip", "all");
        setDefault("limit,motd", "admins,opped");
        setDefault("limit,map", "all");
        setDefault("limit,promote", "all");
        setDefault("limit,reset", "admins,opped");
        setDefault("limit,gamecontrol", "admins");
        setDefault("pickup,servercheck", "off");
        setDefault("pickup,randomcaptains", "off");
        register(JoinIn.class);
        register(KickIn.class);
        register(NickIn.class);
        register(PartIn.class);
        register(QuitIn.class);
        register(ChatMessageIn.class);
        register(SystemIn.class);
        parent.registerForEvent(EVENT_PICKUPSTART, this);
    }

    public void initialise(Object state) {
        if (state != null) {
            final Map stateMap = (Map)state;
            for (Iterator i = stateMap.keySet().iterator(); i.hasNext();) {
                final String channel = (String)i.next();
                final PickupChannel newChannel = startPickup(channel);
                if (newChannel != null) {
                    newChannel.initialise(stateMap.get(channel));
                }
            }
        }
    }

    public Object getState() {
        final Map state = new HashMap();
        for (Iterator i = pickups.keySet().iterator(); i.hasNext();) {
            final String channel = (String)i.next();
            final PickupChannel pickup = (PickupChannel)pickups.get(channel);
            state.put(channel, pickup.getState());
        }
        return state;
    }

    private PickupChannel startPickup(String channel) {
        final String games = ConfigUtils.getChannelVar(getConfig(), "pickup,active", parent.getNetwork(), channel, null);
        if (games != null) {
            if (pickups.containsKey(channel)) {
                log.debug("Removing existing pickup");
                pickups.remove(channel);
            }
            log.debug("Creating new pickup for " + channel + " with " + games);
            PickupChannel newChannel = new PickupChannel(this, channel, games);
            pickups.put(channel, newChannel);
            return newChannel;
        }
        return null;
    }

    public void received(JoinIn message) {
        if (message.getJoiner().equals(parent.getNickname())) {
            startPickup(message.getChannel().toLowerCase());
        }
    }

    public void received(NickIn message) {
        synchronized (pickups) {
            for (Iterator i = pickups.values().iterator(); i.hasNext();) {
                PickupChannel channel = (PickupChannel)i.next();
                channel.nickChange(message.getOldNick(), message.getNewNick());
            }
        }
    }

    public void received(KickIn message) {
        leftChannel(message.getKicked(), message.getChannel());
    }

    public void received(PartIn message) {
        leftChannel(message.getLeaver(), message.getChannel());
    }

    public void received(QuitIn message) {
        synchronized (pickups) {
            for (Iterator i = pickups.values().iterator(); i.hasNext();) {
                PickupChannel channel = (PickupChannel)i.next();
                channel.removeFromAll(message.getQuitter());
            }
        }
    }

    private final void leftChannel(String leaver, String channel) {
        PickupChannel pickupChannel = (PickupChannel)pickups.get(channel.toLowerCase());
        if (pickupChannel != null) {
            pickupChannel.removeFromAll(leaver);
        }
    }

    public void received(ChatMessageIn message) {
        if (!message.isCommand()) return;
        final AuthModule auth;
        try {
            auth = (AuthModule)getModule("auth");
        } catch (NoSuchModuleException e) {
            log.warn("Pickup module requires auth module", e);
            return;
        }
        String channel = message.getTo().startsWith("#") ? message.getTo().toLowerCase() : null;
        boolean replyByPrivmsg = (channel == null) && message.isPrivmsg();
        if (message.getCommand().equals("!pickups") && (channel != null) &&
                auth.permittedTo(message.getFromWithHost(), "limit,gamecontrol", channel)) {
            if (message.getArguments().trim().length() == 0) {
                ConfigUtils.setChannelVar(getConfig(), "pickup,active", null, parent.getNetwork(), channel);
                synchronized (pickups) {
                    pickups.remove(channel);
                }
                send(new TopicOut(channel, ConfigUtils.getVar(getConfig(), "pickup,cleartopic",
                        parent.getNetwork(), channel)));
            } else {
                ConfigUtils.setChannelVar(getConfig(), "pickup,active", message.getArguments().trim(), parent.getNetwork(), channel);
                PickupChannel newPickup = startPickup(channel);
                if (newPickup != null) newPickup.updateTopic();
            }
            return;
        } else if (message.getCommand().equals("!pickuprotate") && (channel != null) &&
                auth.permittedTo(message.getFromWithHost(), "limit,gamecontrol", channel)) {
            // Initialise server rotation - a quicker way of doing it than individual commands
            StringTokenizer tokens = new StringTokenizer(message.getArguments(), ", ");
            if (!tokens.hasMoreTokens()) {
                send(new ChatMessageOut(message.getFrom(), "Usage: !pickuprotate <alias>[,<alias>,...]", replyByPrivmsg));
            } else {
                Set gameIds = new HashSet();
                StringBuffer pickupString = new StringBuffer();
                while (tokens.hasMoreTokens()) {
                    String alias = tokens.nextToken().toLowerCase();
                    String thisServer = ConfigUtils.getChannelVar(getConfig(), "alias," + alias,
                            parent.getNetwork(), channel.toLowerCase(), null);
                    if (thisServer == null) {
                        send(new ChatMessageOut(message.getFrom(), "Usage: !pickuprotate <alias>[,<alias>,...] (all aliases must exist)", replyByPrivmsg));
                        return;
                    } else if (gameIds.contains(alias)) {
                        send(new ChatMessageOut(message.getFrom(), "Usage: !pickuprotate <alias>[,<alias>,...] (aliases can only be listed once)", replyByPrivmsg));
                        return;
                    } else {
                        gameIds.add(alias);
                        pickupString.append(alias);
                        if (tokens.hasMoreTokens()) pickupString.append(',');
                        ConfigUtils.setChannelVar(getConfig(), "pickup,ip," + alias, thisServer, parent.getNetwork(), channel);
                    }
                }
                ConfigUtils.setChannelVar(getConfig(), "pickup,addpolicy", "2", parent.getNetwork(), channel);
                ConfigUtils.setChannelVar(getConfig(), "pickup,active", pickupString.toString(), parent.getNetwork(), channel);
                PickupChannel newPickup = startPickup(channel);
                if (newPickup != null) newPickup.updateTopic();
            }
        }

        PickupChannel pickupChannel = (PickupChannel)pickups.get(message.getTo().toLowerCase());
        if (pickupChannel == null) {
            return;
        }

        if (message.getCommand().startsWith("!add")) {
            try {
                pickupChannel.add(message.getFrom(), new StringTokenizer(message.getArguments(), ", "));
                //send(new ChatMessageOut(message.getFrom(), "im here ", false));
            } catch (PickupUsageException e) {
                log.debug("Error adding", e);
                send(new ChatMessageOut(message.getFrom(), "Couldn't add (" + e.getMessage() + ")", false));
            }
        } else if (message.getCommand().startsWith("!remove")) {
            try {
                pickupChannel.remove(message.getFrom(), new StringTokenizer(message.getArguments(), ", "));
            } catch (PickupUsageException e) {
                send(new ChatMessageOut(message.getFrom(), "Couldn't remove (" + e.getMessage() + ")", false));
            }
        }
        //Add to handle Quakelive bot interface.
        // Check if bot is allowed to issue admin commands
        // Check if user excists in channel
        // Issue !add command
        if (message.getCommand().startsWith("!qladd") &&
                auth.permittedTo(message.getFromWithHost(), "limit,reset", channel)) {
            try {
                String nick = message.getFrom().replaceAll("[<>@]", "");
                StringTokenizer a = new StringTokenizer(message.getArguments(), ", ");
                StringTokenizer b = new StringTokenizer("", ", ");

                if(!a.hasMoreTokens()){
                    return;
                }
                //send(new ChatMessageOut(message.getFrom(), message.getArguments(), false));
                pickupChannel.add(a.nextToken(), b);
            } catch (PickupUsageException e) {
                log.debug("Error adding", e);
                send(new ChatMessageOut(message.getFrom(), "Couldn't add (" + e.getMessage() + ")", false));
            }
        }
        else if (message.getCommand().startsWith("!ip")) {
            try {
                pickupChannel.setIps(message.getArguments(),
                        auth.permittedTo(message.getFromWithHost(), "limit,ip", channel));
            } catch (PickupUsageException e) {
                send(new ChatMessageOut(message.getFrom(), e.getMessage(), false));
            }
        } else if (message.getCommand().startsWith("!deleteip") &&
                auth.permittedTo(message.getFromWithHost(), "limit,ip", channel)) {
            try {
                pickupChannel.deleteIps(message.getArguments());
            } catch (PickupUsageException e) {
                send(new ChatMessageOut(message.getFrom(), e.getMessage(), false));
            }
        } else if (message.getCommand().startsWith("!map")) {
            try {
                pickupChannel.setMap(message.getArguments(),
                        auth.permittedTo(message.getFromWithHost(), "limit,map", channel));
            } catch (PickupUsageException e) {
                send(new ChatMessageOut(message.getFrom(), e.getMessage(), false));
            }
        } else if (message.getCommand().startsWith("!deletemap") &&
                auth.permittedTo(message.getFromWithHost(), "limit,map", channel)) {
            try {
                pickupChannel.deleteMap(message.getArguments());
            } catch (PickupUsageException e) {
                send(new ChatMessageOut(message.getFrom(), e.getMessage(), false));
            }
        } else if (message.getCommand().equals("!who")) {
            try {
                pickupChannel.sendWho(message.getArguments(), message.getFrom());
            } catch (PickupUsageException e) {
                log.debug("Error sending who", e);
                send(new ChatMessageOut(message.getFrom(), e.getMessage(), false));
            }
        } else if (message.getCommand().equals("!lastgame")) {
            send(new ChatMessageOut(message.getFrom(), pickupChannel.getLastgame(), false));
        } else if (message.getCommand().equals("!promote")) {
            if (auth.permittedTo(message.getFromWithHost(), "limit,promote", channel)) {
                try {
                    send(new ChatMessageOut(channel, pickupChannel.getPromote(message.getArguments()), false));
                } catch (PickupUsageException e) {
                    send(new ChatMessageOut(message.getFrom(), e.getMessage(), false));
                }
            }
        } else if (message.getCommand().equals("!reset")) {
            if (auth.permittedTo(message.getFromWithHost(), "limit,reset", channel)) {
                pickupChannel.reset();
            }
        } else if (message.getCommand().equals("!update")) {
            pickupChannel.updateTopic();
            pickupChannel.updateAddPolicy();
        } else if (message.getCommand().equals("!motd") &&
                auth.permittedTo(message.getFromWithHost(), "limit,motd", channel)) {
            pickupChannel.setMotd(message.getArguments());
        } else if (message.getCommand().equals("!deletemotd") &&
                auth.permittedTo(message.getFromWithHost(), "limit,motd", channel)) {
            pickupChannel.setMotd("");
        } else if (message.getCommand().equals("!top10")) {
            showTop10(channel, message.getFrom());
        }

    }

    public void received(SystemIn message) {
        if (message.getNumber() == ERR_CHANOPRIVSNEEDED) {
            StringTokenizer tokens = new StringTokenizer(message.getMessage());
            if (tokens.hasMoreTokens()) {
                final String chan = tokens.nextToken();
                if (pickups.containsKey(chan.toLowerCase())) {
                    send(new ChatMessageOut(chan, "Please op me so that I can set your pickup topic!", true));
                }
            }
        }
    }

    private void showTop10(String channel, String replyTo) {
        try {
            List top10 = ((DbModule)getModule("db")).query(
                    "select n.nickname, count(p.nickid) " +
                    "from pickup_games g, pickup_players p, pickup_nicknames n " +
                    "where g.network = ? " +
                    "and g.channel = ? " +
                    "and gametime > now() - interval '7 days' " +
                    "and g.id = p.gameid " +
                    "and p.nickid = n.id " +
                    "group by p.nickid, n.nickname " +
                    "order by count desc " +
                    "limit 10;", new Object[]{parent.getNetwork(), channel});
            if (!top10.isEmpty()) top10.remove(0); // remove headers
            if (top10.isEmpty()) {
                send(new ChatMessageOut(replyTo,
                        "No games have been played in the last week, sorry!", false));
                return;
            }
            List players = new ArrayList();
            int count = 0;
            for (Iterator i = top10.iterator(); i.hasNext();) {
                Object[] stats = (Object[])i.next();
                Map player = new HashMap();
                player.put("num", Integer.toString(++count));
                player.put("nick", stats[0]);
                player.put("games", stats[1]);
                players.add(player);
            }
            Map result = new HashMap();
            result.put("players", players);
            result.put("network", parent.getNetwork());
            result.put("channel", channel);
            send(new ChatMessageOut(replyTo, FormatUtils.format(getConfig(),
                    "pickup,top10_msg", result), false));
        } catch (DbModule.DbException e) {
            log.info("Failed to query database", e);
        } catch (NoSuchModuleException e) {
            log.warn("Statistics require database connection");
        }
    }

    public void userEvent(int id, Object attachment) {
        if (id == EVENT_PICKUPSTART) {
            UserModuleInterface clone = (UserModuleInterface)((Map)attachment).get("clone");
            if (parent.getNetwork().equals(clone.getNetwork())) {
                 Object startChannel = ((Map)attachment).get("channel");
                 List players = (List)((Map)attachment).get("players");
                 for (Iterator i = pickups.keySet().iterator(); i.hasNext();) {
                     String channel = (String)i.next();
                     if (!channel.equals(startChannel)) {
                         PickupChannel pickup = (PickupChannel)pickups.get(channel);
                         for (Iterator j = players.iterator(); j.hasNext();) {
                             String nick = (String)j.next();
                             pickup.removeFromAll(nick);
                         }
                     }
                 }
             }
        }
    }
}
