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
 * @version $Id: SystemIn.java,v 1.3 2003/08/26 17:21:22 chris Exp $
 */
public class SystemIn implements InboundMessage
{
    private static final Logger log = Logger.getLogger(SystemIn.class);
    private int number;
    private String target, message;

    public SystemIn(int number, String target, String message) {
        this.number = number;
        this.target = target;
        if ((message != null) && message.startsWith(" :") && (message.length() > 2)) {
            this.message = message.substring(2);
        } else {
            this.message = message;
        }
    }

    public void visit(MessageVisitor module) {
        module.received(this);
    }

    public int getNumber() {
        return number;
    }

    public String getTarget() {
        return target;
    }

    public String getMessage() {
        return message;
    }
}
