using System;
using ThingMagic;

namespace ThingMagicSamples
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
                Gen2.Select globalEnable = Common.CreateGen2Select(4, 2, Gen2.Bank.USER, 0x3B0, 8, new byte[] { (byte)0x00 });
                Gen2.Select ocrssiMinFilter = Common.CreateGen2Select(4, 0, Gen2.Bank.USER, 0x3D0, 8, new byte[] { (byte)(0x20 | ocrssiMin - 1) });
                Gen2.Select ocrssiMaxFilter = Common.CreateGen2Select(4, 2, Gen2.Bank.USER, 0x3D0, 8, new byte[] { ocrssiMax });
                MultiFilter selects = new MultiFilter(new Gen2.Select[] { globalEnable, ocrssiMinFilter, ocrssiMaxFilter });

                // parameters to read all three sensor codes at once
                Gen2.ReadData operation = new Gen2.ReadData(Gen2.Bank.RESERVED, 0xA, (byte)5);

                // create configuration
                SimpleReadPlan config = new SimpleReadPlan(Common.antennas, TagProtocol.GEN2, selects, operation, 1000);

                for (int i = 1; i <= readAttempts; i++)
                {
                    Console.WriteLine("\nRead Attempt #" + i);

                    // optimize settings for reading sensors
                    reader.ParamSet("/reader/read/plan", config);
                    reader.ParamSet("/reader/gen2/t4", (UInt32)9000);  // CW delay in microseconds
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
                                int backport1Code = dataWords[0];
                                int backport2Code = dataWords[1];
                                int moistureCode = dataWords[2];
                                int ocrssiCode = dataWords[3];
                                int temperatureCode = dataWords[4];

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
                                        // read, decode and apply calibration one tag at a time
                                        short[] calibrationWords = Common.ReadMemBlockByEpc(reader, tag, Gen2.Bank.USER, 0x12, 4);
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

                                // Backport 1 Sensor
                                String backport1Status;
                                if (ocrssiCode < 5)
                                {
                                    backport1Status = "power too low";
                                }
                                else if (ocrssiCode > 18)
                                {
                                    backport1Status = "power too high";
                                }
                                else
                                {
                                    backport1Status = backport1Code + "";
                                }
                                Console.WriteLine("  - Backport 1: " + backport1Status);

                                // Backport 1 Sensor
                                String backport2Status;
                                if (ocrssiCode < 5)
                                {
                                    backport2Status = "power too low";
                                }
                                else if (ocrssiCode > 18)
                                {
                                    backport2Status = "power too high";
                                }
                                else
                                {
                                    backport2Status = backport2Code + "";
                                }
                                Console.WriteLine("  - Backport 2: " + backport2Status);
                            }
                        }
                    }
                    else
                    {
                        Console.WriteLine("No tag(s) found");
                    }
                    Console.WriteLine();
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

            public TemperatureCalibration(short[] calWords)
            {
                // convert register contents to variables
                Decode(calWords[0], calWords[1], calWords[2], calWords[3]);

                // calculate parity
                int par1Bit2 = (CountBits(fmt) + CountBits(temp1)) % 2;
                int par1Bit1 = CountBits(code1) % 2;
                int par1Calc = (par1Bit2 << 1) | par1Bit1;
                int par2Bit2 = (CountBits(rfu) + CountBits(temp2)) % 2;
                int par2Bit1 = CountBits(code2) % 2;
                int par2Calc = (par2Bit2 << 1) | par2Bit1;

                // determine if calibration is valid
                if ((fmt == 0) && (par1 == par1Calc) && (par2 == par2Calc))
                {
                    slope = .1 * (temp2 - temp1) / ((double)(code2 - code1) * 0.0625);
                    offset = .1 * (temp1 - 600) - (slope * (double)code1 * 0.0625);
                    valid = true;
                }
                else
                {
                    valid = false;
                }
            }

            private void Decode(short reg12, short reg13, short reg14, short reg15)
            {
                fmt = (reg15 >> 13) & 0x0007;
                par1 = (reg15 >> 11) & 0x0003;
                temp1 = reg15 & 0x07FF;
                code1 = reg14 & 0xFFFF;
                rfu = (reg13 >> 13) & 0x0007;
                par2 = (reg13 >> 11) & 0x0003;
                temp2 = reg13 & 0x07FF;
                code2 = reg12 & 0xFFFF;
            }

            public static int CountBits(int value)
            {
                int count = 0;
                while (value != 0)
                {
                    if ((value & 0x1) == 1)
                    {
                        count++;
                    }
                    value = value >> 1;
                }
                return count;
            }
        }
    }
}