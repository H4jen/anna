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
package com.echbot.modules.bans;

import com.echbot.UserModule;
import com.echbot.UserModuleInterface;
import com.echbot.GroupTimer;
import com.echbot.config.Config;
import com.echbot.config.ConfigUtils;
import com.echbot.messages.in.ChatMessageIn;
import com.echbot.messages.in.JoinIn;
import com.echbot.messages.in.NickIn;
import com.echbot.messages.in.SystemIn;
import com.echbot.messages.out.ChatMessageOut;
import com.echbot.messages.out.WhoisOut;
import com.echbot.modules.auth.AuthModule;
import com.echbot.modules.channels.ChannelsModule;
import org.apache.log4j.Logger;

import java.util.*;
import java.io.FileWriter;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * @author Chris Pearson
 * @version $Id: BansModule.java,v 1.17 2003/09/24 18:34:28 chris Exp $
 */
public class BansModule extends UserModule
{
    private static final Logger log = Logger.getLogger(BansModule.class);
    private static final String BAN_USAGE = "Usage: !ban [channel] <nickname> [duration] <reason>     (duration e.g. 1d or 3d2h with m/h/d/w)";
    private static final String BANLIST_USAGE = "Usage: !banlist [channel]";
    private static final String UNBAN_USAGE = "Usage: !unban [channel] <nickname>";
    private static final int SECS_IN_A_WEEK = 604800;
    private static final int SECS_IN_A_DAY = 86400;
    private static final int SECS_IN_AN_HOUR = 3600;
    private static final int SECS_IN_A_MIN = 60;
    private static final int ERR_NOSUCHNICK = 401;
    private static final int RPL_WHOISUSER = 311;
    private static final int RPL_WHOISACCOUNT = 330;
    private static final int WAIT_FOR_AUTH_DELAY = 1000;
    private static final String BAN_FOLDER = "/var/www/echbot.com/bans/";
    private final Set pendingBans = new ExpiringHashSet(SECS_IN_A_DAY);
    private final Map bans = new HashMap();
    private final GroupTimer removeTimer = new GroupTimer(getTimer());

    private static class BanException extends Exception
    {
        public BanException(String message) {
            super(message);
        }
    }

    public BansModule(UserModuleInterface parent) {
        super(parent);
        register(ChatMessageIn.class);
        register(SystemIn.class);
        register(NickIn.class);
        register(JoinIn.class);
        setDefault("limit,ban", "admins,opped");
        setDefault("bans,defaultlength", Integer.toString(SECS_IN_A_DAY));
    }

    public void initialise(Object state) {
        ChannelsModule channels;
        try {
            channels = (ChannelsModule)getModule("channels");
        } catch (NoSuchModuleException e) {
            log.error("Require channels module", e);
            return;
        }
        Config config = getConfig();
        synchronized (config) {
            Set keys = new HashSet(config.keySet());
            for (Iterator i = keys.iterator(); i.hasNext();) {
                String key = (String)i.next();
                if (key.startsWith("vars,") && (key.indexOf(",bans,") > -1)) {
                    log.debug("Examining " + key);
                    StringTokenizer tokens = new StringTokenizer(key, ",");
                    if (tokens.countTokens() == 5) {
                        tokens.nextToken();
                        String network = tokens.nextToken();
                        if (parent.getNetwork().equals(network)) {
                            String channel = tokens.nextToken();
                            if (channels.shouldBeOn(channel)) {
                                log.debug(parent.getName() + " claimed ban");
                                tokens.nextToken();
                                String nickname = tokens.nextToken();
                                try {
                                    addBanWithoutSet(BanEntry.parseConfigString(
                                            config.get(key), channel, nickname, this));
                                } catch (Exception e) {
                                    log.debug("Failed to parse config string", e);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public Object getState() {
        removeTimer.cancel();
        return super.getState();
    }

    public void received(ChatMessageIn message) {
        final String channel = message.getTo().startsWith("#") ? message.getTo() : null;

        if (!message.isCommand()) return;
        if ("!ban".equals(message.getCommand())) {
            try {
                ban(new StringTokenizer(message.getArguments()), channel, message.getFrom(), message.getFromWithHost());
            } catch (BanException e) {
                send(new ChatMessageOut(message.getFrom(), "Couldn't ban (" + e.getMessage() + ")", false));
            }
        } else if ("!banlist".equals(message.getCommand())) {
            try {
                banlist(new StringTokenizer(message.getArguments()), channel, message.getFrom(), message.getFromWithHost());
            } catch (BanException e) {
                send(new ChatMessageOut(message.getFrom(), "Couldn't list (" + e.getMessage() + ")", false));
            }
        } else if ("!unban".equals(message.getCommand())) {
            try {
                unban(new StringTokenizer(message.getArguments()), channel, message.getFromWithHost());
            } catch (BanException e) {
                send(new ChatMessageOut(message.getFrom(), "Couldn't unban (" + e.getMessage() + ")", false));
            }
        }
    }

    private final String getMsgChannel(String channel, StringTokenizer args, String fromHost, String exception) throws BanException {
        // get the reference to the auth module, error if it's not loaded
        final AuthModule auth;
        try {
            auth = (AuthModule)getModule("auth");
        } catch (NoSuchModuleException e) {
            log.warn("Ban module requires auth and channels modules", e);
            return null;
        }
        // if message was received in pm, read a channel in
        if (channel == null) {
            if (!args.hasMoreTokens()) throw new BanException(exception);
            channel = args.nextToken();
            if (!channel.startsWith("#")) throw new BanException(exception);
        }
        // if they're not allowed, just ignore the request
        if (!auth.permittedTo(fromHost, "limit,ban", channel)) {
            log.debug(fromHost + " is not allowed to ban on " + channel);
            return null;
        }
        return channel;
    }

    private void ban(StringTokenizer args, String channel, String from, String fromHost) throws BanException {
        log.debug("Checking channel " + channel);
        channel = getMsgChannel(channel, args, fromHost, BAN_USAGE);
        log.debug("Channel is " + channel);
        if (channel == null) return;
        // next parameter is the nickname of who to ban
        if (!args.hasMoreTokens()) throw new BanException(BAN_USAGE);
        String banNickname = args.nextToken();
        if ((banNickname.charAt(0) == '#') && args.hasMoreTokens()) {
            // see if we're allowed to ban on the specified chan
            channel = getMsgChannel(banNickname, args, fromHost, BAN_USAGE);
            if (channel == null) return;
            banNickname = args.nextToken();
        }
        // now we possibly have a ban duration followed by the reason
        if (!args.hasMoreTokens()) throw new BanException(BAN_USAGE);
        final String remaining = args.nextToken("");
        int start = 0, index = 0, thisNum = 0;
        long banlength = 0;
        while (index < remaining.length()) {
            final char next = remaining.charAt(index);
            if (Character.isDigit(next)) {
                thisNum = thisNum * 10 + Character.getNumericValue(next);
            } else if (next == 'm') {
                banlength += thisNum * SECS_IN_A_MIN;
                thisNum = 0;
                start = index + 1;
            } else if (next == 'h') {
                banlength += thisNum * SECS_IN_AN_HOUR;
                thisNum = 0;
                start = index + 1;
            } else if (next == 'd') {
                banlength += thisNum * SECS_IN_A_DAY;
                thisNum = 0;
                start = index + 1;
            } else if (next == 'w') {
                banlength += thisNum * SECS_IN_A_WEEK;
                thisNum = 0;
                start = index + 1;
            } else if (next == ' ') {
                // ignore spaces
            } else {
                if (thisNum != 0) {
                    // had some number, but no qualifier we recognise
                    throw new BanException(BAN_USAGE);
                } else {
                    break;
                }
            }
            index++;
        }
        if (banlength == 0) {
            try {
                banlength = Integer.parseInt(ConfigUtils.getVar(getConfig(), "bans,defaultlength", parent.getNetwork(), channel));
            } catch (NumberFormatException e) {
                log.warn("Default ban length is non-numeric for " + channel, e);
                throw new BanException(BAN_USAGE);
            }
        }
        final String reason = remaining.substring(start).trim();
        if (reason.length() == 0) throw new BanException(BAN_USAGE);
        // Now we have all the information we need: channel, banNickname, banlength, reason, whoby
        reban(new BanEntry(channel, banNickname, banlength, reason, from, this));
    }

    private void banlist(StringTokenizer args, String channel, String from, String fromHost) throws BanException {
        channel = getMsgChannel(channel, args, fromHost, BANLIST_USAGE);
        if (channel == null) return;

        synchronized (bans) {
            Map banChannel = (Map)bans.get(channel.toLowerCase());
            if (banChannel != null) {
                if (banChannel.size() > 0) {
                    PrintWriter output;
                    String filename = getBanlistFilename();
                    try {
                        output = new PrintWriter(new FileWriter(BAN_FOLDER + filename));
                    } catch (IOException e) {
                        log.debug("Error writing bans", e);
                        throw new BanException("Couldn't write bans: " + e.getMessage());
                    }
                    output.println("Banlist for " + channel + " at " + new Date());

                    for (Iterator i = banChannel.values().iterator(); i.hasNext();) {
                        BanEntry ban = (BanEntry)i.next();
                        long expiresIn = (long)Math.round((ban.getExpiresAt() - System.currentTimeMillis()) / 1000);
//                        send(new ChatMessageOut(from, ban.getNickname() + " banned from " +
//                                channel + " for " + BanEntry.getDuration(ban.getBanlength()) +
//                                " (" + BanEntry.getDuration(expiresIn) +
//                                " left) by " + ban.getBanner() + ": " + ban.getReason(), true));
                        output.println(ban.getNickname() + " banned from " +
                                channel + " for " + BanEntry.getDuration(ban.getBanlength()) +
                                " (" + BanEntry.getDuration(expiresIn) +
                                " left) by " + ban.getBanner() + ": " + ban.getReason());
                    }
                    output.close();
                    send(new ChatMessageOut(from, "Banlist generated at http://www.echbot.com/bans/" + filename, false));
                    return;
                }
            }
        }
        send(new ChatMessageOut(from, "No bans on " + channel, false));
    }

    private static final String getBanlistFilename() {
        String filename = null;
        while (filename == null || new File(BAN_FOLDER + filename).exists()) {
            StringBuffer filenameBuffer = new StringBuffer();
            for (int i = 0; i < 10; i++) {
                filenameBuffer.append(Integer.toString((int)(Math.random() * 10.0)));
            }
            filenameBuffer.append(".txt");
            filename = filenameBuffer.toString();
        }
        return filename;
    }

    private void unban(StringTokenizer args, String channel, String fromHost) throws BanException {
        channel = getMsgChannel(channel, args, fromHost, UNBAN_USAGE);
        if (channel == null) return;
        log.debug("Unbanning on " + channel);
        // next parameter is the nickname of who to unban
        if (!args.hasMoreTokens()) throw new BanException(UNBAN_USAGE);
        String banNickname = args.nextToken();
        if ((banNickname.charAt(0) == '#') && args.hasMoreTokens()) {
            // see if we're allowed to ban on the specified chan
            channel = getMsgChannel(banNickname, args, fromHost, UNBAN_USAGE);
            if (channel == null) return;
            banNickname = args.nextToken();
        }
        BanEntry ban = getBan(channel, banNickname);
        if (ban != null) ban.remove();
    }

    private static final String getQnetAuth(StringTokenizer tokens) {
        final String first = tokens.nextToken();
        if (!first.equals(":is")) return first;
        tokens.nextToken(); // authed
        tokens.nextToken(); // as
        return tokens.nextToken();
    }

    public void received(SystemIn message) {
        if (parent.getNetwork().equals("quakenet") && (message.getNumber() == RPL_WHOISACCOUNT)) {
            StringTokenizer tokens = new StringTokenizer(message.getMessage());
            String person = tokens.nextToken();
            String authedAs = getQnetAuth(tokens);
            BanEntry ban = getPending(person, false);
            if (ban != null) {
                log.debug("Setting quakenet auth for " + person);
                ban.setQnetAuth(authedAs);
            }
        } else if (message.getNumber() == ERR_NOSUCHNICK) {
            StringTokenizer tokens = new StringTokenizer(message.getMessage());
            BanEntry ban = getPending(tokens.nextToken(), true);
            if (ban != null) {
                setBan(ban);
            } else {
                log.debug("Couldn't find ban request");
            }
        } else if (message.getNumber() == RPL_WHOISUSER) {
            final StringTokenizer tokens = new StringTokenizer(message.getMessage());
            getTimer().schedule(new Runnable()
            {
                public void run() {
                    BanEntry ban = getPending(tokens.nextToken(), true);
                    if (ban != null) {
                        ban.setIdent(tokens.nextToken());
                        ban.setHost(tokens.nextToken());
                        setBan(ban);
                    }
                }
            }, WAIT_FOR_AUTH_DELAY);
        }
    }

    private BanEntry getPending(String nickname, boolean remove) {
        synchronized (pendingBans) {
            for (Iterator i = pendingBans.iterator(); i.hasNext();) {
                final BanEntry ban = (BanEntry)i.next();
                if (ban.getNickname().equalsIgnoreCase(nickname)) {
                    if (remove) i.remove();
                    return ban;
                }
            }
        }
        return null;
    }

    private void setBan(BanEntry ban) {
        addBanWithoutSet(ban);
        ban.setBan();
        ConfigUtils.setChannelVar(getConfig(), "bans," + ban.getNickname().toLowerCase(),
                ban.getConfigString(), parent.getNetwork(), ban.getChannel());
    }

    private void addBanWithoutSet(BanEntry ban) {
        if ((ban == null) || ban.isExpired()) return;
        String banNick = ban.getNickname().toLowerCase();
        String banChan = ban.getChannel().toLowerCase();
        synchronized (bans) {
            if (!bans.containsKey(banChan)) {
                Map channelBans = new HashMap();
                channelBans.put(banNick, ban);
                bans.put(banChan, channelBans);
            } else {
                Map channelMap = (Map)bans.get(banChan);
                if (channelMap.containsKey(banNick)) {
                    BanEntry oldBan = (BanEntry)channelMap.get(banNick);
                    ban.combineWith(oldBan);
                }
                channelMap.put(banNick, ban);
            }
        }
    }

    void removeBan(String channel, String nick) {
        log.debug("Removing ban on " + channel + " for " + nick);
        String banNick = nick.toLowerCase();
        String banChan = channel.toLowerCase();
        synchronized (bans) {
            if (bans.containsKey(banChan)) {
                Map channelMap = (Map)bans.get(banChan);
                BanEntry ban = (BanEntry)channelMap.get(banNick);
                if (ban != null) {
                    channelMap.remove(banNick);
                    if (channelMap.isEmpty()) bans.remove(banChan);
                }
            }
            ConfigUtils.setChannelVar(getConfig(), "bans," + banNick, null, parent.getNetwork(), banChan);
        }
    }

    private BanEntry getBan(String channel, String nick) {
        String banNick = nick.toLowerCase();
        String banChan = channel.toLowerCase();
        synchronized (bans) {
            if (bans.containsKey(banChan)) {
                Map channelMap = (Map)bans.get(banChan);
                return (BanEntry)channelMap.get(banNick);
            }
        }
        return null;
    }

    public void received(JoinIn message) {
        BanEntry ban = getBan(message.getChannel(), message.getJoiner());
        if (ban != null) reban(ban);
    }

    public void received(NickIn message) {
        synchronized (bans) {
            for (Iterator i = bans.keySet().iterator(); i.hasNext();) {
                String channel = (String)i.next();
                BanEntry ban = getBan(channel, message.getNewNick());
                if (ban != null) reban(ban);
            }
        }
    }

    private final void reban(BanEntry ban) {
        synchronized (pendingBans) {
            pendingBans.add(ban);
        }
        send(new WhoisOut(ban.getNickname()));
    }
}
