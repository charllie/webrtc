/*
 * (C) Copyright 2014 Kurento (http://kurento.org/)
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-2.1.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 */
var ws = new WebSocket('wss://webrtc.ml/groupcall');
var inRoom = false;
var participants = {};
var name;
var currentButton = 'webcam';
var constraints;

if (sessionStorage.reloadAfterPageLoad) {
	sessionStorage.reloadAfterPageLoad = false;
	alert(sessionStorage.getItem("info"));
	sessionStorage.clear();
}

// Detection of the browser
var userAgent = navigator.userAgent.toLowerCase();
var browserM = userAgent.match(/(opera|chrome|safari|firefox|msie)[\/\s]*([\d\.]+)/);
var browser = navigator.appName.toLowerCase();
if (browserM)
	browser = browserM[1];
var isChrome = (browser === "chrome");
var isFirefox = (browser === "firefox");

setInterval(function() {
	// Keep the websocket alive
	sendMessage({ id: 'stay-alive' });
}, 50000);

// Visual
function toggleButton(button) {
	document.getElementById(button).disabled = !document.getElementById(button).disabled;
}

function disableButton(button) {
	document.getElementById(button).disabled = true;
}

function enableButton(button) {
	document.getElementById(button).disabled = false;
}

// Constraints
// Chrome screenshare
var chromeConsScreen = {
	audio: true,
	video: {
		mandatory: {
			chromeMediaSource: 'desktop',
			maxWidth: 320,
			maxHeight: 180,
			minFrameRate: 1,
			maxFrameRate: 5
		}
	},
	optional: [{googTemporalLayeredScreencast: true}]
};

// Default sharing
var consShare = {
	audio: true,
	video: { width: 320, height: 180 }
};

// Webcam
if (isFirefox) {
	var consWebcam = {
		audio: true,
		video: { width: 320, height: 180 }
	};
} else {
	var consWebcam = {
		audio: true,
		video: {
			mandatory: {
				maxWidth: 320,
				maxFrameRate: 15,
				minFrameRate: 15
			}
		}
	};
}

// Initialization function
function init() {
	inRoom = false;
	participants = {};
	name = null;
	currentButton = 'webcam';
	disableButton('webcam');
	enableButton('screen');
	enableButton('window');
	constraints = consWebcam;
}

// Disable screenshare on Chrome (temporary)
window.onload = function() {
	init();
	if (isChrome) {
		toggleButton('screen');
		toggleButton('window');
	}
};

function refresh() {
	leaveRoom();
	register();
}

function share(type) {
	if (type != currentButton) {
		if (isChrome) {
			constraints = chromeConsScreen;
			refresh();
		} else {
			toggleButton(type);
			toggleButton(currentButton);
			currentButton = type;
			constraints = consShare;
			constraints.video.mediaSource = type;
			refresh();
		}
	}
}

function webcam() {
	toggleButton(currentButton);
	currentButton = 'webcam';
	toggleButton(currentButton);
	constraints = consWebcam;
	refresh();
}

function errorReload(msg) {
	sessionStorage.reloadAfterPageLoad = true;
	sessionStorage.setItem("info", msg);
	window.location.reload(true);
}

window.onbeforeunload = function() {
	if (inRoom === true)
		leaveRoom();
	ws.close();
};

ws.onmessage = function(message) {
	var parsedMessage = JSON.parse(message.data);
	console.info('Received message: ' + message.data);

	switch (parsedMessage.id) {
		case 'existingParticipants':
			onExistingParticipants(parsedMessage);
			break;
		case 'newParticipantArrived':
			onNewParticipant(parsedMessage);
			break;
		case 'participantLeft':
			onParticipantLeft(parsedMessage);
			break;
		case 'receiveVideoAnswer':
			receiveVideoResponse(parsedMessage);
			break;
		case 'existingScreensharer':
			errorReload("A user' screen is currently being shared.");
			break;
		case 'existingName':
			errorReload("This username already exists.");
			break;
		case 'iceCandidate':
			participants[parsedMessage.name].rtcPeer.addIceCandidate(parsedMessage.candidate, function(error) {
				if (error) {
					console.error("Error adding candidate: " + error);
					return;
				}
			});
			break;
		default:
			console.error('Unrecognized message', parsedMessage);
	}
};

function register() {
	inRoom = true;
	name = document.getElementById('name').value;
	var room = document.getElementById('roomName').value;

	document.getElementById('room-header').innerText = 'ROOM ' + room;
	document.getElementById('join').style.display = 'none';
	document.getElementById('room').style.display = 'block';

	var message = {
		id: 'joinRoom',
		name: name,
		room: room,
		mediaSource: currentButton
	};

	sendMessage(message);
}

function onNewParticipant(request) {

	participants[request.name] = new Participant(request.name);
	console.log(request.name + " has just arrived");

	// Get the video from the screensharer
	if (request.isScreensharer && currentButton == 'webcam') {
		toggleButton('screen');
		toggleButton('window');
		receiveVideo(request.name, true);
	}

	// The screensharer wants to get composite
	if (!request.isScreensharer && Object.keys(participants).length == 2 && currentButton != 'webcam')
		receiveVideo(request.name, false);
}

function receiveVideoResponse(result) {
	participants[result.name].rtcPeer.processAnswer(result.sdpAnswer, function(error) {
		if (error) return console.error(error);
	});
}

function callResponse(message) {
	if (message.response != 'accepted') {
		console.info('Call not accepted by peer. Closing call');
		stop();
	} else {
		webRtcPeer.processAnswer(message.sdpAnswer, function(error) {
			if (error) return console.error(error);
		});
	}
}

function onExistingParticipants(msg) {
	console.log(name + " registered in room " + room);
	var participant = new Participant(name);
	participants[name] = participant;
	var video = (currentButton == 'webcam') ? 'remote_video' : 'remote_screenshare';

	var options = {
		//localVideo: video,
		mediaConstraints: constraints,
		onicecandidate: participant.onIceCandidate.bind(participant)
	};

	// Triche
	if (currentButton == 'webcam')
		options.remoteVideo = document.getElementById(video);
	else
		options.localVideo = document.getElementById(video);

	participant.rtcPeer = new kurentoUtils.WebRtcPeer.WebRtcPeerSendonly(options,
		function(error) {
			if ((currentButton == 'window' || currentButton == 'screen') && location.protocol === 'http:' && error)
				alert('Please use https to try screenshare.');
			else if ((currentButton == 'window' || currentButton == 'screen') && error)
				//alert('Allow this domain in about:config media.getusermedia.screensharing.allowed_domains')
				alert('You need to enable the appropriate flag:\n - Open about:config and set media.getusermedia.screensharing.enabled to true \n - In about:config, add our address to media.getusermedia.screensharing.allowed_domains (e.g: "webrtc.ml" )');
			if (error) {
				return console.error(error);
			}
			this.generateOffer(participant.offerToReceiveVideo.bind(participant));
		});

	if (currentButton == 'webcam' && msg.existingScreensharer === true && msg.screensharer != name) {
		receiveVideo(msg.screensharer, true);
		toggleButton('screen');
		toggleButton('window');
	}
	else if (currentButton != 'webcam' && msg.data.length > 0) {
		receiveVideo(msg.data[0], false);
	}

}

function leaveRoom() {
	sendMessage({
		id: 'leaveRoom'
	});

	//for (var key in participants) {
	//	participants[key].dispose();
	//}
	if (participants[name] !== null)
		participants[name].dispose();

	document.getElementById('join').style.display = 'block';
	document.getElementById('room').style.display = 'none';

	//ws.close();
}

function receiveVideo(sender, isScreensharer) {
	var participant = new Participant(sender);
	participants[sender] = participant;
	var video = (!isScreensharer) ? 'remote_video' : 'remote_screenshare';

	var options = {
		remoteVideo: document.getElementById(video),
		onicecandidate: participant.onIceCandidate.bind(participant)
	};

	participant.rtcPeer = new kurentoUtils.WebRtcPeer.WebRtcPeerRecvonly(options,
		function(error) {
			if (error) {
				return console.error(error);
			}
			this.generateOffer(participant.offerToReceiveVideo.bind(participant));
		});
}

function onParticipantLeft(request) {
	console.log('Participant ' + request.name + ' left');
	var participant = participants[request.name];

	if (request.isScreensharer) {
		toggleButton('screen');
		toggleButton('window');
	}

	if (participant !== null) {
		participant.dispose();
		if (currentButton != 'webcam' && request.compositeUserNb > 0)
			receiveVideo(request.compositeLeader, false);
	}

	delete participants[request.name];
}

function sendMessage(message) {
	var jsonMessage = JSON.stringify(message);
	console.log('Senging message: ' + jsonMessage);
	ws.send(jsonMessage);
}
