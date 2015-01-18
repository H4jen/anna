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
 * @version $Id: NickIn.java,v 1.4 2003/08/26 17:21:22 chris Exp $
 */
public class NickIn implements InboundMessage
{
    private static final Logger log = Logger.getLogger(NickIn.class);
    private String oldNick, newNick;

    public NickIn(String oldNick, String newNick) {
        if ((oldNick != null) && (oldNick.length() > 1) && (oldNick.charAt(0) == ':')) {
            this.oldNick = oldNick.substring(1);
        } else {
            this.oldNick = oldNick;
        }
        if ((newNick != null) && (newNick.length() > 1) && (newNick.charAt(0) == ':')) {
            this.newNick = newNick.substring(1);
        } else {
            this.newNick = newNick;
        }
    }

    public void visit(MessageVisitor module) {
        module.received(this);
    }

    public String getOldNick() {
        int exc = oldNick.indexOf('!');
        return (exc == -1) ? oldNick : oldNick.substring(0, exc);
    }

    public String getOldNickWithHost() {
        return oldNick;
    }

    public String getNewNick() {
        return newNick;
    }
}
