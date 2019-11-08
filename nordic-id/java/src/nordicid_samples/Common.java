package nordicid_samples;

import com.nordicid.nurapi.*;

public class Common{

    /**
     * Application Settings
     * These are common parameters which are intended to be customized
     * based on how the reader and tags are deployed.
     *
     * address: specifies how to connect with the reader, for example:
     * - Serial: "COM1"
     * - Network: "192.168.1.100"
     *
     * Power: reader transmit power in dBm
     *
     * Antennas: list of active antenna ports
     *
     * Region: select which regulatory region with which to adhere
     * - to skip configuring, set to 'null'
     *
     * Session: specify which RFID Session Flag to use
     * - S0: smaller tag populations
     * - S1: larger tag populations (along with filtering by OCRSSI)
     */
    static String address = "172.16.1.116";
    static int power = 20;
    public static int[] antennas = { 4 };
    static int region = NurApi.REGIONID_FCC;
    public static int session = NurApi.SESSION_S0;

    /**
     * Reader Performance Settings
     * These parameters can be adjusted to improve performance
     * in specific scenarios.
     */
    static int blf = NurApi.LINK_FREQUENCY_256000;
    static int encoding = NurApi.RXDECODING_M4;
    static int modulation = NurApi.TXMODULATION_PRASK;
    static int q = 0;  // auto
    static int rounds = 10;

    // connect to reader
    public static void connectReader(NurApi reader) {
        NurApiTransport transport;
        if (address.contains(".")){
            int port = 4333;
            transport = new NurApiSocketTransport(address, port);
        }
        else {
            int baudRate = 11520;
            transport = new NurApiSerialTransport(address, baudRate);
        }
        try {
            reader.setTransport(transport);
            reader.connect();
            reader.ping();
        }
        catch (Exception e) {
            System.out.println("Error: could not connect to reader at " + address);
            e.printStackTrace(System.out);
            System.exit(-1);
        }
    }
    
    public static void initializeReader(NurApi reader) {
        try {
            reader.setExtendedCarrier(false);
            reader.setIRState(false);
            reader.setSetupRegionId(region);
            reader.setSetupTxLevel(30 - power);
            configureAntennas(reader, antennas);
            reader.setSetupSelectedAntenna(-1);
            reader.setSetupLinkFreq(blf);
            reader.setSetupRxDecoding(encoding);
            reader.setSetupInventorySession(session);
            reader.setSetupInventoryTarget(NurApi.INVTARGET_A);
            reader.setSetupInventoryRounds(10);
            AutotuneSetup atSetup = new AutotuneSetup();
            atSetup.mode = AutotuneSetup.ATMODE_OFF;
            reader.setSetupAutotune(atSetup);
            reader.setSetupAutoPeriod(0);
        }
        catch (Exception e) {
            System.out.println("Error: could not initialize reader");
            e.printStackTrace(System.out);
            System.exit(-1);
        }
    }
    
    public static void configureAntennas(NurApi reader, int[] antennas) {
        int antennaMask = 0;
        for (int antenna: antennas) {
            antennaMask += 1 << (antenna - 1);
        }
        try {
            reader.setSetupAntennaMask(antennaMask);
            reader.setSetupAntennaMaskEx(antennaMask);
        }
        catch (Exception e) {
            System.out.println("Error: could not configure antennas");
            e.printStackTrace(System.out);
            System.exit(-1);
        }
    }

    public static CustomExchangeParams createCustomExchangeSelect(int target, int action, int bank, int pointer, int length, byte[] mask){
        CustomExchangeParams select = new CustomExchangeParams();
        try {
            select.txLen = NurApi.bitBufferAddValue(select.bitBuffer, 0xA, 4, select.txLen);  // CMD = Select (1010)
            select.txLen = NurApi.bitBufferAddValue(select.bitBuffer, (long)target, 3, select.txLen);  // Target = S0/S1/S2/S3/SL
            select.txLen = NurApi.bitBufferAddValue(select.bitBuffer, (long)action, 3, select.txLen);  // Action = assert/deassert
            select.txLen = NurApi.bitBufferAddValue(select.bitBuffer, (long)bank, 2, select.txLen);  // Bank
            select.txLen = NurApi.bitBufferAddEBV32(select.bitBuffer, pointer, select.txLen);  // Pointer (address)
            select.txLen = NurApi.bitBufferAddValue(select.bitBuffer, (long)length, 8, select.txLen);  // Length
            for (int mask_byte: mask) {
                int bits = 8;
                if (length < 8) {
                    bits = length;
                    mask_byte = mask_byte >> (8 - bits);
                }
                select.txLen = NurApi.bitBufferAddValue(select.bitBuffer, mask_byte, bits, select.txLen);  // Mask
                length -= bits;
            }
            select.txLen = NurApi.bitBufferAddValue(select.bitBuffer, 0x0, 1, select.txLen);  // Truncate
        }
        catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
        select.asWrite = true;
        select.txOnly = true;
        select.noTxCRC = false;
        select.rxLen = 0;
        select.rxTimeout = 20;
        select.appendHandle = false;
        select.xorRN16 = false;
        select.noRxCRC = false;
        select.rxLenUnknown = false;
        select.txCRC5 = false;
        select.rxStripHandle = false;
        return select;
    }
    
    public static NurInventoryExtendedFilter createInventoryExtendedSelect(int target, int action, int bank, int pointer, int length, byte[] mask){
        NurInventoryExtendedFilter select = new NurInventoryExtendedFilter();
        select.targetSession = target;
        select.action = action;
        select.bank = bank;
        select.address = pointer;
        select.maskBitLength = length;
        select.maskdata = mask;
        select.truncate = false;
        return select;
    }

    // read multiple registers from one tag singulated by its EPC
    public static short[] readMemBlockByEpc(NurApi reader, NurTag tag, int bank, int address, int length, int attempts){
        byte[] epcBytes = tag.getEpc();
        NurInventoryExtendedFilter epcFilter = createInventoryExtendedSelect(4, 0, NurApi.BANK_EPC, 0x20, epcBytes.length * 8, epcBytes);
        // Inventory parameters
        NurInventoryExtended invEx = new NurInventoryExtended();
        invEx.inventorySelState = NurApi.INVSELSTATE_SL;
        invEx.session = NurApi.SESSION_S0;
        invEx.inventoryTarget = NurApi.INVTARGET_A;
        invEx.Q = 1;
        invEx.rounds = Common.rounds;
        // Read command parameters
        NurIRConfig config = new NurIRConfig();
        config.irType = NurApi.IRTYPE_EPCDATA;
        config.irBank = bank;
        config.irAddr = address;
        config.irWordCount = length;
        config.IsRunning = true;
        short[] values = null;
        try {
            configureAntennas(reader, new int[] { tag.getAntennaId() + 1 });
            reader.setIRConfig(config);
            for (int i = 0; i < attempts; i++) {
                if (values != null) {
                    break;
                }
                reader.clearIdBuffer();
                NurRespInventory response = reader.inventoryExtended(invEx, epcFilter);
                if (response.numTagsFound == 0) {
                    continue;
                }
                reader.fetchTags();
                NurTagStorage tagStorage = reader.getStorage();
                NurTag[] results = new NurTag[tagStorage.size()];
                for (int j = 0; j < results.length; j++) {
                    results[j] = tagStorage.get(j);
                }
                for (NurTag foundTag: results) {
                    if (tag.getEpcString().equals(foundTag.getEpcString())) {
                        byte[] dataBytes = foundTag.getIrData();
                        values = convertByteArrayToShortArray(dataBytes);
                    }
                }
            }
        }
        catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
            e.printStackTrace(System.out);
            System.exit(-1);
        }
        if (values == null) {
            throw new RuntimeException("Tag not found");
        }
        return values;
    }

    // read multiple registers from one tag singulated by its EPC
    public static short[] readMemBlockByEpc(NurApi reader, NurTag tag, int bank, int address, int length){
        short[] values = readMemBlockByEpc(reader, tag, bank, address, length, 3);
        return values;
    }

    // read one register from one tag singulated by its EPC
    public static short readMemByEpc(NurApi reader, NurTag tag, int bank, int address, int attempts){
        short[] values = readMemBlockByEpc(reader, tag, bank, address, 1, attempts);
        return values[0];
    }

    // read one register from one tag singulated by its EPC
    public static short readMemByEpc(NurApi reader, NurTag tag, int bank, int address){
        short value = readMemByEpc(reader, tag, bank, address, 3);
        return value;
    }

    public static byte[] convertShortArrayToByteArray(short[] shortArray) {
        byte[] byteArray = new byte[shortArray.length * 2];
        for (int i = 0; i < shortArray.length; i++) {
            byteArray[2 * i] = (byte)((shortArray[i] >> 8) & 0xFF);
            byteArray[2 * i + 1] = (byte)(shortArray[i] & 0xFF);
        }
        return byteArray;
    }

    public static short[] convertByteArrayToShortArray(byte[] byteArray) {
        short[] shortArray = new short[byteArray.length / 2];
        for (int i = 0; i < shortArray.length; i++) {
            shortArray[i] = (short)(((byteArray[2 * i] & 0xFF) << 8) | (byteArray[2 * i + 1] & 0xFF));
        }
        return shortArray;
    }
}
