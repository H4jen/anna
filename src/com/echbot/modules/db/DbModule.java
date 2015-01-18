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
package com.echbot.modules.db;

import com.echbot.UserModule;
import com.echbot.UserModuleInterface;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.sql.*;
import java.util.*;

/**
 * @author Chris Pearson
 * @version $Id: DbModule.java,v 1.8 2003/09/24 22:20:21 chris Exp $
 */
public class DbModule extends UserModule
{
    private static final Logger log = Logger.getLogger(DbModule.class);
    private static final String DB_FILENAME = "db.properties";
    private static final int RECONNECT_DELAY = 20000;
    private final int EVENT_PICKUPSTART;
    private final List queuedEvents = new ArrayList();
    private Connection db;

    private static final class QueueEvent
    {
        int id;
        Object attachment;

        public QueueEvent(int id, Object attachment) {
            this.id = id;
            this.attachment = attachment;
        }
    }

    public static final class DbException extends Exception
    {
        public DbException(String message) {
            super(message);
        }

        public DbException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public DbModule(UserModuleInterface parent) {
        super(parent);
        EVENT_PICKUPSTART = getUserEventId("pickup.start");
        parent.registerForEvent(EVENT_PICKUPSTART, this);
    }

    public void initialise(Object state) {
        connect();
    }

    private synchronized void connect() {
        try {
            Class.forName("org.postgresql.Driver");
            Properties props = new Properties();
            InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(DB_FILENAME);
            if (in == null) {
                log.warn("Couldn't find " + DB_FILENAME);
            } else {
                props.load(in);
            }
            String dbUsername = props.getProperty("username");
            String dbPassword = props.getProperty("password");
            log.info("Connecting to database at " + props.getProperty("localhost") + " with " + dbUsername + " and " + dbPassword);
            db = DriverManager.getConnection(props.getProperty("localhost"),
                    (dbUsername == null ? "" : dbUsername),
                    (dbPassword == null ? "" : dbPassword));
            log.debug("Database connection established");
        } catch (ClassNotFoundException e) {
            log.error("Failed to load postgresql driver");
            scheduleReconnect();
        } catch (SQLException e) {
            log.error("Failed to get SQL connection", e);
            scheduleReconnect();
        } catch (IOException e) {
            log.error("Failed to load " + DB_FILENAME, e);
        }
    }

    private synchronized void scheduleReconnect() {
        close();
        getTimer().schedule(new Runnable()
        {
            public void run() {
                connect();
                if (db != null) {
                    // If the connection has succeeded, process events
                    synchronized (queuedEvents) {
                        for (Iterator i = queuedEvents.iterator(); i.hasNext();) {
                            QueueEvent event = (QueueEvent)i.next();
                            userEvent(event.id, event.attachment);
                            i.remove();
                        }
                    }
                }
            }
        }, RECONNECT_DELAY);
    }

    private synchronized void close() {
        try {
            if (db != null) db.close();
        } catch (SQLException e) {
            log.info("Failed to close connection", e);
        }
        db = null;
    }

    public synchronized void userEvent(int id, Object attachment) {
        log.debug("Comparing " + parent + " and " + ((Map)attachment).get("clone"));
        if (db == null) {
            synchronized (queuedEvents) {
                queuedEvents.add(new QueueEvent(id, attachment));
            }
            return;
        }
        try {
            if ((id == EVENT_PICKUPSTART) && (parent == ((Map)attachment).get("clone"))) {
/*
                pickup_nicknames(id, nickname);
                pickup_games(id, network, channel, gameid, gametime, server);
                pickup_players(nickname, game);
                CREATE TABLE pickup_nicknames (
                    id serial PRIMARY KEY,
                    nickname text
                );
                CREATE TABLE pickup_games (
                    id serial PRIMARY KEY,
                    network text,
                    channel text,
                    gameid text,
                    gametime timestamp,
                    server text
                );
                CREATE TABLE pickup_players (
                    gameid bigint REFERENCES pickup_games (id) ON DELETE CASCADE,
                    nickid bigint REFERENCES pickup_nicknames (id) ON DELETE CASCADE,
                    CONSTRAINT pickup_players_pk PRIMARY KEY (gameid,nickid)
                );
                CREATE function pickup_getnick(TEXT) RETURNS BIGINT AS '
	                DECLARE
		                nick ALIAS FOR $1;
		                foundnick pickup_nicknames%ROWTYPE;
	                BEGIN
                		SELECT * INTO foundnick FROM pickup_nicknames WHERE nickname = nick;
                		IF NOT FOUND THEN
    			            INSERT INTO pickup_nicknames (nickname) VALUES (nick);
    			            SELECT * INTO foundnick FROM pickup_nicknames WHERE nickname = nick;
		                END IF;
		                RETURN foundnick.id;
	                END;*/
                // LANGUAGE 'plpgsql';

                Statement stat = db.createStatement();
                ResultSet rs = stat.executeQuery("select nextval('pickup_games_id_seq')");
                if (!rs.next()) {
                    log.warn("Failed to get a unique game id");
                    return;
                }
                long gameid = rs.getLong(1);
                PreparedStatement ps = db.prepareStatement("insert into pickup_games values(?,?,?,?,?,?)");
                ps.setLong(1, gameid);
                ps.setString(2, parent.getNetwork());
                ps.setString(3, (String)((Map)attachment).get("channel"));
                ps.setString(4, (String)((Map)attachment).get("id"));
                ps.setTimestamp(5, new Timestamp(System.currentTimeMillis()));
                ps.setString(6, (String)((Map)attachment).get("ip"));
                ps.execute();

                ps = db.prepareStatement("insert into pickup_players values(?,pickup_getnick(?))");
                List players = (List)((Map)attachment).get("players");
                for (Iterator i = players.iterator(); i.hasNext();) {
                    String nick = (String)i.next();
                    ps.setLong(1, gameid);
                    ps.setString(2, nick);
                    ps.execute();
                }
            }
        } catch (SQLException e) {
            log.warn("Error executing query", e);
            scheduleReconnect();
        } catch (Exception e) {
            log.warn("Error in db code", e);
            scheduleReconnect();
        }
    }

    public synchronized List query(String sql, Object[] params) throws DbException {
        if (db == null) throw new DbException("Database connection not open");
        try {
            PreparedStatement ps = db.prepareStatement(sql);
            for (int i = 0; i < params.length; i++) {
                Object param = params[i];
                if (param instanceof String) {
                    ps.setString(i + 1, (String)param);
                }
            }
            ResultSet rs = ps.executeQuery();
            int columnCount = rs.getMetaData().getColumnCount();
            List results = new ArrayList();
            Object[] row = new Object[columnCount];
            for (int i = 0; i < columnCount; i++) {
                row[i] = rs.getMetaData().getColumnLabel(i + 1);
            }
            results.add(row);
            while (rs.next()) {
                row = new Object[columnCount];
                for (int i = 0; i < columnCount; i++) {
                    row[i] = rs.getObject(i + 1);
                }
                results.add(row);
            }
            return results;
        } catch (SQLException e) {
            log.warn("Error whilst executing query", e);
            scheduleReconnect();
            throw new DbException("Error occurred", e);
        }
    }

    public synchronized void updateChannelStats(String[] chans, int[] occupants) {
        try {
            /*
            CREATE TABLE channel_stats (
                id SERIAL,
                botname text,
                network text,
                channel text,
                people int
            );
            */
            db.setAutoCommit(false);
            PreparedStatement stat = db.prepareStatement("DELETE FROM channel_stats WHERE botname=?");
            stat.setString(1, parent.getName());
            stat.execute();
            stat = db.prepareStatement("INSERT INTO channel_stats (botname,network,channel,people) VALUES (?,?,?,?)");
            for (int i = 0; i < chans.length; i++) {
                stat.setString(1, parent.getName());
                stat.setString(2, parent.getNetwork());
                stat.setString(3, chans[i]);
                stat.setInt(4, occupants[i]);
                stat.execute();
            }
            db.commit();
            db.setAutoCommit(true);
        } catch (SQLException e) {
            log.error("DB error", e);
            scheduleReconnect();
        }
    }
}
