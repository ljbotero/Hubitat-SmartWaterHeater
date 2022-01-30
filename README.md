# Hubitat-SmartWaterHeater
This is a Hubitat app to controlling water heaters and reduce gas or electricity usage. It helps reducing water waste by automatically detecting shower duration and shutting down when exceeding a specified limit.
Features:
1. Schedule maximum shower duration to prevent people taking extremely long showers (you're welcome parents!)
1. Shower counter used to shut down water heater inmediatelly after last person showers
1. Schedule start time for weekday and weekends (or holidays)
1. Automatically schedules best time to initiate water heating based on statistics from previous days
1. Support for external manual override switch to turn on/off water heater
1. Support for optional water-circulator that turns on/of along with water heater
1. Water heater malfunction detection
1. Notify when water heater starts, ends, detects shower, or malfunctions

## Setup instructions
- Comming soon!

### External References

#### Hardware
- [Hubitat Elevation Hub](https://hubitat.com/products)
- [Rheem EcoNet Home Comfort WiFi Module for Gas Heaters (part REWRA631GWH)](https://www.amazon.com/gp/product/B00NOH3HK6)
- Bradford White Accessory Module Kit (part WHACCPKG1005)
- [Connecting Bradford White Accessory Module Kit to Rheem EcoNet Home Comfort WiFi Module](https://forums.raspberrypi.com/viewtopic.php?t=136314#p1696852)
- [G3/4" Hall Effect Liquid Water Flow Sensor](https://www.amazon.com/dp/B07DLZH8P2)

#### Software
- [ESP8266 Running with BotHouse IoT Framework](https://github.com/ljbotero/BotHouse)
  - [Config for Water Flow Sensor](https://github.com/ljbotero/BotHouse/blob/main/Arduino/data-dev/config_water_flow.json)
  - [Config for On/Off Switch](https://github.com/ljbotero/BotHouse/blob/main/Arduino/data-dev/config_on_off_switch.json)
- [Holiday Switcher by Dominick Meglio](https://github.com/dcmeglio/hubitat-holidayswitcher)
- [Hubitat-Rheem by Dominick Meglio](https://github.com/dcmeglio/hubitat-rheem)

## Hardware Architecture
![Architecture](https://raw.githubusercontent.com/ljbotero/Hubitat-SmartWaterHeater/main/Hubitat%20Smart%20Water%20Heater.drawio.png)

## Hubitat App
![Hubitat Options](https://raw.githubusercontent.com/ljbotero/Hubitat-SmartWaterHeater/main/SmartWaterHeaterSettings.png)
