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
package com.echbot.tests;

import com.echbot.OutboundQueue;
import com.echbot.messages.OutboundMessage;
import com.echbot.messages.out.ChatMessageOut;
import com.echbot.messages.out.JoinOut;
import com.echbot.messages.out.PongOut;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * @author Chris Pearson
 * @version $Id: OutboundQueueTest.java,v 1.2 2003/07/27 10:43:49 chris Exp $
 */
public class OutboundQueueTest extends TestCase
{
    public static Test suite() {
        return new TestSuite(OutboundQueueTest.class);
    }

    public void testOutboundQueue() {
        OutboundQueue queue = new OutboundQueue();
        OutboundMessage m1, m2, m3, m4, m5, m6, m7, m8;
        queue.add(m1 = new ChatMessageOut("someone", "hi someone", true), 2);
        queue.add(m2 = new JoinOut("#channel"), 2);
        queue.add(m3 = new ChatMessageOut("someone", "i'm the bot", true), 2);
        queue.add(m4 = new ChatMessageOut("someoneelse", "hi!", false), 2);
        queue.add(m5 = new PongOut("code"), 5);
        queue.add(m6 = new ChatMessageOut("help", "help", true), 0);
        queue.add(m7 = new ChatMessageOut("someone", "how are you?", true), 2);
        assertEquals(m5, queue.removeItem());
        assertEquals(m1, queue.removeItem());
        assertEquals(m2, queue.removeItem());
        assertEquals(m4, queue.removeItem());
        assertEquals(m3, queue.removeItem());
        assertEquals(m7, queue.removeItem());
        assertEquals(m6, queue.removeItem());
    }
}
