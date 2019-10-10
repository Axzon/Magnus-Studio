import com.thingmagic.*;

public class MagnusS3 {

    /**
     * Tag Settings
     * 
     * Read Attempts: number of tries to read all nearby sensor tags
     * 
     * On-Chip RSSI Filters: sensor tags with on-chip RSSI codes outside
     * of these limits won't respond. 
     */
    static int readAttempts = 10;
    static byte ocrssiMin = 3;
    static byte ocrssiMax = 31;
    
    public static void main(String[] args) {
        try {
            // connect to and initialize reader
            Reader reader = Common.establishReader();
            
            // setup sensor activation commands and filters ensuring On-Chip RSSI Min Filter is applied
            Gen2.Select tempsensorEnable = Common.createGen2Select(4, 5, Gen2.Bank.USER, 0xE0, 0, new byte[] { });
            Gen2.Select ocrssiMinFilter = Common.createGen2Select(4, 0, Gen2.Bank.USER, 0xD0, 8, new byte[] { (byte)(0x20 | (ocrssiMin - 1)) });
            Gen2.Select ocrssiMaxFilter = Common.createGen2Select(4, 2, Gen2.Bank.USER, 0xD0, 8, new byte[] { ocrssiMax });
            MultiFilter selects = new MultiFilter(new Gen2.Select[] { tempsensorEnable, ocrssiMinFilter, ocrssiMaxFilter });
            
            // parameters to read all three sensor codes at once
            Gen2.ReadData operation = new Gen2.ReadData(Gen2.Bank.RESERVED, 0xC, (byte)3);
            
            // create configuration
            SimpleReadPlan config = new SimpleReadPlan(Common.antennas, TagProtocol.GEN2, selects, operation, 1000);
            
            for (int i = 1; i <= readAttempts; i++) {
                System.out.println("Read Attempt #" + i);
                
                // optimize settings for reading sensors
                reader.paramSet(TMConstants.TMR_PARAM_READ_PLAN, config);
                reader.paramSet(TMConstants.TMR_PARAM_GEN2_T4, 3000);  // CW delay in microseconds
                reader.paramSet(TMConstants.TMR_PARAM_GEN2_SESSION, Common.session);
                reader.paramSet(TMConstants.TMR_PARAM_GEN2_Q, new Gen2.DynamicQ());
                
                // attempt to read sensor tags
                TagReadData[] results = reader.read(Common.readTime);
                
                // optimize settings for reading an individual tag's memory
                reader.paramSet(TMConstants.TMR_PARAM_GEN2_T4, 300);
                reader.paramSet(TMConstants.TMR_PARAM_GEN2_SESSION, Gen2.Session.S0);
                reader.paramSet(TMConstants.TMR_PARAM_GEN2_Q, new Gen2.StaticQ(0));
                
                if (results.length != 0) {
                    for (TagReadData tag: results) {
                        String epc = tag.epcString();
                        System.out.println("* EPC: " + epc);
                        short[] dataWords = Common.convertByteArrayToShortArray(tag.getData());
                        if (dataWords.length != 0) {
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
                                moistureStatus = moistureCode + " at " + tag.getFrequency() + " kHz";
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
                            else if (temperatureCode < 1000 || 3500 < temperatureCode){
                                temperatureStatus = "bad read";
                            }
                            else {
                                try {
                                    // read, decode and apply calibration
                                    short[] calibrationWords = Common.readMemBlockByEpc(reader, tag, Gen2.Bank.USER, 8, 4);
                                    TemperatureCalibration cal = new TemperatureCalibration(calibrationWords);
                                    if (cal.valid) {
                                        double temperatureValue = cal.slope * temperatureCode + cal.offset;
                                        temperatureStatus = String.format("%.02f degC", temperatureValue);
                                    }
                                    else {
                                        temperatureStatus = "invalid calibration";
                                    }
                                }
                                catch (RuntimeException e) {
                                    temperatureStatus = "failed to read calibration";
                                }
                            }
                            System.out.println("  - Temperature: " + temperatureStatus);
                        }
                    }
                }
                else {
                    System.out.println("No tag(s) found");
                }
                System.out.println();
            }
        }
        catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
            e.printStackTrace(System.out);
            System.exit(-1);
        }
    }
    
    static class TemperatureCalibration {
        public boolean valid = false;
        public int crc;
        public int code1;
        public double temp1;
        public int code2;
        public double temp2;
        public int ver;
        public double slope;
        public double offset;

        public TemperatureCalibration(short[] calWords) {
            // convert register contents to variables
            decode(calWords[0], calWords[1], calWords[2], calWords[3]);
            
            // calculate CRC-16 over non-CRC bytes to compare with stored CRC-16 
            byte[] calBytes = Common.convertShortArrayToByteArray(new short[] {calWords[1], calWords[2], calWords[3]});
            int crcCalc = crc16(calBytes);
            
            // determine if calibration is valid
            if ((ver == 0) && (crc == crcCalc)) {
                slope = .1 * (temp2 - temp1) / (double)(code2 - code1);
                offset = .1 * (temp1 - 800) - (slope * (double)code1);
                valid = true;
            }
            else {
                valid = false;
            }
        }

        private void decode(short reg8, short reg9, short regA, short regB) {
            ver = regB & 0x0003;
            temp2 = (regB >> 2) & 0x07FF;
            code2 = ((regA << 3) & 0x0FF8) | ((regB >> 13) & 0x0007);
            temp1 = ((reg9 << 7) & 0x0780) | ((regA >> 9) & 0x007F);
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