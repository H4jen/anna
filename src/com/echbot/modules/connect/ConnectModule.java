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
package com.echbot.modules.connect;

import com.echbot.UserModule;
import com.echbot.UserModuleInterface;
import com.echbot.messages.in.*;
import com.echbot.messages.out.IsOnOut;

/**
 * @author Chris Pearson
 * @version $Id: ConnectModule.java,v 1.14 2003/09/22 23:02:12 chris Exp $
 */
public class ConnectModule extends UserModule
{
    private static final int KEEPALIVE_PERIOD = 30000;
    private long aliveUntil;
    private final Runnable keepAlive = new Runnable()
    {
        public void run() {
            sendKeepAlive();
        }
    };

    public ConnectModule(UserModuleInterface parent) {
        super(parent);
        register(ChatMessageIn.class);
        register(JoinIn.class);
        register(QuitIn.class);
        register(PartIn.class);
        register(PingIn.class);
        register(SystemIn.class);
        aliveUntil = System.currentTimeMillis() + KEEPALIVE_PERIOD;
    }

    public void initialise(Object state) {
        aliveUntil = System.currentTimeMillis() + KEEPALIVE_PERIOD;
        getTimer().schedule(keepAlive, KEEPALIVE_PERIOD);
    }

    private void sendKeepAlive() {
        if (System.currentTimeMillis() > aliveUntil) {
            send(new IsOnOut("A"));
            aliveUntil = System.currentTimeMillis() + KEEPALIVE_PERIOD;
        }
        getTimer().schedule(keepAlive, KEEPALIVE_PERIOD);
    }

    public void received(PingIn message) {
        aliveUntil = System.currentTimeMillis() + KEEPALIVE_PERIOD;
    }

    public void received(SystemIn message) {
        aliveUntil = System.currentTimeMillis() + KEEPALIVE_PERIOD;
    }

    public void received(UnknownIn message) {
        aliveUntil = System.currentTimeMillis() + KEEPALIVE_PERIOD;
    }

    public void received(ChatMessageIn message) {
        aliveUntil = System.currentTimeMillis() + KEEPALIVE_PERIOD;
    }

    public void received(JoinIn message) {
        aliveUntil = System.currentTimeMillis() + KEEPALIVE_PERIOD;
    }

    public void received(PartIn message) {
        aliveUntil = System.currentTimeMillis() + KEEPALIVE_PERIOD;
    }

    public void received(QuitIn message) {
        aliveUntil = System.currentTimeMillis() + KEEPALIVE_PERIOD;
    }
}
