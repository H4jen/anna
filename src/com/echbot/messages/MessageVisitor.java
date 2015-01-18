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

/**
 * @author Chris Pearson
 * @version $Id: MessageVisitor.java,v 1.2 2003/07/27 10:43:49 chris Exp $
 */
public interface MessageVisitor
{
    public void received(ChatMessageIn message);

    public void received(JoinIn message);

    public void received(KickIn message);

    public void received(ModeIn message);

    public void received(NickIn message);

    public void received(PartIn message);

    public void received(PingIn message);

    public void received(QuitIn message);

    public void received(SystemIn message);

    public void received(TopicIn message);

    public void received(UnknownIn message);
}
