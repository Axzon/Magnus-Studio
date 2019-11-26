using System;
using NurApiDotNet;

namespace NordicIdSamples
{
    public class Common
    {
        /**
         * Application Settings
         * These are common parameters which are intended to be customized
         * based on how the reader and tags are deployed.
         *
         * URI: specifies how to connect with the reader, for example:
         * - Serial: "tmr:///COM1"
         * - Network: "tmr://192.168.1.100"
         *
         * Power: reader transmit power in dBm
         *
         * Antennas: list of active antenna ports
         *
         * Region: select which regulatory region with which to adhere
         *
         * Session: specify which RFID Session Flag to use
         * - S0: smaller tag populations
         * - S1: larger tag populations (along with filtering by OCRSSI)
         */
        static String address = "COM1";
        static int power = 20;  // in dBm
        public static int[] antennas = { 1 };
        static int region = NurApi.REGIONID_FCC;
        static int session = NurApi.SESSION_S0;

        /**
         * Reader Performance Settings
         * These parameters can be adjusted to improve performance
         * in specific scenarios.
         */
        static int blf = 256000;
        static int encoding = NurApi.RXDECODING_M4;
        static int modulation = NurApi.TXMODULATION_PRASK;
        public static int q = 0;  // auto
        public static int rounds = 10;

        // connect to reader
        public static void ConnectReader(NurApi reader)
        {
            try
            {
                if (address.Contains("."))
                {
                    int port = 4333;
                    reader.ConnectSocket(address, port);
                }
                else
                {
                    reader.ConnectUsb(address);
                }
                reader.Ping();
            }
            catch (Exception e)
            {
                Console.WriteLine("Error: could not connect to reader at " + address);
                Console.WriteLine(e.ToString());
                Environment.Exit(-1);
            }
        }

        // initialize reader
        public static void InitializeReader(NurApi reader)
        {
            try
            {
                reader.SetExtendedCarrier(false);
                reader.InventoryReadCtl = false;
                reader.Region = region;
                reader.TxLevel = 30 - power;
                ConfigureAntennas(reader, antennas);
                reader.SelectedAntenna = -1;
                reader.LinkFrequency = blf;
                reader.RxDecoding = encoding;
                reader.TxModulation = modulation;
                reader.InventorySession = session;
                reader.InventoryTarget = NurApi.INVTARGET_A;
                reader.InventoryRounds = rounds;
                reader.AutotuneEnable = false;
                reader.PeriodSetup = 0;
            }
            catch (Exception e)
            {
                Console.WriteLine("Error: could not initialize reader");
                Console.WriteLine(e.ToString());
                Environment.Exit(-1);
            }

        }

        public static void ConfigureAntennas(NurApi reader, int[] antennas)
        {
            uint antennaMask = 0;
            foreach (int antenna in antennas)
            {
                antennaMask += (uint)(1 << (antenna - 1));
            }
            try
            {
                reader.SetEnabledGridAntennas(antennaMask);
            }
            catch (Exception e)
            {
                Console.WriteLine("Error: could not configure antennas");
                Console.WriteLine(e.ToString());
                Environment.Exit(-1);
            }
        }

        // create an RFID Gen2 Select Command with custom parameters
        public static NurApi.CustomExchangeParams CreateCustomExchangeSelect(int target, int action, int bank, int pointer, int length, byte[] mask)
        {
            NurApi.CustomExchangeParams select = new NurApi.CustomExchangeParams();
            try
            {
                select.txLen = (ushort)NurApi.BitBufferAddValue(select.bitBuffer, 0xA, 4, select.txLen);  // CMD = Select (1010)
                select.txLen = (ushort)NurApi.BitBufferAddValue(select.bitBuffer, (uint)target, 3, select.txLen);  // Target = S0/S1/S2/S3/SL
                select.txLen = (ushort)NurApi.BitBufferAddValue(select.bitBuffer, (uint)action, 3, select.txLen);  // Action = assert/deassert
                select.txLen = (ushort)NurApi.BitBufferAddValue(select.bitBuffer, (uint)bank, 2, select.txLen);  // Bank
                select.txLen = (ushort)NurApi.BitBufferAddEBV32(select.bitBuffer, (uint)pointer, select.txLen);  // Pointer (address)
                select.txLen = (ushort)NurApi.BitBufferAddValue(select.bitBuffer, (uint)length, 8, select.txLen);  // Length
                foreach (byte mask_byte in mask)
                {
                    int bits = 8;
                    if (length < 8)
                    {
                        bits = length;
                        byte mask_val = (byte)(mask_byte >> (8 - bits));
                    }
                    select.txLen = (ushort)NurApi.BitBufferAddValue(select.bitBuffer, mask_byte, bits, select.txLen);  // Mask
                    length -= bits;
                }
                select.txLen = (ushort)NurApi.BitBufferAddValue(select.bitBuffer, 0x0, 1, select.txLen);  // Truncate
            }
            catch (Exception e)
            {
                throw new ArgumentException(e.ToString());
            }
            select.asWrite = 1;
            select.txOnly = 1;
            select.noTxCRC = 0;
            select.rxLen = 0;
            select.rxTimeout = 20;
            select.appendHandle = 0;
            select.xorRN16 = 0;
            select.noRxCRC = 0;
            select.rxLenUnknown = 0;
            select.txCRC5 = 0;
            select.rxStripHandle = 0;
            return select;
        }

        public static NurApi.InventoryExFilter CreateInventoryExtendedSelect(int target, int action, int bank, int pointer, int length, byte[] mask)
        {
            NurApi.InventoryExFilter select = new NurApi.InventoryExFilter();
            select.target = (byte)target;
            select.action = (byte)action;
            select.bank = (byte)bank;
            select.address = (byte)pointer;
            select.maskBitLength = length;
            select.maskData = mask;
            select.truncate = false;
            return select;
        }

        // read multiple registers from one tag singulated by its EPC
        public static short[] ReadMemBlockByEpc(NurApi reader, NurApi.Tag tag, int bank, int address, int length, int attempts)
        {
            // NurApi.InventoryExFilter resetFilter = createInventoryExtendedSelect(4, 4, NurApi.BANK_TID, 0x00, 16, new byte[] { 0xE2, 0x82 });
            NurApi.InventoryExFilter epcFilter = CreateInventoryExtendedSelect(4, 0, NurApi.BANK_EPC, 0x20, tag.epc.Length * 8, tag.epc);
            NurApi.InventoryExFilter[] selects = new NurApi.InventoryExFilter[] { epcFilter };

            NurApi.InventoryExParams invEx = new NurApi.InventoryExParams();
            invEx.inventorySelState = NurApi.SELSTATE_SL;
            invEx.session = NurApi.SESSION_S0;
            invEx.inventoryTarget = NurApi.INVTARGET_A;
            invEx.Q = 1;
            invEx.rounds = Common.rounds;

            NurApi.IrInformation config = new NurApi.IrInformation();
            config.type = NurApi.NUR_IR_EPCDATA;
            config.bank = (uint)bank;
            config.wAddress = (uint)address;
            config.wLength = (uint)length;
            config.active = true;
            
            short[] values = null;
            try
            {
                ConfigureAntennas(reader, new int[] { tag.antennaId + 1 });
                reader.SetInventoryRead(ref config);
                for (int i = 0; i < attempts; i++)
                {
                    if (values != null)
                    {
                        break;
                    }
                    reader.ClearIdBuffer();
                    NurApi.InventoryResponse response = reader.InventoryEx(ref invEx, selects);
                    if (response.numTagsFound == 0)
                    {
                        continue;
                    }
                    NurApi.TagStorage tagStorage = reader.FetchTags();
                    NurApi.Tag[] results = new NurApi.Tag[tagStorage.Count];
                    for (int k = 0; k < results.Length; k++)
                    {
                        results[k] = tagStorage[k];
                    }
                        foreach (NurApi.Tag foundTag in results)
                    {
                        if (tag.GetEpcString().Equals(foundTag.GetEpcString()))
                        {
                            values = ConvertByteArrayToShortArray(foundTag.irData);
                        }
                    }
                }
                reader.InventoryReadCtl = false;
            }
            catch (Exception e)
            {
                Console.WriteLine("Error: " + e.ToString());
                Environment.Exit(-1);
            }
            if (values == null)
            {
                throw new SystemException("Tag not found");
            }
            return values;
        }

        // read multiple registers from one tag singulated by its EPC
        public static short[] ReadMemBlockByEpc(NurApi reader, NurApi.Tag tag, int bank, int address, int length)
        {
            short[] values = ReadMemBlockByEpc(reader, tag, bank, address, length, 3);
            return values;
        }

        // read one register from one tag singulated by its EPC
        public static short ReadMemByEpc(NurApi reader, NurApi.Tag tag, int bank, int address, int attempts)
        {
            short[] values = ReadMemBlockByEpc(reader, tag, bank, address, 1, attempts);
            return values[0];
        }

        // read one register from one tag singulated by its EPC
        public static short ReadMemByEpc(NurApi reader, NurApi.Tag tag, int bank, int address)
        {
            short value = ReadMemByEpc(reader, tag, bank, address, 3);
            return value;
        }

        public static byte[] ConvertShortArrayToByteArray(short[] shortArray)
        {
            byte[] byteArray = new byte[shortArray.Length * 2];
            for (int i = 0; i < shortArray.Length; i++)
            {
                byteArray[2 * i] = (byte)((shortArray[i] >> 8) & 0xFF);
                byteArray[2 * i + 1] = (byte)(shortArray[i] & 0xFF);
            }
            return byteArray;
        }

        public static short[] ConvertByteArrayToShortArray(byte[] byteArray)
        {
            short[] shortArray = new short[byteArray.Length / 2];
            for (int i = 0; i < shortArray.Length; i++)
            {
                shortArray[i] = (short)(((byteArray[2 * i] & 0xFF) << 8) | (byteArray[2 * i + 1] & 0xFF));
            }
            return shortArray;
        }
    }
}
