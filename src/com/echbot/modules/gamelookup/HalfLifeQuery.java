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
import java.util.*;

/**
 * @author Chris Pearson
 * @version $Id: HalfLifeQuery.java,v 1.7 2003/08/28 13:54:16 chris Exp $
 */
public class HalfLifeQuery
{
    private static final int BUFFER_SIZE = 4096;
    private static final int PACKET_TIMEOUT = 1200;
    private static final byte ff = ~0;
    private static final byte[] rulesQuery = new byte[]{ff, ff, ff, ff, 'r', 'u', 'l', 'e', 's', 0};
    private static final byte[] infoQuery = new byte[]{ff, ff, ff, ff, 'i', 'n', 'f', 'o', 0};
    private static final byte[] playersQuery = new byte[]{ff, ff, ff, ff, 'p', 'l', 'a', 'y', 'e', 'r', 's', 0};
    private static final Logger log = Logger.getLogger(HalfLifeQuery.class);
    private final Map vars = new HashMap();
    private final String varToUse = "hl";

    public HalfLifeQuery(String address) throws IOException {
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
        DatagramPacket packet = new DatagramPacket(rulesQuery, rulesQuery.length, sendTo);
        socket.send(packet);
        // now receive the reply
        final byte[] buffer = new byte[BUFFER_SIZE];
        packet = new DatagramPacket(buffer, BUFFER_SIZE);
        socket.setSoTimeout(PACKET_TIMEOUT);
        socket.receive(packet);
        if (!validPacket('E', buffer)) throw new IOException("Invalid packet received");
        final byte[] rules = new byte[packet.getLength() - 7];
        System.arraycopy(buffer, 7, rules, 0, rules.length);

        // Send a request for info
        packet = new DatagramPacket(infoQuery, infoQuery.length, sendTo);
        socket.send(packet);
        // now receive the reply
        packet = new DatagramPacket(buffer, BUFFER_SIZE);
        socket.receive(packet);
        if (!validPacket('C', buffer)) throw new IOException("Invalid packet received");
        final byte[] info = new byte[packet.getLength() - 5];
        System.arraycopy(buffer, 5, info, 0, info.length);

        // Send a request for players next
        packet = new DatagramPacket(playersQuery, playersQuery.length, sendTo);
        socket.send(packet);
        // now receive the reply
        packet = new DatagramPacket(buffer, BUFFER_SIZE);
        socket.receive(packet);
        if (!validPacket('D', buffer)) throw new IOException("Invalid packet received");
        log.debug("Players:" + new String(buffer));
        final byte[] playerData = new byte[packet.getLength() - 5];
        System.arraycopy(buffer, 5, playerData, 0, playerData.length);
        log.debug("Players is " + new String(playerData));

        socket.close();

        // now parse the received data
        String key = null;
        int index = 0;
        int mark = 0;
        while (index < rules.length) {
            if (rules[index] == 0) {
                final String chunk = new String(rules, mark, index - mark);
                if (key == null) {
                    key = chunk;
                } else {
                    vars.put(key, chunk);
                    key = null;
                }
                mark = index + 1;
            }
            index++;
        }

        // Now parse the info response
        index = 0;
        mark = 0;
        int iteration = 1;
        while (index < info.length) {
            if (info[index] == 0) {
                final String chunk = new String(info, mark, index - mark);
                if (iteration == 1)
                    vars.put("ech_address", chunk);
                else if (iteration == 2)
                    vars.put("ech_svname", chunk);
                else if (iteration == 3)
                    vars.put("ech_mapname", chunk);
                else if (iteration == 4)
                    vars.put("ech_gamedir", chunk);
                else if (iteration == 5) {
                    vars.put("ech_gamedesc", chunk);
                    break;
                }
                iteration++;
                mark = index + 1;
            }
            index++;
        }
        vars.put("ech_maxclients", Integer.toString(info[info.length - 2]));
        vars.put("ech_protocol", Integer.toString(info[info.length - 1]));

        // first byte of playerData is number of active players
        vars.put("ech_players", Integer.toString(playerData[0]));
        final List players = new ArrayList();
        index = 1;
        for (int i = 0; i < playerData[0]; i++) {
            final byte playerNum = playerData[index++];
            final StringBuffer playerName = new StringBuffer();
            while (playerData[index] != 0) {
                playerName.append((char)playerData[index++]);
            }
            index++;
            final int frags = (0xff & playerData[index++]) |
                    ((0xff & playerData[index++]) << 8) |
                    ((0xff & playerData[index++]) << 16) |
                    ((0xff & playerData[index++]) << 24);
            final float gametime = Float.intBitsToFloat(
                    (0xff & playerData[index++]) |
                    ((0xff & playerData[index++]) << 8) |
                    ((0xff & playerData[index++]) << 16) |
                    ((0xff & playerData[index++]) << 24));
            final Map newPlayer = new HashMap();
            newPlayer.put("playernum", Integer.toString(playerNum));
            newPlayer.put("name", playerName.toString());
            newPlayer.put("score", Integer.toString(frags));
            newPlayer.put("gametime", Float.toString(gametime));
            players.add(newPlayer);
        }
        Collections.sort(players, new Comparator()
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
        vars.put("players", players);
    }

    private static final boolean validPacket(char c, byte[] buffer) {
        if (buffer.length < 5) return false;
        return (buffer[0] == ff) && (buffer[1] == ff) && (buffer[2] == ff) &&
                (buffer[3] == ff) && (buffer[4] == c);
    }

    public Map getVars() {
        return vars;
    }

    public String getVarToUse() {
        return varToUse;
    }
}
