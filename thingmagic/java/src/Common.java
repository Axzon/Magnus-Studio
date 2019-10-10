import com.thingmagic.*;

public class Common{
    
    /**
     * Application Settings
     * These are common parameters which are intended to be customized
     * based on how the reader and tags are deployed.
     * 
     * URI: specifies how to connect with the reader, for example:
     * - Serial: "tmr:///COM1"
     * - Network: "tmr://192.168.1.100"
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
    static String uri = "tmr:///COM30";
    static int power = 20;
    public static int[] antennas = {1};
    static Reader.Region region = Reader.Region.NA;
    public static Gen2.Session session = Gen2.Session.S0;
    
    /**
     * Reader Performance Settings
     * These parameters can be adjusted to improve performance 
     * in specific scenarios.
     */
    static Gen2.LinkFrequency blf = Gen2.LinkFrequency.LINK250KHZ;
    static Gen2.TagEncoding encoding = Gen2.TagEncoding.M4;
    public static long readTime = 75 * antennas.length;  // milliseconds
    
    // connect to and initialize reader
    public static Reader establishReader() {
        Reader reader = null;
        try {
            reader = Reader.create(uri);
            reader.connect();
        }
        catch (ReaderException e) {
            System.out.println("Error: could not connect to reader at " + uri);
            e.printStackTrace(System.out);
            System.exit(-1);
        }
        try {
            reader.paramSet(TMConstants.TMR_PARAM_RADIO_READPOWER, power * 100);
            // reader.paramSet(TMConstants.TMR_PARAM_RADIO_WRITEPOWER, power * 100);
            if (region != null) {
                reader.paramSet(TMConstants.TMR_PARAM_REGION_ID, region);
            }
            reader.paramSet(TMConstants.TMR_PARAM_GEN2_BLF, blf);
            reader.paramSet(TMConstants.TMR_PARAM_GEN2_TAGENCODING, encoding);
            reader.paramSet(TMConstants.TMR_PARAM_GEN2_SESSION, session);
            reader.paramSet(TMConstants.TMR_PARAM_GEN2_TARGET, Gen2.Target.A);
            reader.paramSet(TMConstants.TMR_PARAM_GEN2_TARI, Gen2.Tari.TARI_25US);
            reader.paramSet(TMConstants.TMR_PARAM_GEN2_SEND_SELECT, true);
            reader.paramSet(TMConstants.TMR_PARAM_GEN2_Q, new Gen2.DynamicQ());
            reader.paramSet(TMConstants.TMR_PARAM_GEN2_INITIAL_Q, new Gen2.InitQ());
            // reader.paramSet(TMConstants.TMR_PARAM_COMMANDTIMEOUT, readTime);
        }
        catch (Exception e) {
            System.out.println("Error: could not initialize reader");
            e.printStackTrace(System.out);
            System.exit(-1);
        }
        return reader;
    }
    
    // create an RFID Gen2 Select Command with custom parameters
    public static Gen2.Select createGen2Select(int target, int action, Gen2.Bank bank, int pointer, int length, byte[] mask) {
        Gen2.Select select = new Gen2.Select(false, bank, pointer, length, mask);
        switch (target) {
            case 0:
                select.target = Gen2.Select.Target.Inventoried_S0;
                break;
            case 1:
                select.target = Gen2.Select.Target.Inventoried_S1;
                break;
            case 2:
                select.target = Gen2.Select.Target.Inventoried_S2;
                break;
            case 3:
                select.target = Gen2.Select.Target.Inventoried_S3;
                break;
            case 4:
                select.target = Gen2.Select.Target.Select;
                break;
            default:
                throw new IllegalArgumentException("invalid target value");
        }
        switch (action) {
            case 0:
                select.action = Gen2.Select.Action.ON_N_OFF;
                break;
            case 1:
                select.action = Gen2.Select.Action.ON_N_NOP;
                break;
            case 2:
                select.action = Gen2.Select.Action.NOP_N_OFF;
                break;
            case 3:
                select.action = Gen2.Select.Action.NEG_N_NOP;
                break;
            case 4:
                select.action = Gen2.Select.Action.OFF_N_ON;
                break;
            case 5:
                select.action = Gen2.Select.Action.OFF_N_NOP;
                break;
            case 6:
                select.action = Gen2.Select.Action.NOP_N_ON;
                break;
            case 7:
                select.action = Gen2.Select.Action.NOP_N_NEG;
                break;
            default:
                throw new IllegalArgumentException("invalid action value");
        }
        return select;
    }
    
    // read multiple registers from one tag singulated by its EPC
    public static short[] readMemBlockByEpc(Reader reader, TagReadData tag, Gen2.Bank bank, int address, int length, int attempts){
        byte[] epcBytes = tag.getTag().epcBytes();
        Gen2.Select epcFilter = createGen2Select(4, 0, Gen2.Bank.EPC, 0x20, epcBytes.length * 8, epcBytes);
        Gen2.ReadData operation = new Gen2.ReadData(bank, address, (byte)length);
        SimpleReadPlan config = new SimpleReadPlan(new int[] { tag.getAntenna() }, TagProtocol.GEN2, epcFilter, operation, 1000);
        short[] values = null;
        try {
            reader.paramSet(TMConstants.TMR_PARAM_READ_PLAN, config);
            for (int i = 0; i < attempts; i++) {
                if (values != null) {
                    break;
                }
                TagReadData[] readResults = reader.read(readTime);
                for (TagReadData readResult: readResults) {
                    if (tag.epcString().equals(readResult.epcString())) {
                        byte[] dataBytes = readResult.getData();
                        if (dataBytes.length != 0) {
                            values = convertByteArrayToShortArray(dataBytes);
                        }
                    }
                }
            }
        }
        catch (ReaderException e) {
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
    public static short[] readMemBlockByEpc(Reader reader, TagReadData tag, Gen2.Bank bank, int address, int length){
        short[] values = readMemBlockByEpc(reader, tag, bank, address, length, 3);
        return values;
    }
    
    // read one register from one tag singulated by its EPC
    public static short readMemByEpc(Reader reader, TagReadData tag, Gen2.Bank bank, int address, int attempts){
        short[] values = readMemBlockByEpc(reader, tag, bank, address, 1, attempts);
        return values[0];
    }
    
    // read one register from one tag singulated by its EPC
    public static short readMemByEpc(Reader reader, TagReadData tag, Gen2.Bank bank, int address){
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