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

import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.net.InetSocketAddress;
import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.SocketTimeoutException;
import java.io.IOException;

/**
 * @author Alex Ahern
 * @version $Id: QWQuery.java,v 1.3 2003/10/11 11:05:55 alex Exp $
 */
public class QWQuery
{
    private static final int DEFAULT_QW_SERVER_PORT = 27500;

    private static final Pattern playerPattern = Pattern.compile("(-?\\d+) (-?\\d+) (-?\\d+) (-?\\d+) \"(.*)\" \"(.*)\" (-?\\d+) (-?\\d+)");
    private static final Pattern kteamScorePattern = Pattern.compile("\\[(.*?)\\](\\d+):\\[(.*?)\\](\\d+)");
    private static final Logger log = Logger.getLogger(QWQuery.class);
    private static final byte ff = ~0;
    private static final byte[] query = new byte[]{ff, ff, ff, ff, 's', 't', 'a', 't', 'u', 's', 0};
    private static final byte[] responseToken = new byte[]{ff, ff, ff, ff, 'n', '\\'};
    private static final int BUFFER_SIZE = 4096;
    private static final int PACKET_TIMEOUT = 500;
    private static final int QUERY_RETRIES = 3;
    private final String varToUse = "qw";
    private final Map vars = new HashMap();

    public QWQuery(String address) throws IOException, NumberFormatException {
        InetSocketAddress sendTo;
        int colon = address.indexOf(':');
        if ((colon == -1) || (colon + 1 == address.length())) {
            sendTo = new InetSocketAddress(address, DEFAULT_QW_SERVER_PORT);
        } else {
            StringTokenizer portTokens = new StringTokenizer(address.substring(colon + 1), ",;:-. ");
            int port = portTokens.hasMoreTokens() ? Integer.parseInt(portTokens.nextToken()) : DEFAULT_QW_SERVER_PORT;
            sendTo = new InetSocketAddress(address.substring(0, colon), port);
        }
        DatagramSocket socket = new DatagramSocket();
        byte[] buffer = new byte[BUFFER_SIZE];
        DatagramPacket packet = receive(socket, sendTo, buffer, QUERY_RETRIES);
        socket.close();

        // Find start of players
        int startOfPlayers = 0;
        while (startOfPlayers < buffer.length) {
            if (buffer[startOfPlayers++] == '\n') break;
        }

        // Now escape all the quake special characters.
        deQuake(buffer, startOfPlayers);

        String buf = new String(buffer);
        log.debug("Full buffer is " + buf);

        /*  The first line of the response is the "rules", subsequent lines
            hold player information */
        String[] splitResponse = buf.split("\n");
        if (splitResponse.length < 1 || !splitResponse[0].startsWith(new String(responseToken))) {
            // TODO: shite
            log.warn("Unparseable response, server reply started with " + splitResponse[0].substring(0, 10));
        } else {
            StringTokenizer rulesTk = new StringTokenizer(splitResponse[0].substring(6), "\\");
            HashMap rules = new HashMap();

            while (rulesTk.hasMoreTokens()) {
                rules.put(rulesTk.nextToken(), rulesTk.nextToken());
            }

            if (log.isDebugEnabled()) {
                log.debug("Parsed rules: ");
                for (Iterator i = rules.keySet().iterator(); i.hasNext();) {
                    Object key = i.next();
                    log.debug(key + ": " + rules.get(key));
                }
            }

            /**
             * Now gatherplayers. We use "length - 1" because there is a
             * trailing newline in the response, which gets "split" into the
             * empty string
             */
            List players = new ArrayList();

            for (int i = 1; i < splitResponse.length - 1; i++) {
                // Player format takes the form:
                // id frags time ping name skin topcolor bottomcolor
                // 1506 15 9 82 "ktzb etowen" "base" 13 12

                Matcher m = playerPattern.matcher(splitResponse[i]);

                if (m.matches()) {
                    try {
                        String name = m.group(5);
                        int frags = Integer.parseInt(m.group(2));

                        // Should remove this, useful for future reference however.
                        /*
                        int playerId = Integer.parseInt(m.group(1));
                        int time = Integer.parseInt(m.group(3));
                        int ping = Integer.parseInt(m.group(4));
                        String skin = m.group(6);
                        int topColor = Integer.parseInt(m.group(7));
                        int bottomColor = Integer.parseInt(m.group(8));
                        */

                        HashMap playerMap = new HashMap();
                        playerMap.put("score", new Integer(frags));
                        playerMap.put("name", name);

                        //QWPlayerInfo player = new QWPlayerInfo(playerId, frags, ping, time, name, skin, topColor, bottomColor);
                        players.add(playerMap);
                    } catch (NumberFormatException e) {
                        log.warn("Unparseable player info: " + splitResponse[i], e);
                    }
                } else {
                    log.warn("Ignoring unparseable player: " + splitResponse[i]);
                }
            }

            // Sort into frag ranking
            Collections.sort(players, new Comparator()
            {
                public int compare(Object o, Object o1) {
                    Map m = (Map)o;
                    Map m1 = (Map)o1;

                    return ((Integer)m1.get("score")).compareTo((Integer)m.get("score"));
                }
            });

            /*
             * Special case: Kombat Teams servers create a "rule" called
             * "score" that is of the form [team1]123:[team2]343
             * If this is present, attempt to parse it and add more meaningful
             * information to our server response
             */
            if (rules.get("score") != null) {
                Matcher scoreMatch = kteamScorePattern.matcher((String) rules.get("score"));
                if (scoreMatch.matches()) {
                    vars.put("team1", scoreMatch.group(1));
                    vars.put("team1score", scoreMatch.group(2));
                    vars.put("team2", scoreMatch.group(3));
                    vars.put("team2score", scoreMatch.group(4));
                } else {
                    log.debug("ignored non-kteams score pattern: " + rules.get("score"));
                }
            }

            // Build vars for IRC message
            vars.put("gamedir", rules.get("*gamedir"));
            vars.put("qw_players", Integer.toString(players.size()));
            vars.put("map", rules.get("map"));
            vars.put("maxclients", rules.get("maxclients"));
            vars.put("timelimit", rules.get("timelimit"));
            vars.put("players", players);
        }
    }

    /**
     * Converts high-ascii characters used in QWCL to create coloured names into
     * regular printable chars.
     * @param buffer An array of bytes to escape
     * @param startPos The address of the byte to start at.
     */
    private void deQuake(byte[] buffer, int startPos) {
        // It doesnt get any more unpleasant than this shit.

        int i = startPos;
        while (i < buffer.length) {
            byte b = buffer[i];

            // First remove high ascii
            b = (byte)(b & 127);

            // Now deal with "gold" numbers and brackets
            switch (b) {
                case 0:
                    b = ' ';
                    break;
                case 1:
                case 2:
                case 3:
                case 4:
                case 5:
                case 6:
                case 7:
                case 8:
                case 9:
                    // Important to note no case 10
                case 11:
                case 12:
                case 13:
                    b = '_';
                    break;
                case 14:
                case 15:
                    b = '.';
                    break;
                case 16:
                    b = '[';
                    break;
                case 17:
                    b = ']';
                    break;

                    // Gold numbers
                case 18:
                case 19:
                case 20:
                case 21:
                case 22:
                case 23:
                case 24:
                case 25:
                case 26:
                case 27:
                    b = (byte)(b + 30);
                    break;
                case 28:
                    b = '.';
                    break;
                case 29:
                    b = '<';
                    break;
                case 30:
                    b = '=';
                    break;
                case 31:
                    b = '>';
                    break;
            }
            buffer[i] = b;
            i++;
        }
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

    public String getVarToUse() {
        return varToUse;
    }

    public Map getVars() {
        return vars;
    }

    private class QWPlayerInfo
    {
        private int playerId;
        private int frags;
        private int ping;
        private int time;
        private String name;
        private String skin;
        private int topColor;
        private int bottomColor;

        public QWPlayerInfo(int playerId, int frags, int ping, int time, String name, String skin, int topColor, int bottomColor) {
            this.playerId = playerId;
            this.frags = frags;
            this.ping = ping;
            this.time = time;
            this.name = name;
            this.skin = skin;
            this.topColor = topColor;
            this.bottomColor = bottomColor;
        }

        public int getPlayerId() {
            return playerId;
        }

        public void setPlayerId(int playerId) {
            this.playerId = playerId;
        }

        public int getFrags() {
            return frags;
        }

        public void setFrags(int frags) {
            this.frags = frags;
        }

        public int getPing() {
            return ping;
        }

        public void setPing(int ping) {
            this.ping = ping;
        }

        public int getTime() {
            return time;
        }

        public void setTime(int time) {
            this.time = time;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getSkin() {
            return skin;
        }

        public void setSkin(String skin) {
            this.skin = skin;
        }

        public int getTopColor() {
            return topColor;
        }

        public void setTopColor(int topColor) {
            this.topColor = topColor;
        }

        public int getBottomColor() {
            return bottomColor;
        }

        public void setBottomColor(int bottomColor) {
            this.bottomColor = bottomColor;
        }

        public String toString() {
            return "N:" + name + " F:" + frags + " P:" + ping + " T:" + time + " S:" + skin + " TC:" + topColor + " BC:" + bottomColor + " ID:" + playerId;
        }

    }
}
