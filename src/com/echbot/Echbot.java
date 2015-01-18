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
import org.apache.log4j.*;
import org.apache.log4j.varia.ReloadingPropertyConfigurator;
import org.apache.log4j.helpers.NullEnumeration;

import java.io.IOException;
import java.io.File;
import java.util.*;
import java.net.URL;

/**
 * Main echbot entry point.
 * @author Chris Pearson
 * @version $Id: Echbot.java,v 1.10 2003/09/24 21:52:53 chris Exp $
 */
public class Echbot
{
    private static final Map clones = new HashMap();
    private static final InboundQueue queue = new InboundQueue();
    private static Config config;

    public static void main(String[] args) throws IOException {
        // use lib/log4j.properties if available
        URL res = Thread.currentThread().getContextClassLoader().getResource("lib/log4j.properties");
        if (res != null) PropertyConfigurator.configure(res);
        // use log4j.properties in current dir if available
        File localConfig = new File("log4j.properties");
        if (localConfig.exists()) PropertyConfigurator.configure(localConfig.toURL());
        // start a bot!
        run();
    }

    private static void run() throws IOException {
        // find all the cloneNames that need to be started, and do it
        config = new Config();
        Set cloneNames = ConfigUtils.getCloneNames(config);
        for (int i = 0; i < 4; i++) {
            Thread next = new Thread(new Worker(queue));
            next.setDaemon(true);
            next.start();
        }
        synchronized (clones) {
            for (Iterator i = cloneNames.iterator(); i.hasNext();) {
                String name = (String)i.next();
                Clone newClone = new Clone(name, config, queue);
                clones.put(name, newClone);
                newClone.connect();
				try { Thread.sleep(20000); } catch (Exception e) { }
            }
        }
        // Just exit, and wait for the non-daemon threads to finish
    }

    public static List cloneList() {
        List res = new ArrayList();
        int count = 1;
        synchronized (clones) {
            for (Iterator i = clones.values().iterator(); i.hasNext();) {
                Clone clone = (Clone)i.next();
                res.add("Clone " + (count++) + "(" + clone.getName() + ") on " +
                        clone.getNetwork() + " as " + clone.getNickname());
            }
        }
        return res;
    }

    public static void killClone(String name) {
        synchronized (clones) {
            Clone toKill = getClone(name, false);
            if (toKill != null) {
                toKill.terminate();
                clones.remove(toKill.getName());
            }
        }
    }

    private static Clone getClone(String name, boolean remove) {
        synchronized (clones) {
            for (Iterator i = clones.values().iterator(); i.hasNext();) {
                Clone clone = (Clone)i.next();
                if (clone.getName().equals(name)) {
                    if (remove) i.remove();
                    return clone;
                }
            }
        }
        return null;
    }

    public static void startClones() {
        Set cloneNames = ConfigUtils.getCloneNames(config);
        synchronized (clones) {
            for (Iterator i = cloneNames.iterator(); i.hasNext();) {
                String name = (String)i.next();
                if (!clones.containsKey(name)) {
                    Clone newClone = new Clone(name, config, queue);
                    clones.put(name, newClone);
                    newClone.connect();
                }
            }
        }
    }
}
