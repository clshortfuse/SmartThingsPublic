/**
 *  Google Cast Device
 *
 *  Copyright 2017 Carlos Lopez
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
 */
metadata {
	definition (name: "Google Cast Device", namespace: "clshortfuse", author: "Carlos Lopez") {
        capability "Signal Strength"
        capability "Polling"
        capability "Refresh"
		capability "Music Player"
		capability "Notification"
		capability "Speech Recognition"
		capability "Speech Synthesis"
		capability "Tone"
	}


	simulator {
		// TODO: define status and reply messages here
	}

	tiles {
      valueTile("rssi", "device.rssi") {
        state("rssi", label:'${currentValue}', unit:"db")
      }
	}
}

// parse events into attributes
def parse(String description) {

	def message = parseLanMessage(description);
    log.debug "Entered calledBackHandler()..."            
    log.debug "description in calledBackHandler() is: ${description}"            
    def msg = parseLanMessage(description)            
    log.debug msg.data;
    log.debug msg.data?.length;
    log.debug msg.data?.count;    
    log.debug msg.bucket;    
    log.debug msg.key;    
    return;
	log.debug "Parsing '${message}'"
    def data = parseJson(message.body);
    def map = [ name: "battery", unit: "%" ]
    def result = createEvent(name: "rssi", value: data['signal_level']);
    log.debug "Parse returned ${result?.descriptionText}"
    result;
	// TODO: handle 'status' attribute
	// TODO: handle 'level' attribute
	// TODO: handle 'trackDescription' attribute
	// TODO: handle 'trackData' attribute
	// TODO: handle 'mute' attribute
	// TODO: handle 'phraseSpoken' attribute

}
def sync(ip, port) {
  def existingIp = getDataValue("ip")
  def existingPort = getDataValue("port")
  if (ip && ip != existingIp) {
    updateDataValue("ip", ip)
  }
  if (port && port != existingPort) {
    updateDataValue("port", port)
  }
}

def poll() {
  log.debug "Polling"
  def hosthex = getDataValue("ip");
  def porthex = getDataValue("port")
  def target = "$hosthex:1F49";
  device.deviceNetworkId = target;
  
  byte[] body = buildTLSClientHello();
  
  log.debug "${body.length} ${bytesToHex(body)}";
  String strBody = new String(body, "ISO-8859-1");
  // strBody = "GET /setup/eureka_info?options=detail HTTP/1.1\r\n\r\n";
  
  //subscribe(target, null, calledBackHandler, [filterEvents:false])
  return sendHubCommand(new physicalgraph.device.HubAction(strBody, physicalgraph.device.Protocol.LAN, getDataValue("mac")));
  return null;
  def pollAction = new physicalgraph.device.HubAction(body,physicalgraph.device.Protocol.LAN, "$hosthex:1F49", [callback: req_callback]);
  log.debug "pollAction: ${pollAction} - ${pollAction.getProperties()}"
  sendHubAction(pollAction);
}

byte[] getTLSCipherSuites() {
  Map ciphers = ["TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA": 0xC014];
  ByteArrayOutputStream out = new ByteArrayOutputStream();      
  for(cipher in ciphers)
    writeInt(out, cipher.value, 16);
  out.flush();
  return out.toByteArray();
}

byte[] buildRandomData(size) {
  ByteArrayOutputStream out = new ByteArrayOutputStream();    
  writeInt(out, (int)Math.floor(new Date().getTime() / 1000), 32);
  for(def i = 0; i < size - 4; i++)
    out.write((int)(Math.random() * 0xFF));
  out.flush();
  return out.toByteArray();
}

byte[] getCompressionData() {
  Map compressions = ["None": 0x00];
  ByteArrayOutputStream out = new ByteArrayOutputStream();      
  for(compression in compressions)
    out.write((int)compression.value);
  out.flush();
  return out.toByteArray();
}

byte[] getExtensions() {
  ByteArrayOutputStream out = new ByteArrayOutputStream();      
  
  /*writeInt(out, 0XFF01, 16); //renegotiation info
  writeInt(out, 0x0001, 16); //length
  out.write(0x00); //none
  
  String ip = convertHexToIP(getDataValue("ip"));
  byte[] ipBytes = ip.getBytes("ASCII");
  writeInt(out, 0x0000, 16); //server_name
  writeInt(out, ipBytes.length + 5, 16); //extension length
  writeInt(out, ipBytes.length + 3, 16); //server name length
  out.write(0x00); //server name type (host_name)
  writeInt(out, ipBytes.length, 16); //hostname length
  out.write(ipBytes);
  
  writeInt(out, 0X000D, 16); //signature_algorithms
  writeInt(out, 0X0004, 16); //extension length
  writeInt(out, 0X0002, 16); //hash length
  writeInt(out, 0X0201, 16); //SHA1 + RSA
  
  writeInt(out, 0X000B, 16); //ec_point_formats
  writeInt(out, 0X0002, 16); //extension length
  out.write(0x01); //points length
  out.write(0x00); //none
  
  writeInt(out, 0X000A, 16); //elliptic_curves
  writeInt(out, 0X0006, 16); //extension length
  writeInt(out, 0X0004, 16); //curve length
  writeInt(out, 0x0017, 16); //secp256r1 elliptic curve
  writeInt(out, 0x0018, 16); //secp384r1 elliptic curve*/
  
  out.flush();
  return out.toByteArray();
}



byte[] buildTLSClientHello() {   
  int recordType = 0x16; //Handshake
  int handshakeType = 0x01; //ClientHello  
  int protocolMajor = 3;
  int protocolMinor = 3;
  byte[] handshakeData = getTLSClientHelloHandshakeData();
  ByteArrayOutputStream out = new ByteArrayOutputStream();  
  out.write(recordType);
  out.write(protocolMajor);
  out.write(protocolMinor);
  writeInt(out, handshakeData.length + 4, 16);
  out.write(handshakeType);
  writeInt(out, handshakeData.length, 24);
  out.write(handshakeData, 0, handshakeData.length);
  out.flush();
  return out.toByteArray();
}

byte[] getTLSClientHelloHandshakeData() {  
  int protocolMajor = 3;
  int protocolMinor = 3;
  
  ByteArrayOutputStream out = new ByteArrayOutputStream();  
  out.write(protocolMajor);
  out.write(protocolMinor);
  
  byte[] randomData = buildRandomData(32);  
  out.write(randomData, 0, randomData.length);  
  byte[] sessionData = new Byte[0];
  out.write(sessionData.length);
  log.debug sessionData.length
  out.write(sessionData, 0, sessionData.length);
  
  byte[] cipherSuitesData = getTLSCipherSuites();
  writeInt(out, cipherSuitesData.length, 16);  
  out.write(cipherSuitesData, 0, cipherSuitesData.length);
  
  byte[] compressionData = getCompressionData();
  out.write(compressionData.length);
  out.write(compressionData, 0, compressionData.length);
  
  byte[] extensions = getExtensions();
  writeInt(out, extensions.length, 16);  
  out.write(extensions, 0, extensions.length);
  
  byte[] extensionData = new Byte[0];  
  writeInt(out, extensionData.length, 16);  
  out.write(extensionData, 0, extensionData.length);
  out.flush();
  return out.toByteArray();  
}

public static void writeInt(ByteArrayOutputStream out, int value, int bits) {
  for(def i = bits - 8; i >= 0; i-=8) {
    out.write((byte) (0xFF & (value >> i)));
  }
}


public static String bytesToHex(byte[] bytes) {
    final char[] hexArray = "0123456789ABCDEF".toCharArray();
    char[] hexChars = new char[bytes.length * 2];
    for ( int j = 0; j < bytes.length; j++ ) {
        int v = bytes[j] & 0xFF;
        hexChars[j * 2] = hexArray[v >>> 4];
        hexChars[j * 2 + 1] = hexArray[v & 0x0F];
    }
    return new String(hexChars);
}

void calledBackHandler(physicalgraph.device.HubResponse hubResponse) {
    log.debug "Entered calledBackHandler()..."    
    def description = hubResponse.description
    log.debug "hubResponse in calledBackHandler() is: ${hubResponse}"        
    log.debug "description in calledBackHandler() is: ${description}"            
    log.debug hubResponse.properties;
    def msg = parseLanMessage(description)            
    log.debug msg.properties;
    log.debug msg.data;
    log.debug msg.data?.length;
    log.debug msg.data?.count;    
    log.debug msg.bucket;    
    log.debug msg.key;    
    
}

def poll_working() {
  log.debug "Polling"
  def ip = convertHexToIP(getDataValue("ip"));
  String host = "${ip}:8008";
  def pollAction = new physicalgraph.device.HubAction("""GET /setup/eureka_info?options=detail HTTP/1.1\r\nHOST: $host\r\n\r\n""", physicalgraph.device.Protocol.LAN, getDataValue("mac")) 
  pollAction;
}

def beep() {
  log.debug "Executing 'beep'"
  // TODO: handle 'beep' command 
}

def play() {
	log.debug "Executing 'play'"
	// TODO: handle 'play' command
}

def pause() {
	log.debug "Executing 'pause'"
	// TODO: handle 'pause' command
}

def stop() {
	log.debug "Executing 'stop'"
	// TODO: handle 'stop' command
}

def nextTrack() {
	log.debug "Executing 'nextTrack'"
	// TODO: handle 'nextTrack' command
}

def playTrack() {
	log.debug "Executing 'playTrack'"
	// TODO: handle 'playTrack' command
}

def setLevel() {
	log.debug "Executing 'setLevel'"
	// TODO: handle 'setLevel' command
}

def mute() {
	log.debug "Executing 'mute'"
	// TODO: handle 'mute' command
}

def previousTrack() {
	log.debug "Executing 'previousTrack'"
	// TODO: handle 'previousTrack' command
}

def unmute() {
	log.debug "Executing 'unmute'"
	// TODO: handle 'unmute' command
}

def setTrack() {
	log.debug "Executing 'setTrack'"
	// TODO: handle 'setTrack' command
}

def resumeTrack() {
	log.debug "Executing 'resumeTrack'"
	// TODO: handle 'resumeTrack' command
}

def restoreTrack() {
	log.debug "Executing 'restoreTrack'"
	// TODO: handle 'restoreTrack' command
}

def deviceNotification() {
	log.debug "Executing 'deviceNotification'"
	// TODO: handle 'deviceNotification' command
}

def speak() {
	log.debug "Executing 'speak'"
	// TODO: handle 'speak' command
}

private Integer convertHexToInt(hex) {
  Integer.parseInt(hex,16)
}

private String convertHexToIP(hex) {
  [convertHexToInt(hex[0..1]),convertHexToInt(hex[2..3]),convertHexToInt(hex[4..5]),convertHexToInt(hex[6..7])].join(".")
}