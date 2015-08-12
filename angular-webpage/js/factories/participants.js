app.factory('participants', ['socket', function(socket) {

	var participants = {};
	var name = null;

	var add = function(n) {

		var participant = {
			name: n,
			rtcPeer: {
				presentation: null,
				composite: null
			},

			offer: function(type, error, offerSdp, wp) {
				if (error)
					return console.error("sdp offer error");

				console.log('Invoking SDP offer callback function');

				var msg = {
					id: "receiveVideoFrom",
					sender: n,
					sdpOffer: offerSdp,
					type: type
				};

				socket.send(msg);
			},

			offerToReceive: {
				composite: function(error, offerSdp, wp) {
					this.offer('composite', error, offerSdp, wp);
				},
				presentation: function(error, offerSdp, wp) {
					this.offer('presentation', error, offerSdp, wp);
				}
			},

			iceCandidate: function(type, candidate, wp) {
				console.log("Local candidate" + JSON.stringify(candidate));

				var message = {
					id: 'onIceCandidate',
					candidate: candidate,
					type: type
				};

				socket.send(message);
			},

			onIceCandidate: {
				composite: function(candidate, wp) {
					this.iceCandidate('composite', candidate, wp);
				},

				presentation: function(candidate, wp) {
					this.iceCandidate('presentation', candidate, wp);
				}
			},

			dispose: function() {
				console.log('Disposing participant ' + this.name);

				if (this.rtcPeer['presentation'] !== null && this.rtcPeer['presentation'] !== undefined)
					this.rtcPeer['presentation'].dispose();

				if (this.rtcPeer['composite'] !== null && this.rtcPeer['composite'] !== undefined)
					this.rtcPeer['composite'].dispose();
			},

			getIceCandidate: function(type) {

				if (type != 'composite')
					return this.onIceCandidatePresentation;
				return this.onIceCandidateComposite;
			}
		};

		Object.defineProperty(participant.rtcPeer, 'presentation', {
			writable: true
		});

		Object.defineProperty(participant.rtcPeer, 'composite', {
			writable: true
		});

		participants[n] = participant;

		if (name === null)
			name = n;
	};

	var get = function(name) {
		return participants[name];
	};

	var remove = function(name) {
		delete participants[name];
	};

	var me = function() {
		return participants[name];
	};

	var isEmpty = function() {
		return _.isEmpty(participants);
	};

	var clear = function() {
		for (var key in participants) {
			if (participants[key] !== undefined)
				participants[key].dispose();

			delete participants[key];
		}

		name = null;
	};

	return {
		add: add,
		clear: clear,
		get: get,
		isEmpty: isEmpty,
		me: me,
		remove: remove
	};
}]);