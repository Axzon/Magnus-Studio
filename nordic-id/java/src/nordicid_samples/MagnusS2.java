package nordicid_samples;

import com.nordicid.nurapi.*;

public class MagnusS2 {

    /**
     * Tag Settings
     *
     * Read Attempts: number of tries to read all nearby sensor tags
     *
     * On-Chip RSSI Filters: sensor tags with on-chip RSSI codes outside
     * of these limits won't respond
     * 
     * moistureMode: toggle between reading Moisture or On-Chip RSSI
     * 
     */
    int readAttempts = 10;
    byte ocrssiMin = 3;
    byte ocrssiMax = 31;
    boolean moistureMode = true;
    
    /**
     * Shared class objects
     */
    NurApi reader;
    NurIRConfig config;
    CustomExchangeParams params;
    NurInventoryExtended invEx;
    NurInventoryExtendedFilter[] filters;

    public static void main(String[] args) {
        MagnusS2 m2 = new MagnusS2();
        m2.reader = new NurApi();
        Common.connectReader(m2.reader);
        Common.initializeReader(m2.reader);
        try {
            m2.setupSensorReading();
            for (int i = 1; i <= m2.readAttempts; i++) {
                System.out.println("Read Attempt #" + i);
                NurTag[] results = m2.readSensors();
                if (results.length == 0) {
                    System.out.println("No tag(s) found");
                }
                else {
                    m2.printSensorResults(results);
                }
                System.out.println();
            }
            m2.reader.disconnect();
            m2.reader.dispose();
        }
        catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
            e.printStackTrace(System.out);
            System.exit(-1);
        }
    }
    
    void setupSensorReading() {
        // Select command parameters
        NurInventoryExtendedFilter resetFlag;
        NurInventoryExtendedFilter ocrssiMinFilter;
        NurInventoryExtendedFilter ocrssiMaxFilter;
        NurInventoryExtendedFilter TIDFilter;
        resetFlag = Common.createInventoryExtendedSelect(NurApi.SESSION_SL, 5, NurApi.BANK_TID, 0x00, 24, new byte[] { (byte)0xE2, (byte)0x82, (byte)0x40 });
        ocrssiMinFilter = Common.createInventoryExtendedSelect(NurApi.SESSION_SL, 0, NurApi.BANK_USER, 0xA0, 8, new byte[] { (byte)(0x20 | (ocrssiMin - 1)) });
        ocrssiMaxFilter = Common.createInventoryExtendedSelect(NurApi.SESSION_SL, 2, NurApi.BANK_USER, 0xA0, 8, new byte[] { ocrssiMax });
        TIDFilter = Common.createInventoryExtendedSelect(NurApi.SESSION_SL, 2, NurApi.BANK_TID, 0x00, 28, new byte[] { (byte)0xE2, (byte)0x82, (byte)0x40, (byte)0x20 });
        // Select command parameters
        this.filters = new NurInventoryExtendedFilter[] { resetFlag, ocrssiMinFilter, ocrssiMaxFilter, TIDFilter};
        // Inventory parameters
        this.invEx = new NurInventoryExtended();
        this.invEx.inventorySelState = NurApi.INVSELSTATE_SL;
        this.invEx.session = Common.session;
        this.invEx.inventoryTarget = NurApi.INVTARGET_A;
        this.invEx.Q = Common.q;  // auto
        this.invEx.rounds = Common.rounds;
        // Read command parameters
        this.config = new NurIRConfig();
        this.config.irType = NurApi.IRTYPE_EPCDATA;
        this.config.irBank = NurApi.BANK_PASSWD;
        if (moistureMode) {
            this.config.irAddr = 0xB;
        }
        else {
            this.config.irAddr = 0xD;
        }
        this.config.irWordCount = 1;
        this.config.IsRunning = true;
    }
    
    NurTag[] readSensors() {
        NurTag[] results = { };
        try {
            this.reader.clearIdBuffer();
            this.reader.setIRConfig(this.config);
            NurRespInventory response = this.reader.inventoryExtended(this.invEx, this.filters, this.filters.length);
            if (response.numTagsFound != 0) {
                this.reader.fetchTags();
                NurTagStorage tagStorage = this.reader.getStorage();
                results = new NurTag[tagStorage.size()];
                for (int i = 0; i < results.length; i++) {
                    results[i] = tagStorage.get(i);
                }
            }
            this.reader.setIRState(false);
        }
        catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
        return results;
    }
    
    void printSensorResults(NurTag[] results) {
        for (NurTag tag: results) {
            System.out.println("* EPC: " + tag.getEpcString());
            short[] dataWords = Common.convertByteArrayToShortArray(tag.getIrData());
            if (dataWords.length == 0) {
                continue;
            }
            if (moistureMode) {
                System.out.println("  - Moisture: " + dataWords[0] + " at " + tag.getFreq() + " kHz");
            }
            else {
                System.out.println("  - On-Chip RSSI: " + dataWords[0]);
            }
        }
    }
}