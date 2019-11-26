package nordicid_samples;

import java.util.HashMap;
import com.nordicid.nurapi.*;

public class MagnusS3 {

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
        MagnusS3 m3 = new MagnusS3();
        m3.reader = new NurApi();
        Common.connectReader(m3.reader);
        Common.initializeReader(m3.reader);
        try {
            m3.setupSensorReading();
            for (int i = 1; i <= m3.readAttempts; i++) {
                System.out.println("Read Attempt #" + i);
                NurTag[] results = m3.readSensors();
                if (results.length == 0) {
                    System.out.println("No tag(s) found\n");
                    continue;
                }
                for (NurTag tag: results) {
                    // retrieve calibration if unknown
                    if (!m3.lookupCalibration.containsKey(tag.getEpcString())) {
                        try {
                            short[] calibrationWords = Common.readMemBlockByEpc(tag, NurApi.BANK_USER, 8, 4);
                            TemperatureCalibration cal = new TemperatureCalibration(calibrationWords);
                            m3.lookupCalibration.put(tag.getEpcString(), cal);
                        }
                        catch (RuntimeException e) { }
                    }
                    m3.printSensorResults(tag);
                }
                System.out.println();
            }
            m3.reader.disconnect();
            m3.reader.dispose();
        }
        catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
            e.printStackTrace(System.out);
            System.exit(-1);
        }
    }
    
    void setupSensorReading() {
        // Select command parameters
        CustomExchangeParams temperatureEnable;
        NurInventoryExtendedFilter ocrssiMinFilter;
        NurInventoryExtendedFilter ocrssiMaxFilter;
        NurInventoryExtendedFilter TIDFilter;
        temperatureEnable = Common.createCustomExchangeSelect(NurApi.SESSION_SL, 5, NurApi.BANK_USER, 0xE0, 0, new byte[] { });
        ocrssiMinFilter = Common.createInventoryExtendedSelect(NurApi.SESSION_SL, 0, NurApi.BANK_USER, 0xD0, 8, new byte[] { (byte)(0x20 | (ocrssiMin - 1)) });
        ocrssiMaxFilter = Common.createInventoryExtendedSelect(NurApi.SESSION_SL, 2, NurApi.BANK_USER, 0xD0, 8, new byte[] { ocrssiMax });
        TIDFilter = Common.createInventoryExtendedSelect(NurApi.SESSION_SL, 2, NurApi.BANK_TID, 0x00, 28, new byte[] { (byte)0xE2, (byte)0x82, (byte)0x40, (byte)0x30 });
        // Select command parameters
        this.params = temperatureEnable;
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
        this.config.irAddr = 0xC;
        this.config.irWordCount = 3;
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
            this.reader.customExchange(NurApi.BANK_USER, 0, 0, new byte[] { }, this.params);  // enable Temperature Sensor
            Thread.sleep(3);  // delay to provide CW while Temperature Sensor runs
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
        return results;
    }
    
    void printSensorResults(NurTag tag) {
        System.out.println("* EPC: " + tag.getEpcString());
        short[] dataWords = Common.convertByteArrayToShortArray(tag.getIrData());
        if (dataWords.length == 0) {
            return;
        }
        int moistureCode = dataWords[0];
        int ocrssiCode = dataWords[1];
        int temperatureCode = dataWords[2];
        
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
        String temperatureStatus;
        if (ocrssiCode < 5) {
            temperatureStatus = "power too low";
        }
        else if (ocrssiCode > 18) {
            temperatureStatus = "power too high";
        }
        else if (temperatureCode < 1000 || 4000 < temperatureCode) {
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
    }
    
    static class TemperatureCalibration {
        public boolean valid = false;
        public double slope;
        public double offset;
        private int code1;
        private double temp1;
        private int code2;
        private double temp2;
        private int ver;
        private int crc;

        public TemperatureCalibration(short[] calWords) {
            // convert register contents to variables
            decode(calWords[0], calWords[1], calWords[2], calWords[3]);

            // calculate CRC-16 over non-CRC bytes to compare with stored CRC-16
            byte[] calBytes = Common.convertShortArrayToByteArray(new short[] {calWords[1], calWords[2], calWords[3]});
            int crcCalc = crc16(calBytes);

            // determine if calibration is valid
            if ((ver == 0) && (crc == crcCalc)) {
                slope = (temp2 - temp1) / (double)(code2 - code1);
                offset = temp1 - (slope * (double)code1);
                valid = true;
            }
            else {
                valid = false;
            }
        }

        private void decode(short reg8, short reg9, short regA, short regB) {
            ver = regB & 0x0003;
            temp2 = .1 * ((regB >> 2) & 0x07FF) - 80;
            code2 = ((regA << 3) & 0x0FF8) | ((regB >> 13) & 0x0007);
            temp1 = .1 * (((reg9 << 7) & 0x0780) | ((regA >> 9) & 0x007F)) - 80;
            code1 = (reg9 >> 4) & 0x0FFF;
            crc = reg8 & 0xFFFF;
        }

        // EPC Gen2 CRC-16 Algorithm
        // Poly = 0x1021; Initial Value = 0xFFFF; XOR Output;
        private int crc16(byte[] inputBytes) {
            int crcVal = 0xFFFF;
            for (byte inputByte: inputBytes) {
                crcVal = (crcVal ^ (inputByte << 8));
                for (int i = 0; i < 8; i++) {
                    if ((crcVal & 0x8000) == 0x8000)
                    {
                        crcVal = (crcVal << 1) ^ 0x1021;
                    }
                    else
                    {
                        crcVal = (crcVal << 1);
                    }
                }
                crcVal = crcVal & 0xFFFF;
            }
            crcVal = (crcVal ^ 0xFFFF);
            return crcVal;
        }
    }
}