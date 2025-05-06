/*
 * This file is part of the Jose Project
 * see http://jose-chess.sourceforge.net/
 * (c) 2002-2006 Peter Sch�fer
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 */

package de.jose.db;

import de.jose.Application;
import de.jose.Config;

import java.sql.SQLException;
import java.util.*;

public class ConnectionPool
{
    /** note that we deliberately use the *thread-safe* class Vector
     *  b/c the ConnectionPool may be accessed from different threads
     *  (e.g. in the Web App)
     */
    /** set of free connections */
    protected Vector<JoConnection> free;
    /** set of occupied connections */
    protected Vector<JoConnection> occupied;

    protected Timer watchDog;
    protected static long SECOND = 1000;
    protected static long MINUTE = 60*SECOND;
    protected static long HOUR = 60*MINUTE;
    protected static long WATCH_TIME = 2*HOUR; // 20seconds -> 2 hours

    public ConnectionPool(int initialSize)
        throws SQLException
    {
	    free = new Vector<>(initialSize);
	    occupied = new Vector<>();

        while (initialSize-- > 0)
            free.add(create(null));

        watchDog = new Timer(true); // isDaemon = don't stop application from terminating
        watchDog.schedule(new WatchDogTask(),WATCH_TIME,WATCH_TIME);
    }



    public int size()
    {
        return free.size()+occupied.size();
    }

	public boolean isEmpty()
	{
		return size()==0;
	}

    public synchronized JoConnection get()
        throws SQLException
    {
        JoConnection result=null;
        if (!free.isEmpty())
            result = free.remove(free.size() - 1);
        else
            result = create(null);
//        result.initThread();
        result.lastUsed = System.currentTimeMillis();
        occupied.add(result);
//	System.err.println("	pool: "+occupied.size()+" / "+free.size());
	    return result;
    }

    public synchronized void release(JoConnection conn)
    {
        if (conn != null) {
            conn.lastUsed = System.currentTimeMillis();
            occupied.remove(conn);
            free.add(conn);
        }
//	System.err.println("	pool: "+occupied.size()+" / "+free.size());
    }

    public synchronized Date[] freeSince()
    {
        Date result[] = new Date[free.size()];
        for(int i=0; i < free.size(); i++)
            result[i] = new Date(free.get(i).lastUsed);
        return result;
    }

    public synchronized Date[] occupiedSince()
    {
        Date result[] = new Date[occupied.size()];
        for(int i=0; i < occupied.size(); i++)
            result[i] = new Date(occupied.get(i).lastUsed);
        return result;
    }

    public void remove(JoConnection conn)
    {
        if (conn != null) {
            occupied.remove(conn);
            free.remove(conn);
        }
    }

    public void removeAll()
    {
       while (!occupied.isEmpty())
            remove(occupied.get(0));
       while (!free.isEmpty())
             remove(free.get(0));
    }

	public void closeAll()
	{
	   while (!occupied.isEmpty()) {
		   JoConnection conn = occupied.get(0);
		   conn.close();
		   remove(conn);
	   }
	   while (!free.isEmpty()) {
		   JoConnection conn = free.get(0);
		   conn.close();
		   remove(conn);
	   }
       occupied.clear();
       free.clear();
	}



    public JoConnection create(DBAdapter adapter)
        throws SQLException
    {
		JoConnection conn = new JoConnection(adapter,"pooled.connection");
		conn.setAutoCommit(false);
	    return conn;
    }

    private class WatchDogTask extends TimerTask
    {
        @Override
        public void run() {
            long now = System.currentTimeMillis();
            try {
                synchronized (free) {
                    for (int i = free.size() - 1; i >= 0; i--) {
                        JoConnection conn = free.get(i);
                        if ((now - conn.lastUsed) > WATCH_TIME) {
                            free.remove(i);
                            conn.close();
                        }
                    }
                }
                if (free.isEmpty()) get().release();  //  populate with at least ONE connection
            } catch (Throwable e) {
                /* will that stop us? no!   (keep on scheduling) */
                e.printStackTrace();
            }
        }
    }
}
