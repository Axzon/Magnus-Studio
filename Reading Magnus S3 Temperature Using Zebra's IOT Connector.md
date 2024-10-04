# Zebra IOT Connector Documentation

The documentation can be found at:  
[Zebra IOT Connector Documentation](https://zebradevs.github.io/rfid-ziotc-docs/setupziotc/index.html#)

## Zebra Reader Configuration

Use the reader’s web interface to add a TCP endpoint with the following setup:
![Magnus Studio](https://axzon-docs-public.s3.us-east-2.amazonaws.com/images/github-magnus-md/magnus-studio-1.png)

Then configure the Interfaces as follows:
![Magnus Studio](https://axzon-docs-public.s3.us-east-2.amazonaws.com/images/github-magnus-md/magnus-studio-2.png)

Then the Connection Status should look like:
![Magnus Studio](https://axzon-docs-public.s3.us-east-2.amazonaws.com/images/github-magnus-md/magnus-studio-3.png)

## Connect a Terminal to the Tag Data Events Interface

Use a terminal to connect to the IP address of the reader using port 8081.

## Rest API Tester (i.e. Postman)

### API Definition

The Zebra’s IOT Connector REST API definition can be found at:  
[Zebra’s IOT Connector REST API Definition](https://zebradevs.github.io/rfid-ziotc-docs/best_practices/resources/index.html)

### Login Into Rest API

Using basic authentication with Username (default for FX9600: `admin`) and Password (default for FX9600: `change`), issue the following GET command:

```bash
http://reader-ip-address/cloud/localRestLogin
```
The reader will respond with a Bearer Token.


### Configure the Reader
Use the following PUT command:
```bash
http://reader-ip-address/cloud/mode
```
With the following JSON body:
```bash
{
  "antennas": [
    1
  ],
  "environment": "LOW_INTERFERENCE",
  "transmitPower": [
    10.0
  ],
  "type": "SIMPLE",
  "selects": [
    {
      "target": "S0",
      "action": "INVA_INVB",
      "membank": "USER",
      "pointer": 208,
      "length": 8,
      "mask": "1F",
      "truncate": 0
    },
    {
      "target": "S0",
      "action": "INVA_INVB",
      "membank": "USER",
      "pointer": 224,
      "length": 0,
      "mask": "",
      "truncate": 0
    }
  ],
  "query": {
    "tagPopulation": 2,
    "sel": "ALL",
    "session": "S0",
    "target": "A"
  },
  "delayAfterSelects": 3,
  "accesses": [
    {
      "type": "READ",
      "config": {
        "membank": "USER",
        "wordPointer": 8,
        "wordCount": 4
      }
    },
    {
      "type": "READ",
      "config": {
        "membank": "RESERVED",
        "wordPointer": 12,
        "wordCount": 3
      }
    }
  ],
  "delayBetweenAntennaCycles": {
    "type": "DISABLED",
    "duration": 0
  },
  "tagMetaData": ["ANTENNA", "RSSI", "CHANNEL"]
}
```
The specification for the format of the previous content can be found at:
[Zebra Documentation](https://zebradevs.github.io/rfid-ziotc-docs/schemas/operating_modes/index.html)

### Start the Reader
Use the following PUT command to start the reading process:
```bash
http://reader-ip-address/cloud/start
```
The terminal should start showing the readings as follows:
![Magnus Studio](https://axzon-docs-public.s3.us-east-2.amazonaws.com/images/github-magnus-md/magnus-studio-4.png)

Where the temperature calibration data is: C603, 9068, 9B3E and 5604, the Sensor Code is: 0087, the On-Chip RSSI is: 0016 and the Temperature Code is: 08D0

### Stop the Reader
Use the following PUT command to stop the reading process:
```bash
http://reader-ip-address/cloud/stop
```

