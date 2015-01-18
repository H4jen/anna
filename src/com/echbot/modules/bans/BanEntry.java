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

import com.echbot.GroupTimer;
import com.echbot.UserModule;
import com.echbot.messages.out.ChatMessageOut;
import com.echbot.messages.out.KickOut;
import com.echbot.messages.out.ModeOut;
import com.echbot.modules.channels.ChannelsModule;
import org.apache.log4j.Logger;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.StringTokenizer;


/**
 * @author Chris Pearson
 * @version $Id: BanEntry.java,v 1.22 2003/09/24 00:31:21 chris Exp $
 */
public class BanEntry
{
    private static final Logger log = Logger.getLogger(BanEntry.class);
    private static final int SECS_IN_A_WEEK = 604800;
    private static final int SECS_IN_A_DAY = 86400;
    private static final int SECS_IN_AN_HOUR = 3600;
    private static final int SECS_IN_A_MIN = 60;
    private static final long MIN_REMOVE_DELAY = 10000;
    private final Set bans = new HashSet();
    private String nickname, channel, reason, banner;
    private long banlength;
    private long expiresAt;
    private BansModule module;
    private GroupTimer removeTimer;
    private String qnetAuth, ident, host;
    private boolean expired = false;

    private BanEntry(BansModule module) {
        this.removeTimer = new GroupTimer(module.getTimer());
    }

    public BanEntry(String channel, String nickname, long banlength, String reason, String banner, BansModule module) {
        this.nickname = nickname;
        this.channel = channel.toLowerCase();
        this.reason = reason.replace('#', '*');
        this.banlength = (banlength < 60) ? 60 : banlength;
        this.expiresAt = System.currentTimeMillis() + this.banlength * 1000;
        this.banner = banner;
        this.module = module;
        this.removeTimer = new GroupTimer(module.getTimer());
    }

    public String getNickname() {
        return nickname;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public String getChannel() {
        return channel;
    }

    public long getBanlength() {
        return banlength;
    }

    public String getBanner() {
        return banner;
    }

    public String getReason() {
        return reason;
    }

    public boolean isExpired() {
        return expired;
    }

    public void setQnetAuth(String qnetAuth) {
        this.qnetAuth = qnetAuth;
    }

    public void setIdent(String ident) {
        this.ident = ident;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BanEntry)) return false;

        final BanEntry ban = (BanEntry)o;

        if (!channel.equals(ban.channel)) return false;
        if (!nickname.equals(ban.nickname)) return false;

        return true;
    }

    public int hashCode() {
        int result;
        result = nickname.hashCode();
        result = 29 * result + channel.hashCode();
        return result;
    }

    public void setBan() {
        if (module.parent.getNickname().equalsIgnoreCase(nickname)) {
            log.warn("Tried to ban ourself in " + channel);
            startRemoveTimer();
            return;
        }
        if (qnetAuth != null) {
            module.send(new ChatMessageOut("Q", "chanlev " + channel + " #" + qnetAuth + " +b", true));
        }
        try {
            ChannelsModule channelsModule = (ChannelsModule)module.getModule("channels");
            if (!channelsModule.isOpped(module.parent.getNickname(), channel)) {
                return;
            }
        } catch (UserModule.NoSuchModuleException e) {
            log.info("BansModule would like to use channels module!", e);
            return;
        }
        if (ident != null) {
            String banmask = "*!" + ident + "@" + getBanHost();
            synchronized (bans) {
                if (!bans.contains(banmask)) {
                    bans.add(banmask);
                    log.debug("Added " + banmask);
                }
                module.send(new ModeOut(channel, "+b " + banmask));
            }
        }
        module.send(new KickOut(channel, nickname, "[" + getDuration(banlength) + "] " + reason));
        startRemoveTimer();
    }

    private final String getBanHost() {
        if (host == null) return "*";
        int partCount = 0;
        try {
            StringBuffer sb = new StringBuffer();
            StringTokenizer tokens = new StringTokenizer(host, ".");
            partCount = tokens.countTokens();
            while (tokens.hasMoreTokens()) {
                int nextPart = Integer.parseInt(tokens.nextToken());
                if (tokens.hasMoreTokens()) {
                    sb.append(nextPart).append('.');
                } else {
                    sb.append('*');
                }
            }
            return sb.toString();
        } catch (NumberFormatException e) {
            // Don't change if it has less than 3 parts - i.e. @lamity.org
            if (partCount <= 2) return host;
            // Check for country codes and don't change - i.e. @elite.co.uk
            if ((partCount == 3) && (host.charAt(host.length() - 3) == '.')) return host;
            // Check for quakenet auths
            if (host.endsWith(".users.quakenet.org")) return host;
            int firstDot = host.indexOf('.');
            return "*" + host.substring(firstDot);
        }
    }

    static final String getDuration(long longago) {
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
        }
        if (days != 0) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(days).append("day");
            if (days != 1) sb.append('s');
        }
        if (hours != 0) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(hours).append("hour");
            if (hours != 1) sb.append('s');
        }
        if (mins != 0) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(mins).append("min");
            if (mins != 1) sb.append('s');
        }
        if (longago != 0) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(longago).append("sec");
            if (longago != 1) sb.append('s');
        }
        return sb.toString();
    }

    public void remove() {
        log.debug("Removing ban on " + nickname);
        expired = true;
        cancelTimer();
        ChannelsModule channelsModule;
        try {
            channelsModule = (ChannelsModule)module.getModule("channels");
            if (!channelsModule.shouldBeOn(channel)) {
                log.debug("Maybe not, we shouldn't be on that channel");
                return;
            }
            if (!channelsModule.isOpped(module.parent.getNickname(), channel)) {
                log.debug("Can't remove ban on " + nickname + " as we're not opped");
                startRemoveTimer();
                return;
            }
        } catch (UserModule.NoSuchModuleException e) {
            log.info("BansModule would like to use channels module!", e);
            return;
        }
        if (qnetAuth != null) {
            module.send(new ChatMessageOut("Q", "chanlev " + channel + " #" + qnetAuth + " -b", true));
        }
        synchronized (bans) {
            StringBuffer masks = new StringBuffer();
            StringBuffer bs = new StringBuffer();
            bs.append('-');
            int count = 0;
            for (Iterator i = bans.iterator(); i.hasNext();) {
                bs.append('b');
                masks.append(' ').append(i.next());
                if ((++count == 3) || !i.hasNext()) {
                    bs.append(masks);
                    module.send(new ModeOut(channel, bs.toString()));
                    bs.setLength(1);
                    masks.setLength(0);
                }
            }
        }
        module.removeBan(channel, nickname);
    }

    public void startRemoveTimer() {
        if (!removeTimer.isEmpty()) return;
        long removeDelay = expiresAt - System.currentTimeMillis();
        if (removeDelay < MIN_REMOVE_DELAY) {
            removeDelay = MIN_REMOVE_DELAY;
        }
        Runnable newTask = new Runnable()
        {
            public void run() {
                remove();
            }
        };
        removeTimer.schedule(newTask, removeDelay);
    }

    public String getConfigString() {
        StringBuffer sb = new StringBuffer();
        sb.append(banlength).append('#');
        sb.append(expiresAt).append('#');
        sb.append(banner).append('#');
        sb.append(qnetAuth).append('#');
        sb.append(ident).append('#');
        sb.append(host).append('#');
        synchronized (bans) {
            for (Iterator i = bans.iterator(); i.hasNext();) {
                String banmask = (String)i.next();
                sb.append(banmask).append('#');
            }
        }
        sb.append(reason);
        return sb.toString();
    }

    public static BanEntry parseConfigString(String configString, String channel, String nickname, BansModule module) {
        StringTokenizer tokens = new StringTokenizer(configString, "#");
        if (tokens.countTokens() < 6) return null;
        long banLength = Long.parseLong(tokens.nextToken());
        long expiresAt = Long.parseLong(tokens.nextToken());
        String banner = tokens.nextToken();
        String qnetAuth = tokens.nextToken();
        String ident = tokens.nextToken();
        String host = tokens.nextToken();

        log.debug("ban length " + banLength);
        log.debug("expires " + expiresAt);
        log.debug("banner is " + banner);
        log.debug("qnetAuth is " + qnetAuth);
        log.debug("ident is " + ident);
        log.debug("host is " + host);

        Set banmasks = new HashSet();
        String reason = "";
        while (tokens.hasMoreTokens()) {
            String next = tokens.nextToken();
            if (tokens.hasMoreTokens()) {
                banmasks.add(next);
                log.debug("Banmask: " + next);
            } else {
                reason = next;
                log.debug("Reason is " + reason);
            }
        }

        BanEntry ban = new BanEntry(module);
        ban.nickname = nickname;
        ban.channel = channel;
        ban.module = module;
        ban.banlength = banLength;
        ban.expiresAt = expiresAt;
        ban.banner = banner;
        ban.qnetAuth = "null".equals(qnetAuth) ? null : qnetAuth;
        ban.ident = "null".equals(ident) ? null : ident;
        ban.host = "null".equals(host) ? null : host;
        ban.bans.addAll(banmasks);
        ban.reason = reason;
        ban.startRemoveTimer();
        return ban;
    }

    private synchronized void cancelTimer() {
        if (removeTimer != null) {
            log.debug("Cancelling timer");
            removeTimer.cancel();
            removeTimer = new GroupTimer(module.getTimer());
        }
    }

    public void combineWith(BanEntry oldBan) {
        synchronized (bans) {
            synchronized (oldBan.bans) {
                bans.addAll(oldBan.bans);
                if (qnetAuth == null) qnetAuth = oldBan.qnetAuth;
                oldBan.cancelTimer();
            }
        }
    }
}
