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
         *
         * Mode: select which sensor to use
         * - Moisture: 'true'
         * - On-Chip RSSI: 'false'
         */
        static int readAttempts = 10;
        static byte ocrssiMin = 3;
        static byte ocrssiMax = 31;
        static bool moistureMode = true;

        static void Main(string[] args)
        {
            try
            {
                // connect to and initialize reader
                Reader reader = Common.EstablishReader();

                // setup sensor activation commands and filters ensuring On-Chip RSSI Min Filter is applied
                Gen2.Select tempsensorEnable = Common.CreateGen2Select(4, 4, Gen2.Bank.TID, 0x00, 16, new byte[] { (byte)0xE2, (byte)0x82 });
                Gen2.Select ocrssiMinFilter = Common.CreateGen2Select(4, 0, Gen2.Bank.USER, 0xA0, 8, new byte[] { (byte)(0x20 | ocrssiMin - 1) });
                Gen2.Select ocrssiMaxFilter = Common.CreateGen2Select(4, 2, Gen2.Bank.USER, 0xA0, 8, new byte[] { ocrssiMax });
                MultiFilter selects = new MultiFilter(new Gen2.Select[] { tempsensorEnable, ocrssiMinFilter, ocrssiMaxFilter });
                
                Gen2.ReadData operation;
                if (moistureMode)
                {
                    // read parameters for moisture code
                    operation = new Gen2.ReadData(Gen2.Bank.RESERVED, 0xB, (byte)1);
                }
                else
                {
                    // read parameters for on-chip RSSI code
                    operation = new Gen2.ReadData(Gen2.Bank.RESERVED, 0xD, (byte)1);
                }

                // create configuration
                SimpleReadPlan config = new SimpleReadPlan(Common.antennas, TagProtocol.GEN2, selects, operation, 1000);
                reader.ParamSet("/reader/read/plan", config);

                for (int i = 1; i <= readAttempts; i++)
                {
                    Console.WriteLine("Read Attempt #" + i);

                    // attempt to read sensor tags
                    TagReadData[] results = reader.Read(Common.readTime);

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
                                if (moistureMode)
                                {
                                    Console.WriteLine("  - Moisture: " + dataWords[0] + " at " + tag.Frequency + " kHz");
                                }
                                else
                                {
                                    Console.WriteLine("  - On-Chip RSSI: " + dataWords[0]);
                                }
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
    }
}