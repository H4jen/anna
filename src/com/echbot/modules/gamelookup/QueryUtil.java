package com.echbot.modules.gamelookup;

import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Properties;
import java.net.*;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

/**
 * @author Chris Pearson
 * @version $Id$
 */
public abstract class QueryUtil {
    public static final int BUFFER_SIZE = 4096;
    private static final int SOCKET_TIMEOUT = 1000;
    private static final Logger log = Logger.getLogger(QueryUtil.class.getName());

    public static final int queryUDP(byte[] query, byte[] result, InetSocketAddress server) throws QueryException {
        try {
            DatagramSocket socket = new DatagramSocket();
            socket.setSoTimeout(SOCKET_TIMEOUT);
            socket.send(new DatagramPacket(query, query.length, server));
            DatagramPacket reply = new DatagramPacket(result, BUFFER_SIZE);
            socket.receive(reply);
            return reply.getLength();
        } catch (PortUnreachableException e) {
            throw new QueryException("Unreachable port: " + server.getPort(), e);
        } catch (SocketTimeoutException e) {
            throw new QueryException("Request timed out (server may be down)", e);
        } catch (SocketException e) {
            throw new QueryException("Failed to open socket (" + e.getMessage() + ")", e);
        } catch (IOException e) {
            throw new QueryException("Failed to send/receive data (" + e.getMessage() + ")", e);
        }
    }

    public static final byte[] getBytes(String query) {
        try {
            return query.getBytes("ISO-8859-1");
        } catch (UnsupportedEncodingException e) {
            log.log(Level.WARNING, "Unsupported encoding", e);
            return query.getBytes();
        }
    }

    public static final boolean startsWith(byte[] a1, byte[] a2) {
        for (int i = 0; i < a2.length; i++) {
            if (a1[i] != a2[i]) return false;
        }
        return true;
    }

    public static String asString(byte[] bytes, int offset, int length) {
        try {
            return new String(bytes, offset, length, "ISO-8859-1");
        } catch (UnsupportedEncodingException e) {
            log.log(Level.WARNING, "Unsupported encoding", e);
            return new String(bytes, offset, bytes.length - offset);
        }
    }

    /**
     * Takes a String representing a space-separated list of player numbers, and
     * returns the players that either are or are not included in the list.
     * @param usedString
     * @param chooseUsed
     * @param players
     * @return
     */
    public static final List getPlayers(String usedString, boolean chooseUsed, List players) {
        boolean[] used = new boolean[players.size()];
        Arrays.fill(used, false);
        if (usedString != null) {
            String[] usedParts = usedString.split("\\s+");
            for (int i = 0; i < usedParts.length; i++) {
                try {
                    int num = Integer.parseInt(usedParts[i]);
                    if ((num < 0) || (num > used.length)) {
                        log.warning("Player out of range: " + num);
                    } else {
                        used[num] = true;
                    }
                } catch (NumberFormatException e) {
                    log.log(Level.WARNING, "Failed to parse player " + usedParts[i]);
                }
            }
        }
        // now reconstruct the list of unused
        List result = new ArrayList();
        for (int i = 0; i < used.length; i++) {
            if (used[i] == chooseUsed) {
                result.add(players.get(i));
            }
        }
        return result;
    }

    public static final String getProperties(Properties props, String[] strings) {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < strings.length; i++) {
            if (i > 0) sb.append(' ');
            sb.append(strings[i]).append('=').append(props.getProperty(strings[i], "<not set>"));
        }
        return sb.toString();
    }

    public static final String hexDump(byte[] buffer, int offset, int length) {
        int lineStart = offset;
        StringBuffer result = new StringBuffer();
        StringBuffer hex = new StringBuffer();
        StringBuffer str = new StringBuffer();
        while (offset < length) {
            if (hex.length() > 0) hex.append(' ');
            hex.append(padTo(Integer.toHexString(buffer[offset]), 2));
            if ((buffer[offset] < 32) || (buffer[offset] > 127)) {
                str.append('.');
            } else {
                str.append((char) (buffer[offset] & 0xff));
            }
            if (offset % 16 == 0) lineStart = offset;
            if ((offset + 1) % 16 == 0) {
                if (result.length() > 0) result.append('\n');
                result.append(padTo(Integer.toHexString(lineStart), 4));
                result.append("  ").append(hex).append(' ').append(str);
                hex.setLength(0);
                str.setLength(0);
            }
            offset++;
        }
        if (hex.length() > 0) {
            if (result.length() > 0) result.append('\n');
            result.append(padTo(Integer.toHexString(lineStart), 4));
            result.append("  ").append(hex).append(' ').append(str);
        }
        return result.toString();
    }

    private static final String padTo(String str, int len) {
        if (str == null) str = "";
        if (str.length() > len) return str.substring(str.length() - len);
        if (str.length() == len) return str;
        // string is too short, pad with zeros
        StringBuffer sb = new StringBuffer();
        int addZeros = len - str.length();
        for (int i = 0; i < addZeros; i++) {
            sb.append('0');
        }
        sb.append(str);
        return sb.toString();
    }
}
