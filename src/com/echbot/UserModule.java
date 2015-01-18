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
import com.echbot.messages.InboundMessage;
import com.echbot.messages.MessageVisitor;
import com.echbot.messages.OutboundMessage;
import com.echbot.messages.in.*;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Chris Pearson
 * @version $Id: UserModule.java,v 1.14 2003/09/22 23:02:12 chris Exp $
 */
public abstract class UserModule implements MessageVisitor
{
    private static final List userEvents = new ArrayList();
    public final UserModuleInterface parent;
    private final GroupTimer ourTimer;

    public final static class NoSuchModuleException extends Exception
    {
        public NoSuchModuleException(String message) {
            super(message);
        }
    }

    public UserModule(UserModuleInterface parent) {
        this.parent = parent;
        ourTimer = new GroupTimer(((Clone)parent).getTimer());
    }

    public static final int getUserEventId(String eventString) {
        synchronized (userEvents) {
            if (!userEvents.contains(eventString)) {
                userEvents.add(eventString);
            }
            return userEvents.indexOf(eventString);
        }
    }

    public final void register(Class messageType) {
        if (!InboundMessage.class.isAssignableFrom(messageType)) {
            throw new IllegalArgumentException(messageType + " not an InboundMessage");
        }
        parent.register(messageType, this);
    }

    public final Config getConfig() {
        return parent.getConfig();
    }

    public final UserModule getModule(String name) throws NoSuchModuleException {
        UserModule module = parent.getModule(name);
        if (module == null) throw new NoSuchModuleException(name + " is not loaded");
        return module;
    }

    public final void setDefault(String varName, String varValue) {
        parent.getConfig().setDefault("vars," + varName, varValue);
    }

    public final void send(OutboundMessage message) {
        parent.send(message, 0);
    }

    public final void sendUrgent(OutboundMessage message) {
        parent.send(message, 1);
    }

    /**
     * @return the timer to be used for scheduling events
     */
    public final GroupTimer getTimer() {
        return ourTimer;
    }

    final void cancelTimer() {
        ourTimer.cancel();
    }

    // Methods to override in modules

    public void initialise(Object state) {
    }

    public void received(ChatMessageIn message) {
    }

    public void received(JoinIn message) {
    }

    public void received(KickIn message) {
    }

    public void received(ModeIn message) {
    }

    public void received(NickIn message) {
    }

    public void received(PartIn message) {
    }

    public void received(PingIn message) {
    }

    public void received(QuitIn message) {
    }

    public void received(SystemIn message) {
    }

    public void received(TopicIn message) {
    }

    public void received(UnknownIn message) {
    }

    public void userEvent(int id, Object attachment) {
    }

    public Object getState() {
        return null;
    }
}
