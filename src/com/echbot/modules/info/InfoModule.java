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
package com.echbot.modules.info;

import com.echbot.UserModule;
import com.echbot.UserModuleInterface;
import com.echbot.config.ConfigUtils;
import com.echbot.config.FormatUtils;
import com.echbot.messages.in.ChatMessageIn;
import com.echbot.messages.out.ChatMessageOut;
import com.echbot.modules.db.DbModule;
import org.apache.log4j.Logger;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * @author Chris Pearson
 * @version $Id: InfoModule.java,v 1.1 2003/09/13 16:54:49 chris Exp $
 */
public class InfoModule extends UserModule
{
    private static final Logger log = Logger.getLogger(InfoModule.class);

    public InfoModule(UserModuleInterface parent) {
        super(parent);
        setDefault("info", "off");
        setDefault("info,format", "\00314$id$: \0032[\002$keyword$\002]\00310 $fulltext$");
        register(ChatMessageIn.class);
    }

    public void received(ChatMessageIn message) {
        if (!message.isCommand()) return;
        String channel = message.getTo().startsWith("#") ? message.getTo().toLowerCase() : null;
        if (channel == null) return;
        if (!"on".equals(ConfigUtils.getVar(getConfig(), "info",
                parent.getNetwork(), channel)))
            return;

        /*
        CREATE TABLE info (
            id serial PRIMARY KEY,
            network text,
            channel text,
            keyword text,
            fulltext text
        );
        */
        if ("!find".equals(message.getCommand())) {
            String search = message.getArguments().trim().replace('*', '%');
            if (search.length() == 0) {
                send(new ChatMessageOut(message.getFrom(),
                        "Usage: !find <keywords>   (Use * for a wildcard)", false));
            } else {
                try {
                    List results = ((DbModule)getModule("db")).query(
                            "select * from info where network = ? and " +
                            "channel = ? and keyword ilike ?",
                            new Object[]{parent.getNetwork(), channel, search});
                    Object[] headings = (Object[])results.remove(0);
                    int sendCount = 0;
                    for (Iterator i = results.iterator(); i.hasNext();) {
                        Object[] row = (Object[])i.next();
                        sendCount++;
                        if (sendCount > 5) {
                            send(new ChatMessageOut(message.getFrom(),
                                    "There are more than 5 results, please refine your search to view the others!",
                                    false));
                            break;
                        }
                        Map vars = new HashMap();
                        for (int j = 0; j < row.length; j++) {
                            vars.put(headings[j], row[j]);
                        }
                        send(new ChatMessageOut(message.getFrom(), FormatUtils.format(
                                getConfig(), "info,format", vars,
                                parent.getNetwork(), channel), false));
                    }
                    if (sendCount == 0) {
                        send(new ChatMessageOut(message.getFrom(),
                                "There are no results for your search, please try again!",
                                false));
                    }
                } catch (NoSuchModuleException e) {
                    log.error("info module requires db module");
                } catch (DbModule.DbException e) {
                    log.error(e.getMessage(), e);
                }
            }
        }
    }
}
