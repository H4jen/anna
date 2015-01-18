package com.echbot.modules.gamelookup;

import java.util.logging.Logger;
import java.util.Properties;
import java.net.InetSocketAddress;

/**
 * @author Chris Pearson
 * @version $Id$
 */
public class UT2003Query {
    private static final Logger log = Logger.getLogger(UT2003Query.class.getName());
    private static final byte[] INFO = new byte[]{0x78, 0, 0, 0, 0};
    private static final byte[] INFO_REPLY = new byte[]{0x7f, 0, 0, 0, 0};
    private Properties result = new Properties();
    private byte[] reply = new byte[QueryUtil.BUFFER_SIZE];
    private int replyLength = 0;
    private InetSocketAddress server;

    private static final class StringWithOffset {
        private String string;
        private int offset;

        public StringWithOffset(String string, int offset) {
            this.string = string;
            this.offset = offset;
        }
    }

    public UT2003Query(InetSocketAddress server) {
        this.server = server;
    }

    /**
     * Send a status query to the given server, and parse the returned data.
     * Saves a List of Properties objects in the "player" property.
     * @throws QueryException
     */
    public void queryInfo() throws QueryException {
        result = new Properties();
        replyLength = QueryUtil.queryUDP(INFO, reply, server);
        log.finest("Reply: \n" + QueryUtil.hexDump(reply, 0, replyLength));
        if (!QueryUtil.startsWith(reply, INFO_REPLY)) {
            throw new QueryException("Invalid query reply received (code:1)");
        }
        int offset = INFO_REPLY.length;
        offset += 5; // skip 5 bytes

        int intVar = (reply[offset++] & 0xff) |
                (reply[offset++] & 0xff) << 8 |
                (reply[offset++] & 0xff) << 16 |
                (reply[offset++] & 0xff) << 24;
        result.setProperty("port", Integer.toString(intVar));

        offset += 4; // skip 4 bytes

        StringWithOffset swo = readString(reply, offset);
        result.setProperty("servername", swo.string);
        offset = swo.offset;

        swo = readString(reply, offset);
        result.setProperty("map", swo.string);
        offset = swo.offset;

        swo = readString(reply, offset);
        result.setProperty("gametype", swo.string);
        offset = swo.offset;

        log.fine("Offset is " + offset);
        intVar = (reply[offset++] & 0xff) |
                (reply[offset++] & 0xff) << 8 |
                (reply[offset++] & 0xff) << 16 |
                (reply[offset++] & 0xff) << 24;
        result.setProperty("clients", Integer.toString(intVar));

        intVar = (reply[offset++] & 0xff) |
                (reply[offset++] & 0xff) << 8 |
                (reply[offset++] & 0xff) << 16 |
                (reply[offset++] & 0xff) << 24;
        result.setProperty("maxclients", Integer.toString(intVar));

        log.fine(QueryUtil.getProperties(result, new String[]{"port","servername","map","gametype","clients","maxclients"}));
    }

    public Properties getResult() {
        return result;
    }

    private static final StringWithOffset readString(byte[] buffer, int offset) {
        boolean colourFirst = (buffer[offset] & 0x80) != 0;
        int length = buffer[offset] & 0x7f;
        if (colourFirst) {
            offset += 7;
            StringBuffer sb = new StringBuffer();
            for (int i = 0; i < length - 1; i++) {
                sb.append((char)((buffer[offset++] & 0xff) |
                        (buffer[offset++] & 0xff) << 8));
            }
            return new StringWithOffset(sb.toString(), offset);
        } else {
            return new StringWithOffset(QueryUtil.asString(buffer, offset + 1, length - 1), offset + length + 1);
        }
    }
}
