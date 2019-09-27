//import java.util.EnumSet;
import com.thingmagic.*;

public class MagnusS3 {

    static int readAttempts = 10;
    static byte ocrssiMin = 3;
    static byte ocrssiMax = 31;
    
    public static void main(String[] args) {
        try {
            Reader reader = Common.establishReader();
            reader.paramSet(TMConstants.TMR_PARAM_GEN2_T4, 3000);  // microseconds
            
            Gen2.Select tempsensorEnable = Common.createGen2Select(4, 5, Gen2.Bank.USER, 0xE0, 0, new byte[] {});
            Gen2.Select ocrssiMinFilter = Common.createGen2Select(4, 0, Gen2.Bank.USER, 0xD0, 8, new byte[] {(byte)(0x20 | ocrssiMin)});
            Gen2.Select ocrssiMaxFilter = Common.createGen2Select(4, 2, Gen2.Bank.USER, 0xD0, 8, new byte[] {ocrssiMax});
            MultiFilter selects = new MultiFilter(new Gen2.Select[] {tempsensorEnable, ocrssiMinFilter, ocrssiMaxFilter});
            
//            EnumSet<Gen2.Bank> banks = EnumSet.of(Gen2.Bank.RESERVED, Gen2.Bank.GEN2BANKRESERVEDENABLED, Gen2.Bank.GEN2BANKTIDENABLED, Gen2.Bank.GEN2BANKUSERENABLED);
//            Gen2.ReadData operation = new Gen2.ReadData(banks, 0, (byte)0);
            Gen2.ReadData operation = new Gen2.ReadData(Gen2.Bank.RESERVED, 0xC, (byte)3);
            
            SimpleReadPlan config = new SimpleReadPlan(Common.antennas, TagProtocol.GEN2, selects, operation, 1000);
            
            for (int i = 1; i <= readAttempts; i++) {
                System.out.println("\nRead Attempt #" + i);
                reader.paramSet(TMConstants.TMR_PARAM_READ_PLAN, config);
                reader.paramSet(TMConstants.TMR_PARAM_GEN2_T4, 3000);
                reader.paramSet(TMConstants.TMR_PARAM_GEN2_SESSION, Common.session);
                reader.paramSet(TMConstants.TMR_PARAM_GEN2_Q, new Gen2.DynamicQ());
                
                TagReadData[] results = reader.read(Common.readTime);
                
                reader.paramSet(TMConstants.TMR_PARAM_GEN2_T4, 300);
                reader.paramSet(TMConstants.TMR_PARAM_GEN2_SESSION, Gen2.Session.S0);
                reader.paramSet(TMConstants.TMR_PARAM_GEN2_Q, new Gen2.StaticQ(0));
                
                if (results.length != 0) {
                    for (TagReadData tag: results) {
                        String epc = tag.epcString();
                        System.out.println("* EPC: " + epc);
                        byte[] dataBytes = tag.getData();
                        short[] dataWords = Common.convertByteArrayToShortArray(dataBytes);
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
                                    // reader.paramSet(TMConstants.TMR_PARAM_TAGOP_ANTENNA, tag.getAntenna());
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
            decode(calWords[0], calWords[1], calWords[2], calWords[3]);

            byte[] calBytes = Common.convertShortArrayToByteArray(new short[] {calWords[1], calWords[2], calWords[3]});
            int crcCalc = crc16(calBytes);

            if ((ver == 0) && (crc == crcCalc)) {
                slope = (temp2 - temp1) / (double)(code2 - code1);
                offset = temp1 - slope * (double)code1;
                valid = true;
            }
            else {
                valid = false;
            }
        }

        private void decode(short reg8, short reg9, short regA, short regB) {
            crc = reg8 & 0xFFFF;
            code1 = (reg9 & 0xFFF0) >> 4;
            temp1 = .1 * (((reg9 & 0x000F) << 7) | ((regA & 0xFF80) >> 9)) - 80;
            code2 = ((regA & 0x01FF) << 3) | ((regB & 0xE000) >> 13);
            temp2 = .1 * ((regB & 0x1FFC) >> 2) - 80;
            ver = regB & 0x0003;
        }

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
                crcVal = crcVal & 0xffff;
            }
            crcVal = (crcVal ^ 0xffff);
            return crcVal;
        }
    }
}