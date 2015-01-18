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

import java.util.*;

/**
 * @author Chris Pearson
 * @version $Id: GroupTimer.java,v 1.4 2003/09/22 23:02:12 chris Exp $
 */
public class GroupTimer
{
    private static final Logger log = Logger.getLogger(GroupTimer.class);
    private static final Timer timer = new Timer(true);
    private final GroupTimer parent;
    private final List children = new ArrayList();
    private final List tasks = new ArrayList();
    private boolean cancelled = false;

    /**
     * Empty constructor method for use within core bot classes ONLY.
     * IMPORTANT: do *NOT* use this method within modules as it eliminates the
     * hierarchy of timers.
     */
    public GroupTimer() {
        parent = null;
    }

    public GroupTimer(GroupTimer parent) {
        this.parent = parent;
        synchronized (parent.children) {
            parent.children.add(this);
        }
    }

    public void schedule(final Runnable task, long delay) {
        if (delay < 5) delay = 5;
        TimerTask newTask = new TimerTask()
        {
            public void run() {
                synchronized (tasks) {
                    if (cancelled) return;
                    tasks.remove(this);
                }
                try {
                    task.run();
                } catch (RuntimeException e) {
                    log.error("Error running task:", e);
                }
            }
        };
        synchronized (tasks) {
            if (cancelled) return;
            tasks.add(newTask);
            timer.schedule(newTask, delay);
        }
    }

    public void cancel() {
        if (parent != null) {
            synchronized (parent.children) {
                parent.children.remove(this);
            }
        }
        synchronized (children) {
            while (!children.isEmpty()) {
                ((GroupTimer)children.get(0)).cancel();
            }
        }
        synchronized (tasks) {
            cancelled = true;
            for (Iterator i = tasks.iterator(); i.hasNext();) {
                ((TimerTask)i.next()).cancel();
                i.remove();
            }
        }
    }

    public boolean isEmpty() {
        synchronized (tasks) {
            return tasks.isEmpty();
        }
    }
}
