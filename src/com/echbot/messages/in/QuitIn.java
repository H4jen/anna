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
 * @version $Id: QuitIn.java,v 1.3 2003/08/26 17:21:22 chris Exp $
 */
public class QuitIn implements InboundMessage
{
    private static final Logger log = Logger.getLogger(QuitIn.class);
    private String quitter, message;

    public QuitIn(String quitter, String message) {
        if ((quitter != null) && (quitter.length() > 1) && (quitter.charAt(0) == ':')) {
            this.quitter = quitter.substring(1);
        } else {
            this.quitter = quitter;
        }
        if ((message != null) && message.startsWith(" :") && (message.length() > 2)) {
            this.message = message.substring(2);
        } else {
            this.message = message;
        }
    }

    public void visit(MessageVisitor module) {
        module.received(this);
    }

    public String getQuitter() {
        int exc = quitter.indexOf('!');
        return (exc == -1) ? quitter : quitter.substring(0, exc);
    }

    public String getQuitterWithHost() {
        return quitter;
    }

    public String getMessage() {
        return message;
    }
}
