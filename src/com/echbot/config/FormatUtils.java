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
package com.echbot.config;

import org.apache.log4j.Logger;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * @author Chris Pearson
 * @version $Id: FormatUtils.java,v 1.5 2003/08/10 10:03:26 chris Exp $
 */
public final class FormatUtils
{
    private static final Logger log = Logger.getLogger(FormatUtils.class);

    private FormatUtils() {
    }

    public static String format(Config config, String varName, Map vars) {
        return format(config, varName, vars, null, null);
    }

    public static String format(Config config, String varName, Map vars, String network) {
        return format(config, varName, vars, network, null);
    }

    public static String format(Config config, String varName, Map vars, String network, String channel) {
        final StringBuffer result = new StringBuffer();
        doFormat(varName, network, channel, config, vars, result);
        return result.toString();
    }

    public static String format(Config config, String varName, List vars) {
        return format(config, varName, vars, null, null);
    }

    public static String format(Config config, String varName, List vars, String network) {
        return format(config, varName, vars, network, null);
    }

    public static String format(Config config, String varName, List vars, String network, String channel) {
        final StringBuffer result = new StringBuffer();
        doFormat(varName, network, channel, config, vars, result);
        return result.toString();
    }

    private static final void doFormat(String varName, String network, String channel, Config config, Map vars, StringBuffer result) {
        final String var = ConfigUtils.getVar(config, varName, network, channel);
        if (var == null) {
            log.warn("No variable settings for " + varName);
            return;
        }
        int index = 0;
        while (index < var.length()) {
            char thisChar = var.charAt(index);
            if ((thisChar == '$') && (index + 1 < var.length())) {
                int endVar = var.indexOf('$', index + 1);
                if (endVar != -1) {
                    // Check for ${header}var$
                    String header = "", thisVar = null;
                    if (var.charAt(index + 1) == '{') {
                        int endHeader = var.indexOf('}', index);
                        if ((endHeader > -1) && (endHeader < endVar)) {
                            header = var.substring(index + 2, endHeader);
                            thisVar = var.substring(endHeader + 1, endVar);
                        }
                    }
                    if (thisVar == null) {
                        thisVar = var.substring(index + 1, endVar);
                    }

                    Object thisObj = vars.get(thisVar);
                    if (thisObj == null) {
                        // do nothing
                    } else if (thisObj instanceof Map) {
                        result.append(header);
                        doFormat(varName + "," + thisVar, network, channel, config, (Map)thisObj, result);
                    } else if (thisObj instanceof List) {
                        result.append(header);
                        doFormat(varName + "," + thisVar, network, channel, config, (List)thisObj, result);
                    } else if (!"".equals(thisObj)) { // Just ignore a blank string
                        result.append(header).append(thisObj);
                    }
                    index = endVar;
                } else {
                    result.append(thisChar);
                }
            } else {
                result.append(thisChar);
            }
            index++;
        }
    }

    private static final void doFormat(String varName, String network, String channel, Config config, List list, StringBuffer result) {
        String separator = ConfigUtils.getVar(config, varName + ",sep", network, channel);
        if (separator == null) separator = ",";
        for (Iterator i = list.iterator(); i.hasNext();) {
            final Object thisObj = i.next();
            if (thisObj instanceof Map) {
                doFormat(varName, network, channel, config, (Map)thisObj, result);
            } else {
                result.append(thisObj);
            }
            if (i.hasNext()) result.append(separator);
        }
    }
}
