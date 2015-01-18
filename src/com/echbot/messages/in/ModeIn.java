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
package com.echbot.messages.in;

import com.echbot.messages.InboundMessage;
import com.echbot.messages.MessageVisitor;
import org.apache.log4j.Logger;

/**
 * @author Chris Pearson
 * @version $Id: ModeIn.java,v 1.4 2003/08/27 11:24:59 chris Exp $
 */
public class ModeIn implements InboundMessage
{
    private static final Logger log = Logger.getLogger(ModeIn.class);
    private String from, channel, modes;

    public ModeIn(String from, String channel, String modes) {
        if ((from != null) && (from.length() > 1) && (from.charAt(0) == ':')) {
            this.from = from.substring(1);
        } else {
            this.from = from;
        }
        this.channel = channel.toLowerCase();
        this.modes = modes;
    }

    public void visit(MessageVisitor module) {
        module.received(this);
    }

    public String getFrom() {
        int exc = from.indexOf('!');
        return (exc == -1) ? from : from.substring(0, exc);
    }

    public String getFromWithHost() {
        return from;
    }

    public String getChannel() {
        return channel;
    }

    public String getModes() {
        return modes;
    }
}
