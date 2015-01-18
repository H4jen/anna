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
 * @version $Id: JoinIn.java,v 1.4 2003/08/27 11:24:59 chris Exp $
 */
public class JoinIn implements InboundMessage
{
    private static final Logger log = Logger.getLogger(JoinIn.class);
    private String joiner, channel;

    public JoinIn(String joiner, String channel) {
        if ((joiner != null) && (joiner.length() > 1) && (joiner.charAt(0) == ':')) {
            this.joiner = joiner.substring(1);
        } else {
            this.joiner = joiner;
        }
        if ((channel != null) && (channel.length() > 1) && (channel.charAt(0) == ':')) {
            this.channel = channel.substring(1).toLowerCase();
        } else {
            this.channel = channel.toLowerCase();
        }
    }

    public void visit(MessageVisitor module) {
        module.received(this);
    }

    public String getJoiner() {
        int exc = joiner.indexOf('!');
        return (exc == -1) ? joiner : joiner.substring(0, exc);
    }

    public String getJoinerWithHost() {
        return joiner;
    }

    public String getChannel() {
        return channel;
    }
}
