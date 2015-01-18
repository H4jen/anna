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
package com.echbot.modules.auth;

import java.util.*;

/**
 * @author Chris Pearson
 * @version $Id: ExpiringHashMap.java,v 1.1 2003/07/28 03:18:57 chris Exp $
 */
public class ExpiringHashMap extends HashMap
{
    private long duration = 1000;
    private long nextClear = 0;
    private List clearTimes = new ArrayList();
    private List clearKeys = new ArrayList();

    public ExpiringHashMap(long duration) {
        super();
        this.duration = duration;
    }

    public Object put(Object key, Object value) {
        synchronized (clearTimes) {
            clearTimes.add(new Long(System.currentTimeMillis() + duration));
            clearKeys.add(key);
        }
        clearIfNeeded();
        return super.put(key, value);
    }

    public void putAll(Map m) {
        synchronized (clearTimes) {
            for (Iterator i = m.keySet().iterator(); i.hasNext();) {
                Object key = i.next();
                clearTimes.add(new Long(System.currentTimeMillis() + duration));
                clearKeys.add(key);
            }
        }
        clearIfNeeded();
        super.putAll(m);
    }

    public boolean containsKey(Object key) {
        clearIfNeeded();
        return super.containsKey(key);
    }

    public Set keySet() {
        clearIfNeeded();
        return super.keySet();
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
        final long timeNow = System.currentTimeMillis();
        if ((nextClear != 0) && (nextClear < timeNow)) {
            synchronized (clearTimes) {
                for (Iterator i = clearTimes.iterator(); i.hasNext();) {
                    Long time = (Long)i.next();
                    if (time.longValue() > timeNow) {
                        nextClear = time.longValue();
                    } else {
                        i.remove();
                        remove(clearKeys.remove(0));
                        if (!i.hasNext()) nextClear = 0;
                    }
                }
            }
        }
    }
}
