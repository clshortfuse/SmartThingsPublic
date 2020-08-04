/**
 *  samsungrac
 *
 *  Copyright 2020 Carlos Lopez
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 */

metadata {
  definition (name: 'Samsung RAC', namespace: 'clshortfuse', author: 'Carlos Lopez', cstHandler: true) {
    capability 'Polling'
    capability 'Refresh'
    capability "Health Check"
    capability 'Fan Speed'
    capability "Temperature Measurement"
    capability "Thermostat Cooling Setpoint"
    capability 'Thermostat Heating Setpoint'
    capability 'Thermostat Mode'
    capability 'Thermostat Fan Mode'
    capability 'Fan Speed'
    capability 'Thermostat Operating State'
    capability "Thermostat"
    capability "Thermostat Setpoint"
  }

  simulator {
    // TODO: define status and reply messages here
  }

  tiles {
    valueTile("temperature", "device.temperature", width: 2, height: 2) {
      state("temperature", label:'${currentValue}°', unit:"F",
        backgroundColors: [
          [value: 31, color: "#153591"],
          [value: 44, color: "#1e9cbb"],
          [value: 59, color: "#90d2a7"],
          [value: 74, color: "#44b621"],
          [value: 84, color: "#f1d801"],
          [value: 95, color: "#d04e00"],
          [value: 96, color: "#bc2323"]
        ]
      )
    }
        main "temperature"
        details "temperature"
  }
}

def updated() {
  setupHealthCheck();
}

def installed() {
  setupHealthCheck();
}

def setupHealthCheck() {
  unschedule("poll", [forceForLocallyExecuting: true])
  poll();
  runEvery1Minute("poll", [forceForLocallyExecuting: true])
  
}

// parse events into attributes
def parse(String description) {
  def msg = parseLanMessage(description)
  def data = parseJson(msg.body)
  try {
    if (!data.containsKey('Device')) {
      throw new Exception('Unrecognized data')
    }
  } catch(e) {
    log.debug data;
    return;
  }
  def temperature = data.Device.Temperatures[0]
  def unit = temperature.unit.charAt(0)

  // Temperature Measurement
  sendEvent( name:'temperature', value:temperature.current, unit:unit )

  // Thermostat Cooling Setpoint
  sendEvent( name:'coolingSetpoint', value:temperature.desired, unit:unit )

  // Thermostat Heating Setpoint
  sendEvent( name:'heatingSetpoint', value:temperature.desired, unit:unit )
  
  // Thermostat
  sendEvent( name:'coolingSetpointRange', value:[temperature.minimum, temperature.maximum], unit:unit )
  sendEvent( name:'heatingSetpointRange', value:[temperature.minimum, temperature.maximum], unit:unit )

  // Thermostat Setpoint
  sendEvent( name:'thermostatSetpoint', value:temperature.desired, unit:unit )

  def wind = data.Device.Wind;
  // Thermostat Fan Mode
  sendEvent( name:'thermostatFanMode', value:(wind.direction == 'Fix' ? 'on' : 'circulate') )
  sendEvent( name:'supportedThermostatFanModes', value:['on', 'circulate'] )

  sendEvent( name:'fanSpeed', value:wind.speedLevel )
  
  def mode = data.Device.Mode;
  def power = data.Device.Operation.power;
  String currentMode = mode.modes[0].substring(7).toLowerCase();
  if (power == 'Off') currentMode = 'off'
  sendEvent( name:'thermostatMode', value:currentMode )

  def supportedThermostatModes = []
  mode.supportedModes.each {
    def supportedMode = it.substring(7).toLowerCase()
    supportedThermostatModes.add(supportedMode)
  }
  supportedThermostatModes.add('off');
  sendEvent( name:'supportedThermostatModes', value:supportedThermostatModes )

  switch(mode) {
    case 'off':
      sendEvent( name:'thermostatOperatingState', value:'idle' )
      break;
    case 'cool':
      sendEvent( name:'thermostatOperatingState', value:'cooling' )
      break;
    case 'fan':
      sendEvent( name:'thermostatOperatingState', value:'fan only' )
      break;
    case 'heat':
      sendEvent( name:'thermostatOperatingState', value:'heating' )
      break;
  }

  log.debug 'Device updated.'
}

def poll() {
  log.debug 'poll()'
  sendHubCommand(refresh())
}

def doHttpAction(String path, String method = null, content = null) {
  def parsedMethod = method == null ? (content ? 'POST' : 'GET') : method;
  def parsedContent = (content == null) ? '' : new groovy.json.JsonBuilder(content).toString();
  def parts = device.deviceNetworkId.split(":")
  def ip = convertHexToIP(parts[0])
  def port = convertHexToInt(parts[1])
  def params = [
    path: "${path}",
    method: parsedMethod,
    headers: [
      HOST: "${ip}:${port}",
    ]
  ];
  if (parsedMethod != 'GET') {
    params.body = parsedContent;
    params.headers['Content-Type'] = 'application/json';
    params.headers['Content-Length'] = parsedContent.length();
  }
  def action = new physicalgraph.device.HubAction(params)
  return action;
}

def doHttpGetAction(path) {
  return doHttpAction(path);
}

def doHttpPostAction(path, content) {
  return doHttpAction(path, 'POST', content)
}

def doHttpPutAction(path, content) {
  return doHttpAction(path, 'PUT', content)
}

def refresh() {
  log.debug 'refresh()'
  return doHttpGetAction('/');
}

def ping() {
  log.debug 'ping()'
  refresh()
}

def setCoolingSetpoint(degrees) {
  log.debug "Executing 'setCoolingSetpoint'"
  return [
    doHttpPutAction('/temperatures/0', [ Temperature: [ desired: degrees ] ]),
    refresh()
  ]
}

def setHeatingSetpoint(degrees) {
  log.debug "Executing 'setHeatingSetpoint'"
  return [
    doHttpPutAction('/temperatures/0', [ Temperature: [ desired: degrees ] ]),
    refresh()
  ]
}

def fanOn() {
  log.debug "Executing 'fanOn'"
  return [
    doHttpPutAction('/wind', [ Wind: [ direction: 'Fix' ] ]),
    refresh()
  ]
}


def fanCirculate() {
  log.debug "Executing 'fanCirculate'"
  return [
    doHttpPutAction('/wind', [ Wind: [ direction: 'Up_And_Low' ] ]),
    refresh()
  ]
}

def setThermostatFanMode(String mode) {
  log.debug "Executing 'setThermostatFanMode'"
  if (mode == 'on') return fanOn();
  if (mode == 'circulate') return fanCirculate();
}

def off() {
  log.debug "Executing 'off'"
  return [
    doHttpPutAction('/operation', [ Operation: [ power: 'Off' ] ]),
    refresh()
  ]
}

private changeACMode(String opMode) {
  def isOff = (device.currentValue("thermostatMode") == 'off')
  List actions = [];
  if (isOff) actions.add(doHttpPutAction('/operation', [ Operation: [ power: 'On' ] ]))
  actions.add(doHttpPutAction('/mode', [ Mode: [ modes: [opMode] ] ]));
  actions.add(refresh())
  if (isOff) {
    sendHubCommand(actions, 5000);
  } else {
    return actions;
  }
}

def heat() {
  log.debug "Executing 'heat'"
  changeACMode('Opmode_Heat')
}

def cool() {
  log.debug "Executing 'cool'"
  changeACMode('Opmode_Cool')
}


def auto() {
  log.debug "Executing 'auto'"
  changeACMode('Opmode_Auto')
}

def setThermostatMode(String mode) {
  log.debug "Executing 'setThermostatMode'"
  if (mode == 'heat') return heat();
  if (mode == 'cool') return cool();
}

private Integer convertHexToInt(hex) {
  Integer.parseInt(hex,16)
}

private String convertHexToIP(hex) {
  [convertHexToInt(hex[0..1]),convertHexToInt(hex[2..3]),convertHexToInt(hex[4..5]),convertHexToInt(hex[6..7])].join(".")
}
