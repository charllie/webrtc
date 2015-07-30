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
var speed;
//var bytesToUpload = 2097152;
var bytesToUpload = 209715;
var uploadNb = 3;

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

var chromeExtensionInstalled = false;

if (isChrome) {
	window.addEventListener('message', function(event) {
		if (event.origin != window.location.origin) return;

		// content-script will send a 'SS_PING' msg if extension is installed
		if (event.data.type && (event.data.type === 'SS_PING')) {
			chromeExtensionInstalled = true;
		}

		// user chose a stream
		if (event.data.type && (event.data.type === 'SS_DIALOG_SUCCESS')) {
			constraints.video.mandatory.chromeMediaSourceId = event.data.streamId;
			sendPresentation(null);
		}

		// user clicked on 'cancel' in choose media dialog
		if (event.data.type && (event.data.type === 'SS_DIALOG_CANCEL')) {
			console.log('User cancelled!');
		}
	});
}

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

function disableAllButtons() {
	disableButton('webcam');
	disableButton('screen');
	disableButton('window');
}

function enableAllButtons() {
	enableButton('webcam');
	enableButton('screen');
	enableButton('window');
}

// Constraints
// Chrome screenshare
var chromeConsScreen = {
	audio: false,
	video: {
		mandatory: {
			chromeMediaSource: 'desktop',
			maxWidth: window.screen.width,
			maxHeight: window.screen.height,
		}
	},
	optional: [{googTemporalLayeredScreencast: true}]
};

// Default sharing
var consShare = { audio: false, video: { width: 320, height: 180 } };

// Webcam
var consWebcam = {
	audio: true,
	video: {
		width: {
			min: 160
		},
		height: {
			min: 90
			
		}
	}
};

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
	upload(bytesToUpload, Date.now());
	init();
	
	/*if (isChrome) {
		disableButton('screen');
		disableButton('window');
	}*/
};

function upload(uploadSize) {
	var content = "0".repeat(uploadSize);
	var startTime, endTime;

	speed = 0;

	function beforeSendFunction() {
		startTime = Date.now();
	}

	function successFunction(data) {
		endTime = Date.now();
		speed += (0.008 * uploadSize / (endTime - startTime));
	}

	for (var i = 0; i < uploadNb; i++) {
		$.ajax('https://webrtc.ml/upload', {
			data: {
				content: content
			},
			type: 'POST',
			async: false,
			beforeSend: beforeSendFunction,
			success: successFunction
		});
	}

	speed = speed / uploadNb;
	speed = Math.round(speed * 100) / 100;

	console.log("Upload speedtest: " + speed + "Mbps");

	var consMaxWidth;
	var consMaxHeight;

	if (speed >= 1.2) {
		consMaxWidth = 1280;
		consMaxHeight = 720;
	} else if (1.2 > speed && speed >= 0.5) {
		consMaxWidth = 640;
		consMaxHeight = 480;
	} else if (0.5 > speed) {
		consMaxWidth = 320;
		consMaxHeight = 240;
	}
	
	consWebcam.video.width.max, consWebcam.video.width.ideal = consMaxWidth;
	consWebcam.video.height.max, consWebcam.video.height.ideal = consMaxHeight;
}

function refresh() {
	leaveRoom();
	register();
}

function share(type) {
	if (type != currentButton) {

		if (currentButton != 'webcam')
			stopPresenting();

		if (isChrome) {
			
			if (!chromeExtensionInstalled) {
				var warning = 'Please install the extension:\n' +
								'1. Download the extension at: https://webrtc.ml/extension.crx\n' +
								'2. Go to chrome://extensions\n' +
								'3. Drag the *.crx file on the Google extension page\n';
				alert(warning);
			}

			window.postMessage({ type: 'SS_UI_REQUEST', text: 'start' }, '*');

			constraints = chromeConsScreen;
		} else {
			constraints = consShare;
			constraints.video.mediaSource = type;
		}

		enableAllButtons();
		disableButton(type);

		currentButton = type;

		//refresh();
		var message = {
			id: 'newPresenter',
			name: name,
			room: room,
			mediaSource: currentButton
		};

		sendMessage(message);
	}
}

function webcam() {

	if (currentButton != 'webcam')
		stopPresenting();

	currentButton = 'webcam';
	constraints = consWebcam;

	//refresh();
	enableAllButtons();
	disableButton('webcam');
}

function stopPresenting() {
	var message = {
		id: 'stopPresenting'
	};

	if (participants[name] !== undefined && participants[name].rtcPeerPresentation !== null) {
		participants[name].rtcPeerPresentation.dispose();
		participants[name].rtcPeerPresentation = null;
	}

	enableButton('screen');
	enableButton('window');

	sendMessage(message);
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

		case 'compositeInfo':
			sendComposite(parsedMessage);
			break;

		case 'presentationInfo':
			sendPresentation(parsedMessage);
			break;

		case 'presenterReady':
			onPresenterReady(parsedMessage);
			break;

		case 'cancelPresentation':
			cancelPresentation(parsedMessage);
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

			var rtcPeer = 'participants["' + parsedMessage.name + '"].rtcPeer';
			rtcPeer += (parsedMessage.type == 'webcam') ? 'Composite' : 'Presentation';

			eval(rtcPeer).addIceCandidate(parsedMessage.candidate, function(error) {
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

}

function receiveVideoResponse(result) {

	var rtcPeer = 'participants["' + result.name + '"].rtcPeer';
	rtcPeer += (result.type == 'composite') ? 'Composite' : 'Presentation';

	eval(rtcPeer).processAnswer(result.sdpAnswer, function(error) {
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

function sendComposite(msg) {
	console.log(name + " registered in room " + room);
	var participant = new Participant(name);
	participants[name] = participant;

	var options = {
		//localVideo: video,
		remoteVideo: document.getElementById('remote_video'),
		mediaConstraints: constraints,
		onicecandidate: participant.onIceCandidateComposite.bind(participant)
	};

	participant.rtcPeerComposite = new kurentoUtils.WebRtcPeer.WebRtcPeerSendonly(options,
		function(error) {
			this.generateOffer(participant.offerToReceiveComposite.bind(participant));
		});

	if (msg.existingScreensharer) {
		disableButton('window');
		disableButton('screen');
		receiveVideo(msg.screensharer, true);
	}

}

function sendPresentation(msg) {
	console.log(name + " registered in room " + room);

	if (participants[name] === undefined)
		participants[name] = new Participant(name);

	var participant = participants[name];

	var options = {
		localVideo: document.getElementById('remote_screenshare'),
		mediaConstraints: constraints,
		onicecandidate: participant.onIceCandidatePresentation.bind(participant)
	};

	participant.rtcPeerPresentation = new kurentoUtils.WebRtcPeer.WebRtcPeerSendonly(options,
		function(error) {
			if ((currentButton == 'window' || currentButton == 'screen') && location.protocol === 'http:' && error)
				alert('Please use https to try screenshare.');
			else if ((currentButton == 'window' || currentButton == 'screen') && error && isFirefox)
				alert('You need to enable the appropriate flag:\n - Open about:config and set media.getusermedia.screensharing.enabled to true \n - In about:config, add our address to media.getusermedia.screensharing.allowed_domains (e.g: "webrtc.ml" )');
			if (error) {
				return console.error(error);
			}
			this.generateOffer(participant.offerToReceivePresentation.bind(participant));
		});
}

function cancelPresentation(msg) {
	console.log("Cancelling Presentation");

	if (msg.presenter != name) {
		if (participants[msg.presenter] !== undefined)
			participants[msg.presenter].rtcPeerPresentation.dispose();

		enableButton('screen');
		enableButton('window');
	}
}

function leaveRoom() {
	sendMessage({
		id: 'leaveRoom'
	});

	for (var key in participants) {
		if (participants[key] !== undefined)
			participants[key].dispose();

		delete participants[key];
	}

	document.getElementById('join').style.display = 'block';
	document.getElementById('room').style.display = 'none';

}

function receiveVideo(sender, isScreensharer) {

	if (participants[sender] === undefined)
		participants[sender] = new Participant(sender);

	var participant = participants[sender];

	
	var video = (!isScreensharer) ? 'remote_video' : 'remote_screenshare';
	var suffix = (!isScreensharer) ? 'Composite' : 'Presentation';

	var offerToReceive = 'participant.offerToReceive' + suffix;
	var iceCandidate = 'participant.onIceCandidate' + suffix;
	var rtcPeer = 'participant.rtcPeer' + suffix;

	var options = {
		remoteVideo: document.getElementById(video),
		onicecandidate: eval(iceCandidate).bind(participant)
	};

	var receive = rtcPeer + ' = new kurentoUtils.WebRtcPeer.WebRtcPeerRecvonly(options,' +
		'function(error) { ' +
			'if (error) { return console.error(error); };' +
			'this.generateOffer(' + offerToReceive + '.bind(participant)); })';

	eval(receive);
}

function onPresenterReady(parsedMessage) {
	if (parsedMessage.presenter != name) {
		receiveVideo(parsedMessage.presenter, true);

		disableButton('screen');
		disableButton('window');
	}
}

function onParticipantLeft(request) {
	console.log('Participant ' + request.name + ' left');
	var participant = participants[request.name];

	if (request.isScreensharer) {
		enableButton('screen');
		enableButton('window');

		if (participant !== undefined)
			participant.dispose();
	}

	delete participants[request.name];
}

function sendMessage(message) {
	var jsonMessage = JSON.stringify(message);
	console.log('Senging message: ' + jsonMessage);
	ws.send(jsonMessage);
}

if (!String.prototype.repeat) {
	String.prototype.repeat = function(count) {
		"use strict";
		if (this === null)
			throw new TypeError("ne peut convertir " + this + " en objet");
		var str = "" + this;
		count = +count;
		if (count != count)
			count = 0;
		if (count < 0)
			throw new RangeError("le nombre de répétitions doit être positif");
		if (count === Infinity)
			throw new RangeError("le nombre de répétitions doit être inférieur à l'infini");
		count = Math.floor(count);
		if (str.length === 0 || count === 0)
			return "";
		if (str.length * count >= 1 << 28)
			throw new RangeError("le nombre de répétitions ne doit pas dépasser la taille de chaîne maximale");
		var rpt = "";
		for (;;) {
			if ((count & 1) == 1)
				rpt += str;
			count >>>= 1;
			if (count === 0)
				break;
			str += str;
		}
		return rpt;
	};
}
