package com.post.expo;

import org.apache.logging.log4j.core.config.Configurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.json.JSONObject;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class ThreeSixtyServer implements Runnable {

    private Thread worker;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean stopped = new AtomicBoolean(true);

    private final List<ServerListener> serverListeners = new ArrayList<>();
    private final List<ClientListListener> clientListListeners = new ArrayList<>();
    private static ConcurrentHashMap<String, SocketChannel> clientList = new ConcurrentHashMap<>();
    public CustomMap<Integer, Client> cmClients  = new CustomMap<>();
    Selector selector;
    ServerSocketChannel serverSocket;
    ByteBuffer buffer;
    private final ServerDBHandler serverDBHandler = new ServerDBHandler();
    Logger logger; // = LoggerFactory.getLogger(Server.class);

    public ThreeSixtyServer() {
        Configurator.initialize(null, "./resources/log4j2.xml");
	    logger = LoggerFactory.getLogger(ThreeSixtyServer.class);
    }

    public void start() {
        worker = new Thread(this);
        try {
            selector = Selector.open();
            serverSocket = ServerSocketChannel.open();
            serverSocket.bind(new InetSocketAddress(ServerConstants.configuration.get("threesixty_server_ip"), Integer.parseInt(ServerConstants.configuration.get("threesixty_server_port"))));
            serverSocket.configureBlocking(false);
            serverSocket.register(selector, SelectionKey.OP_ACCEPT);
        } catch (IOException e) {
            logger.error(ServerConstants.configuration.get("threesixty_server_ip") + " - " + Integer.parseInt(ServerConstants.configuration.get("threesixty_server_port")));
            logger.error(e.toString());
            //e.printStackTrace();
        }
        buffer = ByteBuffer.allocate(10240000);
        worker.start();
    }

    public void interrupt() {
		running.set(false);
		notifyListeners("Self", "", "360 Disconnected from Java server");
        worker.interrupt();
    }

    boolean isRunning() {
        return running.get();
    }

    boolean isStopped() {
        return stopped.get();
    }

	public void run() {

		running.set(true);
        stopped.set(false);

		while (running.get()) {
		    //System.out.println("Socket running");
            try {
                selector.select();
                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> iter = selectedKeys.iterator();
                while (iter.hasNext()) {
                    SelectionKey key = iter.next();

                    if (key.isValid() && key.isAcceptable()) {
                        register(selector, serverSocket);
                    }

                    if (key.isValid() && key.isReadable()) {
                        readMessage(key);
                    }

                    iter.remove();
                }

            } catch (IOException e) {
                logger.error(e.toString());
                //e.printStackTrace();
            }
		}

		stopped.set(true);
	}

	private void register(Selector selector, ServerSocketChannel serverSocket) {

        SocketChannel client = null;
        String clientName;
        try {
            client = serverSocket.accept();

            client.configureBlocking(false);
            client.register(selector, SelectionKey.OP_READ);
            clientName = client.getRemoteAddress().toString().split("/")[1];
            clientList.put(clientName, client);
            notifyClientListListeners(clientName, 1);
            notifyListeners(clientName, "", "360 client connected");
			//System.out.println("A new Client connected to 360 server");
            logger.error("360 connected from " + clientName);
        } catch (IOException e) {
            //e.printStackTrace();
            logger.error(e.toString());
        }
    }

    public void disconnectClient(SocketChannel client) {

        String clientName = null;
        try {
            clientName = client.getRemoteAddress().toString().split("/")[1];
            client.close();
            clientList.remove(clientName);
            notifyClientListListeners(clientName, 0);
            notifyListeners(clientName, "", "360 client connection terminated");
            logger.error("360 client disconnected from " + clientName);
        } catch (IOException e) {
            //e.printStackTrace();
            logger.error(e.toString());
        }
    }


    private void readMessage(SelectionKey key) {
        SocketChannel client = (SocketChannel) key.channel();
        String clientName = null;
        try {
            clientName = client.getRemoteAddress().toString().split("/")[1];
        } catch (IOException e) {
            logger.error(e.toString());
            //e.printStackTrace();
        }

        buffer.clear();

        int numRead = 0;
        try {
            numRead = client.read(buffer);
        } catch (IOException e) {
            logger.error(e.toString());
            // The remote forcibly closed the connection, cancel
            // the selection key and close the channel.
            disconnectClient(client);

            return;
        }

        if (numRead == -1) {
            // Remote entity shut the socket down cleanly. Do the
            // same from our end and cancel the channel.
            disconnectClient(client);

            return;
        }

        byte[] b = new byte[buffer.position()];
        buffer.flip();
        buffer.get(b);

        String bufferToString = new String( b, StandardCharsets.UTF_16LE );
		//System.out.println(bufferToString);

        /*try {
            JSONObject jo = new JSONObject(bufferToString);
            if(jo.get("req") != null) {
                processClientRequest(clientName, jo);
            }
        } catch (JSONException | ParseException e) {
            logger.error(e.toString());
            //e.printStackTrace();
        }*/
    }

    public void sendWelcomeMessage(String clientName) {
        if((clientList.size() > 0) && (clientList.get(clientName) != null)) {
            String msg = "{\"test\": \"msg\"}";
            sendMessage(clientName, msg);
        } else {
            System.err.println("Client not found");
        }
    }

    public void processClientRequest(String clientName, JSONObject jsonObject) throws ParseException {
        String req = jsonObject.get("req").toString();

    }

    public void sendMessage(String clientName, String msg) {
        msg = msg + ";#;#;";

        if((clientList.size() > 0) && (clientList.get(clientName) != null)) {
            SocketChannel sc = clientList.get(clientName);
            ByteBuffer buf = ByteBuffer.wrap(msg.getBytes());
            try {
                sc.write(buf);
            } catch (IOException e) {
                //e.printStackTrace();
                logger.error(e.toString());
            }
        } else {
            System.err.println("Client not found");
        }
    }

    public void addCmClients(int machineId, Client client) {
        if (!cmClients.containsKey(machineId)) {
            cmClients.put(machineId, client);
            //System.out.println("CM Client added to server");
        }
    }

    public void addListeners(ServerListener serverListener) {
        serverListeners.add(serverListener);
    }

    public void removeListener(ServerListener serverListener) {
        serverListeners.remove(serverListener);
    }

    public void notifyListeners(String fromName, String toName, String msg) {
        if(serverListeners.size() > 0) {
            serverListeners.forEach((el) -> el.update(fromName, toName, msg));
        }
    }

    public void addClientListListeners(ClientListListener clientListListener) {
        clientListListeners.add(clientListListener);

    }

    public void removeClientListListeners(ClientListListener clientListListener) {
        clientListListeners.remove(clientListListener);
    }

    public void notifyClientListListeners(String socketName, int opt) {
        if(clientListListeners.size() > 0) {
            clientListListeners.forEach((el) -> el.updateClientList(socketName, opt));
        }
    }
}
