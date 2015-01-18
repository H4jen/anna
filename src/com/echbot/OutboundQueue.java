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
import com.echbot.messages.out.TopicOut;

import java.util.*;

/**
 * @author Chris Pearson
 * @version $Id: OutboundQueue.java,v 1.6 2003/09/02 16:40:15 chris Exp $
 */
public class OutboundQueue
{
    private final List priorities = new ArrayList();

    private static class RoundRobinQueue
    {
        private final List order = new ArrayList();
        private final Map messages = new HashMap();

        private synchronized void add(OutboundMessage message) {
            String target = message.getTarget();
            List messageList = (List)messages.get(target);
            if (messageList == null) {
                messageList = new ArrayList();
                order.add(target);
                messages.put(target, messageList);
            }
            messageList.add(message);
        }

        private synchronized OutboundMessage removeItem() {
            String nextTarget = (String)order.remove(0);
            List messageList = (List)messages.get(nextTarget);
            OutboundMessage result = (OutboundMessage)messageList.remove(0);
            // If we're sending a topic change, make sure there's no overriding
            // topic changes later on in the queue
//            if (result instanceof TopicOut) {
//                for (Iterator i = messageList.iterator(); i.hasNext();) {
//                    OutboundMessage message = (OutboundMessage)i.next();
//                    if (message instanceof TopicOut) {
//                        result = message;
//                        i.remove();
//                    }
//                }
//            }
            // If no messages remain for this target, remove it from the order
            if (messageList.isEmpty()) {
                messages.remove(nextTarget);
            } else {
                order.add(nextTarget);
            }
            return result;
        }

        private synchronized boolean isEmpty() {
            return order.isEmpty();
        }
    }

    private static class PriorityQueueItem
    {
        private final int priority;
        private final RoundRobinQueue queue = new RoundRobinQueue();

        private PriorityQueueItem(int priority) {
            this.priority = priority;
        }
    }

    public void add(OutboundMessage message, int priority) {
        synchronized (priorities) {
            getQueue(priority).queue.add(message);
            priorities.notify();
        }
    }

    private PriorityQueueItem getQueue(int priority) {
        synchronized (priorities) {
            for (Iterator i = priorities.iterator(); i.hasNext();) {
                PriorityQueueItem item = (PriorityQueueItem)i.next();
                if (item.priority == priority) {
                    return item;
                }
            }
            PriorityQueueItem item = new PriorityQueueItem(priority);
            // Add the queue at the right place in the list
            int insertBefore = 0;
            while ((insertBefore < priorities.size()) &&
                    ((PriorityQueueItem)priorities.get(insertBefore)).priority > priority) {
                insertBefore++;
            }
            priorities.add(insertBefore, item);
            return item;
        }
    }

    public OutboundMessage removeItem() {
        synchronized (priorities) {
            while (priorities.isEmpty()) {
                try {
                    priorities.wait();
                } catch (InterruptedException e) {
                }
            }
            PriorityQueueItem item = (PriorityQueueItem)priorities.get(0);
            OutboundMessage next = item.queue.removeItem();
            if (item.queue.isEmpty()) {
                priorities.remove(0);
            }
            return next;
        }
    }

}
