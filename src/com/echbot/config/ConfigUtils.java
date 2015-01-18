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
package com.echbot.config;

import org.apache.log4j.Logger;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.StringTokenizer;

/**
 * A library of utility functions for use with <code>Config</code> objects.
 * @author Chris Pearson
 * @version $Id: ConfigUtils.java,v 1.12 2003/08/27 11:24:59 chris Exp $
 */
public final class ConfigUtils
{
    private static final Logger log = Logger.getLogger(ConfigUtils.class);

    public static final class NoSuchNetworkException extends Exception
    {
        public NoSuchNetworkException(String message) {
            super(message);
        }
    }

    private ConfigUtils() {
    }

    /**
     * Return the list of unique bot identifiers, one for each clone to be
     * started.
     * @param config <code>Config</code> object
     * @return a <code>Set</code> of unique <code>String</code> ids
     */
    public static Set getCloneNames(Config config) {
        final Set cloneNames = new HashSet();
        synchronized (config) {
            for (Iterator i = config.keySet().iterator(); i.hasNext();) {
                String key = (String)i.next();
                if (key.startsWith("bot") && (key.indexOf(',') != -1)) {
                    String cloneName = key.substring(0, key.indexOf(','));
                    if (config.containsKey(cloneName + ",network")) {
                        cloneNames.add(cloneName);
                    }
                }
            }
        }
        return cloneNames;
    }

    /**
     * Get a random server from the given network.
     * @param config <code>Config</code> object
     * @param network which IRC network the server should belong to
     * @return a random server from the given network
     * @throws NoSuchNetworkException if no servers have been defined in the
     * config object for the given network
     */
    public static String getRandomServer(Config config, String network) throws NoSuchNetworkException {
        if (network == null) return null;
        String servers = config.get("servers," + network);
        if (servers == null) {
            throw new NoSuchNetworkException("servers," + network + " needs to be defined");
        }
        StringTokenizer serverTokens = new StringTokenizer(servers, ", ");
        Set serverIps = new HashSet();
        while (serverTokens.hasMoreTokens()) {
            serverIps.add(serverTokens.nextToken());
        }
        Object[] ips = serverIps.toArray();
        log.debug("There are " + ips.length + " servers for " + network);
        if (ips.length == 0) {
            throw new NoSuchNetworkException("servers," + network + " is empty");
        }
        return (String)ips[(int)Math.floor(Math.random() * ips.length)];
    }

    /**
     * Retrieves the value of the given global variable.
     * @param config <code>Config</code> object
     * @param varname variable name
     * @return the value of the given variable, or null if it hasn't been
     * defined
     */
    public static String getVar(Config config, String varname) {
        final String key = "vars," + varname;
        if (config.containsKey(key)) {
            return config.get(key);
        }
        return null;
    }

    /**
     * Retrieves the value of the given network-wide variable.
     * @param config <code>Config</code> object
     * @param varname variable name
     * @param network which irc network the variable belongs to
     * @return the value of the given variable, or null if it hasn't been
     * defined
     */
    public static String getVar(Config config, String varname, String network) {
        if (network == null) return getVar(config, varname);
        final String key = "vars," + network + "," + varname;
        if (config.containsKey(key)) {
            return config.get(key);
        }
        return getVar(config, varname);
    }

    /**
     * Retrieves the value of the given channel variable.
     * @param config <code>Config</code> object
     * @param varname variable name
     * @param network which irc network the variable belongs to
     * @param channel which channel on the network the variable is being used in
     * @return the value of the given variable, or null if it hasn't been
     * defined
     */
    public static String getVar(Config config, String varname, String network, String channel) {
        if (channel == null) return getVar(config, varname, network);
        final String key = "vars," + network + "," + channel + "," + varname;
        if (config.containsKey(key)) {
            return config.get(key);
        }
        return getVar(config, varname, network);
    }

    /**
     * Retrieves the value of a given channel variable, without checking for
     * network or global variables. Returns the given default value for the
     * variable if it is not set.
     * @param config <code>Config</code> object
     * @param varname variable name
     * @param network which irc network the variable belongs to
     * @param channel which channel on the network the variable is being used in
     * @param defaultValue the value to return if the variable is not set
     * @return the value of the given variable, or defaultValue if it hasn't
     * been defined
     */
    public static String getChannelVar(Config config, String varname, String network, String channel, String defaultValue) {
        if ((channel == null) || (network == null)) return defaultValue;
        final String varVal = config.get("vars," + network + "," + channel + "," + varname);
        return (varVal == null) ? defaultValue : varVal;
    }

    /**
     * Sets the value of a given channel variable.
     * @param config <code>Config</code> object
     * @param varname variable name
     * @param network which irc network the variable belongs to
     * @param channel which channel on the network the variable is being used in
     */
    public static void setChannelVar(Config config, String varname, String varvalue, String network, String channel) {
        if ((network == null) || (channel == null)) {
            log.warn("setChannelVar called with network=" + network + " and channel=" + channel);
            return;
        }
        if (varvalue == null) {
            config.remove("vars," + network + "," + channel + "," + varname);
        } else {
            config.put("vars," + network + "," + channel + "," + varname, varvalue);
        }
    }
}
