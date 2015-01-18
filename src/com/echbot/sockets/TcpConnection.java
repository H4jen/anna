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
package com.echbot.sockets;

import com.echbot.FloodProtect;
import com.echbot.GroupTimer;
import com.echbot.messages.OutboundMessage;
import org.apache.log4j.Logger;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * @author Chris Pearson
 * @version $Id: TcpConnection.java,v 1.15 2003/09/01 17:54:36 chris Exp $
 */
public class TcpConnection implements Runnable
{
    private static final Logger log = Logger.getLogger(TcpConnection.class);
    private final Thread readerThread;
    private final SocketCallback callback;
    private final InetSocketAddress address, bindto;
    private final GroupTimer timer = new GroupTimer();
    private final FloodProtect flood = new FloodProtect(this);
    private Socket socket;
    private OutputStreamWriter out;
    private boolean closed = false;

    public TcpConnection(SocketCallback callback, InetSocketAddress address, InetSocketAddress bindto) {
        this.callback = callback;
        this.address = address;
        this.bindto = bindto;
        readerThread = new Thread(this);
        readerThread.start();
    }

    public GroupTimer getTimer() {
        return timer;
    }

    public void run() {
        try {
            log.debug("Address is " + address + ", and bindto is " + bindto);
            socket = new Socket(address.getAddress(), address.getPort(), bindto.getAddress(), bindto.getPort());
            out = new OutputStreamWriter(socket.getOutputStream(), "ISO-8859-1");
            final BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "ISO-8859-1"));
            callback.connected();
            String nextLine;
            while (!closed && ((nextLine = reader.readLine()) != null)) {
                callback.gotLine(nextLine);
            }
        } catch (IOException e) {
            log.error("Connection failed", e);
        } catch (NullPointerException e) {
            log.error("Failed to resolve DNS for " + address);
        }
        close();
    }

    public synchronized void close() {
        if (closed) return;
        closed = true;
        timer.cancel();
        callback.disconnected();
        try {
            if (socket != null) socket.close();
        } catch (Exception e) {
            log.info("Failed to close socket!", e);
        }
        readerThread.interrupt();
    }

    public synchronized void write(String toSend) {
        try {
            out.write(toSend);
            out.flush();
        } catch (IOException e) {
            log.warn("Error sending data to socket", e);
            close();
        }
    }

    public void send(OutboundMessage message, int priority) {
        flood.send(message, priority);
    }

    public boolean isConnected() {
        return (socket != null) && socket.isConnected();
    }
}
