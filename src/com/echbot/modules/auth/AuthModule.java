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
package com.echbot.modules.auth;

import com.echbot.UserModule;
import com.echbot.UserModuleInterface;
import com.echbot.config.ConfigUtils;
import com.echbot.messages.in.ChatMessageIn;
import com.echbot.messages.in.SystemIn;
import com.echbot.messages.out.ChatMessageOut;
import com.echbot.messages.out.WhoisOut;
import com.echbot.modules.channels.ChannelsModule;
import org.apache.log4j.Logger;

import java.util.Iterator;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * @author Chris Pearson
 * @version $Id: AuthModule.java,v 1.20 2003/08/27 12:42:37 chris Exp $
 */
public class AuthModule extends UserModule
{
    private static final Logger log = Logger.getLogger(AuthModule.class);
    private static final int RPL_WHOISACCOUNT = 330;
    private static final int SECS_IN_AN_HOUR = 3600;
    private final Map authedUsers = new ExpiringHashMap(SECS_IN_AN_HOUR);
    private final Map pendingQuakenet = new ExpiringHashMap(2000); // 2secs
    private int lostUserEvent;

    public AuthModule(UserModuleInterface parent) {
        super(parent);
        lostUserEvent = getUserEventId("channels.lostuser");
        parent.registerForEvent(lostUserEvent, this);
        register(ChatMessageIn.class);
        register(SystemIn.class);
        setDefault("admins", "quakenet_hajen");
        if (getConfig().get("user,hajen") == null) getConfig().put("user,hajen", "????????");
    }

    public void initialise(Object state) {
        if (state != null) {
            Map[] saved = (Map[])state;
            authedUsers.putAll(saved[0]);
            pendingQuakenet.putAll(saved[1]);
        }
    }

    public Object getState() {
        return new Map[]{authedUsers, pendingQuakenet};
    }

    public void received(ChatMessageIn message) {
        if (!message.isCommand()) return;
        try {
            if (message.getCommand().equals("!auth") &&
                    ((ChannelsModule)getModule("channels")).canSee(message.getFrom())) {
                authUser(message);
            } else if (message.getCommand().equals("!whoami")) {
                String authed = getAuthedAs(message.getFromWithHost());
                if (authed == null) {
                    send(new ChatMessageOut(message.getFrom(), "I don't know who you are!", false));
                } else {
                    send(new ChatMessageOut(message.getFrom(), "I know you as\002 " + authed, false));
                }
            } else if (message.getCommand().equals("!hello") && !"quakenet".equals(parent.getNetwork())) {
                String username = message.getFrom().toLowerCase();
                StringTokenizer tokens = new StringTokenizer(message.getArguments());
                if (!tokens.hasMoreTokens()) {
                    send(new ChatMessageOut(message.getFrom(), "Usage: !hello <password>  - Creates you a new account on the bot with your current nickname", false));
                } else if (getConfig().get("user," + username) != null) {
                    send(new ChatMessageOut(message.getFrom(), "Sorry, someone already has the username " + username + ", please change nick and try again", false));
                } else {
                    String originalPass = tokens.nextToken();
                    getConfig().put("user," + username, UnixCrypt.crypt(originalPass));
                    send(new ChatMessageOut(message.getFrom(), "\0036Thanks! Your account has been created with the username\00310 " + username, false));
                    send(new ChatMessageOut(message.getFrom(), "\00310To auth with the bot, type\0035 /msg " + parent.getNickname() + " !auth " + username + " " + originalPass, false));
                    send(new ChatMessageOut(message.getFrom(), "\00310To change your password, auth first then type\0035 /msg " + parent.getNickname() + " !newpass " + originalPass + " <newpass>", false));
                }
            } else if (message.getCommand().equals("!newpass") && !"quakenet".equals(parent.getNetwork())) {
                // todo
            }

        } catch (NoSuchModuleException e) {
            log.warn("Auth module requires channels module", e);
        }
    }

    private void authUser(ChatMessageIn message) {
        if (parent.getNetwork().equals("quakenet")) {
            synchronized (pendingQuakenet) {
                pendingQuakenet.put(message.getFrom(), message.getFromWithHost());
            }
            send(new WhoisOut(message.getFrom()));
        } else {
            StringTokenizer args = new StringTokenizer(message.getArguments());
            if (args.countTokens() != 2) {
                send(new ChatMessageOut(message.getFrom(), "\00310Usage: /msg " + parent.getNickname() + " !auth <username> <password>", false));
            } else {
                String username = args.nextToken().toLowerCase();
                String password = args.nextToken();
                String varname = "user," + username;
                if (getConfig().get(varname) == null) {
                    send(new ChatMessageOut(message.getFrom(), "\00310Use !hello to create an account first!", false));
                } else if (UnixCrypt.matches(getConfig().get(varname), password)) {
                    authedUsers.put(message.getFromWithHost(), username);
                    send(new ChatMessageOut(message.getFrom(), "You are now authed as " + username, false));
                } else {
                    send(new ChatMessageOut(message.getFrom(), "\00310Usage: /msg " + parent.getNickname() + " !auth <username> <password>", false));
                }
            }
        }
    }

    public void received(SystemIn message) {
        if (parent.getNetwork().equals("quakenet") && (message.getNumber() == RPL_WHOISACCOUNT)) {
            try {
                final StringTokenizer tokens = new StringTokenizer(message.getMessage());
                final String person = tokens.nextToken();
                synchronized (pendingQuakenet) {
                    if (pendingQuakenet.containsKey(person)) {
                        final String authedAs = getQnetAuth(tokens);
                        if (((ChannelsModule)getModule("channels")).canSee(person)) {
                            synchronized (authedUsers) {
                                authedUsers.put(pendingQuakenet.get(person), authedAs);
                                pendingQuakenet.remove(person);
                            }
                            send(new ChatMessageOut(person, "You are now authed as " + authedAs, false));
                        }
                    }
                }
            } catch (NoSuchModuleException e) {
                log.warn("Auth module requires channels module", e);
            }
        }
    }

    private static final String getQnetAuth(StringTokenizer tokens) {
        final String first = tokens.nextToken();
        if (!first.equals(":is")) return "quakenet_" + first.toLowerCase();
        tokens.nextToken(); // authed
        tokens.nextToken(); // as
        return "quakenet_" + tokens.nextToken().toLowerCase();
    }

    public String getAuthedAs(String hostmask) {
        synchronized (authedUsers) {
            if (authedUsers.containsKey(hostmask)) {
                return (String)authedUsers.get(hostmask);
            }
        }
        return null;
    }

    public void userEvent(int id, Object attachment) {
        if (id == lostUserEvent) {
            final String searchFor = (String)attachment + "!";
            synchronized (pendingQuakenet) {
                for (Iterator i = pendingQuakenet.keySet().iterator(); i.hasNext();) {
                    final String nick = (String)i.next();
                    if (nick.equals(attachment)) i.remove();
                }
            }
            synchronized (authedUsers) {
                for (Iterator i = authedUsers.keySet().iterator(); i.hasNext();) {
                    final String hostmask = (String)i.next();
                    if (hostmask.startsWith(searchFor)) i.remove();
                }
            }
        }
    }

    public boolean permittedTo(String hostmask, String varName, String chan) {
        String channel = (chan == null) ? null : chan.toLowerCase();
        log.debug("Checking whether " + hostmask + " is allowed to do " + varName + " on " + channel);
        final int hostmaskEnd = hostmask.indexOf('!');
        final String permissions = ConfigUtils.getVar(getConfig(), varName, parent.getNetwork(), channel);
        try {
            log.debug("Checking " + permissions);
            if (permissions == null) return false;
            final ChannelsModule channels = (ChannelsModule)getModule("channels");
            final StringTokenizer tokens = new StringTokenizer(permissions.toLowerCase(), ", ");
            while (tokens.hasMoreTokens()) {
                final String next = tokens.nextToken();
                if ("admins".equals(next)) {
                    if (isAdmin(hostmask, parent.getNetwork(), channel)) return true;
                } else if ("opped".equals(next)) {
                    if (channels.isOpped(hostmask.substring(0, hostmaskEnd), channel)) return true;
                } else if ("voiced".equals(next)) {
                    if (channels.isVoiced(hostmask.substring(0, hostmaskEnd), channel)) return true;
                } else if ("all".equals(next)) {
                    return true;
                } else {
                    return false;
                }

            }
        } catch (NoSuchModuleException e) {
            log.warn("Auth module requires channels module", e);
        }
        return false;
    }

    public boolean isAdmin(String hostmask, String network, String channel) {
        log.debug("Checking if " + hostmask + " is an admin on " + network + ":" + channel);
        final String admins = getAdmins(network, channel);
        final String authedAs = getAuthedAs(hostmask);
        if (authedAs == null) return false;
        final StringTokenizer adminTokens = new StringTokenizer(admins, ", ");
        while (adminTokens.hasMoreTokens()) {
            if (authedAs.equalsIgnoreCase(adminTokens.nextToken())) return true;
        }
        log.debug("Couldn't find " + authedAs + " in " + admins);
        return false;
    }

    private String getAdmins(String network, String channel) {
        final StringBuffer admins = new StringBuffer();
        String key = "vars,admins";
        if (getConfig().containsKey(key)) admins.append(getConfig().get(key)).append(',');
        if (network != null) {
            key = "vars," + network + ",admins";
            if (getConfig().containsKey(key)) admins.append(getConfig().get(key)).append(',');
            if (channel != null) {
                key = "vars," + network + "," + channel + ",admins";
                if (getConfig().containsKey(key)) admins.append(getConfig().get(key)).append(',');
            }
        }
        return admins.toString();
    }
}
