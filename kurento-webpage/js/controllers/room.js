function RoomCtrl($scope, $params, socket, constraints, participants) {

	$scope.roomName = $params.roomName;

	socket.get().onmessage = function(message) {

		var parsedMessage = JSON.parse(message.data);
		console.info('Received message: ' + message.data);

		switch (parsedMessage.id) {

			case 'compositeInfo':
				sendStream(parsedMessage, 'composite', participants, constraints);
				break;

			case 'presentationInfo':
				sendStream(parsedMessage, 'presentation', participants, constraints);
				break;

			case 'presenterReady':
				onPresenterReady(parsedMessage, participants);
				break;

			case 'cancelPresentation':
				cancelPresentation(parsedMessage, participants);
				break;
			
			case 'newParticipantArrived':
				onNewParticipant(parsedMessage, participants);
				break;
			
			case 'participantLeft':
				onParticipantLeft(parsedMessage, participants);
				break;
			
			case 'receiveVideoAnswer':
				receiveVideoResponse(parsedMessage, participants);
				break;
			
			case 'existingScreensharer':
				// errorReload("A user' screen is currently being shared.");
				break;
			
			case 'existingName':
				// errorReload("This username already exists.");
				break;
			
			case 'iceCandidate':

				participants.get(parsedMessage.name).getRtcPeer(parsedMessage.type).addIceCandidate(parsedMessage.candidate, function(error) {
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

	// Configuration for the extension if it is Chrome
	if (constraints.browserIsChrome) {
		window.addEventListener('message', function(event) {
			if (event.origin != window.location.origin) return;

			// content-script will send a 'SS_PING' msg if extension is installed
			if (event.data.type && (event.data.type === 'SS_PING')) {
				constraints.chromeExtensionDetected();
			}

			// user chose a stream
			if (event.data.type && (event.data.type === 'SS_DIALOG_SUCCESS')) {
				constraints.setId(event.data.streamId);
				sendPresentation(null);
			}

			// user clicked on 'cancel' in choose media dialog
			if (event.data.type && (event.data.type === 'SS_DIALOG_CANCEL')) {
				console.log('User cancelled!');
			}
		});
	}

	$scope.stopPresenting = function() {

		var participant = participants.me();

		if (participant !== undefined && participants.rtcPeerPresentation !== null) {
			participant.rtcPeerPresentation.dispose();
			participant.rtcPeerPresentation = null;
		}

		//enableButton('screen');
		//enableButton('window');

		constraints.setCurrent('webcam');
		socket.send({ id: 'stopPresenting' });
	};

	$scope.share = function(type) {

		var currentType = constraints.getCurrent();

		if (type != currentType) {

			if (currentType != 'webcam')
				this.stopPresenting();

			if (constraints.browserIsChrome) {
			
				if (!constraints.chromeExtensionDetected) {
					var warning = 'Please install the extension:\n' +
									'1. Download the extension at: https://webrtc.ml/extension.crx\n' +
									'2. Go to chrome://extensions\n' +
									'3. Drag the *.crx file on the Google extension page\n';
					alert(warning);
				}

				window.postMessage({ type: 'SS_UI_REQUEST', text: 'start' }, '*');

			}

			//enableAllButtons();
			//disableButton(type);

			constraints.setCurrent(type);

			//refresh();
			socket.send({
				id: 'newPresenter',
				name: participants.me().name,
				room: this.roomName,
				mediaSource: type
			});
		}
	};

}

function receiveVideo(sender, participants, isScreensharer) {

	if (participants.get(sender) === undefined)
		participants.add(sender);

	var participant = participants.get(sender);
	
	var type = (!isScreensharer) ? 'composite' : 'presentation';

	//var offerToReceive = 'participant.offerToReceive' + suffix;
	//var rtcPeer = 'participant.rtcPeer' + suffix;

	var options = {
		remoteVideo: document.getElementById(type),
		onicecandidate: participant.getIceCandidate(type).bind(participant)
	};

	participant.setRtcPeer(type, new kurentoUtils.WebRtcPeer.WebRtcPeerRecvonly(options,
		function(error) {
			if (error) {
				return console.error(error);
			}

			this.generateOffer(participant.getOffer(type).bind(participant));
		})
	);
}

function sendStream(message, type, participants, constraints) {

	var participant = participants.me();

	var options = {
		//remoteVideo: document.getElementById(type),
		mediaConstraints: constraints.get(),
		onicecandidate: participant.getIceCandidate(type).bind(participant)
	};

	if (type == 'composite')
		options.remoteVideo = document.getElementById(type);
	else
		options.localVideo = document.getElementById(type);

	participant.setRtcPeer(type, new kurentoUtils.WebRtcPeer.WebRtcPeerSendonly(options,
		function(error) {
			this.generateOffer(participant.getOffer(type).bind(participant));
		})
	);

	if (message.existingScreensharer && type == 'composite') {
		//disableButton('window');
		//disableButton('screen');
		receiveVideo(message.screensharer, participants, true);
	}

}

function onPresenterReady(message, participants) {

	if (message.presenter != participants.me().name) {
		receiveVideo(message.presenter, participants, true);

		//disableButton('screen');
		//disableButton('window');
	}
}

function cancelPresentation(message, participants) {

	console.log("Cancelling Presentation");

	if (message.presenter != participants.me().name) {
		if (participants.get(message.presenter) !== undefined)
			participants.get(message.presenter).rtcPeerPresentation.dispose();

		//enableButton('screen');
		//enableButton('window');
	}
}

function onNewParticipant(request, participants) {

	participants.add(request.name);
	console.log(request.name + " has just arrived");

}

function onParticipantLeft(request, participants) {

	console.log('Participant ' + request.name + ' left');
	var participant = participants.get(request.name);

	if (request.isScreensharer) {
		//enableButton('screen');
		//enableButton('window');

		if (participant !== undefined)
			participant.dispose();
	}

	participants.remove(request.name);
}

function receiveVideoResponse(result, participants) {

	console.log(result);

	var participant = participants.get(result.name);
	var rtcPeer = participant.getRtcPeer(result.type);
	
	rtcPeer.processAnswer(result.sdpAnswer, function(error) {
		if (error) return console.error(error);
	});
}

/*function sendPresentation(msg) {

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
}*/
