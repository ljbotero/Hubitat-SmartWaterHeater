/**
 *  Copyright 2020 jaime20@boteros.org
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  Thermostat Timer
 *
 *  Author: jaime20@boteros.org
 *
 *  Change Log
 *  2020-12-13  - v01.0 Created
 *
 */

definition(
    name: "Smart Water Heater",
    namespace: "ljbotero",
    author: "jaime20@boteros.org",
    description: "Optimize when you run your water heater",
    category: "Green Living",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/GreenLiving/Cat-GreenLiving.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/GreenLiving/Cat-GreenLiving@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/GreenLiving/Cat-GreenLiving@3x.png"
)

preferences {
  page name: "mainPage", title: "", install: true, uninstall: true
}

def mainPage() {
  dynamicPage(name: "mainPage") {
    section("<h2>Smart Water Heater</h2>"){      
      input "waterHeater", title: "Water heater", required: true,"capability.thermostat"
      input "minutesToRunAfterHeated", "number", range: "0..*", title: "Minutes to run after finish heating", required: true, defaultValue: 120
      input "maxTemp", "number", range: "70..150", title: "Max temperature to set heater", required: true, defaultValue: 115
    }
    section("<h2>Planning</h2>") {
      input "appEnabled", title:"Enable running on schedule", defaultValue: true, "bool"
      input "allowedModes", title: "Run on specific modes", multiple: true, "mode"
      input "timeStartNextWeekDay", title:"Time I wanto have hot water on <b>weekdays</b>", "time"
      input "timeStartNextWeekend", title:"Time I wanto have hot water on <b>weekends</b>", "time"
      input "minutesToHeatWater", range: "0..*", title:"Estimated number of minutes it takes to heat water", "number"
      input "estimateMinutesToHeatWater", title:"Automatically estimate minutes it takes to heat water", defaultValue: true, "bool"
    }
    section("<h2>Control & Status</h2>"){
      input "circulationSwitch", title: "Switch to turn when water heater is active (typically used for water circulation)", "capability.switch"
      input "statusLight", title:"Status light (blinks when heating / solid when ready)", "capability.*"
      input "toggleSwitch", title: "On/Off toggle to manually initiate heater", "capability.contactSensor"
    }
    section("<h2>Notifications</h2>"){
      input "notifyWhenReady", title: "Notify when water is ready", multiple: true, "capability.notification"
      input "notifyWhenReadyMessage", title: "Notification Message", "string"
      input "allowedModesForNotifications", title: "Only notify on specific modes", multiple: true, "mode"
    }
  }
}

def installed() {
  log.debug "Installed app"
  initialize()
}

def updated(settings) {
  unsubscribe()
  unschedule()
  initialize()
  log.debug "updated settings"
}

def initialize() {
  subscribe(waterHeater, "heatingSetpoint", heatingSetpointChangeHandler)    
  subscribe(waterHeater, "thermostatOperatingState", thermostatOperatingStateChangeHandler) 
  if (minutesToHeatWater == null) {
    app.updateSetting("minutesToHeatWater", 10)
    log.debug "Setting minutesToHeatWater to: 10"
  }
  def minTempLimit = new Float(waterHeater.getDataValue("minTemp"))
	def maxTempLimit = new Float(waterHeater.getDataValue("maxTemp"))
  
  if (maxTemp <= minTempLimit) {
    app.updateSetting("maxTemp", minTempLimit + 5)
  } else if (maxTemp > maxTempLimit) {
    log.debug "set maxTemp: ${maxTemp} to maxTempLimit: ${maxTempLimit}"
    app.updateSetting("maxTemp", maxTempLimit)
  }

  scheduleSetup(timeStartNextWeekDay, "MON-FRI")
  scheduleSetup(timeStartNextWeekend, "SAT-SUN")
}

def scheduleSetup(timeStartNextDay, weekdaysRange) {
  if (timeStartNextDay == null) {
    log.debug "No schedule time defined for ${weekdaysRange}"
    return
  }
  def timeStartNextDayMillis = timeToday(timeStartNextDay).getTime()
  def plannedTimeStartNextDay = new Date(timeStartNextDayMillis - (minutesToHeatWater * 60 * 1000));
  def plannedHourStartNextDay = plannedTimeStartNextDay.format("H")
  def plannedMinuteStartNextDay = plannedTimeStartNextDay.format("m")
  // http://www.quartz-scheduler.org/documentation/quartz-2.3.0/tutorials/crontrigger.html
  def weekdaySchedule = "0 ${plannedMinuteStartNextDay} ${plannedHourStartNextDay} ? * ${weekdaysRange}"
  log.debug "Schedule: ${weekdaySchedule}"
  schedule(weekdaySchedule, onScheduleHandler);
}

def getMaxTemp() {
	def maxTempLimit = new Float(waterHeater.getDataValue("maxTemp"))
  if (maxTempLimit < maxTemp) {
    return maxTempLimit
  }
  return maxTemp
}

def getMinTemp() {
	return new Float(waterHeater.getDataValue("minTemp"))
}

def onScheduleHandler() {
  log.debug "onScheduleHandler"
  if (!appEnabled) {
      log.debug "Schedule cancelled since app is not enabled"
      return
  }
  if (allowedModes != null) {
    if (!allowedModes.any{it == location.mode}) {
      log.debug "Schedule cancelled since current mode (${location.mode}) is no allowed to run"
      return
    }
  }
  //waterHeater.setHeatingSetpoint(getMaxTemp())
}

def heatingSetpointChangeHandler(evt) {
  def currSetPoint = new Float(evt.value)
  log.debug "heatingSetpoint: ${evt.name} = ${evt.value} , MinTemp = ${getMinTemp()}, waterHeater.heatingSetpoint = ${currSetPoint}"
  if (getMinTemp() - 0.5 < currSetPoint && getMinTemp() + 0.5 > currSetPoint) {
    log.debug("Water heater is off")
  } else if (getMinTemp() + 0.5 < currSetPoint) {
    log.debug("Water heater is on")
  }
}

def thermostatOperatingStateChangeHandler(evt) {
  log.debug "thermostatOperatingStateChange: ${evt.name} = ${evt.value}"
}

/*
//def timeStartNextWeekDayMillis = new Date().parse("yyy-MM-dd'T'HH:mm:ss.SSSZ", timeStartNextWeekDay).getTime()
//runIn(5, delayTurnOff)
//app.updateSetting("timeStartNextDay", getLocalTime())
//sendEvent(name: "timeStartNextDay", value: 123)
//updateSetting("minutesToRunAfterHeated", [value: "10", type: "number"])
//    subscribe(switch1, "switch.on", delayTurnOff)
//    log.debug "Updated with settings: ${settings}"
//    subscribe(switch1, "switch.off", cancelDelay)

if (opSet < thermostat1.currentValue("temperature")) {
        thermostat1.setCoolingSetpoint(opSet)	
    } else {
        thermostat1.setHeatingSetpoint(opSet)
    }
*/
//  log.debug("Waiting delay to switch off")
//  def delay = 60 * minutes
//runIn(delay, switchOff)
//  runIn(15, switchOff)

