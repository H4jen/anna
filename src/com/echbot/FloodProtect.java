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

import com.echbot.messages.OutboundMessage;
import com.echbot.sockets.TcpConnection;
import org.apache.log4j.Logger;

/**
 * @author Chris Pearson
 * @version $Id: FloodProtect.java,v 1.12 2003/09/01 17:50:43 chris Exp $
 */
public class FloodProtect
{
    private static final Logger log = Logger.getLogger(FloodProtect.class);
    private static final int SERVER_TIMER_INCREMENT = 2000;
    private static final int SERVER_TIMER_MAX = 10000;
    private static final int MAX_SERVER_QUEUE_LENGTH = 512;
    private static final int FULL_BUFFER_DELAY = 500;
    private static final int HISTORY_QUEUE_REMOVE_DELAY = 10000;
    private final TcpConnection connection;
    private final OutboundQueue queue = new OutboundQueue();
    private long serverTimer;
    private int serverQueueLength = 0;

    public FloodProtect(TcpConnection connection) {
        this.connection = connection;
    }

    public void send(OutboundMessage message, int priority) {
        queue.add(message, priority);
        addedMessage();
    }

    public synchronized void addedMessage() {
        long timeNow = System.currentTimeMillis();
        if (serverTimer < timeNow) serverTimer = timeNow;
        serverTimer += SERVER_TIMER_INCREMENT;
        if (serverTimer > timeNow + SERVER_TIMER_MAX) {
            delayedSend(serverTimer - timeNow - SERVER_TIMER_MAX);
        } else {
            sendNow();
        }
    }

    private synchronized void delayedSend(long delay) {
        Runnable sendTask = new Runnable()
        {
            public void run() {
                sendNow();
            }
        };
        connection.getTimer().schedule(sendTask, delay);
    }

    private synchronized void sendNow() {
        if (serverQueueLength > MAX_SERVER_QUEUE_LENGTH) {
            Runnable sendTask = new Runnable()
            {
                public void run() {
                    sendNow();
                }
            };
            connection.getTimer().schedule(sendTask, FULL_BUFFER_DELAY);
        } else {
            final OutboundMessage message = queue.removeItem();
            final StringBuffer sendString = new StringBuffer(message.toSendString());
            if (sendString.length() > 448) sendString.setLength(448);
            log.debug("OUT:" + sendString.toString());
            sendString.append("\r\n");
            String toSend = sendString.toString();
            serverQueueLength += toSend.length();
            connection.write(toSend);
            final int dropsize = toSend.length();
            Runnable sendTask = new Runnable()
            {
                public void run() {
                    dropQueueLength(dropsize);
                }
            };
            connection.getTimer().schedule(sendTask, HISTORY_QUEUE_REMOVE_DELAY);
        }
    }

    private synchronized void dropQueueLength(int bytes) {
        serverQueueLength -= bytes;
    }
}
