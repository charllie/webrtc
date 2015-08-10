function RoomCtrl($scope, $location, $params, socket, constraints, notifications, participants) {

	if (participants.isEmpty())
		$location.path('/');

	$scope.roomName = $params.roomName;
	$scope.presentation = false;
	$scope.participantNames = [];

	//$scope.participants = participants.getAll();

	socket.get().onmessage = function(message) {

		var parsedMessage = JSON.parse(message.data);
		console.info('Received message: ' + message.data);

		switch (parsedMessage.id) {

			case 'compositeInfo':
				sendStream(parsedMessage, 'composite');
				break;

			case 'presentationInfo':
				sendStream(parsedMessage, 'presentation');
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
				// errorReload("A user' screen is currently being shared.");
				break;
			
			case 'existingName':
				// errorReload("This username already exists.");
				break;
			
			case 'iceCandidate':

				participants.get(parsedMessage.name).rtcPeer[parsedMessage.type].addIceCandidate(parsedMessage.candidate, function(error) {
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

	setInterval(function() {
		socket.send({ id: 'stay-alive' });
	}, 30000);

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

		if (participant !== undefined && participant.rtcPeer['presentation'] !== null) {
			participant.rtcPeer['presentation'].dispose();
			participant.rtcPeer['presentation'] = null;
		}

		constraints.setCurrent('composite');
		socket.send({ id: 'stopPresenting' });
	};

	$scope.share = function(type) {

		var currentType = constraints.getCurrent();
		var noProblem = true;

		if (type != currentType && constraints.canPresent) {

			if (currentType != 'composite')
				this.stopPresenting();

			if (constraints.browserIsChrome) {
			
				if (!constraints.isChromeExtensionInstalled()) {
					var warning = {
						title: 'Chrome extension needed',
						content: 'To enable screensharing or window sharing, please use our extension.'
					};
					
					notifications.confirm(warning.title, warning.content, { cancel: 'Cancel', ok: 'Download'}, function(answer) {
						if (answer === true)
							window.location = '/extension.crx';
					});
					
					noProblem = false;
				}

				window.postMessage({ type: 'SS_UI_REQUEST', text: 'start' }, '*');

			}

			if (noProblem) {

				constraints.setCurrent(type);
				socket.send({
					id: 'newPresenter',
					name: participants.me().name,
					room: this.roomName,
					mediaSource: type
				});

			}
		}
	};

	$scope.canPresent = function() {

		// TODO: test https

		return constraints.canPresent;
	};

	$scope.leave = function() {
		socket.send({ id: 'leaveRoom' });
		constraints.setCurrent('composite');
		participants.clear();
		$location.path('/');
	};

	$scope.$on('$destroy', function() {
		constraints.setCurrent('composite');
		participants.clear();
	});

	function receiveVideo(sender, isScreensharer) {

		if (participants.get(sender) === undefined)
			participants.add(sender);

		var participant = participants.get(sender);
		
		var type = (!isScreensharer) ? 'composite' : 'presentation';

		var options = {
			remoteVideo: document.getElementById(type),
			onicecandidate: participant.onIceCandidate[type].bind(participant)
		};

		participant.rtcPeer[type] = new kurentoUtils.WebRtcPeer.WebRtcPeerRecvonly(options,
			function(error) {
				if (error) {
					return console.error(error);
				}

				this.generateOffer(participant.offerToReceive[type].bind(participant));
			});
	}

	function sendStream(message, type) {

		var participant = participants.me();

		var options = {
			mediaConstraints: constraints.get(),
			onicecandidate: participant.onIceCandidate[type].bind(participant)
		};

		if (type == 'composite') {
			$scope.participantNames = message.data;
			$scope.participantNames.push(participant.name);
			generateTableNames($scope.participantNames);
			options.remoteVideo = document.getElementById(type);
		} else
			options.localVideo = document.getElementById(type);

		participant.rtcPeer[type] = new kurentoUtils.WebRtcPeer.WebRtcPeerSendonly(options,
			function(error) {

				this.generateOffer(participant.offerToReceive[type].bind(participant));
			});

		if (message.existingScreensharer && type == 'composite') {
			enablePresentationClass();
			receiveVideo(message.screensharer, true);
		}

	}

	function onPresenterReady(message) {

		enablePresentationClass();

		if (message.presenter != participants.me().name) {
			receiveVideo(message.presenter, true);
		}
	}

	function cancelPresentation(message) {

		console.log("Cancelling Presentation");

		disablePresentationClass();

		if (message.presenter != participants.me().name) {
			if (participants.get(message.presenter) !== undefined)
				participants.get(message.presenter).rtcPeer['presentation'].dispose();
		}
	}

	function onNewParticipant(request) {

		participants.add(request.name);
		$scope.participantNames.push(request.name);

		generateTableNames($scope.participantNames);

		notifications.notify(request.name + ' has joined the room', 'account-plus');

		console.log(request.name + " has just arrived");

	}

	function onParticipantLeft(request) {

		console.log('Participant ' + request.name + ' left');
		var participant = participants.get(request.name);

		if (request.isScreensharer) {
			disablePresentationClass();

			if (participant !== undefined)
				participant.dispose();
		}

		participants.remove(request.name);

		notifications.notify(request.name + ' has left the room', 'account-remove');

		$scope.participantNames = request.data;
		generateTableNames($scope.participantNames);
	}

	function receiveVideoResponse(result) {

		participants.get(result.name).rtcPeer[result.type].processAnswer(result.sdpAnswer, function(error) {
			if (error) return console.error(error);
		});
	}

	function generateTableNames(array) {

		var i = 0;
		var html = '';

		for (i; i < array.length; i++) {
			if ((i % 2) === 0)
				html += '<tr>';

			html += '<td>' + array[i] + '</td>';
		}

		if ((i % 2) == 1) {
			html += '<td></td></tr>';
		}

		$('.overlay > table').html(html);

	}

	// CSS part
	angular.element(document).ready(function () {
		adaptCompositeContainer();

		$(window).resize(function() {
			adaptCompositeContainer();
		});

		$('#composite').resize(function() {
			adaptCompositeContainer();
		});

		$('video').on('play', function() {
			$(this).addClass('playing');
		});
	});

	function adaptCompositeContainer() {
		$('video').css('max-height', $(window).height() - 90 + 'px');
		$('#composite-container > .overlay > table').css('height', $('#composite').height());
		$('#composite-container > .overlay > table').css('width', $('#composite').width());
	}

	function enablePresentationClass() {
		$scope.presentation = true;
		setWidth('.video-room', null, 'hasPresentation', ['noPresentation']);
	}

	function disablePresentationClass() {
		setWidth('.video-room', null, 'noPresentation', ['hasPresentation', 'bigger', 'smaller']);
		$('#presentation').removeClass('playing');
		$scope.presentation = false;
	}

	function setWidth(elt1, elt2, elt1Class, elt2Classes) {
		if ($scope.presentation) {
			$(elt1).animate({
				opacity: 1
			}, {
				duration: 500,
				start: function() {
					for (var k in elt2Classes) {
						$(elt1).removeClass(elt2Classes[k]);
					}

					$(elt1).addClass(elt1Class);
				},
				progress: adaptCompositeContainer
			});

			$(elt2).removeClass(elt1Class);

			for (var k in elt2Classes)
				$(elt2).addClass(elt2Classes[k]);
		}
	}

	var compositeSizeBig = false;
	var presentationSizeBig = false;

	function setBigs(isCompositeBig, isPresentationBig) {
		compositeSizeBig = isCompositeBig;
		presentationSizeBig = isPresentationBig;
	}

	$scope.changeCompositeSize = function() {
		if (!compositeSizeBig) {
			setWidth('#composite-container', '#presentation-container', 'bigger', ['smaller']);
			setBigs(true, false);
		} else {
			setWidth('#composite-container', null, null, ['bigger']);
			setWidth('#presentation-container', null, null, ['smaller']);
			setBigs(false, false);
		}
	};

	$scope.changePresentationSize = function() {
		if (!presentationSizeBig) {
			setWidth('#composite-container', '#presentation-container', 'smaller', ['bigger']);
			setBigs(false, true);
		}  else {
			setWidth('#presentation-container', null, null, ['bigger']);
			setWidth('#composite-container', null, null, ['smaller']);
			setBigs(false, false);
		}
	};

	// Controls part
	$scope.muted = false;
	$scope.mute = function() {
		$('video').prop('muted', true);
		this.muted = true;
	};

	$scope.unmute = function() {
		$('video').prop('muted', false);
		this.muted = false;
	};
}
