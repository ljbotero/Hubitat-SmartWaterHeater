# Hubitat-SmartWaterHeater
This is a Hubitat app to controlling water heaters and reduce gas or electricity usage. It helps reducing water waste by automatically detecting shower duration and shutting down when exceeding a specified limit.
Features:
1. Schedule maximum shower duration to prevent people taking extremely long showers (you're welcome parents!)
1. Shower counter used to shut down water heater inmediatelly after last person showers
1. Schedule start time for weekday and weekends (or holidays)
1. Support for [Holiday Switcher](https://github.com/dcmeglio/hubitat-holidayswitcher) 
1. Automatically schedules best time to initiate water heating based on statistics from previous days
1. Support for external manual override switch to turn on/off water heater
1. Support for optional water-circulator that turns on/of along with water heater

## Hardware Architecture
![Architecture](https://raw.githubusercontent.com/ljbotero/Hubitat-SmartWaterHeater/main/Hubitat%20Smart%20Water%20Heater.drawio.png)

## Hubitat App
![Hubitat Options](https://raw.githubusercontent.com/ljbotero/Hubitat-SmartWaterHeater/main/SmartWaterHeaterSettings.png)
