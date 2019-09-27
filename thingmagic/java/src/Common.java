import com.thingmagic.*;

public class Common{
    
    // Application settings
    static String uri = "tmr:///com30";
    static int power = 30;  // in dBm
    public static int[] antennas = {1};
    static Reader.Region region = Reader.Region.NA;
    
    // Performance settings
    public static Gen2.Session session = Gen2.Session.S0;
    static Gen2.LinkFrequency blf = Gen2.LinkFrequency.LINK250KHZ;
    static Gen2.TagEncoding encoding = Gen2.TagEncoding.M4;
    public static long readTime = 100 * antennas.length;  // milliseconds
    
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
            reader.paramSet(TMConstants.TMR_PARAM_REGION_ID, region);
            reader.paramSet(TMConstants.TMR_PARAM_GEN2_BLF, blf);
            reader.paramSet(TMConstants.TMR_PARAM_GEN2_TAGENCODING, encoding);
            reader.paramSet(TMConstants.TMR_PARAM_GEN2_SESSION, session);
            reader.paramSet(TMConstants.TMR_PARAM_GEN2_TARGET, Gen2.Target.A);
            reader.paramSet(TMConstants.TMR_PARAM_GEN2_TARI, Gen2.Tari.TARI_25US);
            reader.paramSet(TMConstants.TMR_PARAM_GEN2_SEND_SELECT, true);
            reader.paramSet(TMConstants.TMR_PARAM_GEN2_Q, new Gen2.DynamicQ());
            // reader.paramSet(TMConstants.TMR_PARAM_GEN2_InitialQ, new Gen2.DynamicQ());
            // reader.paramSet(TMConstants.TMR_PARAM_COMMANDTIMEOUT, readTime);
        }
        catch (Exception e) {
            System.out.println("Error: could not initialize reader");
            e.printStackTrace(System.out);
            System.exit(-1);
        }
        return reader;
    }
    
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

    public static short[] readMemBlockByEpc(Reader reader, TagReadData tag, Gen2.Bank bank, int address, int length, int attempts){
        byte[] epcBytes = tag.getTag().epcBytes();
        // Filter by EPC
        Gen2.Select epcFilter = createGen2Select(4, 0, Gen2.Bank.EPC, 0x20, epcBytes.length * 8, epcBytes);
        // Specify read parameters
        Gen2.ReadData operation = new Gen2.ReadData(bank, address, (byte)length);
        // Apply settings
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
    
    public static short[] readMemBlockByEpc(Reader reader, TagReadData tag, Gen2.Bank bank, int address, int length){
        short[] values = readMemBlockByEpc(reader, tag, bank, address, length, 3);
        return values;
    }
    
    public static short readMemByEpc(Reader reader, TagReadData tag, Gen2.Bank bank, int address, int attempts){
        short[] values = readMemBlockByEpc(reader, tag, bank, address, 1, attempts);
        return values[0];
    }
    
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