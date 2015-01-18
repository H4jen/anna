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

import org.apache.log4j.Logger;

/**
 * @author Chris Pearson
 * @version $Id: Worker.java,v 1.9 2003/08/27 19:48:37 chris Exp $
 */
public class Worker implements Runnable
{
    private static final Logger log = Logger.getLogger(Worker.class);
    private static int nextWorker = 1;
    private final InboundQueue queue;

    public Worker(InboundQueue queue) {
        this.queue = queue;
    }

    public void run() {
        becomeWorker(queue);
    }

    public static void becomeWorker(InboundQueue queue) {
        synchronized (Worker.class) {
            Thread.currentThread().setName("Worker-" + (nextWorker++));
        }
        while (true) {
            try {
                final InboundQueue.QueueEntry work = queue.getItem();
                ModuleSet modules = work.clone.getModules();
                if (modules != null) modules.received(work.message);
            } catch (Exception e) {
                log.warn("Failed to process work:", e);
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e1) {
                }
            }
        }
    }
}
