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

import com.echbot.messages.InboundMessage;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Chris Pearson
 * @version $Id: InboundQueue.java,v 1.4 2003/08/28 13:56:19 chris Exp $
 */
public class InboundQueue
{
    private final List queue = new ArrayList();

    public static class QueueEntry
    {
        public final InboundMessage message;
        public final Clone clone;

        private QueueEntry(InboundMessage message, Clone clone) {
            this.message = message;
            this.clone = clone;
        }
    }

    public void add(InboundMessage message, Clone clone) {
        synchronized (queue) {
            queue.add(new QueueEntry(message, clone));
            queue.notify();
        }
    }

    public QueueEntry getItem() {
        synchronized (queue) {
            while (queue.isEmpty()) {
                try {
                    queue.wait();
                } catch (InterruptedException e) {
                }
            }
            return (QueueEntry)queue.remove(0);
        }
    }
}
