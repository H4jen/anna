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
package com.echbot.modules.gamelookup;

import com.echbot.UserModule;
import com.echbot.UserModuleInterface;
import com.echbot.config.ConfigUtils;
import com.echbot.config.FormatUtils;
import com.echbot.messages.in.ChatMessageIn;
import com.echbot.messages.out.ChatMessageOut;
import org.apache.log4j.Logger;

import java.util.StringTokenizer;

/**
 * @author Chris Pearson
 * @version $Id: GamelookupModule.java,v 1.20 2003/10/11 11:05:55 alex Exp $
 */
public class GamelookupModule extends UserModule
{
    private static final Logger log = Logger.getLogger(GamelookupModule.class);

    public GamelookupModule(UserModuleInterface parent) {
        super(parent);
        register(ChatMessageIn.class);
        final String start = "Q3$ech_gametype$($gamename$) on \0033$mapname$ \0035Players:\00312$ech_players$/$sv_maxclients$\0034 ";
        setDefault("q3", start + "($team1$)\003 Timelimit:$timelimit$");
        setDefault("q3,team1", "$score$:$name$");
        setDefault("q3,specs", "$name$");
        setDefault("q3osp1v1", start + "$team1$ \003-vs-\00312 $team2$ \003Spec:\00314($specs$)\003 Time left:$ech_timeleft$");
        setDefault("q3osp1v1,team1", "$name$:$score$");
        setDefault("q3osp1v1,team2", "$score$:$name$");
        setDefault("q3osp1v1,specs", "$name$");
        setDefault("q3osptdm", start + "($team1$) Red:\002$Score_Red$\002 \003v \00312\002$Score_Blue$\002:Blue ($team2$) \00314($specs$)\003 Time left:$ech_timeleft$");
        setDefault("q3osptdm,team1", "$score$:$name$");
        setDefault("q3osptdm,team2", "$score$:$name$");
        setDefault("q3osptdm,specs", "$name$");
        setDefault("q3ospctf", start + "($team1$) Red caps:\002$Score_Red$\002 \003v \00312\002$Score_Blue$\002:Blue caps ($team2$) \00314($specs$)\003 Time left:$ech_timeleft$");
        setDefault("q3ospctf,team1", "$score$:$name$");
        setDefault("q3ospctf,team2", "$score$:$name$");
        setDefault("q3ospctf,specs", "$name$");
        setDefault("q3ospca", start + "($team1$) Red:\002$Score_Red$\002 \003v \00312\002$Score_Blue$\002:Blue ($team2$) \00314($specs$)\003 $ech_timeleft$");
        setDefault("q3ospca,team1", "$score$:$name$");
        setDefault("q3ospca,team2", "$score$:$name$");
        setDefault("q3ospca,specs", "$name$");
        setDefault("q3ospffa", start + "($team1$) \003Spec:\00314($specs$)\003 Time left:$ech_timeleft$");
        setDefault("q3ospffa,team1", "$score$:$name$");
        setDefault("q3ospffa,specs", "$name$");
        setDefault("q3gtv", "Q3GTV \0035Players:\00312$ech_players$/$sv_maxclients$\003 >>> \00310$sv_hostname$\003 <<<");
        setDefault("hl", "HL($ech_gamedir$) TL:\00312$mp_timelimit$ \0033$ech_mapname$\0035 Players:\00312$ech_players$/$ech_maxclients$ \0034($players$)");
        setDefault("hl,players", "$score$:$name$");
        setDefault("wolfet", "Wolf:ET $ech_gametype$ on \0033$mapname$ \0035Players:\00312$ech_players$/$sv_maxclients$ \003Timelimit:$timelimit$");
        setDefault("qw", "QW($gamedir$) on \0033$map$ \0035Players:\00312$qw_players$/$maxclients$ ($players$)${ \0034\002}team1$${:}team1score$${\002 \003v \002\00312}team2$${:}team2score$ \003Timelimit:$timelimit$");
        setDefault("qw,players", "$score$:$name$");
//        setDefault("q4", "Q4$si_gametype$($gamename$) on \0033$si_map$ \0035Players:\00312$ech_players$/$si_maxPlayers$\0034 ($players$)\003 Timelimit:$si_timeLimit$ - \00310$si_name$");
//        setDefault("q4,players", "$name$:$ping$ms");
    }

    public void received(ChatMessageIn message) {
        if (!message.isCommand()) return;
        try {
            String channel = message.getTo().startsWith("#") ? message.getTo() : null;
            if (message.getCommand().equals("!q3") ||
                    message.getCommand().equals("!wolf") ||
                    message.getCommand().equals("!et")) {
                StringTokenizer args = new StringTokenizer(message.getArguments());
                if (args.hasMoreTokens()) {
                    Q3Query query = new Q3Query(checkAlias(args.nextToken(), channel));
                    reply(message, FormatUtils.format(getConfig(), query.getVarToUse(), query.getVars(), parent.getNetwork(), channel));
                }
//            } else if(message.getCommand().equals("!q4"))
//                {
//                    StringTokenizer args = new StringTokenizer(message.getArguments());
//                    if(args.hasMoreTokens())
//                    {
//                        Q4Query q4query = new Q4Query(checkAlias(stringtokenizer1.nextToken(), s));
//                        reply(message, FormatUtils.format(getConfig(), q4query.getVarToUse(), q4query.getVars(), parent.getNetwork(), channel));
//                    }
            } else if (message.getCommand().equals("!cs") ||
                    message.getCommand().equals("!hl")) {
                StringTokenizer args = new StringTokenizer(message.getArguments());
                if (args.hasMoreTokens()) {
                    HalfLifeQuery query = new HalfLifeQuery(checkAlias(args.nextToken(), channel));
                    reply(message, FormatUtils.format(getConfig(), query.getVarToUse(), query.getVars(), parent.getNetwork(), channel));
                }
            } else if (message.getCommand().equals("!qw")) {
                StringTokenizer args = new StringTokenizer(message.getArguments());
                if (args.hasMoreTokens()) {
                    QWQuery query = new QWQuery(checkAlias(args.nextToken(), channel));
                    reply(message, FormatUtils.format(getConfig(), query.getVarToUse(), query.getVars(), parent.getNetwork(), channel));
                }
            }


        } catch (Exception e) {
            log.debug("Error querying and parsing", e);
            reply(message, "Error querying server (" + e.getMessage() + ")");
        }
    }

    private String checkAlias(String alias, String channel) {
        if (channel == null) return alias;
        final String aliasValue = ConfigUtils.getChannelVar(getConfig(), "alias," + alias.trim().toLowerCase(), parent.getNetwork(), channel.toLowerCase(), null);
        if (aliasValue == null) return alias;
        if (channel != null) send(new ChatMessageOut(channel, "\0036\002" + alias.trim() + "\002 is " + aliasValue, true));
        return aliasValue;
    }

    private final void reply(ChatMessageIn message, String body) {
        if (message.getTo().charAt(0) == '#') {
            send(new ChatMessageOut(message.getTo(), body, true));
        } else {
            send(new ChatMessageOut(message.getFrom(), body, message.isPrivmsg()));
        }
    }
}
