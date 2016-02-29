/**
 *  LG Smart TV Discovery
 *
 *  Copyright 2015 Daniel Vorster
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
 
definition(
    name: "LG Smart TV Discovery",
    namespace: "dpvorster",
    author: "Daniel Vorster",
    description: "Discovers an LG Smart TV (pre-2012)",
    category: "My Apps",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png"
)


preferences 
{
	page(name:"televisionDiscovery", title:"LG TV Setup", content:"televisionDiscovery", refreshTimeout:5)
	page(name:"televisionAuthenticate", title:"LG TV Pairing", content:"televisionAuthenticate", refreshTimeout:5)
}

def televisionDiscovery() 
{
    int tvRefreshCount = !state.bridgeRefreshCount ? 0 : state.bridgeRefreshCount as int
    state.bridgeRefreshCount = tvRefreshCount + 1
    def refreshInterval = 3

    def options = televisionsDiscovered() ?: []
    def numFound = options.size() ?: 0

    if(!state.subscribe) {
        subscribe(location, null, deviceLocationHandler, [filterEvents:false])    
        state.subscribe = true
    }

    // Television discovery request every 15 seconds
    if((tvRefreshCount % 5) == 0) {
        findTv()
    }

    return dynamicPage(name:"televisionDiscovery", title:"LG TV Search Started!", nextPage:"televisionAuthenticate", refreshInterval:refreshInterval, uninstall: true){
        section("Please wait while we discover your LG TV. Discovery can take five minutes or more, so sit back and relax! Select your device below once discovered."){
            input "selectedTv", "enum", required:false, title:"Select LG TV (${numFound} found)", multiple:false, options:options
        }
    }
}

def televisionAuthenticate() 
{
	tvRequestPairingKey()
    
    return dynamicPage(name:"televisionAuthenticate", title:"LG TV Search Started!", nextPage:"", install:true){
        section("We sent an pairing request to your TV. Please enter the pairing key and click Done."){
        	input "pairingKey", "string", defaultValue:"DDTYGF", required:true, title:"Pairing Key", multiple:false
        }
    }
}

Map televisionsDiscovered() 
{
	def vbridges = getLGTvs()
	def map = [:]
	vbridges.each {
    	log.debug "Discovered List: $it"
        def value = "$it"
        def key = it.value
        
        if (key.contains("!")) {
            def settingsInfo = key.split("!")
            def deviceIp = convertHexToIP(settingsInfo[1])
            value = "LG TV (${deviceIp})"
        }
        
        map["${key}"] = value
	}
	map
}

def installed() 
{
	log.debug "Installed with settings: ${settings}"
    
	initialize()
}

def updated() 
{
	log.debug "Updated with settings: ${settings}"

	initialize()
}

def initialize() 
{
	// Remove UPNP Subscription
	unsubscribe()
	state.subscribe = false
    
    addDevice()

    log.debug "Application Initialized"
    log.debug "Selected TV: $selectedTv"
}

def addDevice()
{ 
  def deviceSettings = selectedTv.split("!")
  def ipAddressHex = deviceSettings[1]
  def ipAddress = convertHexToIP(ipAddressHex)
    
  def dni = "${ipAddressHex}:${convertPortToHex(8080)}"
  def d = getChildDevice(dni)
  if(!d) 
  {
  	log.debug "Hub: " + location.hubs[0].id
    addChildDevice("dpvorster", "LG Smart TV", dni, deviceSettings[2], 
    	[
        	name: "LG Smart TV", 
            label: "", 
            completedSetup: true,
            preferences: [
            	pairingKey: "$pairingKey",
            	televisionIp: "$ipAddress"
            ]
        ])
    d = getChildDevice(dni)
    log.debug "created ${d.displayName} with id $dni"
  } 
  else 
  {
    log.debug "Device with id $dni already created"
  }
}

// Returns a list of the found LG TVs from UPNP discovery
def getLGTvs()
{
	state.televisions = state.televisions ?: [:]
}

// Sends out a UPNP request, looking for the LG TV. Results are sent to [deviceLocationHandler]
private findTv() 
{
	sendHubCommand(new physicalgraph.device.HubAction("lan discovery urn:schemas-upnp-org:device:MediaRenderer:1", physicalgraph.device.Protocol.LAN))
}

// Parses results from [findTv], looking for the specific UPNP result that clearly identifies the TV we can use
def deviceLocationHandler(evt) 
{
	log.debug "Device Location Event: $evt.description"
	def upnpResult = parseEventMessage(evt.description)
    log.debug "upnp: $upnpResult"
    
    def hub = evt?.hubId
    log.debug "hub: $hub"
    
    if (upnpResult?.ssdpPath?.contains("MediaRenderer1.xml")) {
        log.debug "Found TV: ${upnpResult}"
        state.televisions << [device:"${upnpResult.mac}!${upnpResult.ip}!${hub}"]
    }
}

// Display pairing key on TV
private tvRequestPairingKey()
{
	log.debug "Display pairing key"
    
    def deviceSettings = selectedTv.split("!")
    def ipAddressHex = deviceSettings[1]
    def ipAddress = convertHexToIP(ipAddressHex)
    
    def reqKey = "<?xml version=\"1.0\" encoding=\"utf-8\"?><auth><type>AuthKeyReq</type></auth>"
    
    def httpRequest = [
      	method:		"POST",
        path: 		"/hdcp/api/auth",
        body:		"$reqKey",
        headers:	[
        				HOST:			"$ipAddress:8080",
                        "Content-Type":	"application/atom+xml",
                    ]
	]

	def hubAction = new physicalgraph.device.HubAction(httpRequest)
	sendHubCommand(hubAction)
}

private def parseEventMessage(String description) 
{
	def event = [:]
	def parts = description.split(',')
	parts.each { part ->
		part = part.trim()
		if (part.startsWith('devicetype:')) {
			def valueString = part.split(":")[1].trim()
			event.devicetype = valueString
		}
		else if (part.startsWith('mac:')) {
			def valueString = part.split(":")[1].trim()
			if (valueString) {
				event.mac = valueString
			}
		}
		else if (part.startsWith('networkAddress:')) {
			def valueString = part.split(":")[1].trim()
			if (valueString) {
				event.ip = valueString
			}
		}
		else if (part.startsWith('ssdpPath:')) {
			def valueString = part.split(":")[1].trim()
			if (valueString) {
				event.ssdpPath = valueString
			}
		}
		else if (part.startsWith('ssdpUSN:')) {
			part -= "ssdpUSN:"
			def valueString = part.trim()
			if (valueString) {
				event.ssdpUSN = valueString
			}
		}
		else if (part.startsWith('ssdpTerm:')) {
			part -= "ssdpTerm:"
			def valueString = part.trim()
			if (valueString) {
				event.ssdpTerm = valueString
			}
		}
	}

	event
}

private Integer convertHexToInt(hex) 
{
	Integer.parseInt(hex,16)
}

private String convertHexToIP(hex) 
{
	[convertHexToInt(hex[0..1]),convertHexToInt(hex[2..3]),convertHexToInt(hex[4..5]),convertHexToInt(hex[6..7])].join(".")
}

private String convertIPtoHex(ipAddress) 
{ 
    String hex = ipAddress.tokenize( '.' ).collect {  String.format( '%02x', it.toInteger() ) }.join()
    trace("IP address entered is $ipAddress and the converted hex code is $hex")
    return hex
}

private String convertPortToHex(port) 
{
	String hexport = port.toString().format( '%04X', port.toInteger() )
    return hexport
}
