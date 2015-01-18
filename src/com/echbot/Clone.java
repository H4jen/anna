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
package com.echbot;

import com.echbot.config.Config;
import com.echbot.config.ConfigUtils;
import com.echbot.messages.InboundMessage;
import com.echbot.messages.MessageParser;
import com.echbot.messages.OutboundMessage;
import com.echbot.messages.in.PingIn;
import com.echbot.messages.in.SystemIn;
import com.echbot.messages.in.UnknownIn;
import com.echbot.messages.out.NickOut;
import com.echbot.messages.out.PongOut;
import com.echbot.messages.out.QuitOut;
import com.echbot.messages.out.UserOut;
import com.echbot.sockets.SocketCallback;
import com.echbot.sockets.TcpConnection;
import org.apache.log4j.Logger;

import java.net.InetSocketAddress;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;

/**
 * @author Chris Pearson
 * @version $Id: Clone.java,v 1.20 2003/09/22 23:02:12 chris Exp $
 */
class Clone implements SocketCallback, UserModuleInterface
{
    private static final Logger log = Logger.getLogger(Clone.class);
    private static final char NICK_SUFFIX_CHAR = '-';
    private static final int RPL_ENDOFMOTD = 376;
    private static final int ERR_NOMOTD = 422;
    private static final int ERR_NONICKNAMEGIVEN = 431;
    private static final int ERR_ERRONEUSNICKNAME = 432;
    private static final int ERR_NICKNAMEINUSE = 433;
    private static final int ERR_NICKCOLLISION = 436;
    private static final int BASE_RECONNECT_DELAY = 5000;
    private static final int THROTTLED_RECONNECT_DELAY = 30000;
    private final String name, network;
    private final Config config;
    private final InboundQueue inboundQueue;
    private final List nickUntried = new ArrayList();
    private final List nickTried = new ArrayList();
    private final StringBuffer suffix = new StringBuffer();
    private GroupTimer reconnectTimer = new GroupTimer();
    private GroupTimer moduleTimer;
    private TcpConnection socket;
    private ModuleSet modules;
    private String nickname = "?";
    private int reconnectDelay = BASE_RECONNECT_DELAY;
    private boolean terminated = false;

    Clone(String name, Config config, InboundQueue queue) {
        this.name = name;
        this.config = config;
        this.network = config.get(name + ",network");
        this.inboundQueue = queue;
        log.info("Starting clone: " + name);
    }

    ModuleSet getModules() {
        return modules;
    }

    public String getNickname() {
        return modules == null ? null : nickname;
    }

    GroupTimer getTimer() {
        return moduleTimer;
    }

    private static InetSocketAddress getAddress(String hostname) throws UnknownHostException {
        if (hostname == null) return null;
        int split = hostname.indexOf(':');
        int port;
        if (split == -1) {
            // no colon at all
            port = 6667;
        } else if (hostname.length() > split + 1) {
            // colon is before the end character
            try {
                port = Integer.parseInt(hostname.substring(split + 1));
            } catch (NumberFormatException e) {
                log.info("Non-numeric port number in " + hostname + " ignored");
                port = 6667;
            }
        } else {
            // colon is the end character
            hostname = hostname.substring(0, split);
            port = 6667;
        }
        InetAddress[] addresses = InetAddress.getAllByName(hostname);
        InetAddress address = addresses[(int) (Math.random() * (double) addresses.length)];
        return new InetSocketAddress(address, port);
    }

    public void connect() {
        if (socket != null) socket.close();
        String bindAddress = config.get(name + ",bindto");
        InetSocketAddress bindto =
                (bindAddress == null) || bindAddress.length() == 0 ?
                new InetSocketAddress(0) :
                new InetSocketAddress(bindAddress, 0);
        try {
            String serverIp = ConfigUtils.getRandomServer(config, network);
            log.info("Connecting to " + serverIp);
            socket = new TcpConnection(this, getAddress(serverIp), bindto);
        } catch (UnknownHostException e) {
            log.error("No such network: " + e.getMessage(), e);
            socket.close();
        } catch (ConfigUtils.NoSuchNetworkException e) {
            log.error("No such network: " + e.getMessage(), e);
            socket.close();
        }
    }

    private String getConfigVar(String varName, String defaultValue) {
        String var = getName() + "," + varName;
        if (config.containsKey(var)) {
            return config.get(var);
        }
        if (config.containsKey(varName)) {
            return config.get(varName);
        }
        return defaultValue;
    }

    public void connected() {
        log.debug(name + " connected (" + socket + ")");
        resetNick();
        final String ident = getConfigVar("ident", "b0t");
        final String local = getConfigVar("bindto", "local");
        final String remote = getNetwork();
        final String whois = getConfigVar("whois", "Superboten");
        socket.getTimer().schedule(new Runnable()
        {
            public void run() {
                send(new UserOut(ident, local, remote, whois), 0);
                send(new NickOut(nextNick()), 0);
            }
        }, 500);
    }

    public void disconnected() {
        // any timers will now have been cancelled
        log.debug("disconnected");
        if (modules != null) modules.terminate();
        modules = null;
        if (terminated) return;
        reconnectTimer.schedule(new Runnable()
        {
            public void run() {
                connect();
            }
        }, reconnectDelay);
    }

    public void gotLine(String line) {
        log.debug("IN: " + line);
        InboundMessage message = MessageParser.parseMessage(line);
        if (modules == null) {
            // still connecting
            if (message instanceof SystemIn) {
                SystemIn in = (SystemIn)message;
                if (isNickFailure(in)) {
                    // couldn't change nick, try another one
                    send(new NickOut(nextNick()), 0);
                    return;
                } else if (isConnectMessage(in)) {
                    // connected properly, load the modules for this clone
                    reconnectDelay = BASE_RECONNECT_DELAY;
                    if (moduleTimer != null) moduleTimer.cancel();
                    moduleTimer = new GroupTimer(socket.getTimer());
                    modules = new ModuleSet(this);
                    modules.instantiateModules(null);
                    return;
                }
            } else if ((message instanceof UnknownIn) &&

                    (((UnknownIn)message).getLine().indexOf("Your host is trying to (re)connect too fast") > -1)) {
                reconnectDelay += THROTTLED_RECONNECT_DELAY;
            }
        }
        if (message instanceof PingIn) {
            send(new PongOut(((PingIn)message).getPingCode()), 1);
            return;
        }
        if ((message instanceof SystemIn) && (ERR_NICKCOLLISION == ((SystemIn)message).getNumber())) {
            send(new QuitOut("Nickname collision, reconnecting"), 1);
            return;
        }
        if (message instanceof UnknownIn) {
            String msgLine = ((UnknownIn)message).getLine();
            if (msgLine.indexOf(' ') == msgLine.indexOf(" KILL " + nickname + " ")) {
                // we've just been killed!
                socket.close();
                return;
            }
        }
        inboundQueue.add(message, this);
    }

    /**
     * Disconnect from IRC server.
     */
    public void terminate() {
        terminated = true;
        if ((socket != null) && socket.isConnected()) send(new QuitOut("Clone terminated!"), 100);
    }

    private String nextNick() {
        if (nickUntried.isEmpty()) {
            nickUntried.addAll(nickTried);
            nickTried.clear();
            suffix.append(NICK_SUFFIX_CHAR);
        }
        String next = (String)nickUntried.remove(0);
        nickTried.add(next);
        nickname = next + suffix.toString();
        return nickname;
    }

    private void resetNick() {
        suffix.setLength(0);
        nickTried.clear();
        nickUntried.clear();
        StringTokenizer tokens = new StringTokenizer(getConfigVar("nickname", "echbot"), ", ");
        while (tokens.hasMoreTokens()) {
            nickUntried.add(tokens.nextToken());
        }
        if (nickUntried.isEmpty()) {
            nickUntried.add("echbot");
        }
    }

    private static final boolean isConnectMessage(SystemIn message) {
        switch (message.getNumber()) {
            case RPL_ENDOFMOTD:
            case ERR_NOMOTD:
                return true;
        }
        return false;
    }

    private static final boolean isNickFailure(SystemIn message) {
        switch (message.getNumber()) {
            case ERR_NONICKNAMEGIVEN:
            case ERR_ERRONEUSNICKNAME:
            case ERR_NICKNAMEINUSE:
                return true;
        }
        return false;
    }

    /**
     * @return unique id name for the clone
     */
    public String getName() {
        return name;
    }

    /**
     * Register the given module to receive notification of events of type messageClass.
     * @param messageClass
     * @param module
     */
    public void register(Class messageClass, UserModule module) {
        modules.register(messageClass, module);
    }

    /**
     * Register the given module to receive user events with the given id.
     * @param id
     * @param module
     */
    public void registerForEvent(int id, UserModule module) {
        modules.registerForEvent(id, module);
    }

    /**
     * @return the current config object
     */
    public Config getConfig() {
        return config;
    }

    /**
     * @return name of the network the clone should be connecting to
     */
    public String getNetwork() {
        return network;
    }

    /**
     * Send the given message to the IRC server with the given priority. The greater the priority, the sooner it'll be
     * sent to the IRC server if there are other messages queued.
     * @param message
     * @param priority
     */
    public void send(OutboundMessage message, int priority) {
        socket.send(message, priority);
    }

    /**
     * Trigger the given user event.
     * @param id
     * @param attachment
     */
    public void triggerEvent(int id, Object attachment) {
        modules.triggerEvent(id, attachment);
    }

    /**
     * Trigger the given user event for all modules of every connected clone.
     * @param id
     * @param attachment
     */
    public void triggerGlobalEvent(int id, Object attachment) {
        ModuleSet.triggerGlobalEvent(id, attachment);
    }

    /**
     * Reload all modules.
     */
    public void reloadModules() {
        getTimer().schedule(new Runnable()
        {
            public void run() {
                if (moduleTimer != null) moduleTimer.cancel();
                moduleTimer = new GroupTimer(socket.getTimer());
                ModuleSet.reloadModules(getConfig().get("modules"));
            }
        }, 500);
    }

    /**
     * @param name module to add to the module set
     */
    public void addModule(final String name) {
        Set modules = new HashSet();
        StringTokenizer moduleTokens = new StringTokenizer(config.get("modules"), " ,");
        while (moduleTokens.hasMoreTokens()) {
            modules.add(moduleTokens.nextToken());
        }
        log.debug("Checking if " + name + " is in " + config.get("modules"));
        if (modules.contains(name)) return;
        modules.add(name);
        // Recreate the modules string
        StringBuffer newModules = new StringBuffer();
        for (Iterator i = modules.iterator(); i.hasNext();) {
            String moduleName = (String)i.next();
            newModules.append(moduleName);
            if (i.hasNext()) newModules.append(',');
        }
        config.put("modules", newModules.toString());
        // Now actually add the module
        getTimer().schedule(new Runnable()
        {
            public void run() {
                ModuleSet.addModule(name);
            }
        }, 500);
    }

    /**
     * @param name which module to remove
     */
    public void removeModule(final String name) {
        log.debug("Removing module " + name + " from " + config.get("modules"));
        Set modules = new HashSet();
        StringTokenizer moduleTokens = new StringTokenizer(config.get("modules"), " ,");
        while (moduleTokens.hasMoreTokens()) {
            modules.add(moduleTokens.nextToken());
        }
        if (!modules.contains(name)) return;
        modules.remove(name);
        // Recreate the modules string
        StringBuffer newModules = new StringBuffer();
        for (Iterator i = modules.iterator(); i.hasNext();) {
            String moduleName = (String)i.next();
            newModules.append(moduleName);
            if (i.hasNext()) newModules.append(',');
        }
        config.put("modules", newModules.toString());
        log.debug("new module string is " + newModules.toString());
        // Now actually remove the module
        getTimer().schedule(new Runnable()
        {
            public void run() {
                ModuleSet.removeModule(name);
            }
        }, 500);
    }

    /**
     * @param name
     * @return a reference to the given module, or null if it is not loaded
     */
    public UserModule getModule(String name) throws UserModule.NoSuchModuleException {
        UserModule module = modules.getModule(name);
        if (module == null) throw new UserModule.NoSuchModuleException(name + " not found");
        return module;
    }
}

