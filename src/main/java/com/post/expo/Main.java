package com.post.expo;

import org.apache.logging.log4j.core.config.Configurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Map;


public class Main {
    public static void main(String[] args) {

        Configurator.initialize(null, "./resources/log4j2.xml");
        Logger logger = LoggerFactory.getLogger(Main.class);

        int initialSleepTime = Integer.parseInt(ServerConstants.configuration.get("initial_sleep_time"));

        MainGui mainGui = new MainGui();
        ClientListener textAreaListener = new TextAreaListener(mainGui);
        mainGui.startGui();

        DBCache dbCache = DBCache.getInstance();

        try {
            Thread.sleep(initialSleepTime * 1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        Server server = new Server();
        server.start();
        logger.error("Server Started");

        ThreeSixtyClient threeSixtyClient = new ThreeSixtyClient(ServerConstants.configuration.get("threesixty_client_ip"), Integer.parseInt(ServerConstants.configuration.get("threesixty_client_port")));
        threeSixtyClient.addListeners(textAreaListener);
        threeSixtyClient.start();

        ThreeSixtyServer threeSixtyServer = new ThreeSixtyServer();
        threeSixtyServer.start();
        logger.error("360 Server Started");

        DatabaseHandler databaseHandler = new DatabaseHandler();

        for (Map.Entry<Integer, Map<String, String>> entry : dbCache.getMachineList().entrySet()) {
            Map<String, String> v = entry.getValue();

            int machineID = entry.getKey();
            String ipAddress = v.get("ip_address");
            int portNumber = Integer.parseInt(v.get("port_number"));

            Client client = new Client(ipAddress, portNumber, machineID, threeSixtyClient, databaseHandler);

            client.addListeners(textAreaListener);
            client.start();

            server.addCmClients(machineID, client);
        }
    }
}
