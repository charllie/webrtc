function RoomCtrl($scope, $location, $window, $params, $timeout, socket, constraints, notifications, progress, participants) {

	if (participants.isEmpty())
		$location.path('/');

	socket.roomReady();

	$scope.roomName = $params.roomName;

	$scope.lineAvailable = false;
	$scope.lineExtension = '';

	$scope.presentation = {
		active: false,
		presenterIsMe: false,
		disabled: {
			all: function() {
				this.general = true;
				this.screen = true;
				this.window = true;
				updateScope();
			},
			general: false,
			screen: false,
			window: false,
			none: function() {
				this.general = false;
				this.screen = false;
				this.window = false;
				updateScope();
			}
		}
	};

	$scope.participantNames = [];

	socket.get().onmessage = function(message) {

		var parsedMessage = JSON.parse(message.data);
		console.info('Received message: ' + message.data);

		switch (parsedMessage.id) {

			case 'compositeInfo':
				sendStream(parsedMessage, 'composite');
				break;

			case 'presentationInfo':
				if (constraints.browserIsFirefox)
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

			case 'existingPresentation':

				var warning = {
					title: 'Someone is currently presenting',
					content: 'You cannot present until the current presentation has finished.'
				};

				notifications.alert(warning.title, warning.content, 'Ok', function(answer) {
					// This should be handled by lumx (it must be a bug)
					// May be removed in the future
					$('.dialog-filter').remove();
					$('.dialog').remove();
				});

				$scope.stopPresenting();
				break;

			case 'existingName':

				constraints.setWarning(true);
				$scope.leave();

				break;

			case 'iceCandidate':

				participants.get(parsedMessage.userId).rtcPeer[parsedMessage.type].addIceCandidate(parsedMessage.candidate, function(error) {
					if (error) {
						console.error("Error adding candidate: " + error);
						return;
					}
				});

				break;

			case 'lineAvailable':
				setLineExtension(parsedMessage.extension);
				break;

			case 'callInformation':
				notifications.notify(parsedMessage.message);
				console.log(parsedMessage.message);
				break;

			default:
				console.log('Unrecognized message', parsedMessage);
		}
	};

	$scope.$on('$locationChangeStart', function(event) {
		leave();
	});

	// Configuration for the extension if it is Chrome
	if (constraints.browserIsChrome) {
		$window.addEventListener('message', function(event) {

			// user chose a stream
			if (event.data.type && (event.data.type === 'SS_DIALOG_SUCCESS')) {
				constraints.setId(event.data.streamId);
				sendStream({}, 'presentation');
			}

			// user clicked on 'cancel' in choose media dialog
			if (event.data.type && (event.data.type === 'SS_DIALOG_CANCEL')) {
				$scope.stopPresenting();
			}
		});
	}

	$scope.stopPresenting = function() {

		var participant = participants.me();

		if (participant !== undefined && participant.rtcPeer['presentation'] !== null) {
			participant.rtcPeer['presentation'].dispose();
			participant.rtcPeer['presentation'] = null;
		}

		$scope.presentation.presenterIsMe = false;
		constraints.setType('composite');
		socket.send({ id: 'stopPresenting' });
	};

	$scope.share = function(type) {

		var currentType = constraints.getType();
		var success = true;

		// if there is already a presenter who is not me
		if ($scope.presentation.active && !$scope.presentation.presenterIsMe)
			return;

		// on Chrome, the extension handles window or screen
		if ((type != currentType || constraints.browserIsChrome) && constraints.canPresent) {

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
							$window.location = '/extension.crx';
					});

					success = false;

				} else {
					$window.postMessage({ type: 'SS_UI_REQUEST', text: 'start' }, '*');
				}

			}

			if (success) {

				constraints.setType(type);
				$scope.presentation.presenterIsMe = true;

				socket.send({
					id: 'newPresenter',
					userId: participants.me().userId,
					room: this.roomName,
					mediaSource: type
				});

			}
		}
	};

	$scope.canPresent = function(browser) {

		return (constraints.canPresent && browser == constraints.browser);

	};

	$scope.invite = function(number) {
		socket.send({
			id: 'invite',
			callee: number
		});
	};

	$scope.leave = function() {
		leave();
		$location.path('/');
	};

	function leave() {
		socket.send({ id: 'leaveRoom' });
		constraints.setType('composite');
		participants.clear();
	}

	$scope.$on('$destroy', function() {
		constraints.setType('composite');
		participants.clear();
	});

	function receiveVideo(userId, sender, isScreensharer) {

		if (participants.get(userId) === undefined)
			participants.add(userId, sender);

		if (isScreensharer) {
			progress.circular.show('#2196F3', '#progress');
			$scope.presentation.disabled.all();
		}

		var participant = participants.get(userId);

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

		console.log(participant);

		var options = {
			mediaConstraints: constraints.get(),
			onicecandidate: participant.onIceCandidate[type].bind(participant)
		};

		if (message.lineExtension)
			setLineExtension(message.lineExtension);

		if (type == 'composite') {
			if (!_.isEmpty(message)) {
				$scope.participantNames = message.data;
				$scope.participantNames.push(participant.name);
				updateScope();
			}
			options.remoteVideo = document.getElementById(type);
		} else {
			options.localVideo = document.getElementById(type);
			$scope.presentation.disabled[constraints.getType()] = true;
		}

		participant.rtcPeer[type] = new kurentoUtils.WebRtcPeer.WebRtcPeerSendonly(options,
			function(error) {
				if (error)
					$scope.presentation.presenterIsMe = false;

				if (constraints.browserIsFirefox && error && type != 'composite') {

					var warning = {
						title: 'Firefox needs to be configured (about:config)',
						content: 'Set media.getusermedia.screensharing.enabled to true and add our address to media.getusermedia.screensharing.allowed_domains'
					};

					notifications.alert(warning.title, warning.content, 'Ok', function(answer) {
						// This should be handled by lumx (it must be a bug)
						// May be removed in the future
						$('.dialog-filter').remove();
						$('.dialog').remove();
					});
				}

				this.generateOffer(participant.offerToReceive[type].bind(participant));
			});

		if (message.existingScreensharer && type == 'composite') {
			enablePresentationClass();

			if (message.presenterId != participants.me().userId) {
				receiveVideo(message.presenterId, message.screensharer, true);
			}
		}

	}

	function onPresenterReady(message) {

		enablePresentationClass();

		if (message.userId != participants.me().userId) {
			receiveVideo(message.userId, message.presenter, true);
		}
	}

	function cancelPresentation(message) {

		console.log("Cancelling Presentation");

		disablePresentationClass();

		if (message.userId != participants.me().userId) {
			if (participants.get(message.userId) !== undefined)
				participants.get(message.userId).rtcPeer['presentation'].dispose();
		}
	}

	function onNewParticipant(request) {

		participants.add(request.userId, request.name);
		$scope.participantNames.push(request.name);
		updateScope();

		notifications.notify(request.name + ' has joined the room', 'account-plus');

		console.log(request.name + " has just arrived");

	}

	function onParticipantLeft(request) {

		console.log('Participant ' + request.name + ' left');
		var participant = participants.get(request.userId);

		if (request.isScreensharer) {
			disablePresentationClass();
		}

		if (participant !== undefined)
			participant.dispose();

		participants.remove(request.userId);

		notifications.notify(request.name + ' has left the room', 'account-remove');

		$scope.participantNames = request.data;
		updateScope();
	}

	function receiveVideoResponse(result) {

		participants.get(result.userId).rtcPeer[result.type].processAnswer(result.sdpAnswer, function(error) {
			if (error) return console.error(error);
		});
	}

	function setLineExtension(extension) {
		$scope.lineExtension = extension;
		$scope.lineAvailable = true;
		updateScope();
	}

	// CSS part
	angular.element(document).ready(function () {
		adaptCompositeContainer();

		$(window).resize(function() {
			adaptCompositeContainer();
		});

		$('video').resize(function() {
			adaptCompositeContainer();
		}).on('play', function() {
			$(this).addClass('playing');
		});

		$('#presentation').on('play', function() {
			$(this).addClass('playing');
			progress.circular.hide();
		});
	});

	function adaptCompositeContainer() {
		$('video').css('max-height', $(window).height() - 90 + 'px');
	}

	function enablePresentationClass() {
		$scope.presentation.active = true;
		setWidth('.video-room', null, 'hasPresentation', ['noPresentation']);
	}

	function disablePresentationClass() {
		setWidth('.video-room', null, 'noPresentation', ['hasPresentation', 'bigger', 'smaller']);
		$('#presentation').removeClass('playing');
		$scope.presentation.active = false;
		$scope.presentation.disabled.none();
	}

	function setWidth(elt1, elt2, elt1Class, elt2Classes) {
		if ($scope.presentation.active) {
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

	var sizeBig = {
		composite: false,
		presentation: false
	};

	function setBigs(isCompositeBig, isPresentationBig) {
		sizeBig['composite'] = isCompositeBig;
		sizeBig['presentation'] = isPresentationBig;
	}

	$scope.clicked = {
		composite: false,
		presentation: false
	};

	$scope.cancelClick = {
		composite: false,
		presentation: false
	};

	function clickHandler(id, callback_oneClick, callback_twoClicks) {
		if ($scope.clicked[id]) {
			$scope.cancelClick[id] = true;
			callback_twoClicks();
			return;
		}

		$scope.clicked[id] = true;

		$timeout(function() {
			if ($scope.cancelClick[id]) {
				$scope.cancelClick[id] = false;
				$scope.clicked[id] = false;
				return;
			}

			callback_oneClick();

			$scope.cancelClick[id] = false;
			$scope.clicked[id] = false;

		}, 400);
	}

	function setFullScreen(id) {
		var elem = document.getElementById(id);
		if (elem.requestFullscreen)
			elem.requestFullscreen();
		else if (elem.mozRequestFullScreen)
			elem.mozRequestFullScreen();
		else if (elem.webkitRequestFullscreen)
			elem.webkitRequestFullscreen();
	}

	function setCompositeFullScreen() {
		setFullScreen('composite');
	}

	function setPresentationFullScreen() {
		setFullScreen('presentation');
	}

	function changeCompositeSize() {
		if (!sizeBig['composite']) {
			setWidth('#composite-container', '#presentation-container', 'bigger', ['smaller']);
			setBigs(true, false);
		} else {
			setWidth('#composite-container', null, null, ['bigger']);
			setWidth('#presentation-container', null, null, ['smaller']);
			setBigs(false, false);
		}
	}

	function changePresentationSize() {
		if (!sizeBig['presentation']) {
			setWidth('#composite-container', '#presentation-container', 'smaller', ['bigger']);
			setBigs(false, true);
		}  else {
			setWidth('#presentation-container', null, null, ['bigger']);
			setWidth('#composite-container', null, null, ['smaller']);
			setBigs(false, false);
		}
	}

	$scope.compositeVideoClick = function() {
		clickHandler('composite', changeCompositeSize, setCompositeFullScreen);
	};

	$scope.presentationVideoClick = function() {
		clickHandler('presentation', changePresentationSize, setPresentationFullScreen);
	};

	$scope.toggleSidebar = function() {
		var matrix = $('.sidebar').css('transform');
		if (matrix != 'none') {
			var translation = matrix.match(/-?[\d\.]+/g)[4];
			if (translation == "0") {
				translateSidebar(266);
			} else if (translation == "266") {
				translateSidebar(0);
			}
		}
	};

	function translateSidebar(value) {
		$('.sidebar').css({
			'-webkit-transform' : 'translate(' + value + 'px)',
			'-ms-transform' : 'translate(' + value + 'px)',
			'-moz-transform' : 'translate(' + value + 'px)',
			'transform' : 'translate(' + value + 'px)'
		});
	}

	// Volume part
	$scope.volume = {
		muted: false,
		icon: 'mdi-volume-high',
		change: function() {
			this.muted = !this.muted;
			this.icon = (this.muted) ? 'mdi-volume-off' : 'mdi-volume-high';
			$('#composite').prop('muted', this.muted);
		}
	};

	function updateScope() {
		_.defer(function() {
			$scope.$apply();
		});
	}
}
