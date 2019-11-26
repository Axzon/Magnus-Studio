using System;
using NurApiDotNet;

namespace NordicIdSamples
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
        int readAttempts = 10;
        byte ocrssiMin = 3;
        byte ocrssiMax = 31;

        NurApi reader = null;
        NurApi.IrInformation config = new NurApi.IrInformation();
        NurApi.CustomExchangeParams customExchange = new NurApi.CustomExchangeParams();
        NurApi.InventoryExParams invEx = new NurApi.InventoryExParams();
        NurApi.InventoryExFilter[] filters = new NurApi.InventoryExFilter[3];

        static void Main(string[] args)
        {
            Console.WriteLine("TEST A");
            MagnusS3 m3 = new MagnusS3();
            // m3.reader = new NurApi();
            NurApi reader = new NurApi();
            Console.WriteLine("TEST B");
            return;
            Common.connectReader(m3.reader);
            Common.initializeReader(m3.reader);
            try
            {
                m3.setupSensorReading();
                for (int i = 1; i <= m3.readAttempts; i++)
                {
                    Console.WriteLine("Read Attempt #" + i);
                    Common.configureAntennas(m3.reader, Common.antennas);
                    NurApi.Tag[] results = m3.readSensors();
                    if (results.Length == 0)
                    {
                        Console.WriteLine("No tag(s) found");
                        continue;
                    }
                    foreach (NurApi.Tag tagRead in results)
                    {
                        TemperatureCalibration cal = null;
                        try
                        {
                            short[] calibrationWords = Common.ReadMemBlockByEpc(m3.reader, tagRead, NurApi.BANK_USER, 8, 4);
                            cal = new TemperatureCalibration(calibrationWords);
                        }
                        catch (SystemException)
                        {
                            // reading calibration may fail
                        }
                        m3.printSensorResults(tagRead, cal);
                    }
                    Console.WriteLine();
                }
                m3.reader.Disconnect();
                m3.reader.Dispose();
            }
            catch (Exception e)
            {
                Console.WriteLine("Error: " + e.ToString());
                Environment.Exit(-1);
            }
        }

        void setupSensorReading()
        {
            // Select command parameters
            NurApi.CustomExchangeParams temperatureEnable;
            NurApi.InventoryExFilter ocrssiMinFilter;
            NurApi.InventoryExFilter ocrssiMaxFilter;
            NurApi.InventoryExFilter TIDFilter;
            temperatureEnable = Common.createCustomExchangeSelect(NurApi.SESSION_SL, 5, NurApi.BANK_USER, 0xE0, 0, new byte[] { });
            ocrssiMinFilter = Common.createInventoryExtendedSelect(NurApi.SESSION_SL, 0, NurApi.BANK_USER, 0xD0, 8, new byte[] { (byte)(0x20 | (ocrssiMin - 1)) });
            ocrssiMaxFilter = Common.createInventoryExtendedSelect(NurApi.SESSION_SL, 2, NurApi.BANK_USER, 0xD0, 8, new byte[] { ocrssiMax });
            TIDFilter = Common.createInventoryExtendedSelect(NurApi.SESSION_SL, 2, NurApi.BANK_TID, 0x00, 28, new byte[] { (byte)0xE2, (byte)0x82, (byte)0x40, (byte)0x30 });
            this.customExchange = temperatureEnable;
            this.filters = new NurApi.InventoryExFilter[] { ocrssiMinFilter, ocrssiMaxFilter, TIDFilter };
            // Inventory parameters (Query)
            NurApi.InventoryExParams invEx = new NurApi.InventoryExParams();
            invEx.inventorySelState = NurApi.SELSTATE_SL;
            invEx.session = NurApi.SESSION_S0;
            invEx.inventoryTarget = NurApi.INVTARGET_A;
            invEx.Q = Common.q;
            invEx.rounds = Common.rounds;
            this.invEx = invEx;
            // Read command parameters
            NurApi.IrInformation config = new NurApi.IrInformation();
            config.type = NurApi.NUR_IR_EPCDATA;
            config.bank = NurApi.BANK_PASSWD;
            config.wAddress = 0xC;
            config.wLength = 3;
            config.active = true;
            this.config = config;
        }

        NurApi.Tag[] readSensors()
        {
            NurApi.Tag[] results = { };
            try
            {
                this.reader.InventoryReadCtl = false;
                this.reader.Inventory(1, 1, NurApi.SESSION_S0);
                this.reader.ClearIdBuffer();
                this.reader.SetInventoryRead(ref this.config);
                this.reader.SetExtendedCarrier(true);
                this.reader.CustomExchange(0, false, this.customExchange);
                System.Threading.Thread.Sleep(3);
                NurApi.InventoryResponse response = reader.InventoryEx(ref this.invEx, this.filters);
                this.reader.SetExtendedCarrier(false);
                if (response.numTagsFound != 0)
                {
                    NurApi.TagStorage tagStorage = reader.FetchTags();
                    results = new NurApi.Tag[tagStorage.Count];
                    for (int k = 0; k < results.Length; k++)
                    {
                        results[k] = tagStorage[k];
                    }
                }
                reader.InventoryReadCtl = false;
            }
            catch (Exception e)
            {
                Console.WriteLine(e.ToString());
            }
            return results;
        }

        void printSensorResults(NurApi.Tag tagRead, TemperatureCalibration cal)
        {
            // EPC
            Console.WriteLine("* EPC: " + tagRead.GetEpcString());

            // decode memory read
            short[] dataWords = Common.ConvertByteArrayToShortArray(tagRead.irData);
            if (dataWords.Length == 0)
            {
                return;
            }
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
                moistureStatus = moistureCode + " at " + tagRead.frequency + " kHz";
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
            else if (cal == null)
            {
                temperatureStatus = "failed to read calibration";
            }
            else if (!cal.valid)
            {
                temperatureStatus = "invalid calibration";
            }
            else
            {
                double temperatureValue = cal.slope * temperatureCode + cal.offset;
                temperatureStatus = temperatureValue.ToString("0.00") + " degC";
            }
            Console.WriteLine("  - Temperature: " + temperatureStatus);
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