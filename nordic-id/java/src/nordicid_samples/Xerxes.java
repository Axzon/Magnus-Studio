package nordicid_samples;

import java.util.HashMap;
import com.nordicid.nurapi.*;

public class Xerxes {

    /**
     * Tag Settings
     *
     * Read Attempts: number of tries to read all nearby sensor tags
     *
     * On-Chip RSSI Filters: sensor tags with on-chip RSSI codes outside
     * of these limits won't respond
     */
    int readAttempts = 10;
    byte ocrssiMin = 3;
    byte ocrssiMax = 31;
    
    /**
     * Shared class objects
     */
    NurApi reader = null;
    NurIRConfig config = null;
    CustomExchangeParams params = null;
    NurInventoryExtended invEx = null;
    NurInventoryExtendedFilter[] filters = null;
    HashMap<String, TemperatureCalibration> lookupCalibration = new HashMap<>();

    public static void main(String[] args) {
        Xerxes x1 = new Xerxes();
        x1.reader = new NurApi();
        Common.connectReader(x1.reader);
        Common.initializeReader(x1.reader);
        try {
            x1.setupSensorReading();
            for (int i = 1; i <= x1.readAttempts; i++) {
                System.out.println("Read Attempt #" + i);
                NurTag[] results = x1.readSensors();
                if (results.length == 0) {
                    System.out.println("No tag(s) found\n");
                    continue;
                }
                for (NurTag tag: results) {
                    // retrieve calibration if unknown
                    if (!x1.lookupCalibration.containsKey(tag.getEpcString())) {
                        try {
                            short[] calibrationWords = Common.readMemBlockByEpc(tag, NurApi.BANK_USER, 0x12, 4);
                            TemperatureCalibration cal = new TemperatureCalibration(calibrationWords);
                            x1.lookupCalibration.put(tag.getEpcString(), cal);
                        }
                        catch (RuntimeException e) { }
                    }
                    x1.printSensorResults(tag);
                }
                System.out.println();
            }
            x1.reader.disconnect();
            x1.reader.dispose();
        }
        catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
            e.printStackTrace(System.out);
            System.exit(-1);
        }
    }
    
    void setupSensorReading() {
        // Select command parameters
        CustomExchangeParams globalEnable;
        NurInventoryExtendedFilter ocrssiMinFilter;
        NurInventoryExtendedFilter ocrssiMaxFilter;
        NurInventoryExtendedFilter TIDFilter;
        globalEnable = Common.createCustomExchangeSelect(NurApi.SESSION_SL, 2, NurApi.BANK_USER, 0x3B0, 8, new byte[] { 0x00 });
        ocrssiMinFilter = Common.createInventoryExtendedSelect(NurApi.SESSION_SL, 0, NurApi.BANK_USER, 0x3D0, 8, new byte[] { (byte)(0x20 | (ocrssiMin - 1)) });
        ocrssiMaxFilter = Common.createInventoryExtendedSelect(NurApi.SESSION_SL, 2, NurApi.BANK_USER, 0x3D0, 8, new byte[] { ocrssiMax });
        TIDFilter = Common.createInventoryExtendedSelect(NurApi.SESSION_SL, 2, NurApi.BANK_TID, 0x00, 28, new byte[] { (byte)0xE2, (byte)0x82, (byte)0x40, (byte)0x50 });
        // Select command parameters
        this.params = globalEnable;
        this.filters = new NurInventoryExtendedFilter[] { ocrssiMinFilter, ocrssiMaxFilter, TIDFilter};
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
        this.config.irAddr = 0xA;
        this.config.irWordCount = 5;
        this.config.IsRunning = true;
    }
    
    NurTag[] readSensors() {
        NurTag[] results = { };
        try {
            this.reader.setIRState(false);
            this.reader.inventory(1, 1, NurApi.SESSION_S0);
            this.reader.clearIdBuffer();
            this.reader.setIRConfig(this.config);
            this.reader.setExtendedCarrier(true);
            this.reader.customExchange(NurApi.BANK_USER, 0, 0, new byte[] { }, this.params);  // enable Temperature and Backport Sensors
            Thread.sleep(9);  // delay to provide CW
            NurRespInventory response = this.reader.inventoryExtended(this.invEx, this.filters, this.filters.length);
            this.reader.setExtendedCarrier(false); 
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
        try {
            this.reader.setExtendedCarrier(false); 
        }
        catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
        return results;
    }
    
    void printSensorResults(NurTag tag) {
        System.out.println("* EPC: " + tag.getEpcString());
        short[] dataWords = Common.convertByteArrayToShortArray(tag.getIrData());
        if (dataWords.length == 0) {
            return;
        }
        int backport1Code = dataWords[0];
        int backport2Code = dataWords[1];
        int moistureCode = dataWords[2];
        int ocrssiCode = dataWords[3];
        int temperatureCode = dataWords[4];

        // On-Chip RSSI Sensor
        System.out.println("  - On-Chip RSSI: " + ocrssiCode);

        // Moisture Sensor
        String moistureStatus;
        if (ocrssiCode < 5) {
            moistureStatus = "power too low";
        }
        else if (ocrssiCode > 21) {
            moistureStatus = "power too high";
        }
        else {
            moistureStatus = moistureCode + " at " + tag.getFreq() + " kHz";
        }
        System.out.println("  - Moisture: " + moistureStatus);

        // Temperature Sensor
        
        // Temperature Sensor
        String temperatureStatus;
        if (ocrssiCode < 5) {
            temperatureStatus = "power too low";
        }
        else if (ocrssiCode > 18) {
            temperatureStatus = "power too high";
        }
        else if (temperatureCode < 1000 || 4000 < temperatureCode){
            temperatureStatus = "bad read";
        }
        else if (!this.lookupCalibration.containsKey(tag.getEpcString())) {
            temperatureStatus = "failed to read calibration";
        }
        else {
            TemperatureCalibration cal = this.lookupCalibration.get(tag.getEpcString());
            if (cal.valid) {
                double temperatureValue = cal.slope * temperatureCode + cal.offset;
                temperatureStatus = String.format("%.02f degC", temperatureValue);
            }
            else {
                temperatureStatus = "invalid calibration";
            }
        }
        System.out.println("  - Temperature: " + temperatureStatus);

        // Backport 1 Sensor
        String backport1Status;
        if (ocrssiCode < 5) {
            backport1Status = "power too low";
        }
        else if (ocrssiCode > 18) {
            backport1Status = "power too high";
        }
        else {
            backport1Status = backport1Code + "";
        }
        System.out.println("  - Backport 1: " + backport1Status);

        // Backport 2 Sensor
        String backport2Status;
        if (ocrssiCode < 5) {
            backport2Status = "power too low";
        }
        else if (ocrssiCode > 18) {
            backport2Status = "power too high";
        }
        else {
            backport2Status = backport2Code + "";
        }
        System.out.println("  - Backport 1: " + backport2Status);
    }
    
    static class TemperatureCalibration {
        public boolean valid = false;
        public int fmt;
        public int par1;
        public int temp1;
        public int code1;
        public int rfu;
        public int par2;
        public int temp2;
        public int code2;
        public double slope;
        public double offset;

        public TemperatureCalibration(short[] calWords) {
            // convert register contents to variables
            decode(calWords[0], calWords[1], calWords[2], calWords[3]);

            // calculate parity
            int par1Bit2 = (Integer.bitCount(fmt) + Integer.bitCount(temp1)) % 2;
            int par1Bit1 = Integer.bitCount(code1) % 2;
            int par1Calc = (par1Bit2 << 1) | par1Bit1;
            int par2Bit2 = (Integer.bitCount(rfu) + Integer.bitCount(temp2)) % 2;
            int par2Bit1 = Integer.bitCount(code2) % 2;
            int par2Calc = (par2Bit2 << 1) | par2Bit1;

            // determine if calibration is valid
            if ((fmt == 0) && (par1 == par1Calc) && (par2 == par2Calc) && ((code2 - code1) != 0)) {
                slope = .1 * (temp2 - temp1) / ((double)(code2 - code1) * 0.0625);
                offset = .1 * (temp1 - 600) - (slope * (double)code1 * 0.0625);
                valid = true;
            }
            else {
                valid = false;
            }
        }

        private void decode(short reg12, short reg13, short reg14, short reg15) {
            this.fmt = (reg15 >> 13) & 0x0007;
            this.par1 = (reg15 >> 11) & 0x0003;
            this.temp1 = reg15 & 0x07FF;
            this.code1 = reg14 & 0xFFFF;
            this.rfu = (reg13 >> 13) & 0x0007;
            this.par2 = (reg13 >> 11) & 0x0003;
            this.temp2 = reg13 & 0x07FF;
            this.code2 = reg12 & 0xFFFF;
        }
    }
}