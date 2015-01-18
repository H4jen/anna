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
package com.echbot.modules.channels;

import com.echbot.UserModule;
import com.echbot.UserModuleInterface;
import com.echbot.modules.auth.AuthModule;
import com.echbot.modules.db.DbModule;
import com.echbot.messages.in.*;
import com.echbot.messages.out.ChatMessageOut;
import com.echbot.messages.out.JoinOut;
import org.apache.log4j.Logger;

import java.util.*;

/**
 * @author Chris Pearson
 * @version $Id: ChannelsModule.java,v 1.30 2003/09/24 18:33:56 chris Exp $
 */
public final class ChannelsModule extends UserModule
{
    private static final Logger log = Logger.getLogger(ChannelsModule.class);
    private static final int ERR_CHANNELISFULL = 471;
    private static final int ERR_INVITEONLYCHAN = 473;
    private static final int ERR_BANNEDFROMCHAN = 474;
    private static final int ERR_BADCHANNELKEY = 475;
    private static final int ERR_NEEDREGGEDNICK = 477;
    private static final int RPL_NAMREPLY = 353;
    private static final int REJOIN_DELAY = 600000;
    private static final long STATS_UPDATE_DELAY = 60000;
    private final Map occupants = Collections.synchronizedMap(new HashMap());
    private final Map voices = Collections.synchronizedMap(new HashMap());
    private final Map ops = Collections.synchronizedMap(new HashMap());
    private int lostUserEvent;
    private final Runnable rejoinTask = new Runnable()
    {
        public void run() {
            rejoinChannels(true);
        }
    };

    public ChannelsModule(UserModuleInterface parent) {
        super(parent);
        register(ChatMessageIn.class);
        register(JoinIn.class);
        register(KickIn.class);
        register(ModeIn.class);
        register(NickIn.class);
        register(PartIn.class);
        register(QuitIn.class);
        register(SystemIn.class);
        lostUserEvent = getUserEventId("channels.lostuser");
        getTimer().schedule(rejoinTask, REJOIN_DELAY);
    }

    public void initialise(Object state) {
        log.debug("Initialising the channels module");
        if (state != null) {
            try {
                Map[] saved = (Map[])state;
                occupants.putAll(saved[0]);
                ops.putAll(saved[1]);
                voices.putAll(saved[2]);
            } catch (Exception e) {
                log.debug("Failed to restore state", e);
            }
        }
        String chans = getConfig().get(parent.getName() + ",channels");
        StringTokenizer tokens = new StringTokenizer(chans, ",");
        List channels = new ArrayList();
        List passwords = new ArrayList();
        while (tokens.hasMoreTokens()) {
            String next = tokens.nextToken().trim();
            int space = next.indexOf(' ');
            if ((space != -1) && (next.length() > space + 1)) {
                String channel = next.substring(0, space);
                if (!occupants.containsKey(channel)) {
                    channels.add(channel);
                    passwords.add(next.substring(space + 1));
                }
            } else if (!occupants.containsKey(next)) {
                channels.add(next);
            }
        }
        StringBuffer join = new StringBuffer();
        for (Iterator i = channels.iterator(); i.hasNext();) {
            String channel = (String)i.next();
            join.append(channel);
            if (i.hasNext()) join.append(',');
        }
        if (!passwords.isEmpty()) join.append(' ');
        for (Iterator i = passwords.iterator(); i.hasNext();) {
            String password = (String)i.next();
            join.append(password);
            if (i.hasNext()) join.append(',');
        }
        if (!"".equals(join.toString().trim())) send(new JoinOut(join.toString()));
        startStatsTimer();
    }

    public Object getState() {
        return new Map[]{occupants, ops, voices};
    }

    public void received(JoinIn message) {
        addToMap(occupants, message.getChannel().toLowerCase(), message.getJoiner());
    }

    public void received(final KickIn message) {
        leftChan(message.getChannel(), message.getKicked());
        if (!canSee(message.getKicked())) {
            parent.triggerEvent(lostUserEvent, message.getKicked());
        }
        if (message.getKicked().equals(parent.getNickname())) {
            String errorMessage = "Kicked from " + message.getChannel() + " by " + message.getKicker();
            send(new ChatMessageOut("#echbot.control", errorMessage, true));
            log.warn(errorMessage);
            getTimer().schedule(new Runnable()
            {
                public void run() {
                    StringTokenizer tokens = new StringTokenizer(getConfig().get(parent.getName() + ",channels"), ",");
                    while (tokens.hasMoreTokens()) {
                        String chan = tokens.nextToken().trim().toLowerCase();
                        if (chan.equals(message.getChannel()) ||
                                chan.startsWith(message.getChannel() + " ")) {
                            send(new JoinOut(chan));
                        }
                    }
                }
            }, 3000);
        }
    }

    public void received(NickIn message) {
        replaceEntries(occupants, message.getOldNick(), message.getNewNick());
        replaceEntries(ops, message.getOldNick(), message.getNewNick());
        replaceEntries(voices, message.getOldNick(), message.getNewNick());
    }

    public void received(PartIn message) {
        leftChan(message.getChannel(), message.getLeaver());
        if (!canSee(message.getLeaver())) {
            parent.triggerEvent(lostUserEvent, message.getLeaver());
        }
    }

    public void received(QuitIn message) {
        for (Iterator i = occupants.keySet().iterator(); i.hasNext();) {
            String channel = (String)i.next();
            leftChan(channel, message.getQuitter());
        }
        parent.triggerEvent(lostUserEvent, message.getQuitter());
    }

    public void received(SystemIn message) {
        if (isJoinFailure(message)) {
            log.info("Failed to join channel: " + message.getMessage());
            String errorMessage = "Failed to (re)join channel: " + message.getMessage();
            send(new ChatMessageOut("#echbot.control", errorMessage, true));
            log.warn(errorMessage);
        } else if (message.getNumber() == RPL_NAMREPLY) {
            parseNameList(message);
        }
    }

    private static final boolean isJoinFailure(SystemIn message) {
        switch (message.getNumber()) {
            case ERR_CHANNELISFULL:
            case ERR_INVITEONLYCHAN:
            case ERR_BANNEDFROMCHAN:
            case ERR_BADCHANNELKEY:
            case ERR_NEEDREGGEDNICK:
                return true;
            default:
                return false;
        }
    }

    private final void parseNameList(SystemIn message) {
        StringTokenizer names = new StringTokenizer(message.getMessage(), ",: ");
        /* pointless(?) symbol */
        if (!names.hasMoreTokens()) return;
        names.nextToken();
        /* channel name */
        if (!names.hasMoreTokens()) return;
        String channel = names.nextToken().toLowerCase();
        while (names.hasMoreTokens()) {
            String name = names.nextToken();
            if (name.charAt(0) == '+') {
                name = name.substring(1);
                addToMap(occupants, channel, name);
                addToMap(voices, channel, name);
            } else if (name.charAt(0) == '@') {
                name = name.substring(1);
                addToMap(occupants, channel, name);
                addToMap(ops, channel, name);
            } else {
                addToMap(occupants, channel, name);
            }
        }
    }

    private final void leftChan(String chan, String nick) {
        String channel = chan.toLowerCase();
        if (parent.getNickname().equals(nick)) {
            occupants.remove(channel);
            ops.remove(channel);
            voices.remove(channel);
        } else {
            delFromMap(occupants, channel, nick);
            delFromMap(ops, channel, nick);
            delFromMap(voices, channel, nick);
        }
    }

    private static final void addToMap(Map map, Object key, Object value) {
        if (map.containsKey(key)) {
            ((Set)map.get(key)).add(value);
        } else {
            Set newSet = Collections.synchronizedSet(new HashSet());
            newSet.add(value);
            map.put(key, newSet);
        }
    }

    private static final void delFromMap(Map map, Object key, Object value) {
        if (map.containsKey(key)) {
            ((Set)map.get(key)).remove(value);
        }
    }

    private static final void replaceEntries(Map map, String oldNick, String newNick) {
        synchronized (map) {
            for (Iterator i = map.values().iterator(); i.hasNext();) {
                Set set = (Set)i.next();
                if (set.contains(oldNick)) {
                    set.remove(oldNick);
                    set.add(newNick);
                }
            }
        }
    }

    /**
     * Check whether the given nickname is on any of our channels.
     * @param person who to search for (just nickname, no ident etc)
     * @return true if the nickname is known on any of the channels we're in
     */
    public boolean canSee(String person) {
        synchronized (occupants) {
            for (Iterator i = occupants.values().iterator(); i.hasNext();) {
                Set people = (Set)i.next();
                if (people.contains(person)) return true;
            }
        }
        return false;
    }

    public boolean isOpped(String nick, String channel) {
        if (channel == null) return false;
        String chan = channel.toLowerCase();
        synchronized (ops) {
            if (!ops.containsKey(chan)) return false;
            return ((Set)ops.get(chan)).contains(nick);
        }
    }

    public boolean isVoiced(String nick, String channel) {
        if (channel == null) return false;
        String chan = channel.toLowerCase();
        synchronized (voices) {
            if (!voices.containsKey(chan)) return false;
            return ((Set)voices.get(chan)).contains(nick);
        }
    }

    public void received(ModeIn message) {
        // modes requiring a parameter are bkl
        StringTokenizer modes = new StringTokenizer(message.getModes());
        if (!modes.hasMoreTokens()) return;
        String plusminus = modes.nextToken();
        boolean adding = true;
        int index = 0;
        while (index < plusminus.length()) {
            char next = plusminus.charAt(index++);
            if (next == '+')
                adding = true;
            else if (next == '-')
                adding = false;
            else if (next == 'k') {
                String newKey = modes.nextToken();
                setChannelKey(message.getChannel(), adding ? newKey : null);
            } else if ((next == 'b') || (next == 'l')) {
                if (modes.hasMoreTokens()) modes.nextToken();
            } else if (next == 'o') {
                if (modes.hasMoreTokens()) {
                    synchronized (ops) {
                        String nickname = modes.nextToken();
                        log.debug((adding ? "Opped " : "Deopped ") + nickname);
                        if (adding)
                            addToMap(ops, message.getChannel(), nickname);
                        else
                            delFromMap(ops, message.getChannel(), nickname);
                    }
                }
            } else if (next == 'v') {
                if (modes.hasMoreTokens()) {
                    synchronized (voices) {
                        String nickname = modes.nextToken();
                        log.debug((adding ? "Voiced " : "Devoiced ") + nickname);
                        if (adding)
                            addToMap(voices, message.getChannel(), nickname);
                        else
                            delFromMap(voices, message.getChannel(), nickname);
                    }
                }
            }
        }
    }

    private void setChannelKey(String channel, String newKey) {
        StringBuffer newChannels = new StringBuffer();
        StringTokenizer tokens = new StringTokenizer(getConfig().get(parent.getName() + ",channels"), ",");
        while (tokens.hasMoreTokens()) {
            String chan = tokens.nextToken().trim();
            if (chan.equals(channel) && (newKey != null)) {
                newChannels.append(chan).append(' ').append(newKey);
            } else if (chan.startsWith(channel + " ")) {
                if (newKey == null)
                    newChannels.append(channel);
                else
                    newChannels.append(channel).append(' ').append(newKey);
            } else {
                newChannels.append(chan);
            }
            if (tokens.hasMoreTokens()) newChannels.append(',');
        }
        getConfig().put(parent.getName() + ",channels", newChannels.toString());
    }

    private void rejoinChannels(boolean reschedule) {
        List channels = new ArrayList(), passwords = new ArrayList();
        StringTokenizer tokens = new StringTokenizer(getConfig().get(parent.getName() + ",channels"), ",");
        while (tokens.hasMoreTokens()) {
            String chan = tokens.nextToken().trim();
            if ((chan.indexOf(' ') > -1) && !occupants.containsKey(chan.substring(0, chan.indexOf(' ')))) {
                channels.add(chan.substring(0, chan.indexOf(' ')));
                passwords.add(chan.substring(chan.indexOf(' ') + 1));
            } else if ((chan.indexOf(' ') == -1) &&!occupants.containsKey(chan)) {
                channels.add(chan);
            }
        }
        // Now form the message
        StringBuffer joinList = new StringBuffer();
        for (Iterator i = channels.iterator(); i.hasNext();) {
            String channel = (String)i.next();
            joinList.append(channel);
            if (i.hasNext()) joinList.append(',');
        }
        if (!passwords.isEmpty()) joinList.append(' ');
        for (Iterator i = passwords.iterator(); i.hasNext();) {
            String password = (String)i.next();
            joinList.append(password);
            if (i.hasNext()) joinList.append(',');
        }
        if (joinList.length() > 0) send(new JoinOut(joinList.toString()));
        if (reschedule) getTimer().schedule(rejoinTask, REJOIN_DELAY);
    }

    public void received(ChatMessageIn message) {
        if (!message.isCommand()) return;
        AuthModule auth;
        try {
            auth = (AuthModule)getModule("auth");
        } catch (NoSuchModuleException e) {
            log.warn("Requires auth module", e);
            return;
        }
        if ("!rejoin".equals(message.getCommand()) &&
                auth.isAdmin(message.getFromWithHost(), parent.getNetwork(), null)) {
            rejoinChannels(false);
        }
    }

    public boolean shouldBeOn(String channel) {
        List channels = new ArrayList();
        StringTokenizer tokens = new StringTokenizer(getConfig().get(parent.getName() + ",channels"), ",");
        while (tokens.hasMoreTokens()) {
            String chan = tokens.nextToken().trim();
            if (chan.indexOf(' ') > -1) {
                channels.add(chan.substring(0, chan.indexOf(' ')));
            } else {
                channels.add(chan);
            }
        }
        for (Iterator i = channels.iterator(); i.hasNext();) {
            String chan = (String)i.next();
        }
        return channels.contains(channel);
    }

    private void generateStats() {
        DbModule db;
        try {
            db = (DbModule)getModule("db");
        } catch (NoSuchModuleException e) {
            log.warn("Stats require DB module");
            return;
        }
        int chanCount = occupants.size();
        String[] chans = new String[chanCount];
        int[] occupantCount = new int[chanCount];
        int count = 0;
        for (Iterator i = occupants.keySet().iterator(); i.hasNext();) {
            String channel = (String)i.next();
            Set names = (Set)occupants.get(channel);
            chans[count] = channel;
            occupantCount[count] = names.size();
            count++;
        }
        db.updateChannelStats(chans, occupantCount);
        startStatsTimer();
    }

    private void startStatsTimer() {
        getTimer().schedule(new Runnable() {
            public void run() {
                generateStats();
            }
        }, STATS_UPDATE_DELAY);
    }
}
