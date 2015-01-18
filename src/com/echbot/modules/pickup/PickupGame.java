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

import com.echbot.config.ConfigUtils;
import com.echbot.config.FormatUtils;
import com.echbot.messages.out.ChatMessageOut;
import com.echbot.modules.gamelookup.*;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.*;
import java.net.InetSocketAddress;

/**
 * @author Chris Pearson
 * @version $Id: PickupGame.java,v 1.24 2003/10/09 19:56:12 alex Exp $
 */
public class PickupGame
{
    private static final Logger log = Logger.getLogger(PickupGame.class);
    private static final int ANNOUNCED_NONE = 0;
    private static final int ANNOUNCED_ERROR = 1;
    private static final int ANNOUNCED_FULL = 2;
    private static final int ANNOUNCED_FIND_SERVER = 2;
    private final PickupModule module;
    private final PickupChannel channel;
    private final String id;
    private final List players = new ArrayList();
    private final List lastPlayers = new ArrayList();
    private final int maxplayers;
    private int announced = ANNOUNCED_NONE;
    private String ip = "";
    private String map = "";


    public PickupGame(PickupModule module, PickupChannel channel, String id, int maxplayers) {
        this.module = module;
        this.channel = channel;
        this.id = id;
        this.maxplayers = (maxplayers < 2) ? 2 : ((maxplayers > 20) ? 20 : maxplayers);
        ip = ConfigUtils.getChannelVar(module.getConfig(), "pickup,ip," + id,
                module.parent.getNetwork(), channel.getChannel(), "");
        map = ConfigUtils.getChannelVar(module.getConfig(), "pickup,map," + id,
                module.parent.getNetwork(), channel.getChannel(), "");
        log.debug("New pickup game in " + channel.getChannel() + " with id " + id + " and " + maxplayers + " players");
    }

    public void initialise(Object state) {
        if (state != null) {
            final List playerList = (List)state;
            for (Iterator i = playerList.iterator(); i.hasNext();) {
                final String nick = (String)i.next();
                if (!players.contains(nick)) {
                    players.add(nick);
                }
            }
        }
    }

    public Object getState() {
        return players;
    }

    public String getId() {
        return id;
    }

    public int getPlayerCount() {
        synchronized (players) {
            return players.size();
        }
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        log.debug("Ips for " + id + " is now " + ip);
        this.ip = ip;
        ConfigUtils.setChannelVar(module.getConfig(), "pickup,ip," + id, ip,
                module.parent.getNetwork(), channel.getChannel());
        announced = ANNOUNCED_NONE;
        gameReady();
    }

    public String getMap() {
        return map;
    }

    public void setMap(String map) {
        this.map = map;
        ConfigUtils.setChannelVar(module.getConfig(), "pickup,map," + id, map,
                module.parent.getNetwork(), channel.getChannel());
    }

    public int getMaxplayers() {
        return maxplayers;
    }

    public void nickChange(String oldNick, String newNick) {
        synchronized (players) {
            final int index = players.indexOf(oldNick);
            if (index != -1) {
                players.set(index, newNick);
            }
        }
    }

    public boolean add(String nick) throws PickupUsageException {
        synchronized (players) {
            if (players.contains(nick)) {
                return false;
            } else {
                players.add(nick);
                return true;
            }
        }
    }

    public boolean remove(String nick) {
        synchronized (players) {
            final boolean removed = players.remove(nick);
            if (removed && (players.size() == maxplayers - 1)) {
                announced = ANNOUNCED_NONE;
            }
            return removed;
        }
    }

    public void gameReady() {
        synchronized (players) {
            if (players.size() < maxplayers) return;
        }
        if ("".equals(ip)) {
            if (announced < ANNOUNCED_FIND_SERVER) {
                module.send(new ChatMessageOut(channel.getChannel(),
                        "Please find a server (Use !ip " + id + " <ip> to set)", true));
                announced = ANNOUNCED_FIND_SERVER;
            }
            return;
        }
        try {
            if (enoughSlots()) {
                startGame();
            } else {
                if (announced < ANNOUNCED_FULL) {
                    module.send(new ChatMessageOut(channel.getChannel(),
                            "Not enough free slots on [\002" + id + "\002] server for everyone! " +
                            "\0036Wait for game to finish or add to more games!", true));
                    announced = ANNOUNCED_FULL;
                }
                scheduleReadyCheck();
            }
        } catch (Exception e) {
            if (announced < ANNOUNCED_ERROR) {
                module.send(new ChatMessageOut(channel.getChannel(),
                        "Couldn't query server(" + e.getMessage() + "), will keep trying!", true));
                log.debug("Error querying server", e);
                announced = ANNOUNCED_ERROR;
            }
            scheduleReadyCheck();
        }
    }

    private boolean enoughSlots() throws IOException {
        String serverCheck = ConfigUtils.getChannelVar(module.getConfig(),
                "pickup,servercheck", module.parent.getNetwork(), channel.getChannel(), null);
        if ((serverCheck == null) || "off".equalsIgnoreCase(serverCheck)) return true;
        if ("q3".equalsIgnoreCase(serverCheck)) {
            StringTokenizer ipTokens = new StringTokenizer(ip);
            Q3Query query = new Q3Query(ipTokens.hasMoreTokens() ? ipTokens.nextToken() : ip);
            try {
                int maxclients = Integer.parseInt((String)query.getVars().get("sv_maxclients"));
                int players = Integer.parseInt((String)query.getVars().get("ech_players"));
                return (maxclients - players >= maxplayers) && (players < 5);
            } catch (NumberFormatException e) {
                log.warn("Error parsing number of players on server, assuming its ok", e);
                return true;
            }

//        } else if(!"q4".equalsIgnoreCase(serverCheck)) {
//            StringTokenizer ipTokens = new StringTokenizer(ip);
//            Q4Query q4query = new Q4Query(ipTokens.hasMoreTokens() ? ipTokens.nextToken() : ip);
//            try {
//                int maxclients = Integer.parseInt((String)q4query.getVars().get("si_maxPlayers"));
//                int players = Integer.parseInt((String)q4query.getVars().get("ech_players"));
//                return (maxclients - players >= maxplayers) && (players < 5);
//            } catch (NumberFormatException e) {
//                log.warn("Error parsing number of players on server, assuming its ok", e);
//                return true;
//            }

        } else if ("hl".equalsIgnoreCase(serverCheck)) {
            StringTokenizer ipTokens = new StringTokenizer(ip);
            HalfLifeQuery query = new HalfLifeQuery(ipTokens.hasMoreTokens() ? ipTokens.nextToken() : ip);
            int maxclients = Integer.parseInt((String)query.getVars().get("ech_maxclients"));
            int players = Integer.parseInt((String)query.getVars().get("ech_players"));
            return (maxclients - players >= maxplayers) && (players < 5);

        } else if ("ut2003".equalsIgnoreCase(serverCheck) || "ut2004".equalsIgnoreCase(serverCheck)) {
            InetSocketAddress server;
            String toSplit = ip.trim();
            toSplit = toSplit.indexOf(' ') == -1 ? toSplit : toSplit.substring(0, toSplit.indexOf(' '));
            if (toSplit.indexOf(':') == -1) {
                server = new InetSocketAddress(toSplit, 7778);
            } else {
                String portString = toSplit.substring(toSplit.indexOf(':') + 1);
                toSplit = toSplit.substring(0, toSplit.indexOf(':'));
                try {
                    server = new InetSocketAddress(toSplit, Integer.parseInt(portString) + 1);
                } catch (NumberFormatException e) {
                    server = new InetSocketAddress(toSplit, 7778);
                }
            }
            UT2003Query query = new UT2003Query(server);
            try {
                query.queryInfo();
            } catch (QueryException e) {
                throw new IOException(e.getMessage());
            }
            int maxclients = Integer.parseInt(query.getResult().getProperty("maxclients"));
            int players = Integer.parseInt(query.getResult().getProperty("clients"));
            return (maxclients - players >= maxplayers) && (players < 5);
        }
        log.debug("Unknown server lookup type: " + serverCheck);
        return true;
    }

    private void startGame() {
        Map broadcastVars = new HashMap();
        synchronized (players) {
            announced = ANNOUNCED_NONE;
            final StringBuffer names = new StringBuffer();
            final List removedPlayers = new ArrayList();
            for (Iterator i = players.iterator(); i.hasNext();) {
                if (removedPlayers.size() >= maxplayers) break;
                final String nick = (String)i.next();
                names.append(nick);
                if (i.hasNext()) names.append(',');
                i.remove();
                removedPlayers.add(nick);
            }
            synchronized (lastPlayers) {
                lastPlayers.clear();
                lastPlayers.addAll(removedPlayers);
            }
            StringBuffer individualNotification = new StringBuffer();
            individualNotification.append("You're needed now at " + ip + " or in " + channel.getChannel() + " for a game you signed up for!");
            StringBuffer readyMsg = new StringBuffer("Game [\002" + id + "\002] ready to start in " + channel.getChannel() + " !!!");

            // Create the keys for the formatted channel message
            Map readyKeys = new HashMap();

            // Random Captains generation
            if ("on".equals(ConfigUtils.getVar(module.getConfig(), "pickup,randomcaptains",
                    module.parent.getNetwork(), channel.getChannel()))) {
                // Select random captains
                int firstCaptain = (int)Math.floor(Math.random() * removedPlayers.size());
                int secondCaptain;
                do {
                    secondCaptain = (int)Math.floor(Math.random() * removedPlayers.size());
                } while (secondCaptain == firstCaptain);

                readyKeys.put("cap1", removedPlayers.get(firstCaptain));
                readyKeys.put("cap2", removedPlayers.get(secondCaptain));

                StringBuffer captainsMsg = new StringBuffer(" Your captains will be ");
                captainsMsg.append(removedPlayers.get(firstCaptain) + " and ");
                captainsMsg.append(removedPlayers.get(secondCaptain) + "!");
                individualNotification.append(captainsMsg.toString());
            }
            if ("on".equals(ConfigUtils.getVar(module.getConfig(), "pickup,readymessage", module.parent.getNetwork(), channel.getChannel()))) {
                module.send(new ChatMessageOut(channel.getChannel(), readyMsg.toString(), false));
            }

            module.send(new ChatMessageOut(names.toString(), individualNotification.toString(), true));

            for (Iterator i = removedPlayers.iterator(); i.hasNext();) {
                channel.removeFromAll((String)i.next(), false);
            }

            readyKeys.put("ip", ip);
            readyKeys.put("players", removedPlayers);
            String readyMessage = FormatUtils.format(module.getConfig(),
                    "pickup,ready_msg", readyKeys, module.parent.getNetwork(),
                    channel.getChannel());
            channel.gameStarted(readyMessage, ip);
            if ("on".equals(ConfigUtils.getVar(module.getConfig(), "pickup,autodeleteip",
                    module.parent.getNetwork(), channel.getChannel()))) {
                setIp("");
            }
            module.send(new ChatMessageOut(channel.getChannel(), readyMessage.toString(), true));
            final PickupGame thisGame = this;
            module.getTimer().schedule(new Runnable()
            {
                public void run() {
                    if ("on".equals(ConfigUtils.getVar(module.getConfig(), "pickup,rotateservers",
                            module.parent.getNetwork(), channel.getChannel()))) {
                        channel.putToBack(thisGame);
                    }
                    channel.updateTopic();
                }
            }, 100);
            broadcastVars.put("players", removedPlayers);
            broadcastVars.put("id", id);
            broadcastVars.put("ip", ip);
            broadcastVars.put("channel", channel.getChannel());
            broadcastVars.put("clone", module.parent);
        }
        module.parent.triggerGlobalEvent(module.EVENT_PICKUPSTART, broadcastVars);
    }

    public Map getVars() {
        final Map vars = new HashMap();
        vars.put("id", id);
        vars.put("ip", ip);
        vars.put("map", map);
        vars.put("maxplayers", Integer.toString(maxplayers));
        vars.put("playercount", (players.size() > maxplayers) ?
                maxplayers + "+" + Integer.toString(players.size() - maxplayers) :
                Integer.toString(players.size()));
        final List playerVars = new ArrayList();
        final List noEmptyPlayers = new ArrayList();
        for (int i = 0; i < maxplayers; i++) {
            final String nick = (i >= players.size()) ? "?" : (String)players.get(i);
            final Map playerMap = new HashMap();
            playerMap.put("nick", nick);
            playerMap.put("num", Integer.toString(i + 1));
            playerVars.add(playerMap);
            if (i < players.size()) noEmptyPlayers.add(playerMap);
        }
        vars.put("players", playerVars);
        vars.put("players_compact", noEmptyPlayers);
        return vars;
    }

    public void reset() {
        synchronized (players) {
            players.clear();
        }
    }

    private void scheduleReadyCheck() {
        module.getTimer().schedule(new Runnable()
        {
            public void run() {
                synchronized (players) {
                    if (players.size() >= maxplayers) {
                        gameReady();
                    }
                }
            }
        }, 10000);
    }
}
