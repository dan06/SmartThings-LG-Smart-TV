/**
 *  LG Smart TV Device Type
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
metadata {
	definition (name: "LG Smart TV", namespace: "dpvorster", author: "Daniel Vorster") 
    {
		    capability "TV"
        capability "Music Player"
        capability "Refresh"
        
        attribute "sessionId", "string"    
        
        command "refresh"
	}
    
    preferences {
        input("televisionIp", "string", title:"Television IP Address", description: "Television's IP address", required: true, displayDuringSetup: false)
        input("pairingKey", "string", title:"Pairing Key", description: "Pairing key", required: true, displayDuringSetup: false)
	}

	simulator 
    {
		// TODO: define status and reply messages here
	}

	tiles 
    {
        standardTile("mute", "device.mute", inactiveLabel:false, decoration:"flat") {
            state "default", label:"Mute", icon:"st.custom.sonos.muted", action:"Music Player.mute"
        }
        standardTile("volumeUp", "device.status", inactiveLabel:false, decoration:"flat") {
            state "default", label:'', icon:"st.thermostat.thermostat-up", action:"TV.volumeUp"
        }
        standardTile("volumeDown", "device.status", inactiveLabel:false, decoration:"flat") {
            state "default", label:'', icon:"st.thermostat.thermostat-down", action:"TV.volumeDown"
        }
        standardTile("refresh", "device.status", inactiveLabel:false, decoration:"flat") {
            state "default", icon:"st.secondary.refresh", action:"Refresh.refresh"
        }
        standardTile("channelUp", "device.status", inactiveLabel:false, decoration:"flat") {
            state "default", label:'BBC1', icon:"st.thermostat.thermostat-up", action:"TV.channelUp"
        }
        standardTile("channelDown", "device.status", inactiveLabel:false, decoration:"flat") {
            state "default", label:'C4', icon:"st.thermostat.thermostat-up", action:"TV.channelDown"
        }
        
        main (["mute"])
        
		details(["volumeDown", "volumeUp", "mute", "refresh","channelUp","channelDown"])
	}
}

// parse events into attributes
def parse(String description) 
{
	log.debug "Parsing '${description}'"
    
    if (description == "updated") 
    {
    	sendEvent(name:'refresh', displayed:false)
    }
    else
    {
    	parseHttpResult(description)
    }
	
	// TODO: handle 'volume' attribute
	// TODO: handle 'channel' attribute
	// TODO: handle 'power' attribute
	// TODO: handle 'picture' attribute
	// TODO: handle 'sound' attribute
	// TODO: handle 'movieMode' attribute

}

def channelUp() 
{
	log.debug "Executing 'channelUp' (BBC1)"
	return sendCommand(17)
}

def channelDown() 
{
	log.debug "Executing 'channelDown' (C4)"
	return sendCommand(20)
}


// handle commands
def volumeUp() 
{
	log.debug "Executing 'volumeUp'"
	return sendCommand(2)
}

def volumeDown() 
{
	log.debug "Executing 'volumeDown'"
	return sendCommand(3)
}


def refresh() 
{
    log.debug "Executing 'refresh'"
    return sessionIdCommand()
}

def mute() 
{
	log.debug "Executing 'mute'"   
    return sendCommand(9)
}

def sendCommand(cmd)
{
	def actions = []
    
    if (device.currentValue('sessionId') == null || device.currentValue('sessionId') == "") 
    {
    	actions << sessionIdCommand()
        actions << delayHubAction(500)
    }
    else {
    	actions << tvCommand(cmd)
    }
    actions = actions.flatten()
    return actions
    
}

def sessionIdCommand()
{
    def commandText = "<?xml version=\"1.0\" encoding=\"utf-8\"?><auth><type>AuthReq</type><value>$pairingKey</value></auth>"       
    def httpRequest = [
      	method:		"POST",
        path: 		"/hdcp/api/auth",
        body:		"$commandText",
        headers:	[
        				HOST:			"$televisionIp:8080",
                        "Content-Type":	"application/atom+xml",
                    ]
	]
    
    try 
    {
    	def hubAction = new physicalgraph.device.HubAction(httpRequest)
        log.debug "hub action: $hubAction"
        return hubAction
    }
    catch (Exception e) 
    {
		log.debug "Hit Exception $e on $hubAction"
	}
}

def tvCommand(cmd)
{
	def sessionId = ""
    def commandText = "<?xml version=\"1.0\" encoding=\"utf-8\"?><command><session>${device.currentValue('sessionId')}</session><type>HandleKeyInput</type><value>${cmd}</value></command>"
    def httpRequest = [
      	method:		"POST",
        path: 		"/hdcp/api/dtv_wifirc",
        body:		"$commandText",
        headers:	[
        				HOST:			"$televisionIp:8080",
                        "Content-Type":	"application/atom+xml",
                    ]
	]
    
    try 
    {
    	def hubAction = new physicalgraph.device.HubAction(httpRequest)
        log.debug "hub action: $hubAction"
    	return hubAction
    }
    catch (Exception e) 
    {
		log.debug "Hit Exception $e on $hubAction"
	}
}

private parseHttpResult (output)
{
	def headers = ""
	def parsedHeaders = ""
    
    def msg = parseLanMessage(output)

    def headersAsString = msg.header // => headers as a string
    def headerMap = msg.headers      // => headers as a Map
    def body = msg.body              // => request body as a string
    def status = msg.status          // => http status code of the response
    def json = msg.json              // => any JSON included in response body, as a data structure of lists and maps
    def xml = msg.xml                // => any XML included in response body, as a document tree structure
    def data = msg.data              // => either JSON or XML in response body (whichever is specified by content-type header in response)

	log.debug "headers: $headerMap, status: $status, body: $body, data: $json"
  
    if (status == 200){
    	parseSessionId(body)
    }
    else if (status == 401){
    	log.debug "Unauthorized - clearing session value"
    	sendEvent(name:'sessionId', value:'', displayed:false)
        sendEvent(name:'refresh', displayed:false)
    }
}

def String parseSessionId(bodyString)
{
	def sessionId = ""
	def body = new XmlSlurper().parseText(bodyString)
  	sessionId = body.session.text()

	if (sessionId != null && sessionId != "")
  	{
  		sendEvent(name:'sessionId', value:sessionId, displayed:false)
  		log.debug "session id: $sessionId"
    }
}

private parseHttpHeaders(String headers) 
{
	def lines = headers.readLines()
	def status = lines[0].split()

	def result = [
	  protocol: status[0],
	  status: status[1].toInteger(),
	  reason: status[2]
	]

	if (result.status == 200) {
		log.debug "Authentication successful! : $status"
	}

	return result
}

private def delayHubAction(ms) 
{
    log.debug("delayHubAction(${ms})")
    return new physicalgraph.device.HubAction("delay ${ms}")
}
