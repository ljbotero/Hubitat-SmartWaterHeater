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
 *  Author: jaime20@boteros.org
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
      input "waterHeater", title: "Water heater", required: true, "capability.thermostat"
      input "maxTemp", "number", range: "70..150", title: "Max temperature to set heater", required: true, defaultValue: 115
    }
    section("<h2>Planning</h2>") {
      input "enableSchedule", title: "Enable running on schedule", defaultValue: true, "bool"
      input "allowedModes", title: "Run on specific modes", multiple: true, "mode"
      input "timeStartNextWeekDay", title: "Time I wanto have hot water on <b>weekdays</b>", "time"
      input "timeStartNextWeekend", title: "Time I wanto have hot water on <b>weekends</b> or holidays", "time"
      input "minutesToRunAfterHeated", "number", range: "0..*", title: "Minutes to keep water hot after scheduled time", required: true, defaultValue: 120
      input "minutesToRunAfterHeatedManually", "number", range: "0..*", title: "Minutes to keep water hot after manually activated", required: true, defaultValue: 30
      input "approxMinutesToStartWaterHeater", "number", range: "0..*", title: "Approx. number of minutes for water heater to start heating (for malfunction detection)", required: true, defaultValue: 5.0
      input "estimateMinutesToHeatWater", title: "Automatically estimate minutes it takes to heat water from cold", defaultValue: true, "bool"
      input "minutesToHeatWater", range: "0..*", title: "Estimated number of minutes it takes to heat water from cold (for next day planning)", "number"
      input "minutesToReHeatWater", range: "0..*", title: "Estimated number of minutes it takes to re-heat water from hot (for long shower detection)", "number", defaultValue: 2
      input "maxMinutesWaterHeaterStaysHot", "number", range: "10..*", title: "Max number of minutes for water to stay hot (for malfunction detection)", required: true, defaultValue: 15.0
    }
    section("<h2>Control & Status</h2>"){
      input "turnOnWhenWaterIsHot", title: "Turn on when water is hot", "capability.switch"
      input "circulationSwitch", title: "Switch to turn when water heater is active (typically used for water circulation)", "capability.switch"
      input "statusLight", title: "Status light (blinks when heating / solid when ready)", "capability.switch"
      input "toggleSwitches", title: "On/Off switch to manually initiate heater", multiple: true, "capability.switch"
      input "holidaySwitch", title: "Switch that is turned-on on holidays (i.e. <a href='https://github.com/dcmeglio/hubitat-holidayswitcher'>hubitat-holidayswitcher</a>)", "capability.switch"
      input "enableAutoShutOffWhenLongShowerDetected", title: "Enable auto-shut off when long shower detected", defaultValue: true, "bool"
    }
    section("<h2>Notifications</h2>"){
      input "notifyWhenStart1Devices", title: "Notify when water heater starts", multiple: true, "capability.notification"
      input "notifyWhenStart1Message", title: "Notification Message", default: "Water heater has started heating water", "string"
      input "notifyWhenStart1Modes", title: "Only notify on specific modes", multiple: true, "mode"
      input "notifyWhenReady1Devices", title: "Notify when water is ready", multiple: true, "capability.notification"
      input "notifyWhenReady1Message", title: "Notification Message", default: "Water heater has finished heating water", "string"
      input "notifyWhenReady1Modes", title: "Only notify on specific modes", multiple: true, "mode"
      input "notifyWhenErrorDevices", title: "Notify if problems are detected", multiple: true, "capability.notification"
      input "notifyWhenErrorModes", title: "Only notify on specific modes", multiple: true, "mode"
      input "notifyWhenLongShowerDetectedDevices", title: "Notify when long shower detected", multiple: true, "capability.notification"
      input "notifyWhenLongShowerDetectedMessage", title: "Notification Message", default: "Auto shut-off activated after heating water for #minutes# minutes", "string"
      input "notifyWhenLongShowerDetectedModes", title: "Only notify on specific modes", multiple: true, "mode"
    }
    section("<h2>Testing</h2>"){
      input "dryRun", title: "Dry-run (won't execute any device changes)", defaultValue: false, "bool"
      input "debugEnabled", title: "Log debug messages", defaultValue: false, "bool"
    }
  }
}

/****************************************************************************/
/*  HELPER FUNCTIONS /*
/****************************************************************************/

def debug(msg) {
  if (debugEnabled) {
    log.debug msg
  }
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
  debug("setWaterHeaterOn")
  state.timeHeatingStarted = now()
  state.timeHeatingEnded = state.timeHeatingStarted

  def minutesSinceLastRan = Math.round((now() - state.timeHeatingEnded) / (60 * 1000)).toInteger()
  debug("minutesSinceLastRan = ${minutesSinceLastRan}")
  if (!state.isHeating && minutesSinceLastRan > maxMinutesWaterHeaterStaysHot) {
    def runInSeconds = ((approxMinutesToStartWaterHeater + state.rollingVarianceMinutesToStartWaterHeater) * 60).toInteger() 
    debug("checkWaterHeaterStarted in ${runInSeconds / 60} minutes")
    runIn(runInSeconds, "checkWaterHeaterStarted")    
  }
  if (!dryRun) { waterHeater.setHeatingSetpoint(getMaxTemp()) }
}

def checkWaterHeaterStarted() {
  def minutesSinceLastRan = Math.round((now() - state.timeHeatingEnded) / (60 * 1000)).toInteger()
  if (state.waterHeaterActive && !state.isHeating && minutesSinceLastRan > maxMinutesWaterHeaterStaysHot) {
    def notifyWhenErrorMessage = "Water heater has not started since ${minutesSinceLastRan} minutes ago"
    sendNotifications(notifyWhenErrorDevices, notifyWhenErrorModes, notifyWhenErrorMessage)
    debug(notifyWhenErrorMessage)
  }  
}

def setWaterHeaterOff() {
  debug("setWaterHeaterOff")
  if (!dryRun) { waterHeater.setHeatingSetpoint(getMinTemp()) }
}

def circulateWaterOn() {
  if (!dryRun && circulationSwitch != null) {
    circulationSwitch.on()
    debug("Circulation switch on")
  }
}

def circulateWaterOff() {
  if (!dryRun && circulationSwitch != null) {
    circulationSwitch.off()
    debug("Circulation switch off")
  }
}

/****************************************************************************/
/*  SETUP /*
/****************************************************************************/

def installed() {
  debug("Installed app")
  initialize()
}

def updated(settings) {
  unsubscribe()
  initialize()
  debug("updated settings")
}

def enableToggleSwitchContactChange() {
  if (toggleSwitches == null) {
    return
  }
  toggleSwitches.each { device -> 
      subscribe(device, "switch", toggleSwitchContactChangeHandler)
  }
}

def initialize() {
  state.testing = dryRun //false // used to run unit tests
  state.rollingVarianceMinutesToStartWaterHeater = 0

  subscribe(waterHeater, "heatingSetpoint", heatingSetpointChangeHandler)
  subscribe(waterHeater, "thermostatOperatingState", thermostatOperatingStateChangeHandler)
  
  enableToggleSwitchContactChange()
  
  if (minutesToHeatWater == null) {
    debug("Setting minutesToHeatWater to: 10")
    app.updateSetting("minutesToHeatWater", 10)
  }
  def minTempLimit = new Float(waterHeater.getDataValue("minTemp"))
  def maxTempLimit = new Float(waterHeater.getDataValue("maxTemp"))

  if (maxTemp <= minTempLimit) {
    app.updateSetting("maxTemp", minTempLimit + 5)
  } else if (maxTemp > maxTempLimit) {
    debug("set maxTemp: ${maxTemp} to maxTempLimit: ${maxTempLimit}")
    app.updateSetting("maxTemp", maxTempLimit)
  }
  initSchedule()

  if (state.testing) {
    debug("Running tests")
    notificationTest()
    onFinishedHeatingWaterTest()
  }
}

def initSchedule() {
  unschedule(onScheduleHandlerWeekday)
  unschedule(onScheduleHandlerWeekend)

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
    debug("No schedule time defined for ${weekdaysRange}")
    return null
  }
  def timeStartNextDayMillis = timeToday(timeStartNextDay).getTime()
  def plannedTimeStartNextDay = new Date(timeStartNextDayMillis - (minutesToHeatWater * 60 * 1000))
  def plannedHourStartNextDay = plannedTimeStartNextDay.format("H")
  def plannedMinuteStartNextDay = plannedTimeStartNextDay.format("m")
  // http://www.quartz-scheduler.org/documentation/quartz-2.3.0/tutorials/crontrigger.html
  def weekdaySchedule = "0 ${plannedMinuteStartNextDay} ${plannedHourStartNextDay} ? * ${weekdaysRange}"
  debug "Schedule: ${weekdaySchedule}"
  return weekdaySchedule
}

/****************************************************************************/
/*  EVENT HANDLERS /*
/****************************************************************************/
def toggleSwitchChangeValue(evtValue) {
  if (state.toggleSwitchContactState == evtValue) {
    return
  }
  state.toggleSwitchContactState = evtValue
  if (evtValue == "off") {
    // Stop heating
    setWaterHeaterOff()   
    toggleSwitches.each { device -> 
      device.off()
    }
  } else {
    // Start heating
    setWaterHeaterOn()
    toggleSwitches.each { device ->
      device.on()
    }
  }
  debug("toggleSwitchChangeValue = ${evtValue}")
}

def toggleSwitchContactChangeHandler(evt) {
  debug("${evt.name} = ${evt.value}")
  toggleSwitchChangeValue(evt.value)
}


def onScheduleHandlerWeekday() {
  if (holidaySwitch.currentValue("switch") == "on") {
    debug("Running schedule for Holiday")
    state.targetTime = timeToday(timeStartNextWeekend).getTime()
    def targetWaitMillis = (state.targetTime - (minutesToHeatWater * 60 * 1000)) - now()
    if (targetWaitMillis > 0) {
      debug("Delaying start to ${targetWaitMillis/ (60 * 1000)} minutes")
      runInMillis(targetWaitMillis, "onScheduleHandler")
      return
    }
    debug("Use case not handled: When holiday start stime is earlier than weekday's start times")
  } 
  state.targetTime = timeToday(timeStartNextWeekDay).getTime()
  onScheduleHandler()
}

def onScheduleHandlerWeekend() {
  state.targetTime = timeToday(timeStartNextWeekend).getTime()
  onScheduleHandler()
}

def onScheduleHandler() {
  debug("onScheduleHandler")
  if (!enableSchedule) {
    debug("Schedule cancelled since app is not enabled")
    return
  }
  if (allowedModes != null) {
    if (!allowedModes.any{ it == location.mode }) {
      debug("Schedule cancelled since current mode (${location.mode}) is no allowed to run")
      return
    }
  }
  state.startedOnSchedule = true
  setWaterHeaterOn()
}

def heatingSetpointChangeHandler(evt) {
  if (!evt.value.isNumber()) {
    debug("[ERROR] Invalid Event Value: ${evt.value}")
    return;
  }
  def currSetPoint = new Float(evt.value)
  debug("${evt.name} = ${evt.value}")
  if (getMinTemp() - 0.5 < currSetPoint && getMinTemp() + 0.5 > currSetPoint) {
    debug("Smart water heater is Inactive (${evt.name} = ${evt.value})")
    state.waterHeaterActive = false    
    state.timeHeaterActiveStarted = now()
    turnOnWhenWaterIsHot.off()
    circulateWaterOff()
    toggleSwitchChangeValue("off")
  } else if (getMaxTemp() - 0.5 < currSetPoint && getMaxTemp() + 0.5 > currSetPoint) {
    debug("Smart water heater is Active (${evt.name} = ${evt.value})")
    state.waterHeaterActive = true
    state.notificationStartedSent = false
    state.notificationEndedSent = false
    circulateWaterOn()
    toggleSwitchChangeValue("on")
  }
  unschedule(updateStatusLight)
  schedule("0/2 * * * * ?", updateStatusLight)
}

def updateWaterHeaterApproxTimes() {
  if (state.waterHeaterActive == false) {
    debug("updateWaterHeaterApproxTimes: canceled")
    return
  }
  if (state.timeHeaterActiveStarted > state.timeHeatingStarted) {
    // First time started heating    
    def minutesToStartWaterHeater = Math.round((now() - state.timeHeaterActiveStarted) / (60 * 1000)).toInteger()
    if (minutesToStartWaterHeater > approxMinutesToStartWaterHeater * 4 && approxMinutesToStartWaterHeater > 0) {
      debug("[WARNING] Not updating approxMinutesToStartWaterHeater since difference is too high: ${minutesToStartWaterHeater} > ${approxMinutesToStartWaterHeater}")
      return;
    }
    def rollingMinutesToStartWaterHeater = approxMinutesToStartWaterHeater - (approxMinutesToStartWaterHeater / 10)
    rollingMinutesToStartWaterHeater = rollingMinutesToStartWaterHeater + (minutesToStartWaterHeater / 10)
    // Update max variance
    def varianceMinutesToStartWaterHeater = Math.abs(approxMinutesToStartWaterHeater - minutesToStartWaterHeater)
    if (state.rollingVarianceMinutesToStartWaterHeater < varianceMinutesToStartWaterHeater) {
      state.rollingVarianceMinutesToStartWaterHeater = varianceMinutesToStartWaterHeater
    } else {
      state.rollingVarianceMinutesToStartWaterHeater = 
        (state.rollingVarianceMinutesToStartWaterHeater - (state.rollingVarianceMinutesToStartWaterHeater/10)) +
        (state.rollingVarianceMinutesToStartWaterHeater + (varianceMinutesToStartWaterHeater / 10))
    }
    debug("approxMinutesToStartWaterHeater: ${rollingMinutesToStartWaterHeater}")
    debug("rollingVarianceMinutesToStartWaterHeater: ${state.rollingVarianceMinutesToStartWaterHeater}")
    app.updateSetting("approxMinutesToStartWaterHeater", rollingMinutesToStartWaterHeater.toInteger())
  } else {    
    // Re-heating
    def minutesWaterHeaterStaysHot = Math.round((now() - state.timeHeatingEnded) / (60 * 1000)).toInteger()
    if (minutesWaterHeaterStaysHot > maxMinutesWaterHeaterStaysHot) {
      debug("[WARNING] Not updating maxMinutesWaterHeaterStaysHot since difference is too high: ${minutesWaterHeaterStaysHot} > ${maxMinutesWaterHeaterStaysHot}")
      return;
    }
    def rollingMinutesWaterHeaterStaysHot = maxMinutesWaterHeaterStaysHot - (maxMinutesWaterHeaterStaysHot / 10)
    rollingMinutesWaterHeaterStaysHot = rollingMinutesWaterHeaterStaysHot + (minutesWaterHeaterStaysHot / 10)
    debug("maxMinutesWaterHeaterStaysHot: ${rollingMinutesWaterHeaterStaysHot}")
    app.updateSetting("maxMinutesWaterHeaterStaysHot", rollingMinutesWaterHeaterStaysHot.toInteger())
  } 
}

def setWaterHeaterOffAuto() {
  debug("setWaterHeaterOffAuto")
  setWaterHeaterOff()
  def minutesSinceHeating = Math.round((now() - state.timeHeatingStarted) / (60 * 1000)).toString()
  def notificationMessage = notifyWhenLongShowerDetectedMessage.replace("#minutes#", minutesSinceHeating)
  sendNotifications(notifyWhenLongShowerDetectedDevices, notifyWhenLongShowerDetectedModes, notificationMessage)
}

def thermostatOperatingStateChangeHandler(evt) {
  debug("${evt.name} = ${evt.value}")
  if (evt.value == "heating") {
    unschedule("checkWaterHeaterStarted")
    updateWaterHeaterApproxTimes()
    //debug("thermostatOperatingStateChangeHandler: startedOnSchedule=${state.startedOnSchedule}, state.waterHeaterActive=${state.waterHeaterActive}, timeHeatingEnded=${state.timeHeatingEnded}, timeHeatingStarted=${state.timeHeatingStarted}")
    if (!state.startedOnSchedule && state.waterHeaterActive 
    && state.timeHeatingEnded > state.timeHeatingStarted
    && minutesToReHeatWater > 0 && enableAutoShutOffWhenLongShowerDetected) {
        debug("setWaterHeaterOffAuto: ${minutesToReHeatWater}, state.timeHeatingEnded ${state.timeHeatingEnded}, state.timeHeatingStarted: ${state.timeHeatingStarted}")
        runIn(minutesToReHeatWater * 60, "setWaterHeaterOffAuto")
    }
    state.timeHeatingStarted = now()
    state.isHeating = true
    if (state.waterHeaterActive && !state.notificationStartedSent) {
      sendNotifications(notifyWhenStart1Devices, notifyWhenStart1Modes, notifyWhenStart1Message)
      state.notificationStartedSent = true
    }
    debug("Started at ${new Date()}")
  } else {
    unschedule("setWaterHeaterOffAuto");
    state.timeHeatingEnded = now()
    state.isHeating = false
    if (state.startedOnSchedule) {
      onFinishedHeatingWater(minutesToRunAfterHeated)
    } else {
      onFinishedHeatingWater(minutesToRunAfterHeatedManually)
    }
    state.startedOnSchedule = false
    debug("Ended at ${new Date()}")
  }
  unschedule(updateStatusLight)
  schedule("0/2 * * * * ?", updateStatusLight)
}

def updateStatusLight() {
  if (statusLight == null) {
    debug("No status light has beed defined")
    unschedule(updateStatusLight)
    return;
  }
  if (!state.waterHeaterActive) {
    unschedule(updateStatusLight)
    debug("Turning statusLight off")
    if (!dryRun) { statusLight.off() }
    return
  }
  if (!state.isHeating) {
    unschedule(updateStatusLight)
    debug("Turning statusLight on")
    if (!dryRun) { statusLight.on() }
    return
  }
  if (state.statusLightOn) {
    //debug("Turning statusLight off - toggle")
    if (!dryRun) { statusLight.off() }
  } else {
    //debug("Turning statusLight on - toggle")
    if (!dryRun) { statusLight.on() }
  }
  state.statusLightOn = !state.statusLightOn
}

def onFinishedHeatingWater(minutesToRunAfter) {
  if (!state.waterHeaterActive) {
      return
  }
  if (estimateMinutesToHeatWater && state.startedOnSchedule) {
    // Update estimate
    state.minutesHeating = Math.round((state.timeHeatingEnded - state.timeHeatingStarted) / (60 * 1000)).toInteger()
    // Assuming 5 is the minimum it takes to heat the water from min temp and 60 is the max time it could take
    if (state.minutesHeating > 5 && state.minutesHeating < 60) {
      debug("Updating minutesToHeatWater to: ${state.minutesHeating}")
      runInMillis(5000, "initSchedule") // Wait a bit to ensure minutesToHeatWater is already persisted
      app.updateSetting("minutesToHeatWater", state.minutesHeating)
    }
  }
  // Calculate for how much longer keep it running
  if (state.startedOnSchedule) {
    def endTimeMillis = state.targetTime + (minutesToRunAfter * 60 * 1000)
    if (endTimeMillis < now()) {
      state.waitMinsUntilShutOff = 0
      setWaterHeaterOff();
    } else {
      def waitMillis = endTimeMillis - now()
      state.waitMinsUntilShutOff = Math.round(waitMillis / (60 * 1000)).toInteger()
      runInMillis(waitMillis, "setWaterHeaterOff")
      debug("Wait ${state.waitMinsUntilShutOff} minutes until turning water heater off")
    }
  } else {
    runIn(minutesToRunAfter * 60, "setWaterHeaterOff")
    debug("Wait ${state.waitMinsUntilShutOff} minutes until turning water heater off")
  }
  if (!state.notificationEndedSent) {
    sendNotifications(notifyWhenReady1Devices, notifyWhenReady1Modes, notifyWhenReady1Message)
    turnOnWhenWaterIsHot.on()
    state.notificationEndedSent = true
  }
}

def sendNotifications(notifyDevices, notifyModes, notifyMessage) {
  if (notifyDevices == null) {
    return
  }
  if (notifyModes != null) {
    if (!notifyModes.any{ it == location.mode }) {
      return
    }
  }
  notifyDevices.each { 
    device -> device.deviceNotification(notifyMessage)
  }
}

/****************************************************************************/
/*  UNIT TESTS                                                              /*
/****************************************************************************/
def notificationTest() {
  def minutesSinceHeating = Math.round((now() - state.timeHeatingStarted) / (60 * 1000)).toString()
  def notificationMessage = notifyWhenLongShowerDetectedMessage.replace("#minutes#", minutesSinceHeating)
  debug(notificationMessage);
}

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
