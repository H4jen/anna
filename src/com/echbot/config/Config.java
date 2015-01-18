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

import com.echbot.GroupTimer;
import org.apache.log4j.Logger;

import java.io.*;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;

/**
 * Class storing all the data for a bot. Saves state and settings between
 * runs.
 * @author Chris Pearson
 * @version $Id: Config.java,v 1.17 2003/09/29 20:24:31 chris Exp $
 */
public class Config
{
    private static final Logger log = Logger.getLogger(Config.class);
    private static final String CONFIG_FILENAME = "echbot.cfg";
    private static final int AUTOSAVE_INTERVAL = 60000;
    private final Properties defaults = new Properties();
    private final GroupTimer timer = new GroupTimer();
    private Properties config;
    private boolean changed = false;

    /**
     * Creates a new config object. The current config is loaded, or if none
     * exists then a fresh one is created.
     * @throws IOException if there is an error loading or creating the config
     */
    public Config() throws IOException {
        File file = new File(CONFIG_FILENAME);
        if (file.exists() && file.canRead()) {
            config = new Properties();
            config.load(new BufferedInputStream(new FileInputStream(file)));
            log.info("Loaded config file: " + file.getPath());
        } else {
            config = createConfig();
        }
        final Runnable saveTask = new Runnable()
        {
            public void run() {
                save();
                timer.schedule(this, AUTOSAVE_INTERVAL);
            }
        };
        timer.schedule(saveTask, AUTOSAVE_INTERVAL);
    }

    private Properties createConfig() throws IOException {
        Properties properties = new Properties();
        properties.put("servers,quakenet", "se.quakenet.org");
        properties.put("modules", "admin,auth,bans,channels,connect,gamelookup,pickup,qauth");
        properties.put("bot1,network", "quakenet");
        properties.put("bot1,channels", "#qlpickup.se");
        properties.put("bot1,nickname", "ra3bot");

        File file = new File(CONFIG_FILENAME);
        FileOutputStream configFile = new FileOutputStream(file);
        BufferedOutputStream bos = new BufferedOutputStream(configFile);
        properties.store(bos, "Automatically generated echbot.cfg");
        configFile.close();
        log.info("Written new config file: " + file.getPath());
        return properties;
    }

    /**
     * Checks to see if the config has the given element.
     * @param key unique reference of the config key
     * @return true if the config contains an item by this name, false if not
     */
    public synchronized boolean containsKey(Object key) {
        return config.containsKey(key);
    }

    /**
     * Return the value associated with the given config key, or null if there
     * is no element referenced by the key.
     * @param key unique reference of the config key
     * @return the value associated with the given key, or null if the given key
     * does not exist in the config
     */
    public synchronized String get(String key) {
        return config.getProperty(key);
    }

    /**
     * Saves the given value, referenced by the given key.
     * @param key unique reference of the config key
     * @param value the value to be associated with the given key
     */
    public synchronized void put(String key, String value) {
        changed = true;
        config.setProperty(key, value);
    }

    /**
     * Get the set of all keys in the config object. Should synchronize this
     * on the config object.
     * @return the <code>Set</code> of all keys defined in the config
     */
    public synchronized Set keySet() {
        return config.keySet();
    }

    /**
     * Remove a key from the config, along with its associated value.
     * @param key unique reference of the config key
     */
    public synchronized void remove(String key) {
        changed = config.containsKey(key);
        config.remove(key);
    }

    private final synchronized void save() {
        if (!changed) return;
        changed = false;
        try {
            File file = new File(CONFIG_FILENAME);
            FileOutputStream configFile = new FileOutputStream(file);
            BufferedOutputStream bos = new BufferedOutputStream(configFile);

            Properties nonVars = new Properties();
            nonVars.putAll(config);
            Properties theVars = new Properties();
            theVars.putAll(nonVars);
            for (Iterator i = nonVars.keySet().iterator(); i.hasNext();) {
                final String key = (String)i.next();
                if (defaults.containsKey(key) && defaults.getProperty(key).equals(nonVars.get(key))) {
                    i.remove();
                    theVars.remove(key);
                } else if (key.startsWith("vars,")) {
                    i.remove();
                } else {
                    theVars.remove(key);
                }
            }
            nonVars.store(bos, "Automatically generated echbot.cfg");
            bos.write('\n');
            theVars.store(bos, "Everything below are variables");

            configFile.close();
            log.debug("Autosaved configuration");
        } catch (IOException e) {
            log.warn("Error autosaving config file", e);
        }
    }

    /**
     * Object destructor. Saves the configuration file.
     * @throws Throwable if something goes wrong?
     */
    protected void finalize() throws Throwable {
        save();
        super.finalize();
    }

    /**
     * Set the given default - any defaults will not be saved to the config.
     * @param key
     * @param value
     */
    public synchronized void setDefault(String key, String value) {
        boolean reset = !containsKey(key) || get(key).equals(defaults.get(key));
        defaults.setProperty(key, value);
        if (reset) put(key, value);
    }
}
