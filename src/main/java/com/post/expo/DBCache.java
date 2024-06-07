package com.post.expo;

import org.apache.logging.log4j.core.config.Configurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static java.lang.Math.floorDiv;
import static java.lang.String.format;

public class DBCache {

    static Connection dbConn = null;
    private static final Map<Integer, Map<String, String>> machineList = new HashMap<>();
    private static final Map<String, Map<String, String>> alarmData = new HashMap<>();
    private static final Map<Integer, Map<Integer, Integer>> binData = new HashMap<>();
    private static final Map<Integer, Map<Integer, Integer>> deviceData = new HashMap<>();
    private static final Map<Integer, Map<Integer, Integer>> conveyorData = new HashMap<>();
    private static final Map<Integer, Map<Integer, String>> eventData = new HashMap<>();
    private static final Map<Integer, Map<Integer, String>> sensorData = new HashMap<>();
    private static final Map<Integer, Map<Integer, Integer>> inputData = new HashMap<>();
    private static final Map<Integer, Map<Integer, Integer>> inputActiveStates = new HashMap<>();
    private static final Map<Integer, Map<Integer, String>> inputDescriptions = new HashMap<>();
    private static final Map<Integer, Map<Integer, List<Integer>>> machineAlarms = new HashMap<>();
    private static final Map<Integer, List<Integer>> machineBins = new HashMap<>();
    private static final Map<Integer, List<Integer>> machineInputs = new HashMap<>();
    private static final Map<Integer, List<Integer>> machineDevices = new HashMap<>();
    private static final Map<Integer, List<Integer>> machineConveyors = new HashMap<>();
    private static final Map<Integer, List<Integer>> machineEvents = new HashMap<>();
    private static final Map<Integer, List<Integer>> machineSensors = new HashMap<>();
    private static final Map<Integer, List<Integer>> machineHistoryDisabledInputs = new HashMap<>();
    private static final List<Long> existingProducts = new ArrayList<>();
    private static Map<Long, Long> mailIdtoSQLId = new HashMap<>();
    private static long mySQLProductId = 0;
    private static DBCache INSTANCE;
    private static Logger logger;

    private DBCache(){
        try {
            Class.forName("com.mysql.jdbc.Driver");
            //System.out.println("DBCache Database class loaded");
        } catch (ClassNotFoundException e) {
            //e.printStackTrace();
            logger.error(e.toString());
        }

        Configurator.initialize(null, "./resources/log4j2.xml");
	    logger = LoggerFactory.getLogger(DBCache.class);

        loadData();
    }

    public static DBCache getInstance(){
        if(INSTANCE == null){
            INSTANCE = new DBCache();
        }

        //logger.error("DB CACHE GENERATED AFTER INITIAL SLEEP");
        return INSTANCE;
    }

    private static void loadData() {

        try {
            dbConn = DataSource.getConnection();
            Statement stmt = dbConn.createStatement();

            String alarmsQuery = "SELECT * FROM alarms WHERE 1";
            ResultSet rs = stmt.executeQuery(alarmsQuery);
            while (rs.next())
            {
                Map<String, String> singleAlarmData = new HashMap<>();

                String comboId = format("%d%d%d", rs.getInt("machine_id"), rs.getInt("alarm_id"), rs.getInt("alarm_type"));
                singleAlarmData.put("alarm_id", Integer.toString(rs.getInt("alarm_id")));
                singleAlarmData.put("machine_id", Integer.toString(rs.getInt("machine_id")));
                singleAlarmData.put("alarm_type", Integer.toString(rs.getInt("alarm_type")));
                singleAlarmData.put("alarm_class", Integer.toString(rs.getInt("alarm_class")));
                singleAlarmData.put("description", rs.getString("description"));
                singleAlarmData.put("location", rs.getString("location"));
                singleAlarmData.put("variable_name", rs.getString("variable_name"));
                singleAlarmData.put("gui_alarm_id", Integer.toString(rs.getInt("gui_alarm_id")));

                alarmData.put(comboId, singleAlarmData);

                if (!machineAlarms.containsKey(rs.getInt("machine_id"))) {
                    machineAlarms.put(rs.getInt("machine_id"), new HashMap<>());
                }

                if(!machineAlarms.get(rs.getInt("machine_id")).containsKey(rs.getInt("alarm_type")))
                {
                    machineAlarms.get(rs.getInt("machine_id")).put(rs.getInt("alarm_type"), new ArrayList<>());
                }

                machineAlarms.get(rs.getInt("machine_id")).get(rs.getInt("alarm_type")).add(rs.getInt("alarm_id"));
            }

            String binsQuery = "SELECT bin_id, machine_id, gui_bin_id FROM bins WHERE 1";
            rs = stmt.executeQuery(binsQuery);
            while (rs.next())
            {
                if (!machineBins.containsKey(rs.getInt("machine_id"))) {
                    machineBins.put(rs.getInt("machine_id"), new ArrayList<>());
                }

                machineBins.get(rs.getInt("machine_id")).add(rs.getInt("bin_id"));

                if (!binData.containsKey(rs.getInt("machine_id"))) {
                    binData.put(rs.getInt("machine_id"), new HashMap<>());
                }

                if (!binData.get(rs.getInt("machine_id")).containsKey(rs.getInt("bin_id"))) {
                    binData.get(rs.getInt("machine_id")).put(rs.getInt("bin_id"), rs.getInt("gui_bin_id"));
                }
            }

            String inputsQuery = "SELECT input_id, machine_id, gui_input_id, input_type, active_state, enable_history, description, device_type FROM inputs WHERE 1";
            rs = stmt.executeQuery(inputsQuery);
            while (rs.next())
            {
                int inputType = rs.getInt("input_type");
                int activeState = rs.getInt("active_state");
                int deviceType = rs.getInt("device_type");
                int enableHistory = rs.getInt("enable_history");

                if (!machineInputs.containsKey(rs.getInt("machine_id"))) {
                    machineInputs.put(rs.getInt("machine_id"), new ArrayList<>());
                }

                machineInputs.get(rs.getInt("machine_id")).add(rs.getInt("input_id"));

                if (!machineHistoryDisabledInputs.containsKey(rs.getInt("machine_id"))) {
                    machineHistoryDisabledInputs.put(rs.getInt("machine_id"), new ArrayList<>());
                }

                if(enableHistory == 0) {
                    machineHistoryDisabledInputs.get(rs.getInt("machine_id")).add(rs.getInt("input_id"));
                }

                if (!inputData.containsKey(rs.getInt("machine_id"))) {
                    inputData.put(rs.getInt("machine_id"), new HashMap<>());
                }

                if (!inputData.get(rs.getInt("machine_id")).containsKey(rs.getInt("input_id"))) {
                    inputData.get(rs.getInt("machine_id")).put(rs.getInt("input_id"), rs.getInt("gui_input_id"));
                }
                if(deviceType != 0) {
                    if (!inputDescriptions.containsKey(rs.getInt("machine_id"))) {
                        inputDescriptions.put(rs.getInt("machine_id"), new HashMap<>());
                    }

                    if (!inputDescriptions.get(rs.getInt("machine_id")).containsKey(rs.getInt("input_id"))) {
                        inputDescriptions.get(rs.getInt("machine_id")).put(rs.getInt("input_id"), rs.getString("description"));
                    }
                }

                if (!inputActiveStates.containsKey(rs.getInt("machine_id"))) {
                    inputActiveStates.put(rs.getInt("machine_id"), new HashMap<>());
                }

                if (!inputActiveStates.get(rs.getInt("machine_id")).containsKey(rs.getInt("input_id"))) {
                    inputActiveStates.get(rs.getInt("machine_id")).put(rs.getInt("input_id"), rs.getInt("active_state"));
                }

                /*if (!inputActiveStates.get(rs.getInt("machine_id")).containsKey(rs.getInt("input_id"))) {
                    inputActiveStates.get(rs.getInt("machine_id")).put(rs.getInt("device_type"), rs.getInt("device_type"));
                }

                if (!inputActiveStates.get(rs.getInt("machine_id")).containsKey(rs.getInt("input_id"))) {
                    inputActiveStates.get(rs.getInt("machine_id")).put(rs.getInt("device_number"), rs.getInt("device_number"));
                }*/
            }

            String productHistoryIdQuery = format("SELECT product_id FROM %s WHERE 1 ORDER BY product_id DESC LIMIT 1", "product_history");
            ResultSet phidRes = stmt.executeQuery(productHistoryIdQuery);
            long product_history_id = 0;
            if(phidRes.next()) {
                product_history_id = phidRes.getLong("product_id");
                mySQLProductId = product_history_id;
            }

            String productIdQuery = format("SELECT id FROM %s WHERE 1 ORDER BY id DESC LIMIT 1", "products");
            ResultSet pidRes = stmt.executeQuery(productIdQuery);
            long products_id = 0;
            if(pidRes.next()) {
                products_id = pidRes.getLong("id");

                if(products_id > product_history_id) {
                    mySQLProductId = products_id;
                }
            }

            String productsQuery = format("SELECT id, mail_id FROM %s WHERE 1", "products");
            ResultSet productRes = stmt.executeQuery(productsQuery);

            while (productRes.next())
            {
                //existingProducts.add(productRes.getLong("mail_id"));
                mailIdtoSQLId.put(productRes.getLong("mail_id"), productRes.getLong("id"));
            }

            String devicesQuery = "SELECT device_id, machine_id, gui_device_id FROM devices WHERE 1";
            rs = stmt.executeQuery(devicesQuery);
            while (rs.next())
            {
                if (!machineDevices.containsKey(rs.getInt("machine_id"))) {
                    machineDevices.put(rs.getInt("machine_id"), new ArrayList<>());
                }

                machineDevices.get(rs.getInt("machine_id")).add(rs.getInt("device_id"));

                if (!deviceData.containsKey(rs.getInt("machine_id"))) {
                    deviceData.put(rs.getInt("machine_id"), new HashMap<>());
                }

                if (!deviceData.get(rs.getInt("machine_id")).containsKey(rs.getInt("device_id"))) {
                    deviceData.get(rs.getInt("machine_id")).put(rs.getInt("device_id"), rs.getInt("gui_device_id"));
                }
            }

            String conveyorQuery = "SELECT conveyor_id, machine_id, gui_conveyor_id FROM conveyors WHERE 1";
            rs = stmt.executeQuery(conveyorQuery);
            while (rs.next())
            {
                if (!machineConveyors.containsKey(rs.getInt("machine_id"))) {
                    machineConveyors.put(rs.getInt("machine_id"), new ArrayList<>());
                }

                machineConveyors.get(rs.getInt("machine_id")).add(rs.getInt("conveyor_id"));

                if (!conveyorData.containsKey(rs.getInt("machine_id"))) {
                    conveyorData.put(rs.getInt("machine_id"), new HashMap<>());
                }

                if (!conveyorData.get(rs.getInt("machine_id")).containsKey(rs.getInt("conveyor_id"))) {
                    conveyorData.get(rs.getInt("machine_id")).put(rs.getInt("conveyor_id"), rs.getInt("gui_conveyor_id"));
                }
            }

            String eventQuery = "SELECT event_id, machine_id, description FROM events WHERE 1";
            rs = stmt.executeQuery(eventQuery);
            while (rs.next())
            {
                if (!machineEvents.containsKey(rs.getInt("machine_id"))) {
                    machineEvents.put(rs.getInt("machine_id"), new ArrayList<>());
                }

                machineEvents.get(rs.getInt("machine_id")).add(rs.getInt("event_id"));

                if (!eventData.containsKey(rs.getInt("machine_id"))) {
                    eventData.put(rs.getInt("machine_id"), new HashMap<>());
                }

                if (!eventData.get(rs.getInt("machine_id")).containsKey(rs.getInt("event_id"))) {
                    eventData.get(rs.getInt("machine_id")).put(rs.getInt("event_id"), rs.getString("description"));
                }
            }

            String sensorQuery = "SELECT sensor_id, machine_id, description FROM sensors WHERE 1";
            rs = stmt.executeQuery(sensorQuery);
            while (rs.next())
            {
                if (!machineSensors.containsKey(rs.getInt("machine_id"))) {
                    machineSensors.put(rs.getInt("machine_id"), new ArrayList<>());
                }

                machineSensors.get(rs.getInt("machine_id")).add(rs.getInt("sensor_id"));

                if (!sensorData.containsKey(rs.getInt("machine_id"))) {
                    sensorData.put(rs.getInt("machine_id"), new HashMap<>());
                }

                if (!sensorData.get(rs.getInt("machine_id")).containsKey(rs.getInt("sensor_id"))) {
                    sensorData.get(rs.getInt("machine_id")).put(rs.getInt("sensor_id"), rs.getString("description"));
                }
            }

            String machineQuery = "SELECT machine_id, ip_address, machine_name, port_number, maintenance_gui_ip FROM machines WHERE 1";
            rs = stmt.executeQuery(machineQuery);
            while (rs.next())
            {
                Map<String, String> singleMachine = new HashMap<>();
                singleMachine.put("ip_address", rs.getString("ip_address"));
                singleMachine.put("machine_name", rs.getString("machine_name"));
                singleMachine.put("port_number", Integer.toString(rs.getInt("port_number")));
                singleMachine.put("maintenance_ip", rs.getString("maintenance_gui_ip"));

                machineList.put(rs.getInt("machine_id"), singleMachine);

                int machineId = rs.getInt("machine_id");
                String statisticsTbl = "statistics";
                boolean doManualStatsInsertion = false;
                Statement stmt2 = dbConn.createStatement();

                String statisticsSyncQuery = format("SELECT created_at FROM %s WHERE machine_id=%d ORDER BY id DESC LIMIT 1", statisticsTbl, machineId);
                ResultSet rs2 = stmt2.executeQuery(statisticsSyncQuery);

                if(rs2.next()) {
                    String createdAt = rs2.getTimestamp("created_at").toString();
                    Date createdAtDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(createdAt);
                    Date nowDate = new Date();
                    long duration = getDuration(createdAtDate, nowDate, TimeUnit.SECONDS);

                    if(duration > 300) {
                        doManualStatsInsertion = true;
                    }
                } else {
                    doManualStatsInsertion = true;
                }


                if(doManualStatsInsertion) {
                    String datePart1 = new java.text.SimpleDateFormat("yyyy-MM-dd HH:").format(Calendar.getInstance().getTime());

                    String datePart2 = new java.text.SimpleDateFormat("mm").format(Calendar.getInstance().getTime());
                    int datePart2toInt = Integer.parseInt(datePart2);

                    int newMinutes;
                    int minuteRemainder = datePart2toInt % 10;
                    if(minuteRemainder > 4) {
                        newMinutes = floorDiv(datePart2toInt, 10) * 10 + 5;
                    } else {
                        newMinutes = floorDiv(datePart2toInt, 10) * 10;
                    }

                    String newMinutesStr = String.format("%d", newMinutes);

                    if(newMinutes < 10) {
                        newMinutesStr = "0" + newMinutesStr;
                    }

                    String datePart3 = ":00";

                    String manualDate = datePart1 + newMinutesStr + datePart3;
                    String statsInsertQuery = format("INSERT IGNORE INTO statistics (machine_id, created_at) VALUES(%d, '%s')", machineId, manualDate);
                    //Have to check about autocommit false, true
                    dbConn.setAutoCommit(false);
                    stmt2.execute(statsInsertQuery);
                    dbConn.commit();
                    dbConn.setAutoCommit(true);
                }

                rs2.close();
                stmt2.close();
            }

            rs.close();
            stmt.close();
            dbConn.close(); // connection close

        } catch (SQLException | ParseException e) {
            logger.error(e.toString());
            //e.printStackTrace();
        }
    }

    public static Map<String, String> getAlarmData(String id) {
        Map<String, String> singleAlarmData = new HashMap<>();

        if(alarmData.containsKey(id)) {
            singleAlarmData = alarmData.get(id);
        }

        return singleAlarmData;
    }

    public static int getGuiId(int machineId, int id, String type) {
        int guiId = 0;
        Map<Integer, Map<Integer, Integer>> relatedTypeData = new HashMap<>();

        switch (type) {
            case "bin":
                relatedTypeData = binData;
                break;
            case "input":
                relatedTypeData = inputData;
                break;
            case "device":
                relatedTypeData = deviceData;
                break;
            case "conveyor":
                relatedTypeData = conveyorData;
                break;
        }

        if(relatedTypeData.containsKey(machineId) && relatedTypeData.get(machineId).containsKey(id)) {
            guiId = relatedTypeData.get(machineId).get(id);
        }

        return guiId;
    }

    public static int getInputActiveState(int machineId, int id) {
        int activeState = 0;

        if(inputActiveStates.containsKey(machineId) && inputActiveStates.get(machineId).containsKey(id)) {
            activeState = inputActiveStates.get(machineId).get(id);
        }

        return activeState;
    }

    public static String getInputDescription(int machineId, int id) {
        String description = "";

        if(inputDescriptions.containsKey(machineId) && inputDescriptions.get(machineId).containsKey(id)) {
            description = inputDescriptions.get(machineId).get(id);
        }

        return description;
    }

    public static Map<Integer, Map<String, String>> getMachineList() {
        return machineList;
    }

    public static List<Integer> getMachineBins(int machineId) {
        return machineBins.get(machineId);
    }

    public static List<Integer> getMachineDevices(int machineId) {
        return machineDevices.get(machineId);
    }

    public static List<Integer> getMachineInputs(int machineId) {
        return machineInputs.get(machineId);
    }

    public static List<Integer> getMachineConveyors(int machineId) {
        return machineConveyors.get(machineId);
    }

    public static List<Integer> getMachineEvents(int machineId) {
        return machineEvents.get(machineId);
    }
    public static String getEventData(int machineId, int eventId) { return eventData.get(machineId).get(eventId); }

    public static List<Integer> getMachineSensors(int machineId) {
        return machineSensors.get(machineId);
    }
    public static String getSensorData(int machineId, int sensorId) { return sensorData.get(machineId).get(sensorId); }

    public static List<Integer> getMachineHistoryDisabledInputs(int machineId) {
        return machineHistoryDisabledInputs.get(machineId);
    }

    public static List<Integer> getMachineAlarms(int machineId, int jamErrorType) {
        return machineAlarms.get(machineId).get(jamErrorType);
    }

    public static long getDuration(Date date1, Date date2, TimeUnit timeUnit) {
        long diffInMillies = date2.getTime() - date1.getTime();
        return timeUnit.convert(diffInMillies, TimeUnit.MILLISECONDS);
    }

    public static long getMySQLProductId(long mailId) {
        long SQLId = 0;
        if(mailIdtoSQLId.containsKey(mailId)) {
            SQLId = mailIdtoSQLId.get(mailId);
        }
        return SQLId;
    }

    public static void removeSQLId(long mailId) {
        if(mailIdtoSQLId.containsKey(mailId)) {
            mailIdtoSQLId.remove(mailId);
        }
    }

    public static void increaseMySQLProductId(long mailId) {
        mySQLProductId++;
        mailIdtoSQLId.put(mailId, mySQLProductId);
    }
}
