import com.thingmagic.*;

public class Xerxes {

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
            Gen2.Select globalEnable = Common.createGen2Select(4, 2, Gen2.Bank.USER, 0x3B0, 8, new byte[] { (byte)0x00 });
            Gen2.Select ocrssiMinFilter = Common.createGen2Select(4, 0, Gen2.Bank.USER, 0x3D0, 8, new byte[] { (byte)(0x20 | (ocrssiMin - 1)) });
            Gen2.Select ocrssiMaxFilter = Common.createGen2Select(4, 2, Gen2.Bank.USER, 0x3D0, 8, new byte[] { ocrssiMax });
            MultiFilter selects = new MultiFilter(new Gen2.Select[] { globalEnable, ocrssiMinFilter, ocrssiMaxFilter });
            
            // parameters to read all three sensor codes at once
            Gen2.ReadData operation = new Gen2.ReadData(Gen2.Bank.RESERVED, 0xA, (byte)5);
            
            // create configuration
            SimpleReadPlan config = new SimpleReadPlan(Common.antennas, TagProtocol.GEN2, selects, operation, 1000);
            
            for (int i = 1; i <= readAttempts; i++) {
                System.out.println("Read Attempt #" + i);
                
                // optimize settings for reading sensors
                reader.paramSet(TMConstants.TMR_PARAM_READ_PLAN, config);
                reader.paramSet(TMConstants.TMR_PARAM_GEN2_T4, 9000);  // CW delay in microseconds
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
                                    // read, decode and apply calibration one tag at a time
                                    short[] calibrationWords = Common.readMemBlockByEpc(reader, tag, Gen2.Bank.USER, 0x12, 4);
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
            if ((fmt == 0) && (par1 == par1Calc) && (par2 == par2Calc)) {
                slope = .1 * (temp2 - temp1) / ((double)(code2 - code1) * 0.0625);
                offset = .1 * (temp1 - 600) - (slope * (double)code1 * 0.0625);
                valid = true;
            }
            else {
                valid = false;
            }
        }

        private void decode(short reg12, short reg13, short reg14, short reg15) {
            fmt = (reg15 >> 13) & 0x0007;
            par1 = (reg15 >> 11) & 0x0003;
            temp1 = reg15 & 0x07FF;
            code1 = reg14 & 0xFFFF;
            rfu = (reg13 >> 13) & 0x0007;
            par2 = (reg13 >> 11) & 0x0003;
            temp2 = reg13 & 0x07FF;
            code2 = reg12 & 0xFFFF;
        }
    }
}