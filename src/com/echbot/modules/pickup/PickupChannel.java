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
import com.echbot.messages.out.TopicOut;
import org.apache.log4j.Logger;

import java.util.*;

/**
 * @author Chris Pearson
 * @version $Id: PickupChannel.java,v 1.21 2003/09/01 20:52:12 chris Exp $
 */
public class PickupChannel
{
    private static final Logger log = Logger.getLogger(PickupChannel.class);
    private static final int DEFAULT_ADD_NONE = 0;
    private static final int DEFAULT_ADD_ALL = 1;
    private static final int ONLY_ADD_ALL = 2;
    private static final int SECS_IN_A_WEEK = 604800;
    private static final int SECS_IN_A_DAY = 604800;
    private static final int SECS_IN_AN_HOUR = 3600;
    private static final int SECS_IN_A_MIN = 60;
    private static final int PROMOTE_DELAY = 120000;
    private static final int DEFAULT_PLAYERCOUNT = 8;
    private final List games = new ArrayList();
    private final PickupModule module;
    private final String channel;
    private int addPolicy = DEFAULT_ADD_ALL;
    private String oldTopic;
    private String motd = "";
    private long lastPromote = 0;

    public PickupChannel(PickupModule module, String channel, String gameTokens) {
        log.debug(channel + " is now a pickup channel");
        this.module = module;
        this.channel = channel;
        try {
            setAddPolicy(Integer.parseInt(ConfigUtils.getVar(module.getConfig(), "pickup,addpolicy", module.parent.getNetwork(), channel)));
        } catch (NumberFormatException e) {
            log.warn("pickup,addpolicy for " + channel + " is not numeric");
        }
        final StringTokenizer tokens = new StringTokenizer(gameTokens, ", ");
        while (tokens.hasMoreTokens()) {
            String gameId = tokens.nextToken();
            int playercount;
            // See if there's a channel-specific default
            try {
                playercount = Integer.parseInt(ConfigUtils.getVar(module.getConfig(),
                        "pickup,playercount", module.parent.getNetwork(), channel));
            } catch (NumberFormatException e) {
                playercount = DEFAULT_PLAYERCOUNT;
            }
            // See if there are game-specific counts
            int gamePlayerSplit = gameId.indexOf(':');
            if ((gamePlayerSplit > -1) && (gamePlayerSplit < gameId.length() - 1)) {
                try {
                    playercount = Integer.parseInt(gameId.substring(gamePlayerSplit + 1));
                    gameId = gameId.substring(0, gamePlayerSplit);
                } catch (NumberFormatException e) {
                    log.debug("Non-numeric player count", e);
                }
            }
            if (!games.contains(gameId)) {
                games.add(new PickupGame(module, this, gameId, playercount));
            }
        }
        motd = ConfigUtils.getChannelVar(module.getConfig(), "pickup,motd", module.parent.getNetwork(), channel, "");
    }

    public void initialise(Object state) {
        if (state != null) {
            final Map saved = (Map)state;
            for (Iterator i = saved.keySet().iterator(); i.hasNext();) {
                final String gameId = (String)i.next();
                final PickupGame game = getGame(gameId);
                game.initialise(saved.get(gameId));
            }
        }
    }

    public Object getState() {
        final Map state = new HashMap();
        for (Iterator i = games.iterator(); i.hasNext();) {
            final PickupGame game = (PickupGame)i.next();
            state.put(game.getId(), game.getState());
        }
        return state;
    }

    public void setMotd(String motd) {
        this.motd = motd;
        ConfigUtils.setChannelVar(module.getConfig(), "pickup,motd", motd,
                module.parent.getNetwork(), channel);
        updateTopic();
    }

    public String getChannel() {
        return channel;
    }

    private void setAddPolicy(int addPolicy) {
        if ((addPolicy < DEFAULT_ADD_NONE) || (addPolicy > ONLY_ADD_ALL)) {
            log.warn("pickup,addpolicy is out of range: " + addPolicy);
            return;
        }
        this.addPolicy = addPolicy;
    }

    public void nickChange(String oldNick, String newNick) {
        synchronized (games) {
            for (Iterator i = games.iterator(); i.hasNext();) {
                final PickupGame game = (PickupGame)i.next();
                game.nickChange(oldNick, newNick);
            }
        }
    }

    public void removeFromAll(String nick) {
        removeFromAll(nick, true);
    }

    void removeFromAll(String nick, boolean updateTopic) {
        boolean changeTopic = false;
        synchronized (games) {
            for (Iterator i = games.iterator(); i.hasNext();) {
                final PickupGame game = (PickupGame)i.next();
                if (game.remove(nick)) changeTopic = true;
            }
        }
        if (changeTopic && updateTopic) updateTopic();
    }

    public void add(String nick, StringTokenizer tokens) throws PickupUsageException {
        addRemove(nick, tokens, true);
    }

    public void remove(String nick, StringTokenizer tokens) throws PickupUsageException {
        addRemove(nick, tokens, false);
    }

    private void addRemove(String nick, StringTokenizer tokens, boolean adding) throws PickupUsageException {
        boolean changeTopic = false;
        final Set gameIds = new HashSet();
        // If we allow adding to separate games, split them into tokens
        // Otherwise, just add "all" to the list
        if (addPolicy == ONLY_ADD_ALL) {
            gameIds.add("all");
        } else {
            while (tokens.hasMoreTokens()) {
                gameIds.add(tokens.nextToken().toLowerCase());
            }
        }
        // Either there's no games, or we want to add to all
        if (gameIds.isEmpty() || gameIds.contains("all")) {
            synchronized (games) {
                if (games.isEmpty()) {
                    throw new PickupUsageException("No games exist");
                } else if (games.size() == 1) {
                    for (Iterator i = games.iterator(); i.hasNext();) {
                        final PickupGame game = (PickupGame)i.next();
                        if (adding) {
                            if (game.add(nick)) changeTopic = true;
                        } else {
                            if (game.remove(nick)) changeTopic = true;
                        }
                    }
                } else if (adding && (addPolicy == DEFAULT_ADD_NONE)) {
                    throw new PickupUsageException("No game specified");
                } else if (!adding || (addPolicy == DEFAULT_ADD_ALL) || gameIds.contains("all")) {
                    synchronized (games) {
                        for (Iterator i = games.iterator(); i.hasNext();) {
                            final PickupGame game = (PickupGame)i.next();
                            if (adding) {
                                if (game.add(nick)) changeTopic = true;
                            } else {
                                if (game.remove(nick)) changeTopic = true;
                            }
                        }
                    }
                } else {
                    throw new PickupUsageException("Invalid add policy");
                }
            }
        } else {
            boolean found = false;
            synchronized (games) {
                for (Iterator i = games.iterator(); i.hasNext();) {
                    final PickupGame game = (PickupGame)i.next();
                    if (gameIds.contains(game.getId().toLowerCase())) {
                        if (adding) {
                            if (game.add(nick)) changeTopic = true;
                        } else {
                            if (game.remove(nick)) changeTopic = true;
                        }
                        found = true;
                    }
                }
            }
            if (!found) throw new PickupUsageException("None of those games exist!");
        }
        if (changeTopic) updateTopic();
        if (changeTopic && adding) {
            List gameCopy;
            synchronized (games) {
                gameCopy = new ArrayList(games);
            }
            for (Iterator i = gameCopy.iterator(); i.hasNext();) {
                final PickupGame game = (PickupGame)i.next();
                game.gameReady();
            }
        }
    }

    void updateTopic() {
        log.debug("Updating topic, old one is: " + oldTopic);
        final Map vars = new HashMap();
        vars.put("motd", motd);
        final List gameVars = new ArrayList();
        synchronized (games) {
            for (Iterator i = games.iterator(); i.hasNext();) {
                PickupGame game = (PickupGame)i.next();
                Map nextGameVars = game.getVars();
                // If we're on the first game, list it as firstgame
                if (gameVars.isEmpty()) vars.put("firstgame", nextGameVars);
                // Add the game to the list of pickup games so far
                gameVars.add(nextGameVars);
            }
        }
        vars.put("games", gameVars);

        final String newTopic = FormatUtils.format(module.getConfig(), "pickup,format", vars, module.parent.getNetwork(), channel);
        if (!newTopic.equals(oldTopic)) {
            module.send(new TopicOut(channel, newTopic));
            oldTopic = newTopic;
        }
    }

    public void setIps(String arguments, boolean canChange) throws PickupUsageException {
        StringTokenizer tokens = new StringTokenizer(arguments);
        // If no arguments were given, use the only game if there is one
        if (!tokens.hasMoreTokens()) {
            synchronized (games) {
                if (games.size() == 1) {
                    PickupGame game = (PickupGame)games.get(0);
                    String ips = game.getIp();
                    String id = game.getId();
                    if ("".equals(ips)) {
                        throw new PickupUsageException("IP for " + id + " is not set, use \002!ip " +
                                id + " <ip>\002 to set it");
                    } else {
                        throw new PickupUsageException("IP for " + id + " is " + ips);
                    }
                } else {
                    throw new PickupUsageException("Usage: !ip [gameid] <ips>");
                }
            }
        }
        // We've been given some arguments, guess that the first is a game id
        String firstArg = tokens.nextToken();
        PickupGame chosenGame = getGame(firstArg);
        if (chosenGame != null) {
            if (canChange && tokens.hasMoreTokens()) {
                chosenGame.setIp(checkAlias(tokens.nextToken("")).trim());
                updateTopic();
                return;
            } else {
                String ips = chosenGame.getIp();
                String id = chosenGame.getId();
                if ("".equals(ips)) {
                    if (canChange) {
                        throw new PickupUsageException("IP for " + id + " is not set, use \002!ip " +
                                id + " <ip>\002 to set it");
                    } else {
                        throw new PickupUsageException("IP for " + id + " is not set");
                    }
                } else {
                    throw new PickupUsageException("IP for " + id + " is " + ips);
                }
            }
        }
        // First arg must have been the ip to set (or a wrong game id)
        synchronized (games) {
            if (games.isEmpty()) {
                throw new PickupUsageException("No games exist");
            } else if (canChange && games.size() == 1) {
                ((PickupGame)games.get(0)).setIp(checkAlias(arguments).trim());
                updateTopic();
            } else {
                throw new PickupUsageException("Usage: !ip [gameid] <ips>");
            }
        }
    }

    public void deleteIps(String arguments) throws PickupUsageException {
        StringTokenizer tokens = new StringTokenizer(arguments);
        // If no arguments were given, use the only game if there is one
        if (!tokens.hasMoreTokens()) {
            synchronized (games) {
                if (games.size() == 1) {
                    PickupGame game = (PickupGame)games.get(0);
                    game.setIp("");
                    updateTopic();
                    return;
                } else {
                    throw new PickupUsageException("Usage: !deleteip [gameid]");
                }
            }
        }
        // We've been given some arguments, guess that the first is a game id
        PickupGame chosenGame = getGame(tokens.nextToken());
        if (chosenGame != null) {
            chosenGame.setIp("");
            updateTopic();
        } else {
            throw new PickupUsageException("Usage: !deleteip [gameid]");
        }
    }

    private PickupGame getGame(String id) {
		id = id.toLowerCase();
        synchronized (games) {
            for (Iterator i = games.iterator(); i.hasNext();) {
                PickupGame pickupGame = (PickupGame)i.next();
                if (pickupGame.getId().toLowerCase().equals(id)) return pickupGame;
            }
        }
        return null;
    }

    public void sendWho(String triggers, String sendTo) throws PickupUsageException {
        synchronized (games) {
            final List gamesToFormat = new ArrayList();
            final StringTokenizer tokens = new StringTokenizer(triggers, ", ");
            if (tokens.hasMoreTokens()) {
                boolean sentsome = false;
                while (tokens.hasMoreTokens()) {
                    final String next = tokens.nextToken();
                    final PickupGame game = getGame(next);
                    if (game != null) {
                        sentsome = true;
                        gamesToFormat.add(game.getVars());
                        if (gamesToFormat.size() == 2) {
                            module.send(new ChatMessageOut(sendTo, getWhoMessage(gamesToFormat), false));
                            gamesToFormat.clear();
                        }
                    }
                }
                if (sentsome) {
                    if (gamesToFormat.size() > 0) {
                        module.send(new ChatMessageOut(sendTo, getWhoMessage(gamesToFormat), false));
                    }
                    return;
                }
            } else if (games.isEmpty()) {
                throw new PickupUsageException("Usage: !who <gameid>");
            }
            for (Iterator i = games.iterator(); i.hasNext();) {
                PickupGame game = (PickupGame)i.next();
                gamesToFormat.add(game.getVars());
                if (gamesToFormat.size() == 2) {
                    module.send(new ChatMessageOut(sendTo, getWhoMessage(gamesToFormat), false));
                    gamesToFormat.clear();
                }
            }
            if (gamesToFormat.size() > 0) {
                module.send(new ChatMessageOut(sendTo, getWhoMessage(gamesToFormat), false));
            }
        }
    }

    private String getWhoMessage(List games) {
        return FormatUtils.format(module.getConfig(), "pickup,who_msg",
                games, module.parent.getNetwork(), channel);
    }

    public String getLastgame() {
        final String lastgame = ConfigUtils.getChannelVar(module.getConfig(),
                "pickup,lastgame", module.parent.getNetwork(), channel, null);
        final long lastgametime;
        try {
            lastgametime = Long.parseLong(ConfigUtils.getChannelVar(
                    module.getConfig(), "pickup,lastgametime", module.parent.getNetwork(), channel, "-1"));
        } catch (NumberFormatException e) {
            return "I don't remember organising a game here!";
        }
        long longago = (long)Math.floor(System.currentTimeMillis() / 1000) - lastgametime;
        if ((lastgame == null) || (longago > 31536000)) {
            return "I don't remember the last game I organised here!";
        }
        final int weeks = (int)Math.floor(longago / SECS_IN_A_WEEK);
        longago -= weeks * SECS_IN_A_WEEK;
        final int days = (int)Math.floor(longago / SECS_IN_A_DAY);
        longago -= days * SECS_IN_A_DAY;
        final int hours = (int)Math.floor(longago / SECS_IN_AN_HOUR);
        longago -= hours * SECS_IN_AN_HOUR;
        final int mins = (int)Math.floor(longago / SECS_IN_A_MIN);
        longago -= mins * SECS_IN_A_MIN;
        final StringBuffer sb = new StringBuffer();
        if (weeks != 0) {
            sb.append(weeks).append("week");
            if (weeks != 1) sb.append('s');
            sb.append(" ");
        }
        if (days != 0) {
            sb.append(days).append("day");
            if (days != 1) sb.append('s');
            sb.append(" ");
        }
        if (hours != 0) {
            sb.append(hours).append("hour");
            if (hours != 1) sb.append('s');
            sb.append(" ");
        }
        if (mins != 0) {
            sb.append(mins).append("min");
            if (mins != 1) sb.append('s');
            sb.append(" ");
        }
        sb.append(longago).append("sec");
        if (longago != 1) sb.append('s');
        sb.append(" ago: ").append(lastgame);
        return sb.toString();
    }

    public void gameStarted(String lastgame, String ip) {
        ConfigUtils.setChannelVar(module.getConfig(), "pickup,lastgame",
                lastgame, module.parent.getNetwork(), channel);
        ConfigUtils.setChannelVar(module.getConfig(), "alias,lastgame",
                ip, module.parent.getNetwork(), channel);
        ConfigUtils.setChannelVar(module.getConfig(), "pickup,lastgametime",
                Long.toString((long)Math.floor(System.currentTimeMillis() / 1000)), module.parent.getNetwork(), channel);
    }

    private String checkAlias(String alias) {
        return ConfigUtils.getChannelVar(module.getConfig(), "alias," + alias.trim().toLowerCase(),
                module.parent.getNetwork(), channel.toLowerCase(), alias);
    }

    public String getPromote(String arguments) throws PickupUsageException {
        if (System.currentTimeMillis() - lastPromote < PROMOTE_DELAY) {
            throw new PickupUsageException("Only one promote every two minutes! :p");
        }
        StringTokenizer args = new StringTokenizer(arguments);
        String gameName = args.hasMoreTokens() ? args.nextToken() : null;
        final int REALLY_HIGH_NUMBER = 10000;
        int least = REALLY_HIGH_NUMBER;
        synchronized (games) {
            for (Iterator i = games.iterator(); i.hasNext();) {
                final PickupGame game = (PickupGame)i.next();
                final int needed = game.getMaxplayers() - game.getPlayerCount();
                if (game.getId().equalsIgnoreCase(gameName)) {
                    // we only care about this game
                    least = needed;
                    break;
                } else if ((needed > 0) && (needed < least)) {
                    // we'll find the least needed for any game
                    least = needed;
                }
            }
        }
        StringBuffer message = new StringBuffer("\0035\002Please !add up in\00310 ");
        message.append(channel).append(" !!!\0035");
        if (least < REALLY_HIGH_NUMBER) {
            message.append(" ").append(least).append(" more ");
            message.append(least == 1 ? "person" : "people").append(" needed!");
        }
        lastPromote = System.currentTimeMillis();
        return message.toString();
    }

    public void reset() {
        log.debug("Resetting games");
        synchronized (games) {
            for (Iterator i = games.iterator(); i.hasNext();) {
                PickupGame game = (PickupGame)i.next();
                game.reset();
            }
            updateTopic();
        }
    }

    public void putToBack(PickupGame game) {
        synchronized (games) {
            if (games.remove(game)) games.add(game);
            final StringBuffer active = new StringBuffer();
            for (Iterator i = games.iterator(); i.hasNext();) {
                PickupGame next = (PickupGame)i.next();
                active.append(next.getId());
                if (next.getMaxplayers() != DEFAULT_PLAYERCOUNT) active.append(':').append(next.getMaxplayers());
                if (i.hasNext()) active.append(',');
            }
            ConfigUtils.setChannelVar(module.getConfig(), "pickup,active", active.toString(),
                    module.parent.getNetwork(), channel);
        }
    }

    void updateAddPolicy() {
        try {
            setAddPolicy(Integer.parseInt(ConfigUtils.getVar(module.getConfig(), "pickup,addpolicy", module.parent.getNetwork(), channel)));
        } catch (NumberFormatException e) {
            log.warn("pickup,addpolicy for " + channel + " is not numeric");
        }
    }

    public void setMap(String arguments, boolean canChange) throws PickupUsageException {
        StringTokenizer args = new StringTokenizer(arguments);
        synchronized (games) {
            // In case we're just showing the map for the only game
            if (!args.hasMoreTokens()) {
                if (games.size() == 1) {
                    PickupGame game = (PickupGame)games.get(0);
                    if ((game.getMap() != null) && (game.getMap().length() > 0)) {
                        throw new PickupUsageException("\00310" + game.getId() + " on map\0036 " + game.getMap());
                    } else {
                        throw new PickupUsageException("\00310" + game.getId() +
                                " map has not been set, use !map " + game.getId() + " <mapname> to set");
                    }
                } else {
                    throw new PickupUsageException("\0035Usage:\003 !map <gameid> [mapname]");
                }
            }
            // We've been given some arguments, guess that the first is a game id
            String firstArg = args.nextToken();
            PickupGame chosenGame = getGame(firstArg);
            if (chosenGame != null) {
                if (canChange && args.hasMoreTokens()) {
                    chosenGame.setMap(args.nextToken("").trim());
                    updateTopic();
                    return;
                } else {
                    throw new PickupUsageException("\00310" + chosenGame.getId() + " on map\0036 " + chosenGame.getMap());
                }
            }
            // First argument isn't a game id, so we should only have one game
            if (canChange && games.size() == 1) {
                PickupGame game = (PickupGame)games.get(0);
                game.setMap(arguments);
                updateTopic();
                return;
            }
            // Got more than one game, and first argument not a game id
            throw new PickupUsageException("\0035Usage:\003 !map <gameid> [mapname]");
        }
    }

    public void deleteMap(String arguments) throws PickupUsageException {
        StringTokenizer args = new StringTokenizer(arguments);
        synchronized (games) {
            if (!args.hasMoreTokens()) {
                if (games.size() == 1) {
                    PickupGame game = (PickupGame)games.get(0);
                    game.setMap("");
                    updateTopic();
                    return;
                } else {
                    throw new PickupUsageException("\0035Usage:\003 !deletemap <gameid>");
                }
            }
            PickupGame game = getGame(args.nextToken());
            if (game != null) {
                game.setMap("");
                updateTopic();
                return;
            } else {
                throw new PickupUsageException("\0035Usage:\003 !deletemap <gameid>");
            }
        }
    }
}
