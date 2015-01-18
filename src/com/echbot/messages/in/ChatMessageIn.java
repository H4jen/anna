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
 * @version $Id: ChatMessageIn.java,v 1.9 2003/08/27 11:24:59 chris Exp $
 */
public class ChatMessageIn implements InboundMessage
{
    private static final Logger log = Logger.getLogger(ChatMessageIn.class);
    private String from, to, message;
    private boolean privmsg;
    private Boolean cachedIsCommand = null;
    private String cachedCommand = null;

    public ChatMessageIn(String from, String to, String message, boolean privmsg) {
        if ((from != null) && (from.length() > 1) && (from.charAt(0) == ':')) {
            this.from = from.substring(1);
        } else {
            this.from = from;
        }
        this.to = (to.charAt(0) == '#') ? to.toLowerCase() : to;
        if ((message != null) && message.startsWith(" :") && (message.length() > 2)) {
            this.message = message.substring(2);
        } else {
            this.message = message;
        }
        this.privmsg = privmsg;
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

    public String getTo() {
        return to;
    }

    public String getMessage() {
        return message;
    }

    public boolean isPrivmsg() {
        return privmsg;
    }

    public boolean isCommand() {
        if (cachedIsCommand != null) return cachedIsCommand.booleanValue();
        for (int i = 0; i < message.length(); i++) {
            final char next = message.charAt(i);
            if (next != ' ') {
                if (next == '!') {
                    cachedIsCommand = Boolean.TRUE;
                    return true;
                } else {
                    cachedIsCommand = Boolean.FALSE;
                    return false;
                }
            }
        }
        cachedIsCommand = Boolean.FALSE;
        return false;
    }

    public String getCommand() {
        if (cachedCommand != null) return cachedCommand;
        final int exc = message.indexOf('!');
        int end = message.indexOf(' ', exc);
        if (end == -1) end = message.length();
        cachedCommand = message.substring(exc, end).toLowerCase();
        return cachedCommand;
    }

    public String getArguments() {
        if (!isCommand()) return "";
        final String cmd = getCommand();
        int endindex = message.indexOf(cmd) + cmd.length() + 1;
        if (endindex >= message.length()) return "";
        return message.substring(endindex);
    }
}
