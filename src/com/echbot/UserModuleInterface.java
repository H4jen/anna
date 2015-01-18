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
import com.echbot.messages.OutboundMessage;

/**
 * @author Chris Pearson
 * @version $Id: UserModuleInterface.java,v 1.4 2003/09/22 23:02:12 chris Exp $
 */
public interface UserModuleInterface
{
    /**
     * @return unique id name for the clone
     */
    public String getName();

    /**
     * Register the given module to receive notification of events of type messageClass.
     * @param messageClass
     * @param module
     */
    public void register(Class messageClass, UserModule module);

    /**
     * Register the given module to receive user events with the given id.
     * @param id
     * @param module
     */
    public void registerForEvent(int id, UserModule module);

    /**
     * @return the current config object
     */
    public Config getConfig();

    /**
     * @return name of the network the clone should be connecting to
     */
    public String getNetwork();

    /**
     * @return current nickname of the bot, or null if it is not connected
     */
    public String getNickname();

    /**
     * Send the given message to the IRC server with the given priority. The greater the priority, the sooner it'll be
     * sent to the IRC server if there are other messages queued.
     * @param message
     * @param priority
     */
    public void send(OutboundMessage message, int priority);

    /**
     * Trigger the given user event for all modules of the current clone.
     * @param id
     * @param attachment
     */
    public void triggerEvent(int id, Object attachment);

    /**
     * Trigger the given user event for all modules of every connected clone.
     * @param id
     * @param attachment
     */
    public void triggerGlobalEvent(int id, Object attachment);

    /**
     * Reload all modules.
     */
    public void reloadModules();

    /**
     * @param name module to add to the module set
     */
    public void addModule(String name);

    /**
     * @param name which module to remove
     */
    public void removeModule(String name);

    /**
     * @param name
     * @return a reference to the given module, or null if it is not loaded
     */
    public UserModule getModule(String name) throws UserModule.NoSuchModuleException;
}
