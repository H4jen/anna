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

/**
 * @author Chris Pearson
 * @version $Id: PingIn.java,v 1.2 2003/07/27 10:43:49 chris Exp $
 */
public class PingIn implements InboundMessage
{
    private String pingCode;

    public PingIn(String pingCode) {
        this.pingCode = pingCode;
    }

    public void visit(MessageVisitor module) {
        module.received(this);
    }

    public String getPingCode() {
        return pingCode;
    }
}
