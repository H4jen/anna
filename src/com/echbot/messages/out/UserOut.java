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
package com.echbot.messages.out;

import com.echbot.messages.OutboundMessage;

/**
 * @author Chris Pearson
 * @version $Id: UserOut.java,v 1.2 2003/07/27 10:43:49 chris Exp $
 */
public class UserOut implements OutboundMessage
{
    private String user, local, remote, whois;

    public UserOut(String user, String local, String remote, String whois) {
        this.user = user;
        this.local = local;
        this.remote = remote;
        this.whois = whois;
    }

    public String toSendString() {
        return "USER " + user + " \"" + local + "\" \"" + remote + "\" :" + whois;
    }

    public String getTarget() {
        return null;
    }
}
