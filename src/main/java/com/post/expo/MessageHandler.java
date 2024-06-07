package com.post.expo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.*;

public class MessageHandler {

    private final DatabaseWrapper dbWrapper;
    private final ThreeSixtyClient threeSixtyClient;
    Logger logger = LoggerFactory.getLogger(MessageHandler.class);

    public MessageHandler(ThreeSixtyClient passedThreeSixtyClient, DatabaseHandler databaseHandler) {
        dbWrapper = new DatabaseWrapper(databaseHandler);
        threeSixtyClient = passedThreeSixtyClient;
    }

    public byte[] joinTwoBytesArray(byte[] a, byte[] b) {
        byte[] returnArray = new byte[a.length + b.length];

        ByteBuffer buff = ByteBuffer.wrap(returnArray);
        buff.put(a);
        buff.put(b);

        return buff.array();
    }

    public int[] decodeHeader(byte[] encodedHeader) {
        int[] returnArray = {0, 0};
        if(encodedHeader.length == 8) {
            byte[] messageIdBytes = Arrays.copyOfRange(encodedHeader, 0, 4);
            byte[] messageLengthBytes = Arrays.copyOfRange(encodedHeader, 4, encodedHeader.length);

            long messageId = bytesToLong(messageIdBytes);
            long messageLength = bytesToLong(messageLengthBytes);

            returnArray[0] = (int) messageId;
            returnArray[1] = (int) messageLength;
        }

        return returnArray;
    }

    public int getMessageLength(int messageId, int sizeTable) {
        int messageLength;
        switch (messageId) {
            case 2:
                messageLength = 172;
                break;
            case 3:
            case 7:
            case 9:
            case 11:
            case 13:
            case 15:
                messageLength = 15;
                break;
            case 4:
            case 5:
            case 14:
            case 123:
                messageLength = 20;
                break;
            case 6:
            case 8:
            case 10:
            case 12:
                messageLength = 16 + (sizeTable * 4);
                break;
            case 20:
                messageLength = 33;
                break;
            case 21:
                /*It has to be decided later*/
                messageLength = 333;
                break;
            case 22:
                messageLength = 23;
                break;
            case 16:
            case 30:
            case 101:
            case 102:
            case 103:
            case 105:
            case 106:
            case 107:
            case 108:
            case 112:
            case 116:
            case 130:
                messageLength = 8;
                break;
            case 111:
                messageLength = 11;
                break;
            case 120:
                messageLength = 9;
                break;
            case 125:
                messageLength = 10;
                break;
            default:
                messageLength = 13;
                break;
        }

        return messageLength;
    }

    public byte[] longToBytes(long x, int byteLength) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(x);
        byte[] wordArray = buffer.array();
        byte[] requiredByteArray = new byte[byteLength];
        int wordArrayLength = wordArray.length;
        int ignoredLength = wordArrayLength - byteLength;
        for (int i=0; i < wordArrayLength; i++) {
           if(i > (ignoredLength - 1)) {
               requiredByteArray[i-ignoredLength] = wordArray[i];
           }
        }

        return requiredByteArray;
    }

    public long bytesToLong(byte[] bytes) {
        byte[] fillArray = new byte[Long.BYTES - bytes.length];
        byte[] longArray = joinTwoBytesArray(fillArray, bytes);
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.put(longArray);
        buffer.flip();//need flip
        return buffer.getLong();
    }

    public byte[] encodeHeader(int messageId, int sizeTable) {
        int messageLength = getMessageLength(messageId, sizeTable);
        byte[] messageIdBytes = longToBytes(messageId, 4);
        byte[] messageLengthBytes = longToBytes(messageLength, 4);

        return joinTwoBytesArray(messageIdBytes, messageLengthBytes);
    }

    public byte[] encodeRequestMessage(int messageId) {

        return encodeHeader(messageId, 0);
    }

    public byte[] encodeRequestMessage(int messageId, int mode, int id) {
        int messageLength = getMessageLength(messageId, 0);

        byte[] headerBytes = encodeHeader(messageId, 0);
        int headerLength = headerBytes.length;
        int bodyLength = messageLength - headerLength;
        byte[] bodyBytes = new byte[bodyLength];

        if(messageId == 120) {
            bodyBytes = longToBytes(Integer.toUnsignedLong(mode), 1);
        } else if(messageId == 123) {
            byte[] deviceBytes = longToBytes(Integer.toUnsignedLong(mode), 4);
            byte[] operationBytes = longToBytes(Integer.toUnsignedLong(id), 4);
            byte[] futureBytes = longToBytes(0, 4);

            byte[] devOpBytes = joinTwoBytesArray(deviceBytes, operationBytes);
            bodyBytes = joinTwoBytesArray(devOpBytes, futureBytes);
        } else if(messageId == 111) {
            byte[] idBytes = longToBytes(Integer.toUnsignedLong(id), 2);
            byte[] modeBytes = longToBytes(Integer.toUnsignedLong(mode), 1);

            bodyBytes = joinTwoBytesArray(idBytes, modeBytes);
        } else if(messageId == 125) {
            bodyBytes = longToBytes(Integer.toUnsignedLong(mode), 2);
        }

        return joinTwoBytesArray(headerBytes, bodyBytes);
    }

    public Map<Integer, String> decodeMessage(byte[] b, int machineId) throws IOException {
        int receivedMessageLength = b.length;

        Map<Integer, String> returnStr = new HashMap<>();

        if(b.length < 8) {
            System.out.println("Wrong message. Buffer Length =" + b.length + " Time=" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Calendar.getInstance().getTime()));
            returnStr.put(0, "Wrong message");

        } else {
            byte[] bodyBytes = null;
            byte[] headerBytes = Arrays.copyOfRange(b, 0, 8);

            int[] headerParts = decodeHeader(headerBytes);
            int messageId = headerParts[0];
            int messageLength = headerParts[1];
            List<String> returnMsg = new ArrayList<>();

            returnMsg.add(ServerConstants.MESSAGE_IDS.get(messageId));

            if(messageLength > 8) {
                bodyBytes = Arrays.copyOfRange(b, 8, messageLength);
            }

            if(bodyBytes != null) {
                byte[] timestampBytes = Arrays.copyOfRange(bodyBytes, 0, 4);
                long timestampLong = bytesToLong(timestampBytes);

                byte[] dataBytes = Arrays.copyOfRange(bodyBytes, 4, bodyBytes.length);
                if(messageId == 1) {
                    byte[] currentStateBytes = Arrays.copyOfRange(dataBytes, 0, 1);
                    int currentState = (int) bytesToLong(currentStateBytes);
                    byte[] currentModeBytes = Arrays.copyOfRange(dataBytes, 1, dataBytes.length);
                    int currentMode = (int) bytesToLong(currentModeBytes);
                    returnMsg.add("State =" + ServerConstants.SYSTEM_STATES.get(currentState));
                    returnMsg.add("Mode =" + ServerConstants.SYSTEM_MODES.get(currentMode));
                    if (dbWrapper.updateMachineStateMode(currentState, currentMode, machineId)) {
                        /*if(currentState == 2) {
                            String beltStatusXML = "<belts_status unit=\"ips\">31</belts_status>";
                            threeSixtyClient.sendMessage(5, beltStatusXML);
                        }*/
                        returnMsg.add("DB operations done");
                    }
                } else if(messageId == 44) {
                    byte[] mailIdBytes = Arrays.copyOfRange(dataBytes, 0, 4);
                    byte[] sensorIdBytes = Arrays.copyOfRange(dataBytes, 4, 8);
                    byte[] sensorStatusBytes = Arrays.copyOfRange(dataBytes, 8, 9);


                    long mailId = bytesToLong(mailIdBytes);
                    int sensorId = (int) bytesToLong(sensorIdBytes);
                    String sensorName = DBCache.getSensorData(machineId, sensorId);
                    int sensorStatus = (int) bytesToLong(sensorStatusBytes);

                    if((sensorId == 1) && (sensorStatus == 1)) {
                        if (dbWrapper.processSensorHits(mailId, machineId)) {
                            DBCache.increaseMySQLProductId(mailId);
                            long productId = DBCache.getMySQLProductId(mailId);
                            if(threeSixtyClient.isRunning()) {
                                String newMailPieceXML = "<new_mailpiece piece_id=\"" + productId + "\" lane=\"" + machineId + "\" />";
                                threeSixtyClient.sendMessage(4, newMailPieceXML, mailId, machineId);
                            }
                            returnMsg.add("DB operations done");
                        }
                    }

                    long productId = DBCache.getMySQLProductId(mailId);
                    if(threeSixtyClient.isRunning()) {
                        String sensorHitXML = "<sensor_hit piece_id=\"" + productId + "\" lane=\"" + machineId + "\" name=\"" + sensorName + "\">" + sensorStatus + "</sensor_hit >";
                        threeSixtyClient.sendMessage(3, sensorHitXML, mailId, machineId);
                    }

                    logger.info("Sensor "+ sensorId+" hit=" + sensorStatus + " for ID=" + mailId);
                } else if(messageId == 45) {
                    byte[] eventIdBytes = Arrays.copyOfRange(dataBytes, 0, 4);
                    int eventId = (int) bytesToLong(eventIdBytes);
                    String eventName = DBCache.getEventData(machineId, eventId);
                    //<event type="start_button">1</button>
                    if(threeSixtyClient.isRunning()) {
                        String eventXML = "<event lane=\""+ machineId +"\" type=\""+ eventName.replaceAll(" ", "_").toLowerCase() +"\">"+ eventName  + "</event>";
                        threeSixtyClient.sendMessage(2, eventXML, 0, machineId);
                    }
                    /*if (dbWrapper.processEvents(eventId, machineId)) {
                        logger.info("Event "+ eventId +" for machine=" + machineId);
                        returnMsg.add("DB operations done");
                    }*/


                } else if(ServerConstants.INPUTS_ERRORS_JAMS_DEVICES.contains(messageId)) {
                    int[] intBitSeq = bitSequenceTranslator(dataBytes, 4);
                    boolean dbOperationDone = false;
                    if(messageId == 2 || messageId == 14) {
                        dbOperationDone = dbWrapper.processInputsDevicesStates(intBitSeq, messageId, machineId);
                    } else if(messageId == 4 || messageId == 5) {
                        dbOperationDone = dbWrapper.processAlarms(intBitSeq, messageId, machineId);
                    }

                    if(dbOperationDone) {
                        returnMsg.add("DB operations done");
                    }

                } else if(ServerConstants.MESSAGES_WITH_SIZE_TABLE.contains(messageId)) {
                    byte[] actualDataBytes = Arrays.copyOfRange(dataBytes, 4, dataBytes.length);

                    int byteHop = 4;
                    if(messageId == 42) {
                        byteHop = 1;
                    }

                    int[] intBitSeq = bitSequenceTranslator(actualDataBytes, byteHop);
                    if(messageId == 42) {
                         if (dbWrapper.processInputsDevicesStates(intBitSeq, messageId, machineId)) {
                            returnMsg.add("DB operations done");
                        }
                    } else {
                        if (dbWrapper.processBins(intBitSeq, messageId, machineId)) {
                            returnMsg.add("DB operations done");
                        }
                    }
                } else if(ServerConstants.SINGLE_STATUS_CHANGE_MESSAGES.contains(messageId)) {
                    if(dataBytes.length == 3) {
                        byte[] idBytes = Arrays.copyOfRange(dataBytes, 0, 2);
                        byte[] stateByte = Arrays.copyOfRange(dataBytes, 2, 3);

                        int idLong = (int) bytesToLong(idBytes);
                        int stateValue = (int) bytesToLong(stateByte);

                        if(messageId == 3 || messageId == 15 || messageId == 43) {
                            if(dbWrapper.processSingleInputDeviceState(idLong, stateValue, messageId, machineId)) {
                                returnMsg.add("DB operations done");
                            }
                        } else {
                            if (dbWrapper.processSingleBinState(idLong, stateValue, messageId, machineId)) {
                                returnMsg.add("DB operations done");
                            }
                        }
                    } else {
                        //System.err.println("Error in single status change message. Message ID = " + messageId);
                        logger.error("Error in single status change message. Message ID = " + messageId + " Machine ID = " + machineId);
                    }
                } else if(messageId == 20) {
                    if(dataBytes.length == 21) {
                        //MAIL_ID, Length, Width, Height, Weight, RejectCode
                        byte[] mailIdBytes = Arrays.copyOfRange(dataBytes, 0, 4);
                        byte[] lengthBytes = Arrays.copyOfRange(dataBytes, 4, 8);
                        byte[] widthBytes = Arrays.copyOfRange(dataBytes, 8, 12);
                        byte[] heightBytes = Arrays.copyOfRange(dataBytes, 12, 16);
                        byte[] weightBytes = Arrays.copyOfRange(dataBytes, 16, 20);
                        byte[] rejectCodeByte = Arrays.copyOfRange(dataBytes, 20, 21);

                        long mailId = bytesToLong(mailIdBytes);
                        long length = bytesToLong(lengthBytes);
                        long width = bytesToLong(widthBytes);
                        long height = bytesToLong(heightBytes);
                        long weight = bytesToLong(weightBytes);
                        int rejectCode = (int) bytesToLong(rejectCodeByte);

                        /*if(DBCache.checkExistingProduct(mailId)) {
                            DBCache.increaseMySQLProductId();
                        }*/

                        if (dbWrapper.processDimension(mailId, length, width, height, weight, rejectCode, machineId)) {
                            //DBCache.increaseMySQLProductId();
                            long productId = DBCache.getMySQLProductId(mailId);
                            logger.info("Dimension Processed. ID=" + mailId);
                            if(threeSixtyClient.isRunning()) {

                                String lengthUnit = ServerConstants.configuration.get("threesixty_length_unit");
                                String weightUnit = ServerConstants.configuration.get("threesixty_weight_unit");


                                double lengthForThreeSixty = length;
                                double widthForThreeSixty = width;
                                double heightForThreeSixty = height;

                                if(lengthUnit.equals("in")) {
                                    lengthForThreeSixty = length / 25.4;
                                    widthForThreeSixty = width / 25.4;
                                    heightForThreeSixty = height / 25.4;
                                }

                                DecimalFormat df = new DecimalFormat("#.##");
                                df.setRoundingMode(RoundingMode.UP);

                                double weightForThreeSixty = weight;

                                if(weightUnit.equals("oz")) {
                                    weightForThreeSixty = weight / 28.35;
                                } else if(weightUnit.equals("lb")) {
                                    weightForThreeSixty = weight / 453.6;
                                }

                                String readerXML = "<reader piece_id=\"" + productId + "\" name=\"Length Reader\" lane=\"" + machineId + "\" type=\"LENGTH\" unit=\""+ lengthUnit +"\">" + df.format(lengthForThreeSixty) + "</reader>";
                                threeSixtyClient.sendMessage(4, readerXML, mailId, machineId);
                                readerXML = "<reader piece_id=\"" + productId + "\" name=\"Width Reader\" lane=\"" + machineId + "\" type=\"WIDTH\" unit=\""+ lengthUnit +"\">" + df.format(widthForThreeSixty) + "</reader>";
                                threeSixtyClient.sendMessage(4, readerXML, mailId, machineId);
                                readerXML = "<reader piece_id=\"" + productId + "\" name=\"Height Reader\" lane=\"" + machineId + "\" type=\"HEIGHT\" unit=\""+ lengthUnit +"\">" + df.format(heightForThreeSixty) + "</reader>";
                                threeSixtyClient.sendMessage(4, readerXML, mailId, machineId);
                                readerXML = "<reader piece_id=\"" + productId + "\" name=\"Weight Reader\" lane=\"" + machineId + "\" type=\"WEIGHT\" unit=\""+ weightUnit +"\">" + df.format(weightForThreeSixty) + "</reader>";
                                threeSixtyClient.sendMessage(4, readerXML, mailId, machineId);
                            }
                            returnMsg.add("DB operations done");
                        }
                    } else {
                        //System.err.println("Error in dimension message.");
                        logger.error("Error in dimension message.");
                    }
                } else if(messageId == 21) {
                    byte[] mailIdBytes = Arrays.copyOfRange(dataBytes, 0, 4);
                    long mailId = bytesToLong(mailIdBytes);

                    byte[] numberOfResultsBytes = Arrays.copyOfRange(dataBytes, 4, 6);
                    int numberOfResults = (int) bytesToLong(numberOfResultsBytes);

                    //System.out.println("BARCODE NUM: " + numberOfResults);
                    int barcodeType = 0;

                    //No Question mark only empty
                    String barcodeStringCleaned = "";

                    Map<Integer, Map<String, String>> barcodeTypeWithString = new HashMap<>();
                    Map<Integer, Map<Integer, String>> barcodesFor360 = new HashMap<>();

                    if(numberOfResults > 0) {
                        int startOfCurrentBCBytes = 0;
                        byte[] barcodeFullBytes = Arrays.copyOfRange(dataBytes, 6, dataBytes.length);
                        //System.out.println(barcodeFullBytes);
                        for(int currentBC = 0; currentBC < numberOfResults; currentBC++) {
                            int columnCounter = currentBC + 1;

                            byte[] barcodeTypeByte = Arrays.copyOfRange(dataBytes, 6+startOfCurrentBCBytes, 7+startOfCurrentBCBytes);
                            barcodeType = (int) bytesToLong(barcodeTypeByte);
                            //System.out.println("Type:" + barcodeType);
                            byte[] barcodeLengthBytes = Arrays.copyOfRange(dataBytes, 7+startOfCurrentBCBytes, 9+startOfCurrentBCBytes);
                            int barcodeStringLength = (int) bytesToLong(barcodeLengthBytes);
                            //System.out.println("BC #" + currentBC + " L:" + barcodeStringLength);
                            byte[] barcodeStringBytes = Arrays.copyOfRange(dataBytes, 9+startOfCurrentBCBytes, 9+startOfCurrentBCBytes+barcodeStringLength);

                            String barcodeString = new String(barcodeStringBytes, StandardCharsets.UTF_8);
                            barcodeStringCleaned = barcodeString.replaceAll("\\P{Print}", "");

                            //System.out.println("CODE: " + barcodeStringCleaned);

                            //only limited upto barcode3
                            if(columnCounter < 4) {
                                String barcodeTypeColumnName = "barcode"+ columnCounter + "_type";
                                String barcodeStringColumnName = "barcode"+ columnCounter + "_string";

                                Map<String, String> barcodeAndString = new HashMap<>();
                                barcodeAndString.put(barcodeTypeColumnName, Integer.toString(barcodeType));
                                barcodeAndString.put(barcodeStringColumnName, barcodeStringCleaned);

                                barcodeTypeWithString.put(columnCounter, barcodeAndString);

                                Map<Integer, String> barcodeFor360 = new HashMap<>();
                                barcodeFor360.put(barcodeType, barcodeStringCleaned);

                                barcodesFor360.put(columnCounter, barcodeFor360);
                            }

                            startOfCurrentBCBytes = 3 + startOfCurrentBCBytes + barcodeStringLength;
                        }
                    } else {
                        Map<String, String> barcodeAndString = new HashMap<>();
                        barcodeAndString.put("barcode1_type", Integer.toString(barcodeType));
                        barcodeAndString.put("barcode1_string", barcodeStringCleaned);

                        barcodeTypeWithString.put(1, barcodeAndString);

                        Map<Integer, String> barcodeFor360 = new HashMap<>();
                        barcodeFor360.put(barcodeType, barcodeStringCleaned);

                        barcodesFor360.put(1, barcodeFor360);
                    }

                    if (dbWrapper.processBarcodeResult(mailId, barcodeTypeWithString, numberOfResults, machineId)) {
                        logger.info("Barcode processed. ID=" + mailId);

                        if(barcodesFor360.size() > 0) {
                            for (Map.Entry<Integer, Map<Integer, String>> e : barcodesFor360.entrySet()) {
                                Integer k = e.getKey();
                                Map<Integer, String> v = e.getValue();
                                HashMap<Integer, String> typeAndString = (HashMap<Integer, String>) v;
                                for (Map.Entry<Integer, String> entry : typeAndString.entrySet()) {
                                    Integer bcType = entry.getKey();
                                    String bcString = entry.getValue();
                                    bcString = bcString.replace("'", "");
                                    bcString = bcString.replace("\"", "");
                                    String bcTypeFor360 = ServerConstants.CONVERTED_BARCODES_FOR_360.get(bcType);

                                    long productId = DBCache.getMySQLProductId(mailId);
                                    if(threeSixtyClient.isRunning()) {
                                        String readerXML = "<reader piece_id=\"" + productId + "\" name=\"Barcode Reader\" lane=\"" + machineId + "\" type=\"" + bcTypeFor360 + "\">" + bcString + "</reader>";
                                        threeSixtyClient.sendMessage(4, readerXML, mailId, machineId);
                                    }
                                }
                            }
                        }
                        returnMsg.add("DB operations done");
                    }
                } else if(messageId == 22) {
                    byte[] mailIdBytes = Arrays.copyOfRange(dataBytes, 0, 4);
                    byte[] destinationBytes = Arrays.copyOfRange(dataBytes, 4, 6);
                    byte[] altDestinationBytes = Arrays.copyOfRange(dataBytes, 6, 8);
                    byte[] finalDestinationBytes = Arrays.copyOfRange(dataBytes, 8, 10);
                    byte[] reasonBytes = Arrays.copyOfRange(dataBytes, 10, 11);

                    long mailId = bytesToLong(mailIdBytes);
                    int destination = (int) bytesToLong(destinationBytes);
                    int altDestination = (int) bytesToLong(altDestinationBytes);
                    int finalDestination = (int) bytesToLong(finalDestinationBytes);
                    int reason = (int) bytesToLong(reasonBytes);
                    logger.error("Destination confirmed received. MailId=" + mailId);
                    if (dbWrapper.processConfirmDestination(mailId, destination, altDestination, finalDestination, reason, machineId)) {
                        logger.error("Destination confirmed DB operation done. MailId=" + mailId);
                        long productId = DBCache.getMySQLProductId(mailId);
                        String reasonText = ServerConstants.BIN_REJECT_CODES.get(reason);
                        if(threeSixtyClient.isRunning()) {
                            String pieceStackedXML = "<piece_stacked  piece_id=\"" + productId + "\" bin=\"" + finalDestination + "\" reason=\"" + reasonText + "\">" + finalDestination + "</piece_stacked>";
                            threeSixtyClient.sendMessage(4, pieceStackedXML, mailId, machineId);
                        }
                        DBCache.removeSQLId(mailId);
                        returnMsg.add("DB operations done");
                    }
                }
            } else {
                if(messageId == 16) {
                   //System.out.println("Sync Response");
                } else if(messageId == 30) {
                   //System.out.println("Ping Response");
                }
            }

            if(receivedMessageLength > messageLength) {

                byte[] nextMessage = Arrays.copyOfRange(b, messageLength, receivedMessageLength);

                Map<Integer, String> nextMsgValues = decodeMessage(nextMessage, machineId);
                if(nextMsgValues.size() > 0) {
                    for (Map.Entry<Integer, String> entry : nextMsgValues.entrySet()) {
                        Integer k = entry.getKey();
                        String v = entry.getValue();
                        returnStr.put(k, v);
                    }
                }
            }

            returnStr.put(messageId, String.join(", ", returnMsg));
        }

        return returnStr;
    }

    // Char -> Decimal -> Hex
    public static String convertStringToHex(String str) {

        StringBuilder hex = new StringBuilder();

        // loop chars one by one
        for (char temp : str.toCharArray()) {

            // convert char to int, for char `a` decimal 97
            // convert int to hex, for decimal 97 hex 61
            hex.append(Integer.toHexString(temp));
        }

        return hex.toString();
    }

    public int[] bitSequenceTranslator(byte[] dataBytes, int byteHop) {
        int dataBytesLength = dataBytes.length;

        StringBuilder bitSeq = new StringBuilder();

        for(int i = dataBytesLength; i>0; i-=byteHop) {

            int bytesPartTo = i-byteHop;
            if(bytesPartTo < 0) {
                bytesPartTo = 0;
            }

            byte[] dataBytesPart = Arrays.copyOfRange(dataBytes, bytesPartTo, i);
            long bitSeqLong = bytesToLong(dataBytesPart);

            bitSeq.append(longToBinaryString(bitSeqLong));
        }

        String[] strBitSeqArr = bitSeq.toString().split("");

        int bitSeqLength = bitSeq.length();

        int[] intBitSeqArray = new int[bitSeqLength];

        //LSB is bit 0
        for (int i = 0; i < bitSeqLength; i++) {
            intBitSeqArray[bitSeqLength-i-1] = Integer.parseInt(strBitSeqArr[i]);
        }

        return intBitSeqArray;
    }

    public String longToBinaryString(long x) {
        String str = Long.toBinaryString(x);


        int startBlank = 32 - str.length();

        if(startBlank > 0) {
            str = String.join("", Collections.nCopies(startBlank, "0")) + str;
        }

        return str;
    }

    public byte[] getSystemStateMessage() {
        byte[] messageHeader = encodeHeader(1, 0);
        int currentState = 3;
        byte[] currentStateByte = longToBytes(currentState, 1);

        return joinTwoBytesArray(messageHeader, currentStateByte);
    }
}
