package com.echbot.modules.nickometer;

import com.echbot.UserModule;
import com.echbot.UserModuleInterface;
import com.echbot.config.FormatUtils;
import com.echbot.config.ConfigUtils;
import com.echbot.messages.in.ChatMessageIn;
import com.echbot.messages.out.ChatMessageOut;

import java.util.StringTokenizer;
import java.util.HashMap;
import java.util.Map;
import java.text.NumberFormat;

/**
 * Module to rate how lame a nickname is based on a series of rather
 * over-complicated tests.
 * @author Chris Pearson
 * @version $Id: NickometerModule.java,v 1.3 2003/10/20 14:29:04 chris Exp $
 */
public class NickometerModule extends UserModule
{
    public NickometerModule(UserModuleInterface parent) {
        super(parent);
        setDefault("nickometer", "on");
        setDefault("nickometer,format", "\0035\002$nick$\002\0036 is $lameness$% lame");
        setDefault("nickometer,toolong", "\0035\002$nick$\002\0036 is pretty lame.. too long to be a nickname too ;P");
        register(ChatMessageIn.class);
    }

    public void received(ChatMessageIn message) {
        if (!message.isCommand()) return;

        boolean isToChannel = message.getTo().startsWith("#");
        // if nickometer is not turned on, stop processing
        if (isToChannel && !"on".equals(ConfigUtils.getVar(
                getConfig(), "nickometer", parent.getNetwork(), message.getTo()))) {
            return;
        }
        String replyTo = isToChannel ? message.getTo() : message.getFrom();
        boolean replyByPrivmsg = isToChannel || message.isPrivmsg();
        if ("!nickometer".equals(message.getCommand())) {
            StringTokenizer tokens = new StringTokenizer(message.getArguments());
            if (!tokens.hasMoreTokens()) {
                send(new ChatMessageOut(message.getFrom(), "Usage: !nickometer <nick>", false));
            } else {
                String checkNick = tokens.nextToken();
                Map lameKeys = new HashMap();
                lameKeys.put("nick", checkNick);

                if (checkNick.length() > 20) {
                    String msg = FormatUtils.format(getConfig(), "nickometer,toolong", lameKeys);
                    send(new ChatMessageOut(replyTo, msg, replyByPrivmsg));
                } else {
                    double lameness = Nickometer.nickometer(checkNick);
                    NumberFormat format = NumberFormat.getInstance();

                    lameKeys.put("lameness", format.format(lameness));
                    String msg = FormatUtils.format(getConfig(), "nickometer,format", lameKeys);
                    send(new ChatMessageOut(replyTo, msg, replyByPrivmsg));
                }
            }
        }
    }
}
