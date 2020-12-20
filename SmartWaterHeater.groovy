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
      input "maxTemp", "number", range: "70..150", title: "Max temperature to set heater", required: true, defaultValue: 115
    }
    section("<h2>Planning</h2>") {
      input "enableSchedule", title:"Enable running on schedule", defaultValue: true, "bool"
      input "allowedModes", title: "Run on specific modes", multiple: true, "mode"
      input "timeStartNextWeekDay", title:"Time I wanto have hot water on <b>weekdays</b>", "time"
      input "timeStartNextWeekend", title:"Time I wanto have hot water on <b>weekends</b>", "time"
      input "minutesToRunAfterHeated", "number", range: "0..*", title: "Minutes to keep water hot after scheduled time", required: true, defaultValue: 120
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
  initSchedule()
  
  state.testing = true //false // used to run unit tests
/*
  if (state?.testing) {
    onFinishedHeatingWaterTest()
  }
*/
}

def initSchedule() {
  def scheduleString = scheduleSetup(timeStartNextWeekDay, "MON-FRI")
  if (scheduleString != null) {
    schedule(scheduleString, onScheduleHandlerWeekday)
  }

  scheduleString = scheduleSetup(timeStartNextWeekend, "SAT-SUN")
  if (scheduleString != null) {
    schedule(scheduleString, onScheduleHandlerWeekend)
  }
}

def scheduleSetup(timeStartNextDay, weekdaysRange) {
  if (timeStartNextDay == null) {
    log.debug "No schedule time defined for ${weekdaysRange}"
    return null
  }
  def timeStartNextDayMillis = timeToday(timeStartNextDay).getTime()
  def plannedTimeStartNextDay = new Date(timeStartNextDayMillis - (minutesToHeatWater * 60 * 1000))
  def plannedHourStartNextDay = plannedTimeStartNextDay.format("H")
  def plannedMinuteStartNextDay = plannedTimeStartNextDay.format("m")
  // http://www.quartz-scheduler.org/documentation/quartz-2.3.0/tutorials/crontrigger.html
  def weekdaySchedule = "0 ${plannedMinuteStartNextDay} ${plannedHourStartNextDay} ? * ${weekdaysRange}"
  log.debug "Schedule: ${weekdaySchedule}"
  return weekdaySchedule
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

def setWaterHeaterOn() {
  log.debug("setWaterHeaterOn")
  if (!state?.testing) {
    waterHeater.setHeatingSetpoint(getMaxTemp())     
  }
}

def setWaterHeaterOff() {
  log.debug("setWaterHeaterOff")
  if (!state?.testing) {
    waterHeater.setHeatingSetpoint(getMinTemp())     
  }
}

def onScheduleHandlerWeekday() {
  state.targetTime = timeToday(timeStartNextWeekDay).getTime()
  onScheduleHandler()
}

def onScheduleHandlerWeekend() {
  state.targetTime = timeToday(timeStartNextWeekend).getTime()
  onScheduleHandler()
}

def onScheduleHandler() {
  log.debug "onScheduleHandler"
  if (!enableSchedule) {
      log.debug "Schedule cancelled since app is not enabled"
      return
  }
  if (allowedModes != null) {
    if (!allowedModes.any{it == location.mode}) {
      log.debug "Schedule cancelled since current mode (${location.mode}) is no allowed to run"
      return
    }
  }
  state.startedOnSchedule = true  
  setWaterHeaterOn()
}

def heatingSetpointChangeHandler(evt) {
  def currSetPoint = new Float(evt.value)
  log.debug "heatingSetpoint: ${evt.name} = ${evt.value} , MaxTemp = ${getMaxTemp()}, waterHeater.heatingSetpoint = ${currSetPoint}"
  if (getMinTemp() - 0.5 < currSetPoint && getMinTemp() + 0.5 > currSetPoint) {
    log.debug("Smart water heater is Inactive (${evt.name} = ${evt.value})")
    state.waterHeaterActive = false
  } else if (getMaxTemp() - 0.5 < currSetPoint && getMaxTemp() + 0.5 > currSetPoint) {
    log.debug("Smart water heater is Active (${evt.name} = ${evt.value})")
    state.waterHeaterActive = true
  }
  unschedule(updateStatusLight)
  schedule("0/2 * * * * ?", "updateStatusLight")
}

def thermostatOperatingStateChangeHandler(evt) {
  log.debug "${evt.name} = ${evt.value}"
  if (evt.value == "heating") {
    state.timeHeatingStarted = now()        
    state.isHeating = true
    log.debug "Started at ${new Date()}"
  } else {
    state.timeHeatingEnded = now()
    state.isHeating = false
    onFinishedHeatingWater(minutesToRunAfterHeated)
    state.startedOnSchedule = false
    log.debug "Ended at ${new Date()}"
  }
  unschedule(updateStatusLight)
  schedule("0/2 * * * * ?", "updateStatusLight")
}

def updateStatusLight() {
  if (statusLight == null) {
    unschedule(updateStatusLight)
    return;
  }
  if (!state?.waterHeaterActive) {
    unschedule(updateStatusLight)
    log.debug "Turning statusLight off"
    if (!state?.testing) {
      statusLight.off()
    }
    return
  }
  if (!state?.isHeating) {
    unschedule(updateStatusLight)
    log.debug "Turning statusLight on"
    if (!state?.testing) {
      statusLight.on()
    }
  }
  if (state?.statusLightOn) {
    log.debug "Turning statusLight off - toggle"
    if (!state?.testing) {
      statusLight.off()  
    }
  } else {
    log.debug "Turning statusLight on - toggle"
    if (!state?.testing) {
      statusLight.on()  
    }
  }
  state.statusLightOn = !state?.statusLightOn
}

def onFinishedHeatingWater(minutesToRunAfter) {
  if (!state?.waterHeaterActive) {
    return
  }
  if (estimateMinutesToHeatWater && state?.startedOnSchedule) {
    // Update estimate
    state.minutesHeating = Math.round((state.timeHeatingEnded - state.timeHeatingStarted) / (60 * 1000)).toInteger()
    // Assuming 5 is the minimum it takes to heat the water from min temp and 60 is the max time it could take
    if (state.minutesHeating > 5 && state.minutesHeating < 60) { 
      app.updateSetting("minutesToHeatWater", state.minutesHeating)
      log.debug "Updating minutesToHeatWater to: ${state.minutesHeating}"
      unschedule(onScheduleHandlerWeekday)
      unschedule(onScheduleHandlerWeekend)
      runInMillis(5000, "initSchedule") // Wait a bit to ensure minutesToHeatWater is already persisted
    }
  }
  // Calculate for how much longer keep it running
  if (state?.startedOnSchedule) {
    def endTimeMillis = state.targetTime + (minutesToRunAfter * 60 * 1000)
    if (endTimeMillis < now()) {
      state.waitMinsUntilShutOff = 0
      setWaterHeaterOff();
    } else {
      def waitMillis = endTimeMillis - now()
      state.waitMinsUntilShutOff = Math.round(waitMillis / (60 * 1000)).toInteger()
      runInMillis(waitMillis, "setWaterHeaterOff")
      log.debug "Wait ${state.waitMinsUntilShutOff} minutes until turning water heater off"
    }    
  } 
}

/****************************************************************************/
/*  UNIT TESTS                                                              /*
/****************************************************************************/

def onFinishedHeatingWaterTest() {   
  state.waterHeaterActive = true
  state.startedOnSchedule = true
  state.timeHeatingStarted = 1608413794070
  state.timeHeatingEnded = 1608415254031
  state.targetTime = now() + (20 * 60 * 1000)
  onFinishedHeatingWater(10)
  
  // Unit test: Updating extimate
  if (state.minutesHeating != 24) {
    log.error("Failed unit test - onFinishedHeatingWaterTest - Updating extimate");
  }
  
  // Unit test: Keep it running for 10 minute, but still 20 minutes until reahing the target time
  if (state.waitMinsUntilShutOff != 30) {
    log.error("Failed unit test - onFinishedHeatingWaterTest -  Keep it running for 10 minute, but still 20 minutes until reahing the target time (state.waitMinsUntilShutOff=${state.waitMinsUntilShutOff})");
  }
  
  state.targetTime = now() - (9 * 60 * 1000)
  onFinishedHeatingWater(10)
  // Unit test: Keep it running for 10 minute, but it passed 9 minutes ago
  if (state.waitMinsUntilShutOff != 1) {
    log.error("Failed unit test - onFinishedHeatingWaterTest - Keep it running for 10 minute, but it passed 9 minutes ago (state.waitMinsUntilShutOff=${state.waitMinsUntilShutOff})");
  }
  
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


def getDayOfTheWeek() {
  // Sun: 1, Mon:2, Tue: 3, Wed: 4: Thu: 5, Fri: 6, SAT: 7
  //Calendar localCalendar = Calendar.getInstance(TimeZone.getDefault())
  //return localCalendar.get(Calendar.DAY_OF_WEEK)
  def dayWeekStr = new Date().format("EEE", location.timeZone)
  if (dayWeekStr == "Mon"
}

*/
//  log.debug("Waiting delay to switch off")
//  def delay = 60 * minutes
//runIn(delay, switchOff)
//  runIn(15, switchOff)
