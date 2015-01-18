package com.echbot.modules.gamelookup;

/**
 * @author Chris Pearson
 * @version $Id$
 */
public class QueryException extends Exception {
    public QueryException(String message) {
        super(message);
    }


    public QueryException(String message, Throwable cause) {
        super(message, cause);
    }
}
