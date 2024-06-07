package com.post.expo;

import org.apache.logging.log4j.core.config.Configurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class DatabaseHandler implements Runnable {
    static Connection dbConn = null;
    private final BlockingQueue<String> queue = new LinkedBlockingQueue<String>();
    private volatile boolean started = false;
    private volatile boolean stopped = false;
    Logger logger;

    public DatabaseHandler(){

        try {
            Class.forName("com.mysql.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            logger.error(e.toString());
            //e.printStackTrace();
        }

        Configurator.initialize(null, "./resources/log4j2.xml");
	    logger = LoggerFactory.getLogger(DatabaseHandler.class);
        started = true;
        new Thread(this).start();
    }

    public void append(String sql) {
        if (!started) {
            throw new IllegalStateException("open() call expected before append()");
        }

        try {
            queue.put(sql);
        } catch (InterruptedException ignored) {
            logger.error(ignored.toString());
        }
    }

    @Override
    public void run() {
        while (!(stopped && queue.isEmpty())) {
            try {
                String sql = queue.poll(5, TimeUnit.MICROSECONDS);
                if (sql != null) {
                    //System.out.println(sql);
                    try {
                        dbConn = DataSource.getConnection();
                        dbConn.setAutoCommit(false);
                        Statement stmt = dbConn.createStatement();
                        boolean done = stmt.execute(sql);
                        //System.err.println(sql + "\n" + done+ "\n");
                        dbConn.commit();
                        dbConn.setAutoCommit(true);
                        stmt.close();
                        dbConn.close(); // connection close
                    } catch (SQLException e) {
                        logger.error(e.toString());
                        e.printStackTrace();
                    }
                }
            } catch (InterruptedException e) {
                logger.error(e.toString());
                e.printStackTrace();
            }
        }
    }

    public void close() {
        System.out.println("Closing file handler");
        stopped = true;
    }
}
