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
package com.echbot.messages;

import com.echbot.messages.in.*;

import java.util.StringTokenizer;

/**
 * @author Chris Pearson
 * @version $Id: MessageParser.java,v 1.2 2003/07/27 10:43:49 chris Exp $
 */
public final class MessageParser
{
    private MessageParser() {
    }

    public static InboundMessage parseMessage(String line) {
        if (line == null) return new UnknownIn(line);

        StringTokenizer tokens = new StringTokenizer(line);
        final int tokenCount = tokens.countTokens();

        if (tokenCount < 2) return new UnknownIn(line);
        String part1 = tokens.nextToken();
        String part2 = tokens.nextToken();
        if (part1.equals("PING")) {
            return new PingIn(part2);
        }
        if (part2.equals("QUIT")) {
            return new QuitIn(part1, tokens.nextToken(""));
        }

        if (tokenCount < 3) return new UnknownIn(line);
        String part3 = tokens.nextToken();
        if (part2.equals("JOIN")) {
            return new JoinIn(part1, part3);
        }
        if (part2.equals("PART")) {
            String msg = tokens.hasMoreTokens() ? tokens.nextToken("") : "";
            return new PartIn(part1, part3, msg);
        }
        if (part2.equals("NICK")) {
            return new NickIn(part1, part3);
        }

        if (tokenCount < 4) return new UnknownIn(line);
        if (part2.equals("PRIVMSG")) {
            return new ChatMessageIn(part1, part3, tokens.nextToken(""), true);
        }
        if (part2.equals("NOTICE")) {
            return new ChatMessageIn(part1, part3, tokens.nextToken(""), false);
        }
        if (part2.equals("MODE")) {
            return new ModeIn(part1, part3, tokens.nextToken(""));
        }
        if (part2.equals("TOPIC")) {
            return new TopicIn(part1, part3, tokens.nextToken(""));
        }

        // If the second parameter is numeric, it's a system message
        try {
            return new SystemIn(Integer.parseInt(part2), part3, tokens.nextToken(""));
        } catch (NumberFormatException e) {
        }

        if (tokenCount < 5) return new UnknownIn(line);
        String part4 = tokens.nextToken();
        if (part2.equals("KICK")) {
            return new KickIn(part1, part3, part4, tokens.nextToken(""));
        }

        return new UnknownIn(line);
    }
}
