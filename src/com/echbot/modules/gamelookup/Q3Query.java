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

import org.apache.log4j.Logger;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.util.*;

/**
 * @author Chris Pearson
 * @version $Id: Q3Query.java,v 1.10 2003/08/28 13:54:16 chris Exp $
 */
public class Q3Query
{
    private static final Logger log = Logger.getLogger(Q3Query.class);
    private static final byte ff = ~0;
    private static final byte[] query = new byte[]{ff, ff, ff, ff, 'g', 'e', 't', 's', 't', 'a', 't', 'u', 's', 0};
    private static final int BUFFER_SIZE = 4096;
    private static final int PACKET_TIMEOUT = 500;
    private static final int QUERY_RETRIES = 3;
    private final Map vars = new HashMap();
    private final String varToUse;

    public Q3Query(String address) throws IOException, NumberFormatException {
        InetSocketAddress sendTo;
        final int colon = address.indexOf(':');
        if ((colon == -1) || (colon + 1 == address.length())) {
            sendTo = new InetSocketAddress(address, 27960);
        } else {
            final StringTokenizer portTokens = new StringTokenizer(address.substring(colon + 1), ",;:-. ");
            final int port = portTokens.hasMoreTokens() ? Integer.parseInt(portTokens.nextToken()) : 27960;
            sendTo = new InetSocketAddress(address.substring(0, colon), port);
        }
        DatagramSocket socket = new DatagramSocket();
        final byte[] buffer = new byte[BUFFER_SIZE];
        DatagramPacket packet = receive(socket, sendTo, buffer, QUERY_RETRIES);
        socket.close();

        // now parse the reply
        int endPos = 0;
        while (endPos < packet.getLength()) {
            if (buffer[endPos++] == '\n') break;
        }
        final int startVars = endPos;
        while (endPos < packet.getLength()) {
            if (buffer[endPos] == '\n') break;
            endPos++;
        }
        log.debug("Full buffer is " + new String(buffer));
        log.debug("startVars is " + startVars + " and endpos is " + endPos);
        log.debug("Putting this into tokens:" + new String(buffer, startVars, endPos - startVars));
        StringTokenizer tokens = new StringTokenizer(new String(buffer, startVars, endPos - startVars), "\\");
        String key = null;
        while (tokens.hasMoreTokens()) {
            if (key == null) {
                key = tokens.nextToken();
            } else {
                vars.put(key, tokens.nextToken());
                key = null;
            }
        }
        log.debug("Splitting remaining " + (packet.getLength() - endPos - 1) + " chars from " + endPos + 1);
        final List players = new ArrayList();
        tokens = new StringTokenizer(new String(buffer, endPos + 1, packet.getLength() - endPos - 1));
        while (tokens.hasMoreTokens()) {
            final Map player = new HashMap();
            if (tokens.hasMoreTokens()) player.put("score", tokens.nextToken(" \n"));
            if (tokens.hasMoreTokens()) player.put("ping", tokens.nextToken(" \n"));
            if (tokens.hasMoreTokens()) player.put("name", deQuake(tokens.nextToken("\n")));
            players.add(player);
        }

        // Now do the custom variables
        String team1 = "", team2 = "", specs = "";
        final int gametype = Integer.parseInt((String)vars.get("g_gametype"));
        final boolean cpmaOrOsp = "cpma".equals(vars.get("gamename")) ||
                "osp".equals(vars.get("gamename")) ||
                noNull("gameversion").startsWith("OSP ");
        vars.put("ech_players", Integer.toString(players.size()));
        if ("etmain".equals(vars.get("gamename"))) {
            vars.put("ech_gametype", getEtGametype(gametype));
            team1 = noNull("Players_Allies");
            team2 = noNull("Players_Axis");
            specs = getUnused(players.size(), team1 + " " + team2);
            varToUse = "wolfet";
        } else if (vars.containsKey("game") && ((String)vars.get("game")).startsWith("GTV")) {
            team1 = "";
            team2 = "";
            specs = "";
            varToUse = "q3gtv";
        } else {
            vars.put("ech_gametype", getGametype(gametype));
            final String timeleft = noNull("Score_Time");
            vars.put("ech_timeleft", timeleft.equals("") ? noNull("timelimit") : timeleft);
            if (gametype == 0) {
                if (cpmaOrOsp) {
                    team1 = noNull("Players_Active");
                    specs = getUnused(players.size(), team1);
                    varToUse = "q3ospffa";
                } else {
                    team1 = getUnused(players.size(), "");
                    varToUse = "q3";
                }
            } else if (gametype == 1) {
                if (cpmaOrOsp) {
                    final StringTokenizer st = new StringTokenizer(noNull("Players_Active"));
                    team1 = st.hasMoreTokens() ? st.nextToken() : "";
                    team2 = st.hasMoreTokens() ? st.nextToken() : "";
                    specs = getUnused(players.size(), team1 + " " + team2);
                    varToUse = "q3osp1v1";
                } else {
                    team1 = getUnused(players.size(), "");
                    varToUse = "q3";
                }
            } else if (gametype == 3) {
                if (cpmaOrOsp) {
                    team1 = noNull("Players_Red");
                    team2 = noNull("Players_Blue");
                    specs = getUnused(players.size(), team1 + " " + team2);
                    varToUse = "q3osptdm";
                } else {
                    team1 = getUnused(players.size(), "");
                    varToUse = "q3";
                }
            } else if (gametype == 4) {
                if (cpmaOrOsp) {
                    team1 = noNull("Players_Red");
                    team2 = noNull("Players_Blue");
                    specs = getUnused(players.size(), team1 + " " + team2);
                    varToUse = "q3ospctf";
                } else {
                    team1 = getUnused(players.size(), "");
                    varToUse = "q3";
                }
            } else if (gametype == 5) {
                if (cpmaOrOsp) {
                    team1 = noNull("Players_Red");
                    team2 = noNull("Players_Blue");
                    specs = getUnused(players.size(), team1 + " " + team2);
                    varToUse = "q3ospca";
                } else {
                    team1 = getUnused(players.size(), "");
                    varToUse = "q3";
                }
            } else {
                team1 = getUnused(players.size(), "");
                varToUse = "q3";
            }
        }
        vars.put("team1", getPlayerVarList(team1, players));
        vars.put("team2", getPlayerVarList(team2, players));
        vars.put("specs", getPlayerVarList(specs, players));
    }

    private final static DatagramPacket receive(DatagramSocket socket, InetSocketAddress sendTo, final byte[] receiveBuffer, int retries) throws IOException {
        DatagramPacket packet = null;
        for (int i = 0; i < retries; i++) {
            try {
                packet = new DatagramPacket(query, query.length, sendTo);
                socket.send(packet);
                // now receive the reply
                packet = new DatagramPacket(receiveBuffer, BUFFER_SIZE);
                socket.setSoTimeout(PACKET_TIMEOUT);
                socket.receive(packet);
                return packet;
            } catch (IOException e) {
                log.debug("Failed to receive, " + retries + " retries left", e);
            }
        }
        throw new SocketTimeoutException("No replies to " + retries + " queries");
    }

    public Map getVars() {
        return vars;
    }

    public String getVarToUse() {
        return varToUse;
    }

    /*** Utility functions ***/


    private static final String deQuake(String quotedNameIn) {
        final String quotedName = quotedNameIn.trim();
        if ((quotedName == null) || (quotedName.length() < 2)) {
            return "";
        }
        final String name = quotedName.substring(1, quotedName.length() - 1);
        final StringBuffer finalName = new StringBuffer();
        for (int index = 0; index < name.length(); index++) {
            if (index == name.length() - 1) {
                finalName.append(unWeird(name.charAt(index)));
            } else if ((name.charAt(index) == '^') && ((name.charAt(index + 1) == 'X') || (name.charAt(index + 1) == 'x'))) {   // This is a stupid long colour code (^XC0C0C0)
                index += 7; // skips all the colour code
            } else if ((name.charAt(index) == '^') && (name.charAt(index + 1) != '^')) {    // This is a colour code, skip it
                index++; // skips the next character
            } else {    // This is NOT a colour code, add it to the string
                finalName.append(unWeird(name.charAt(index)));
            }
        }
        return finalName.toString();
    }

    private static final char unWeird(char weird) {
        if ((weird < 32) || (weird > 126)) {    // This is a weird code, skip it
            return '_';
        } else {    // This is NOT a colour code, add it to the string
            return weird;
        }
    }

    private static final String getGametype(int gametype) {
        switch (gametype) {
            case 0:
                return "FFA";
            case 1:
                return "1v1";
            case 2:
                return "1player";
            case 3:
                return "TDM";
            case 4:
                return "CTF";
            case 5:
                return "CA";
            default:
                return "unknown";
        }
    }

    private static final String getEtGametype(int gametype) {
        switch (gametype) {
            case 2:
                return "Objective";
            case 3:
                return "Stopwatch";
            case 4:
                return "Campaign";
            case 5:
                return "Last Man Standing";
            default:
                return "unknown";
        }
    }

    private static final String getUnused(int players, String done) {
        final Set unused = new HashSet();
        for (int i = 1; i <= players; i++) {
            unused.add(Integer.toString(i));
        }
        final StringTokenizer tokens = new StringTokenizer(done);
        while (tokens.hasMoreTokens()) {
            unused.remove(tokens.nextToken());
        }
        final StringBuffer res = new StringBuffer();
        for (Iterator i = unused.iterator(); i.hasNext();) {
            String num = (String)i.next();
            res.append(num);
            if (i.hasNext()) res.append(' ');
        }
        return res.toString();
    }

    private final String noNull(String varName) {
        final String str = (String)vars.get(varName);
        if (str == null) return "";
        if ("(None)".equals(str)) return "";
        return str;
    }

    public static final List getPlayerVarList(String team, List players) {
        final List playerList = new ArrayList();
        final StringTokenizer tokens = new StringTokenizer(team);
        while (tokens.hasMoreTokens()) {
            playerList.add(players.get(Integer.parseInt(tokens.nextToken()) - 1));
        }
        Collections.sort(playerList, new Comparator()
        {
            public int compare(Object o1, Object o2) {
                try {
                    final int o1s = Integer.parseInt((String)((Map)o1).get("score"));
                    final int o2s = Integer.parseInt((String)((Map)o2).get("score"));
                    return o2s - o1s;
                } catch (NumberFormatException e) {
                    return 0;
                }
            }
        });
        return playerList;
    }
}
