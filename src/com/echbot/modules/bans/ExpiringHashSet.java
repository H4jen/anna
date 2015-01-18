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
package com.echbot.modules.bans;

import java.util.HashSet;
import java.util.Iterator;

/**
 * @author Chris Pearson
 * @version $Id: ExpiringHashSet.java,v 1.2 2003/07/29 17:41:07 chris Exp $
 */
public class ExpiringHashSet extends HashSet
{
    private long duration = 1000;
    private long nextClear = 0;

    public ExpiringHashSet(long duration) {
        this.duration = duration;
    }

    public boolean add(Object o) {
        clearIfNeeded();
        return super.add(o);
    }

    public boolean contains(Object o) {
        clearIfNeeded();
        return super.contains(o);
    }

    public Iterator iterator() {
        clearIfNeeded();
        return super.iterator();
    }

    public int size() {
        clearIfNeeded();
        return super.size();
    }

    public boolean isEmpty() {
        clearIfNeeded();
        return super.isEmpty();
    }

    public final synchronized void clearIfNeeded() {
        if ((nextClear != 0) && (nextClear < System.currentTimeMillis())) {
            clear();
        }
        nextClear = System.currentTimeMillis() + duration;
    }
}
