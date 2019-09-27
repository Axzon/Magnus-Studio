using System;
using ThingMagic;

namespace AxzonDemo
{
    public class Common
    {
        // Application settings
        static String uri = "tmr:///com30";
        static int power = 20;  // in dBm
        public static int[] antennas = { 1 };
        static Reader.Region region = Reader.Region.NA;

        // Performance settings
        public static Gen2.Session session = Gen2.Session.S0;
        static Gen2.LinkFrequency blf = Gen2.LinkFrequency.LINK250KHZ;
        static Gen2.TagEncoding encoding = Gen2.TagEncoding.M4;
        public static int readTime = 100 * antennas.Length;  // milliseconds

        public static Reader EstablishReader()
        {
            Reader reader = null;
            try
            {
                reader = Reader.Create(uri);
                reader.Connect();
            }
            catch (ReaderException e)
            {
                Console.WriteLine("Error: could not connect to reader at " + uri);
                Console.WriteLine(e.ToString());
                Environment.Exit(-1);
            }
            try
            {
                reader.ParamSet("/reader/radio/readPower", power * 100);
                // reader.ParamSet("/reader/radio/writePower", power * 100);
                reader.ParamSet("/reader/region/id", region);
                reader.ParamSet("/reader/gen2/BLF", blf);
                reader.ParamSet("/reader/gen2/tagEncoding", encoding);
                reader.ParamSet("/reader/gen2/session", session);
                reader.ParamSet("/reader/gen2/target", Gen2.Target.A);
                reader.ParamSet("/reader/gen2/tari", Gen2.Tari.TARI_25US);
                reader.ParamSet("/reader/gen2/sendSelect", true);
                reader.ParamSet("/reader/gen2/q", new Gen2.DynamicQ());
                // reader.ParamSet("/reader/gen2/initQ", 6);
                // reader.ParamSet("/reader/commandTimeout", 100);
            }
            catch (Exception e)
            {
                Console.WriteLine("Error: could not initialize reader");
                Console.WriteLine(e.ToString());
                Environment.Exit(-1);
            }
            return reader;
        }

        public static Gen2.Select CreateGen2Select(int target, int action, Gen2.Bank bank, int pointer, int length, byte[] mask)
        {
            Gen2.Select select = new Gen2.Select(false, bank, (uint)pointer, (ushort)length, mask);
            switch (target)
            {
                case 0:
                    select.target = Gen2.Select.Target.Inventoried_S0;
                    break;
                case 1:
                    select.target = Gen2.Select.Target.Inventoried_S1;
                    break;
                case 2:
                    select.target = Gen2.Select.Target.Inventoried_S2;
                    break;
                case 3:
                    select.target = Gen2.Select.Target.Inventoried_S3;
                    break;
                case 4:
                    select.target = Gen2.Select.Target.Select;
                    break;
                default:
                    throw new ArgumentException("invalid target value");
            }
            switch (action)
            {
                case 0:
                    select.action = Gen2.Select.Action.ON_N_OFF;
                    break;
                case 1:
                    select.action = Gen2.Select.Action.ON_N_NOP;
                    break;
                case 2:
                    select.action = Gen2.Select.Action.NOP_N_OFF;
                    break;
                case 3:
                    select.action = Gen2.Select.Action.NEG_N_NOP;
                    break;
                case 4:
                    select.action = Gen2.Select.Action.OFF_N_ON;
                    break;
                case 5:
                    select.action = Gen2.Select.Action.OFF_N_NOP;
                    break;
                case 6:
                    select.action = Gen2.Select.Action.NOP_N_ON;
                    break;
                case 7:
                    select.action = Gen2.Select.Action.NOP_N_NEG;
                    break;
                default:
                    throw new ArgumentException("invalid action value");
            }
            return select;
        }

        public static short[] ReadMemBlockByEpc(Reader reader, TagReadData tag, Gen2.Bank bank, int address, int length, int attempts)
        {
            // Filter by EPC
            Gen2.Select resetFilter = CreateGen2Select(4, 4, Gen2.Bank.TID, 0x00, 16, new byte[] { 0xE2, 0x82 });
            Gen2.Select epcFilter = CreateGen2Select(4, 0, Gen2.Bank.EPC, 0x20, tag.Epc.Length * 8, tag.Epc);
            MultiFilter selects = new MultiFilter(new Gen2.Select[] { resetFilter, epcFilter });
            // Specify read parameters
            Gen2.ReadData operation = new Gen2.ReadData(bank, (uint)address, (byte)length);
            // Apply settings
            SimpleReadPlan config = new SimpleReadPlan(new int[] { tag.Antenna }, TagProtocol.GEN2, selects, operation, 1000);

            short[] values = null;
            try
            {
                reader.ParamSet("/reader/read/plan", config);
                for (int i = 0; i < attempts; i++)
                {
                    if (values != null)
                    {
                        break;
                    }
                    TagReadData[] readResults = reader.Read(readTime);
                    foreach (TagReadData readResult in readResults)
                    {
                        if (tag.EpcString.Equals(readResult.EpcString))
                        {
                            byte[] dataBytes = readResult.Data;
                            if (dataBytes.Length != 0)
                            {
                                values = ConvertByteArrayToShortArray(dataBytes);
                            }
                        }
                    }
                }
            }
            catch (ReaderException e)
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

        public static short[] ReadMemBlockByEpc(Reader reader, TagReadData tag, Gen2.Bank bank, int address, int length)
        {
            short[] values = ReadMemBlockByEpc(reader, tag, bank, address, length, 3);
            return values;
        }

        public static short ReadMemByEpc(Reader reader, TagReadData tag, Gen2.Bank bank, int address, int attempts)
        {
            short[] values = ReadMemBlockByEpc(reader, tag, bank, address, 1, attempts);
            return values[0];
        }

        public static short ReadMemByEpc(Reader reader, TagReadData tag, Gen2.Bank bank, int address)
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
