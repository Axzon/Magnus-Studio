using System;
using ThingMagic;

namespace AxzonDemo
{
    class MagnusS3
    {
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

        static void Main(string[] args)
        {
            try
            {
                // connect to and initialize reader
                Reader reader = Common.EstablishReader();

                // setup sensor activation commands and filters ensuring On-Chip RSSI Min Filter is applied
                Gen2.Select tempsensorEnable = Common.CreateGen2Select(4, 5, Gen2.Bank.USER, 0xE0, 0, new byte[] { });
                Gen2.Select ocrssiMinFilter = Common.CreateGen2Select(4, 0, Gen2.Bank.USER, 0xD0, 8, new byte[] { (byte)(0x20 | ocrssiMin - 1) });
                Gen2.Select ocrssiMaxFilter = Common.CreateGen2Select(4, 2, Gen2.Bank.USER, 0xD0, 8, new byte[] { ocrssiMax });
                MultiFilter selects = new MultiFilter(new Gen2.Select[] { tempsensorEnable, ocrssiMinFilter, ocrssiMaxFilter });

                // parameters to read all three sensor codes at once
                Gen2.ReadData operation = new Gen2.ReadData(Gen2.Bank.RESERVED, 0xC, (byte)3);

                // create configuration
                SimpleReadPlan config = new SimpleReadPlan(Common.antennas, TagProtocol.GEN2, selects, operation, 1000);

                for (int i = 1; i <= readAttempts; i++)
                {
                    Console.WriteLine("\nRead Attempt #" + i);

                    // optimize settings for reading sensors
                    reader.ParamSet("/reader/read/plan", config);
                    reader.ParamSet("/reader/gen2/t4", (UInt32)3000);
                    reader.ParamSet("/reader/gen2/session", Common.session);
                    reader.ParamSet("/reader/gen2/q", new Gen2.DynamicQ());

                    // attempt to read sensor tags
                    TagReadData[] results = reader.Read(Common.readTime);

                    reader.ParamSet("/reader/gen2/t4", (UInt32)300);
                    reader.ParamSet("/reader/gen2/session", Gen2.Session.S0);
                    reader.ParamSet("/reader/gen2/q", new Gen2.StaticQ(0));

                    if (results.Length != 0)
                    {
                        foreach (TagReadData tag in results)
                        {
                            String epc = tag.EpcString;
                            Console.WriteLine("* EPC: " + epc);
                            byte[] dataBytes = tag.Data;
                            short[] dataWords = Common.ConvertByteArrayToShortArray(dataBytes);
                            if (dataWords.Length != 0)
                            {
                                int moistureCode = dataWords[0];
                                int ocrssiCode = dataWords[1];
                                int temperatureCode = dataWords[2];

                                // On-Chip RSSI Sensor
                                Console.WriteLine("  - On-Chip RSSI: " + ocrssiCode);

                                // Moisture Sensor
                                String moistureStatus;
                                if (ocrssiCode < 5)
                                {
                                    moistureStatus = "power too low";
                                }
                                else if (ocrssiCode > 21)
                                {
                                    moistureStatus = "power too high";
                                }
                                else
                                {
                                    moistureStatus = moistureCode + " at " + tag.Frequency + " kHz";
                                }
                                Console.WriteLine("  - Moisture: " + moistureStatus);

                                // Temperature Sensor
                                String temperatureStatus;
                                if (ocrssiCode < 5)
                                {
                                    temperatureStatus = "power too low";
                                }
                                else if (ocrssiCode > 18)
                                {
                                    temperatureStatus = "power too high";
                                }
                                else if (temperatureCode < 1000 || 3500 < temperatureCode)
                                {
                                    temperatureStatus = "bad read";
                                }
                                else
                                {
                                    try
                                    {
                                        // reader.paramSet(TMConstants.TMR_PARAM_TAGOP_ANTENNA, tag.getAntenna());
                                        short[] calibrationWords = Common.ReadMemBlockByEpc(reader, tag, Gen2.Bank.USER, 8, 4);
                                        TemperatureCalibration cal = new TemperatureCalibration(calibrationWords);
                                        if (cal.valid)
                                        {
                                            double temperatureValue = cal.slope * temperatureCode + cal.offset;
                                            temperatureStatus = temperatureValue.ToString("0.00") + " degC";
                                        }
                                        else
                                        {
                                            temperatureStatus = "invalid calibration";
                                        }
                                    }
                                    catch (Exception)
                                    {
                                        temperatureStatus = "failed to read calibration";
                                    }
                                }
                                Console.WriteLine("  - Temperature: " + temperatureStatus);
                            }
                        }
                    }
                    else
                    {
                        Console.WriteLine("No tag(s) found");
                    }
                }
            }
            catch (Exception e)
            {
                Console.WriteLine("Error: " + e.ToString());
                Environment.Exit(-1);
            }
        }

        class TemperatureCalibration
        {
            public bool valid = false;
            public int crc;
            public int code1;
            public double temp1;
            public int code2;
            public double temp2;
            public int ver;
            public double slope;
            public double offset;

            public TemperatureCalibration(short[] calWords)
            {
                // convert register contents to variables
                Decode(calWords[0], calWords[1], calWords[2], calWords[3]);

                // calculate CRC-16 over non-CRC bytes to compare with stored CRC-16 
                byte[] calBytes = Common.ConvertShortArrayToByteArray(new short[] { calWords[1], calWords[2], calWords[3] });
                int crcCalc = Crc16(calBytes);

                // determine if calibration is valid
                if ((ver == 0) && (crc == crcCalc))
                {
                    slope = (temp2 - temp1) / (double)(code2 - code1);
                    offset = temp1 - slope * (double)code1;
                    valid = true;
                }
                else
                {
                    valid = false;
                }
            }

            private void Decode(short reg8, short reg9, short regA, short regB)
            {
                crc = reg8 & 0xFFFF;
                code1 = (reg9 & 0xFFF0) >> 4;
                temp1 = .1 * (((reg9 & 0x000F) << 7) | ((regA & 0xFF80) >> 9)) - 80;
                code2 = ((regA & 0x01FF) << 3) | ((regB & 0xE000) >> 13);
                temp2 = .1 * ((regB & 0x1FFC) >> 2) - 80;
                ver = regB & 0x0003;
            }

            // EPC Gen2 CRC-16 Algorithm
            // Poly = 0x1021; Initial Value = 0xFFFF; XOR Output;
            private int Crc16(byte[] inputBytes)
            {
                int crcVal = 0xFFFF;
                foreach (byte inputByte in inputBytes)
                {
                    crcVal = (crcVal ^ (inputByte << 8));
                    for (int i = 0; i < 8; i++)
                    {
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
}